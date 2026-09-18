package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import com.neoworksuite.neocanvas.renderer.PngExporter
import com.neoworksuite.neocanvas.ui.EditorFileActions
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Windows-local chooser and file writer. It never leaves the device or retains an account. */
class WindowsEditorFileActions(
    private val documents: com.neoworksuite.neocanvas.core.store.DocumentStore = WindowsDocumentStore(),
    private val fileChooser: ((String, Int, String?) -> String?)? = null,
) : EditorFileActions {
    override val supportsSaveAs = true
    override val supportsLocalLibrary = true
    private val libraryDirectory = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "NeoCanvas/Documents",
    )
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
    private inline fun mutateLocal(name: String, action: (File) -> SaveResult): SaveResult {
        if (name != File(name).name) return SaveResult.Failure("Invalid artwork name.")
        val source = File(libraryDirectory, name)
        if (!source.isFile) return SaveResult.Failure("Artwork was not found.")
        return try { action(source) } catch (error: Exception) { SaveResult.Failure(error.message ?: "Local artwork operation failed.") }
    }
    private fun validArtworkName(value: String): String? = value.trim().takeIf {
        it.matches(Regex("[\\p{L}\\p{N} _()-]{1,80}"))
    }
    override val supportsRecovery = true
    private val recoveryFile get() = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "NeoCanvas/recovery/last-session.neocanvas",
    )
    override fun loadRecovery(): LoadResult? = recoveryFile.let { if (it.exists()) documents.load(it.absolutePath) else null }
    override fun saveRecovery(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        documents.save(recoveryFile.absolutePath, document, tiles)
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
        return PngExporter.export(document, tiles) { bytes -> File(path.ensureExtension(".png")).writeBytes(bytes) }
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
