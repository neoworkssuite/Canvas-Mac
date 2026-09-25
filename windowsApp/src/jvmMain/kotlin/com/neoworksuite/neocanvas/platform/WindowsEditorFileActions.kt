package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import com.neoworksuite.neocanvas.renderer.EditableObjectRasterizer
import com.neoworksuite.neocanvas.renderer.GalleryThumbnail
import com.neoworksuite.neocanvas.renderer.PngExporter
import com.neoworksuite.neocanvas.renderer.PsdCodec
import com.neoworksuite.neocanvas.renderer.TextRasterizer
import com.neoworksuite.neocanvas.renderer.TiffExporter
import com.neoworksuite.neocanvas.core.store.NeoCanvasPackage
import com.neoworksuite.neocanvas.ui.EditorFileActions
import com.neoworksuite.neocanvas.ui.PendingBrushImport
import java.awt.Color
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject

/** Windows-local chooser and file writer. It never leaves the device or retains an account. */
class WindowsEditorFileActions(
    private val documents: com.neoworksuite.neocanvas.core.store.DocumentStore = WindowsDocumentStore(),
    private val appDataRoot: File = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "NeoCanvas",
    ),
    private val fileChooser: ((String, Int, String?) -> String?)? = null,
) : EditorFileActions {
    override val supportsSaveAs = true
    override val supportsLocalLibrary = true
    override val supportsPsdImport = true
    override val supportsPsdExport = true
    override val supportsJpegExport = true
    override val supportsPdfExport = true
    override val supportsTiffExport = true
    override val supportsEditableObjectPsdFlattening = true
    private val libraryDirectory = File(appDataRoot, "Documents")
    private val brushLibraryFile get() = File(libraryDirectory, "brush-library.txt")
    private val preferencesFile get() = File(libraryDirectory, "preferences.txt")
    override fun resetDocumentTarget() { currentDocumentPath = null }
    override fun listLocalDocuments(): List<String> = libraryDirectory.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".neocanvas", true) }
        .sortedByDescending { it.lastModified() }
        .map { it.name }
    override fun openLocalDocument(name: String): LoadResult {
        if (name != File(name).name || name !in listLocalDocuments()) return LoadResult.Failure("Artwork was not found.")
        val target = File(libraryDirectory, name)
        return documents.load(target.absolutePath).also { if (it is LoadResult.Success) currentDocumentPath = target.absolutePath }
    }
    override fun saveNamedCopy(name: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val clean = validArtworkName(name) ?: return SaveResult.Failure("Use 1–80 letters, numbers, spaces, hyphens or parentheses.")
        val target = File(libraryDirectory, "$clean.neocanvas")
        if (listLocalDocuments().any { it.equals(target.name, true) }) return SaveResult.Failure("That name already exists.")
        return saveTo(target.absolutePath, document, tiles)
    }
    override fun renameLocalDocument(name: String, newName: String): SaveResult = mutateLocal(name) { source ->
        val clean = validArtworkName(newName) ?: return@mutateLocal SaveResult.Failure("Use a valid name up to 80 characters.")
        val target = File(libraryDirectory, "$clean.neocanvas")
        if (target.exists() && !target.name.equals(source.name, true)) return@mutateLocal SaveResult.Failure("That name already exists.")
        if (!source.renameTo(target)) SaveResult.Failure("Could not rename artwork.") else {
            if (currentDocumentPath == source.absolutePath) currentDocumentPath = target.absolutePath
            SaveResult.Success
        }
    }
    override fun duplicateLocalDocument(name: String): SaveResult = mutateLocal(name) { source ->
        val base = source.nameWithoutExtension
        var index = 2
        var target = File(libraryDirectory, "$base copy.neocanvas")
        while (target.exists()) target = File(libraryDirectory, "$base copy ${index++}.neocanvas")
        source.copyTo(target); SaveResult.Success
    }
    override fun deleteLocalDocument(name: String): SaveResult = mutateLocal(name) { source ->
        val trash = File(libraryDirectory, ".trash").apply { mkdirs() }
        val target = File(trash, "${System.currentTimeMillis()}-${source.name}")
        if (source.renameTo(target)) SaveResult.Success else SaveResult.Failure("Could not move artwork to local trash.")
    }
    override fun localDocumentThumbnail(name: String): ByteArray? = runCatching {
        if (name != File(name).name) return@runCatching null
        com.neoworksuite.neocanvas.core.store.NeoCanvasPackage.readThumbnail(File(libraryDirectory, name).readBytes())
    }.getOrNull()
    private val galleryStackFile get() = File(libraryDirectory, "gallery-stack.txt")
    override fun loadGalleryStack(): Set<String> = runCatching {
        if (!galleryStackFile.exists()) emptySet()
        else galleryStackFile.readLines()
            .map(String::trim)
            .filter { it.isNotBlank() && it == File(it).name && it.endsWith(".neocanvas", true) }
            .toCollection(linkedSetOf())
    }.getOrDefault(emptySet())
    override fun saveGalleryStack(members: Set<String>): SaveResult = try {
        libraryDirectory.mkdirs()
        val safe = members.filter { it.isNotBlank() && it == File(it).name && it.endsWith(".neocanvas", true) }
            .distinct()
            .sortedBy(String::lowercase)
        val temporary = File(libraryDirectory, "gallery-stack.tmp")
        temporary.writeText(safe.joinToString("\n"))
        java.nio.file.Files.move(
            temporary.toPath(),
            galleryStackFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not save Gallery stack: " + (error.message ?: "storage error"))
    }
    private inline fun mutateLocal(name: String, action: (File) -> SaveResult): SaveResult {
        if (name != File(name).name) return SaveResult.Failure("Invalid artwork name.")
        val source = File(libraryDirectory, name)
        if (!source.isFile) return SaveResult.Failure("Artwork was not found.")
        return try { action(source) } catch (error: Exception) { SaveResult.Failure(error.message ?: "Local artwork operation failed.") }
    }
    private fun validArtworkName(value: String): String? = value.trim().takeIf {
        it.matches(Regex("[\\p{L}\\p{N} _()-]{1,80}"))
    }
    private fun validVersionLabel(value: String): String? = value.trim().takeIf {
        it.matches(Regex("[\\p{L}\\p{N} _()-]{1,60}"))
    }
    private fun validVersionBranch(value: String): String? = value.trim().takeIf {
        it.matches(Regex("[\\p{L}\\p{N} _()-]{1,30}"))
    }
    private fun safeDocumentId(value: String): String = value.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.') character else '_'
    }.joinToString("").take(120).ifEmpty { "document" }
    private fun versionFilename(
        createdAt: Long,
        label: String,
        branch: String,
        parentVersionId: String?,
    ): String {
        val parentToken = parentVersionId
            ?.substringBefore('~')
            ?.substringBefore("__")
            ?.takeIf { it.all(Char::isDigit) }
            ?: "root"
        return createdAt.toString() + "~" + branch + "~" + parentToken + "~" + label + ".neoversion"
    }
    private fun parseVersionEntry(filename: String): com.neoworksuite.neocanvas.ui.LocalVersionEntry? {
        if (!isSafeVersionId(filename)) return null
        val stem = filename.removeSuffix(".neoversion")
        if ('~' in stem) {
            val parts = stem.split('~', limit = 4)
            if (parts.size != 4) return null
            val createdAt = parts[0].toLongOrNull() ?: return null
            val branch = parts[1].takeIf(String::isNotBlank) ?: return null
            val parent = parts[2].takeUnless { it == "root" || it.isBlank() }
            val label = parts[3].takeIf(String::isNotBlank) ?: return null
            return com.neoworksuite.neocanvas.ui.LocalVersionEntry(filename, label, createdAt, branch, parent)
        }
        val split = stem.indexOf("__")
        if (split <= 0 || split >= stem.lastIndex) return null
        val createdAt = stem.substring(0, split).toLongOrNull() ?: return null
        val label = stem.substring(split + 2).takeIf(String::isNotBlank) ?: return null
        return com.neoworksuite.neocanvas.ui.LocalVersionEntry(filename, label, createdAt, "Main", null)
    }
    private fun isSafeVersionId(value: String): Boolean =
        value.isNotBlank() && value == File(value).name && value.endsWith(".neoversion", true)
    override val supportsRecovery = true
    override val supportsVersions = true
    override val supportsVersionBranches = true
    override val supportsWorkbench = true
    override val supportsDeepLayers = true
    private val recoveryFile get() = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "NeoCanvas/recovery/last-session.neocanvas",
    )
    override fun loadRecovery(): LoadResult? = recoveryFile.let { if (it.exists()) documents.load(it.absolutePath) else null }
    override fun saveRecovery(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        documents.save(recoveryFile.absolutePath, document, tiles)
    override fun clearRecovery(): SaveResult = try {
        if (!recoveryFile.exists() || recoveryFile.delete()) SaveResult.Success
        else SaveResult.Failure("Could not retire Windows recovery copy.")
    } catch (error: Exception) {
        SaveResult.Failure("Could not retire Windows recovery copy: " + (error.message ?: "storage error"))
    }

    private val versionsDirectory get() = File(libraryDirectory.parentFile, "Versions")

    override fun listVersions(documentId: String): List<com.neoworksuite.neocanvas.ui.LocalVersionEntry> {
        val directory = File(versionsDirectory, safeDocumentId(documentId))
        return directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".neoversion", true) }
            .mapNotNull { parseVersionEntry(it.name) }
            .sortedByDescending { it.createdAtEpochMillis }
    }

    override fun createVersion(
        label: String,
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult = createVersionOnBranch(label, "Main", null, document, tiles)

    override fun createVersionOnBranch(
        label: String,
        branch: String,
        parentVersionId: String?,
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult = try {
        val clean = validVersionLabel(label)
            ?: return SaveResult.Failure("Use a version name from 1–60 letters, numbers, spaces, hyphens or parentheses.")
        val cleanBranch = validVersionBranch(branch)
            ?: return SaveResult.Failure("Use a branch name from 1–30 letters, numbers, spaces, hyphens or parentheses.")
        val directory = File(versionsDirectory, safeDocumentId(document.id)).apply { mkdirs() }
        var createdAt = System.currentTimeMillis()
        var target = File(directory, versionFilename(createdAt, clean, cleanBranch, parentVersionId))
        while (target.exists()) {
            createdAt++
            target = File(directory, versionFilename(createdAt, clean, cleanBranch, parentVersionId))
        }
        val thumbnail = runCatching { GalleryThumbnail.render(document, tiles).encode() }
            .getOrElse { NeoCanvasPackage.transparentThumbnail() }
        target.writeBytes(NeoCanvasPackage.write(document, tiles, thumbnail))
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not create local version: " + (error.message ?: "unknown error"))
    }

    override fun loadVersion(documentId: String, versionId: String): LoadResult {
        if (!isSafeVersionId(versionId)) return LoadResult.Failure("Invalid local version.")
        val target = File(File(versionsDirectory, safeDocumentId(documentId)), versionId)
        if (!target.isFile) return LoadResult.Failure("Local version was not found.")
        return NeoCanvasPackage.read(target.readBytes())
    }

    override fun deleteVersion(documentId: String, versionId: String): SaveResult {
        if (!isSafeVersionId(versionId)) return SaveResult.Failure("Invalid local version.")
        val target = File(File(versionsDirectory, safeDocumentId(documentId)), versionId)
        if (!target.isFile) return SaveResult.Failure("Local version was not found.")
        return if (target.delete()) SaveResult.Success else SaveResult.Failure("Could not delete local version.")
    }
    private val workbenchDirectory get() = File(libraryDirectory.parentFile, "Workbench")

    override fun loadWorkbench(documentId: String): ByteArray? {
        val file = File(workbenchDirectory, safeDocumentId(documentId) + ".ncworkbench")
        return if (file.isFile) file.readBytes() else null
    }

    override fun saveWorkbench(documentId: String, bytes: ByteArray): SaveResult = try {
        workbenchDirectory.mkdirs()
        File(workbenchDirectory, safeDocumentId(documentId) + ".ncworkbench").writeBytes(bytes)
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not save Workbench: " + (error.message ?: "unknown error"))
    }

    private val deepLayersDirectory get() = File(libraryDirectory.parentFile, "DeepLayers")

    private fun dormantLayerFile(documentId: String, layerId: String): File =
        File(File(deepLayersDirectory, safeDocumentId(documentId)), safeDocumentId(layerId) + ".ncdormant")

    override fun loadDormantLayer(documentId: String, layerId: String): ByteArray? =
        dormantLayerFile(documentId, layerId).takeIf(File::isFile)?.readBytes()

    override fun saveDormantLayer(documentId: String, layerId: String, bytes: ByteArray): SaveResult = try {
        val target = dormantLayerFile(documentId, layerId)
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not hibernate layer: " + (error.message ?: "unknown error"))
    }

    override fun deleteDormantLayer(documentId: String, layerId: String): SaveResult {
        val target = dormantLayerFile(documentId, layerId)
        return if (!target.exists() || target.delete()) SaveResult.Success
        else SaveResult.Failure("Could not remove dormant layer cache.")
    }

    override fun importPsd(onResult: (Result<com.neoworksuite.neocanvas.renderer.PsdImportResult?>) -> Unit) {
        onResult(runCatching {
            val path = choose("Import Photoshop PSD", FileDialog.LOAD, null) ?: return@runCatching null
            require(path.endsWith(".psd", ignoreCase = true)) { "Choose a Photoshop .psd file." }
            PsdCodec.decode(File(path).readBytes())
        })
    }

    override fun importImage(onResult: (Result<com.neoworksuite.neocanvas.ui.ImportedImage?>) -> Unit) {
        onResult(runCatching {
            val path = choose("Import PNG or JPEG image", FileDialog.LOAD, null) ?: return@runCatching null
            javax.imageio.ImageIO.createImageInputStream(File(path)).use { stream ->
                requireNotNull(stream) { "Unable to read image." }
                val readers = javax.imageio.ImageIO.getImageReaders(stream)
                require(readers.hasNext()) { "Choose a PNG or JPEG image." }
                val reader = readers.next()
                try {
                    require(reader.formatName.lowercase() in setOf("png", "jpeg", "jpg")) { "Choose a PNG or JPEG image." }
                    reader.input = stream
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    require(width.toLong() * height <= 16_000_000) { "Image is too large; use an image below 16 megapixels." }
                    val image = reader.read(0)
                    com.neoworksuite.neocanvas.ui.ImportedImage(File(path).nameWithoutExtension, width, height,
                        image.getRGB(0, 0, width, height, null, 0, width))
                } finally { reader.dispose() }
            }
        })
    }
    private val palettePreferences by lazy { java.util.prefs.Preferences.userRoot().node("com/neoworksuite/neocanvas") }
    override fun loadPalette(): List<String> = palettePreferences.get("palette", "").split(',').filter { it.isNotBlank() }
    override fun savePalette(colors: List<String>): SaveResult = try {
        palettePreferences.put("palette", colors.joinToString(","))
        palettePreferences.flush()
        SaveResult.Success
    } catch (error: Exception) { SaveResult.Failure("Could not save palette: ${error.message}") }

    override fun loadBrushLibrary(): ByteArray? = runCatching {
        brushLibraryFile.takeIf(File::isFile)?.readBytes()
    }.getOrNull()

    override fun saveBrushLibrary(bytes: ByteArray): SaveResult = try {
        libraryDirectory.mkdirs()
        brushLibraryFile.writeBytes(bytes)
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not save custom brushes locally on Windows: " + (error.message ?: "storage error"))
    }

    override fun openBrushFile(onResult: (Result<PendingBrushImport?>) -> Unit) {
        onResult(runCatching {
            val path = choose("Import NeoCanvas brush", FileDialog.LOAD, null) ?: return@runCatching null
            val file = File(path)
            require(file.isFile) { "Brush file was not found." }
            require(file.name.endsWith(".neobrush", true) || file.name.endsWith(".neobrushpack", true)) {
                "Choose a NeoCanvas brush or brush pack."
            }
            require(file.length() <= 25L * 1024L * 1024L) { "This brush file is larger than 25 MiB." }
            PendingBrushImport(file.name, file.readBytes())
        })
    }

    override fun shareBrushFile(name: String, bytes: ByteArray): SaveResult = try {
        require(name.endsWith(".neobrush", true) || name.endsWith(".neobrushpack", true)) {
            "Use a NeoCanvas brush filename."
        }
        val safeName = File(name).name
        val path = choose("Export NeoCanvas brush", FileDialog.SAVE, safeName)
            ?: return SaveResult.Failure("Brush export cancelled.")
        val target = File(path.ensureExtension(if (safeName.endsWith(".neobrushpack", true)) ".neobrushpack" else ".neobrush"))
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export brush file: " + (error.message ?: "storage error"))
    }

    override fun loadPreferences(): Map<String, String> = runCatching {
        if (!preferencesFile.isFile) emptyMap()
        else preferencesFile.readLines().mapNotNull { line ->
            val split = line.indexOf('=')
            if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
        }.toMap()
    }.getOrDefault(emptyMap())

    override fun savePreferences(values: Map<String, String>): SaveResult = try {
        libraryDirectory.mkdirs()
        val text = values.entries.sortedBy { it.key }.joinToString("\n") { entry ->
            "${entry.key}=${entry.value}"
        }
        preferencesFile.writeText(text)
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not save NeoCanvas preferences on Windows: " + (error.message ?: "storage error"))
    }

    override fun openExternalUrl(url: String): Boolean = runCatching {
        val target = URI(url)
        if (target.scheme?.lowercase() !in setOf("https", "http")) return@runCatching false
        if (!Desktop.isDesktopSupported()) return@runCatching false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return@runCatching false
        desktop.browse(target)
        true
    }.getOrDefault(false)
    private var currentDocumentPath: String? = null

    override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        libraryDirectory.mkdirs()
        val path = currentDocumentPath ?: if (fileChooser != null) {
            choose("Save NeoCanvas document", FileDialog.SAVE, "Untitled.neocanvas")
                ?: return SaveResult.Failure("Save cancelled.")
        } else File(libraryDirectory, "Untitled-${java.util.UUID.randomUUID()}.neocanvas").absolutePath
        return saveTo(path.ensureExtension(".neocanvas"), document, tiles)
    }
    override fun saveAs(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val suggested = currentDocumentPath?.let { File(it).nameWithoutExtension + "-copy.neocanvas" } ?: "Untitled.neocanvas"
        val path = choose("Save NeoCanvas document as", FileDialog.SAVE, suggested)
            ?: return SaveResult.Failure("Save As cancelled.")
        return saveTo(path.ensureExtension(".neocanvas"), document, tiles)
    }
    private fun saveTo(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val thumbnail = com.neoworksuite.neocanvas.renderer.GalleryThumbnail.render(document, tiles).encode()
        val result = documents.saveWithThumbnail(path, document, tiles, thumbnail)
        if (result == SaveResult.Success) currentDocumentPath = path
        return result
    }

    override fun open(): LoadResult {
        val path = choose("Open NeoCanvas document", FileDialog.LOAD, null) ?: return LoadResult.Failure("Open cancelled.")
        val result = documents.load(path)
        if (result is LoadResult.Success) currentDocumentPath = path
        return result
    }

    override fun exportPng(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val suggested = currentDocumentPath?.let { File(it).nameWithoutExtension + ".png" } ?: "Untitled.png"
        val path = choose("Export PNG", FileDialog.SAVE, suggested) ?: return SaveResult.Failure("Export cancelled.")
        return PngExporter.export(document, tiles, textRasterizer = windowsTextRasterizer) { bytes ->
            File(path.ensureExtension(".png")).writeBytes(bytes)
        }
    }

    override fun exportJpeg(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
        quality: Int,
    ): SaveResult = try {
        val suggested = currentDocumentPath?.let { File(it).nameWithoutExtension + ".jpg" } ?: "Untitled.jpg"
        val path = choose("Export JPEG", FileDialog.SAVE, suggested)
            ?: return SaveResult.Failure("JPEG export cancelled.")
        val flattened = PngExporter.render(document, tiles, textRasterizer = windowsTextRasterizer)
        val image = BufferedImage(flattened.width, flattened.height, BufferedImage.TYPE_INT_RGB)
        val pixels = IntArray(flattened.width * flattened.height)
        var source = 0
        for (index in pixels.indices) {
            val red = flattened.rgba[source].toInt() and 255
            val green = flattened.rgba[source + 1].toInt() and 255
            val blue = flattened.rgba[source + 2].toInt() and 255
            val alpha = flattened.rgba[source + 3].toInt() and 255
            val inverse = 255 - alpha
            val opaqueRed = (red * alpha + 255 * inverse + 127) / 255
            val opaqueGreen = (green * alpha + 255 * inverse + 127) / 255
            val opaqueBlue = (blue * alpha + 255 * inverse + 127) / 255
            pixels[index] = (opaqueRed shl 16) or (opaqueGreen shl 8) or opaqueBlue
            source += 4
        }
        image.setRGB(0, 0, flattened.width, flattened.height, pixels, 0, flattened.width)

        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull()
            ?: return SaveResult.Failure("No Windows JPEG encoder is available.")
        try {
            FileImageOutputStream(File(path.ensureExtension(".jpg"))).use { output ->
                writer.output = output
                val params = writer.defaultWriteParam
                if (params.canWriteCompressed()) {
                    params.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                    params.compressionQuality = quality.coerceIn(1, 100) / 100f
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export JPEG: " + (error.message ?: "unknown output error"))
    }

    override fun exportPdf(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult = try {
        val suggested = currentDocumentPath?.let { File(it).nameWithoutExtension + ".pdf" } ?: "Untitled.pdf"
        val path = choose("Export PDF", FileDialog.SAVE, suggested)
            ?: return SaveResult.Failure("PDF export cancelled.")
        val flattened = PngExporter.render(document, tiles, textRasterizer = windowsTextRasterizer)
        val target = File(path.ensureExtension(".pdf"))
        PDDocument().use { pdf ->
            val page = PDPage(PDRectangle(flattened.width.toFloat(), flattened.height.toFloat()))
            pdf.addPage(page)
            val image = PDImageXObject.createFromByteArray(pdf, flattened.encode(), "NeoCanvas")
            PDPageContentStream(pdf, page).use { content ->
                content.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
            }
            pdf.save(target)
        }
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export PDF: " + (error.message ?: "unknown output error"))
    }

    override fun exportTiff(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val suggested = currentDocumentPath?.let { File(it).nameWithoutExtension + ".tiff" } ?: "Untitled.tiff"
        val path = choose("Export TIFF", FileDialog.SAVE, suggested)
            ?: return SaveResult.Failure("TIFF export cancelled.")
        return TiffExporter.export(document, tiles, textRasterizer = windowsTextRasterizer) { bytes ->
            File(path.ensureExtension(".tiff")).writeBytes(bytes)
        }
    }

    override fun exportPsd(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult = try {
        val suggested = currentDocumentPath?.let { File(it).nameWithoutExtension + ".psd" } ?: "Untitled.psd"
        val path = choose("Export layered Photoshop PSD", FileDialog.SAVE, suggested)
            ?: return SaveResult.Failure("PSD export cancelled.")
        val flattened = EditableObjectRasterizer.rasterize(
            document,
            tiles,
            textRasterizer = windowsTextRasterizer,
        )
        File(path.ensureExtension(".psd")).writeBytes(PsdCodec.encode(flattened.document, flattened.tiles))
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export PSD: " + (error.message ?: "unknown output error"))
    }

    private fun choose(title: String, mode: Int, suggested: String?): String? {
        fileChooser?.let { return it(title, mode, suggested) }
        val owner = Frame()
        return try {
            FileDialog(owner, title, mode).apply { file = suggested; isVisible = true }.let { dialog ->
                dialog.file?.let { File(dialog.directory, it).absolutePath }
            }
        } finally { owner.dispose() }
    }
    private fun String.ensureExtension(extension: String): String = if (endsWith(extension, ignoreCase = true)) this else this + extension
}


private val windowsTextRasterizer = TextRasterizer { text, outputWidth, outputHeight ->
    val image = BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val style = (if (text.bold) Font.BOLD else Font.PLAIN) or (if (text.italic) Font.ITALIC else Font.PLAIN)
        var font = windowsExportFont(text.fontFamily, style, text.fontSize)
        if (text.tracking != 0f) {
            val trackingEm = (text.tracking / text.fontSize).coerceIn(-0.2f, 1f)
            font = font.deriveFont(mapOf(TextAttribute.TRACKING to trackingEm))
        }
        graphics.font = font
        graphics.color = Color(text.colorArgb, true)

        val centerX = text.x + text.width / 2f
        val centerY = text.y + text.height / 2f
        graphics.rotate(Math.toRadians(text.rotationDegrees.toDouble()), centerX.toDouble(), centerY.toDouble())
        graphics.clip(Rectangle2D.Float(text.x, text.y, text.width, text.height))

        val metrics = graphics.fontMetrics
        val renderedText = if (text.uppercase) text.text.uppercase() else text.text
        val lines = wrapWindowsText(renderedText, metrics, text.width)
        val lineHeight = text.fontSize * text.lineSpacing
        var baseline = text.y + text.fontSize + text.baselineOffset
        for (line in lines) {
            if (baseline - text.fontSize > text.y + text.height) break
            val lineWidth = metrics.stringWidth(line).toFloat()
            val drawX = when (text.alignment) {
                com.neoworksuite.neocanvas.core.model.TextAlignment.Left -> text.x
                com.neoworksuite.neocanvas.core.model.TextAlignment.Center -> text.x + (text.width - lineWidth) / 2f
                com.neoworksuite.neocanvas.core.model.TextAlignment.Right -> text.x + text.width - lineWidth
            }
            graphics.drawString(line, drawX, baseline)
            if (text.underline && line.isNotEmpty()) {
                val underlineY = baseline + maxOf(1f, text.fontSize * .08f)
                graphics.drawLine(
                    drawX.toInt(),
                    underlineY.toInt(),
                    (drawX + lineWidth).toInt(),
                    underlineY.toInt(),
                )
            }
            baseline += lineHeight
        }
    } finally {
        graphics.dispose()
    }

    val pixels = IntArray(outputWidth * outputHeight)
    image.getRGB(0, 0, outputWidth, outputHeight, pixels, 0, outputWidth)
    ByteArray(pixels.size * 4).also { rgba ->
        pixels.forEachIndexed { index, argb ->
            val offset = index * 4
            rgba[offset] = (argb ushr 16).toByte()
            rgba[offset + 1] = (argb ushr 8).toByte()
            rgba[offset + 2] = argb.toByte()
            rgba[offset + 3] = (argb ushr 24).toByte()
        }
    }
}



private val bundledWindowsFontFiles = mapOf(
    "inter" to "inter_variable.ttf",
    "noto sans" to "noto_sans_variable.ttf",
    "lora" to "lora_variable.ttf",
    "playfair display" to "playfair_display_variable.ttf",
    "caveat" to "caveat_variable.ttf",
    "jetbrains mono" to "jetbrains_mono_variable.ttf",
)

private val bundledWindowsFontCache = mutableMapOf<String, Font>()

internal fun windowsExportFont(familyName: String, style: Int, size: Float): Font {
    val key = familyName.trim().lowercase()
    val systemFamily = when (key) {
        "sans", "sans-serif", "sans serif", "system" -> Font.SANS_SERIF
        "serif" -> Font.SERIF
        "mono", "monospace" -> Font.MONOSPACED
        else -> null
    }
    if (systemFamily != null) return Font(systemFamily, style, size.coerceAtLeast(1f).toInt())

    val bundledFile = bundledWindowsFontFiles[key]
    if (bundledFile != null) {
        val base = synchronized(bundledWindowsFontCache) {
            bundledWindowsFontCache[key] ?: loadBundledWindowsFont(bundledFile)?.also {
                bundledWindowsFontCache[key] = it
            }
        }
        if (base != null) return base.deriveFont(style, size.coerceAtLeast(1f))
    }

    return Font(familyName.ifBlank { Font.SANS_SERIF }, style, size.coerceAtLeast(1f).toInt())
}

private fun loadBundledWindowsFont(fileName: String): Font? {
    val loader = WindowsEditorFileActions::class.java.classLoader
    val candidates = listOf(
        "composeResources/com.neoworksuite.neocanvas.ui.resources/font/$fileName",
        "composeResources/com/neoworksuite/neocanvas/ui/resources/font/$fileName",
        "font/$fileName",
    )
    for (path in candidates) {
        val stream = loader.getResourceAsStream(path) ?: continue
        stream.use {
            return runCatching { Font.createFont(Font.TRUETYPE_FONT, it) }.getOrNull()
        }
    }
    return null
}

private fun wrapWindowsText(value: String, metrics: java.awt.FontMetrics, maxWidth: Float): List<String> {
    if (value.isEmpty()) return listOf("")
    val output = mutableListOf<String>()
    value.split('\n').forEach { paragraph ->
        if (paragraph.isEmpty()) {
            output += ""
            return@forEach
        }
        var current = ""
        paragraph.split(Regex("\\s+")).filter(String::isNotEmpty).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                output += current
                current = word
            } else {
                current = candidate
            }
        }
        output += current
    }
    return output
}
