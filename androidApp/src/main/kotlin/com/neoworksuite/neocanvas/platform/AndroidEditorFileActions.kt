package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import com.neoworksuite.neocanvas.renderer.PngExporter
import com.neoworksuite.neocanvas.renderer.TiffExporter
import com.neoworksuite.neocanvas.renderer.PsdCodec
import com.neoworksuite.neocanvas.renderer.EditableObjectRasterizer
import com.neoworksuite.neocanvas.ui.EditorFileActions
import java.io.File

/** Tablet-safe local storage bridge. V1 keeps files in the app's local documents directory. */
class AndroidEditorFileActions(private val localDirectory: File,
    private val imagePicker: (((Result<com.neoworksuite.neocanvas.ui.ImportedImage?>) -> Unit) -> Unit)? = null,
    private val documentPicker: (((Result<LoadResult?>) -> Unit) -> Unit)? = null,
    private val fileSharer: ((File, String) -> Unit)? = null,
) : EditorFileActions {
    override val supportsPsdExport = true
    override val supportsTiffExport = true
    override val supportsEditableObjectPsdFlattening = true
    override val supportsSaveAs = true
    override val supportsLocalLibrary = true
    private var currentDocumentFile: File? = null
    override fun resetDocumentTarget() { currentDocumentFile = null }
    override fun listLocalDocuments(): List<String> {
        if (!localDirectory.exists()) return emptyList()
        val files = localDirectory.listFiles() ?: error("Unable to read local document folder.")
        return files.filter { it.isFile && it.name.endsWith(".neocanvas", true) &&
            !it.name.endsWith(".recovery.neocanvas", true) }.map { it.name }.sortedBy { it.lowercase() }
    }
    override fun openLocalDocument(name: String): LoadResult {
        if (name != File(name).name || name !in listLocalDocuments()) return LoadResult.Failure("Document not found in local library.")
        val target = File(localDirectory, name)
        val result = documents.load(target.absolutePath)
        if (result is LoadResult.Success) currentDocumentFile = target
        return result
    }
    override fun saveNamedCopy(name: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val clean = name.trim()
        if (!clean.matches(Regex("[\\p{L}\\p{N} _()-]{1,80}")))
            return SaveResult.Failure("Use 1–80 letters, numbers, spaces, hyphens or parentheses.")
        val filename = "$clean.neocanvas"
        if (listLocalDocuments().any { it.equals(filename, ignoreCase = true) })
            return SaveResult.Failure("That name already exists. Choose another name to keep both copies.")
        return saveTo(File(localDirectory, filename), document, tiles)
    }
    override fun renameLocalDocument(name: String, newName: String): SaveResult = mutateLocal(name) { source ->
        val clean = newName.trim()
        if (!clean.matches(Regex("[\\p{L}\\p{N} _()-]{1,80}"))) return@mutateLocal SaveResult.Failure("Use a valid name up to 80 characters.")
        val target = File(localDirectory, "$clean.neocanvas")
        if (target.exists() && !target.name.equals(source.name, true)) return@mutateLocal SaveResult.Failure("That name already exists.")
        if (source.renameTo(target)) { if (currentDocumentFile == source) currentDocumentFile = target; SaveResult.Success }
        else SaveResult.Failure("Could not rename artwork.")
    }
    override fun duplicateLocalDocument(name: String): SaveResult = mutateLocal(name) { source ->
        val base = source.nameWithoutExtension
        var index = 2
        var target = File(localDirectory, "$base copy.neocanvas")
        while (target.exists()) target = File(localDirectory, "$base copy ${index++}.neocanvas")
        source.copyTo(target); SaveResult.Success
    }
    override fun deleteLocalDocument(name: String): SaveResult = mutateLocal(name) { source ->
        val trash = File(localDirectory, ".trash").apply { mkdirs() }
        if (source.renameTo(File(trash, "${System.currentTimeMillis()}-${source.name}"))) SaveResult.Success
        else SaveResult.Failure("Could not move artwork to local trash.")
    }
    override fun localDocumentThumbnail(name: String): ByteArray? = runCatching {
        if (name != File(name).name) return@runCatching null
        com.neoworksuite.neocanvas.core.store.NeoCanvasPackage.readThumbnail(File(localDirectory, name).readBytes())
    }.getOrNull()
    private val galleryStackFile get() = File(localDirectory, "gallery-stack.txt")
    override fun loadGalleryStack(): Set<String> = runCatching {
        if (!galleryStackFile.exists()) emptySet()
        else galleryStackFile.readLines()
            .map(String::trim)
            .filter { it.isNotBlank() && it == File(it).name && it.endsWith(".neocanvas", true) }
            .toCollection(linkedSetOf())
    }.getOrDefault(emptySet())
    override fun saveGalleryStack(members: Set<String>): SaveResult = try {
        localDirectory.mkdirs()
        val safe = members.filter { it.isNotBlank() && it == File(it).name && it.endsWith(".neocanvas", true) }
            .distinct()
            .sortedBy(String::lowercase)
        val temporary = File(localDirectory, "gallery-stack.tmp")
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
        val source = File(localDirectory, name)
        if (!source.isFile) return SaveResult.Failure("Artwork was not found.")
        return try { action(source) } catch (error: Exception) { SaveResult.Failure(error.message ?: "Local artwork operation failed.") }
    }
    private fun saveTo(target: File, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val thumbnail = com.neoworksuite.neocanvas.renderer.GalleryThumbnail.render(document, tiles).encode()
        val result = documents.saveWithThumbnail(target.absolutePath, document, tiles, thumbnail)
        if (result == SaveResult.Success) currentDocumentFile = target
        return result
    }
    override val supportsRecovery = true
    private val recoveryFile get() = File(localDirectory, "recovery/last-session.neocanvas")
    override fun loadRecovery(): LoadResult? = recoveryFile.let { if (it.exists()) documents.load(it.absolutePath) else null }
    override fun saveRecovery(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        documents.save(recoveryFile.absolutePath, document, tiles)
    override fun clearRecovery(): SaveResult = try {
        if (!recoveryFile.exists() || recoveryFile.delete()) SaveResult.Success
        else SaveResult.Failure("Could not retire Android recovery copy.")
    } catch (error: Exception) {
        SaveResult.Failure("Could not retire Android recovery copy: " + (error.message ?: "storage error"))
    }
    override fun importImage(onResult: (Result<com.neoworksuite.neocanvas.ui.ImportedImage?>) -> Unit) {
        imagePicker?.invoke(onResult) ?: onResult(Result.failure(IllegalStateException("Image picker unavailable.")))
    }
    override fun openDocumentFile(onResult: (Result<LoadResult?>) -> Unit) {
        documentPicker?.invoke(onResult) ?: onResult(Result.failure(IllegalStateException("Document picker unavailable.")))
    }
    override fun loadPalette(): List<String> = File(localDirectory, "palette.txt").let {
        if (it.exists()) it.readLines() else emptyList()
    }
    override fun savePalette(colors: List<String>): SaveResult = try {
        localDirectory.mkdirs()
        val target = File(localDirectory, "palette.txt")
        val temporary = File(localDirectory, "palette.tmp")
        temporary.writeText(colors.joinToString("\n"))
        java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        SaveResult.Success
    } catch (error: Exception) { SaveResult.Failure("Could not save palette: ${error.message}") }
    private val documents = AndroidDocumentStore()
    private val documentFile get() = File(localDirectory, "NeoCanvas.neocanvas")
    private fun exportFile(extension: String) = File(localDirectory, "NeoCanvas-export.$extension")

    override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        saveTo(currentDocumentFile ?: File(localDirectory, "Untitled-${java.util.UUID.randomUUID()}.neocanvas"), document, tiles)

    override fun open(): LoadResult = if (documentFile.exists()) openLocalDocument(documentFile.name)
    else LoadResult.Failure("No local NeoCanvas document has been saved yet.")

    override fun exportPng(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val target = exportFile("png")
        val result = PngExporter.export(document, tiles) { bytes -> target.parentFile?.mkdirs(); target.writeBytes(bytes) }
        if (result == SaveResult.Success) fileSharer?.invoke(target, "image/png")
        return result
    }
    override fun exportTiff(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        val target = exportFile("tiff")
        val result = TiffExporter.export(document, tiles) { bytes -> target.parentFile?.mkdirs(); target.writeBytes(bytes) }
        if (result == SaveResult.Success) fileSharer?.invoke(target, "image/tiff")
        return result
    }
    override fun exportPsd(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult = try {
        val target = exportFile("psd")
        val flattened = EditableObjectRasterizer.rasterize(document, tiles)
        target.parentFile?.mkdirs()
        target.writeBytes(PsdCodec.encode(flattened.document, flattened.tiles))
        fileSharer?.invoke(target, "image/vnd.adobe.photoshop")
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export PSD: " + (error.message ?: "unknown output error"))
    }
}
