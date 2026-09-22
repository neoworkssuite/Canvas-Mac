package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult

/** Host bridge for explicit, local-only file actions. UI code never selects paths or uses a network. */
interface EditorFileActions {
    val supportsLocalLibrary: Boolean get() = false
    fun listLocalDocuments(): List<String> = emptyList()
    fun openLocalDocument(name: String): LoadResult = LoadResult.Failure("Local library unavailable.")
    fun saveNamedCopy(name: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("Local library unavailable.")
    fun renameLocalDocument(name: String, newName: String): SaveResult = SaveResult.Failure("Local library unavailable.")
    fun duplicateLocalDocument(name: String): SaveResult = SaveResult.Failure("Local library unavailable.")
    fun deleteLocalDocument(name: String): SaveResult = SaveResult.Failure("Local library unavailable.")
    fun localDocumentThumbnail(name: String): ByteArray? = null
    fun loadGalleryStack(): Set<String> = emptySet()
    fun saveGalleryStack(members: Set<String>): SaveResult =
        SaveResult.Failure("Gallery stack storage is unavailable in this host.")
    val supportsSaveAs: Boolean get() = false
    fun resetDocumentTarget() {}
    fun saveAs(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("Save As is unavailable in this host.")
    val supportsRecovery: Boolean get() = false
    fun loadRecovery(): LoadResult? = null
    fun clearRecovery(): SaveResult = SaveResult.Success

    val supportsVersions: Boolean get() = false
    val supportsVersionBranches: Boolean get() = false
    fun listVersions(documentId: String): List<LocalVersionEntry> = emptyList()
    fun createVersion(
        label: String,
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult = SaveResult.Failure("Local version history is unavailable in this host.")

    fun createVersionOnBranch(
        label: String,
        branch: String,
        parentVersionId: String?,
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): SaveResult = createVersion(label, document, tiles)
    fun loadVersion(documentId: String, versionId: String): LoadResult =
        LoadResult.Failure("Local version history is unavailable in this host.")
    fun deleteVersion(documentId: String, versionId: String): SaveResult =
        SaveResult.Failure("Local version history is unavailable in this host.")

    val supportsWorkbench: Boolean get() = false
    fun loadWorkbench(documentId: String): ByteArray? = null
    fun saveWorkbench(documentId: String, bytes: ByteArray): SaveResult =
        SaveResult.Failure("Workbench storage is unavailable in this host.")

    val supportsDeepLayers: Boolean get() = false
    fun loadDormantLayer(documentId: String, layerId: String): ByteArray? = null
    fun saveDormantLayer(documentId: String, layerId: String, bytes: ByteArray): SaveResult =
        SaveResult.Failure("Deep Layers storage is unavailable in this host.")
    fun deleteDormantLayer(documentId: String, layerId: String): SaveResult =
        SaveResult.Failure("Deep Layers storage is unavailable in this host.")

    fun saveRecovery(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("Recovery storage is unavailable.")
    fun importImage(onResult: (Result<ImportedImage?>) -> Unit) {
        onResult(Result.failure(IllegalStateException("Image import is unavailable in this host.")))
    }
    val supportsPsdImport: Boolean get() = false
    val supportsPsdExport: Boolean get() = false
    val supportsJpegExport: Boolean get() = false
    val supportsPdfExport: Boolean get() = false
    val supportsTiffExport: Boolean get() = false
    val supportsEditableObjectPsdFlattening: Boolean get() = false
    fun importPsd(onResult: (Result<com.neoworksuite.neocanvas.renderer.PsdImportResult?>) -> Unit) {
        onResult(Result.failure(IllegalStateException("PSD import is unavailable in this host.")))
    }
    fun exportPsd(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("PSD export is unavailable in this host.")
    fun exportJpeg(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
        quality: Int = 92,
    ): SaveResult = SaveResult.Failure("JPEG export is unavailable in this host.")
    fun exportPdf(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("PDF export is unavailable in this host.")
    fun exportTiff(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("TIFF export is unavailable in this host.")
    fun loadPalette(): List<String> = emptyList()
    fun savePalette(colors: List<String>): SaveResult = SaveResult.Failure("Palette storage is unavailable in this host.")
    fun loadBrushLibrary(): ByteArray? = null
    fun saveBrushLibrary(bytes: ByteArray): SaveResult = SaveResult.Failure("Brush library storage is unavailable in this host.")
    fun openExternalUrl(url: String): Boolean = false
    val supportsUpdateChecks: Boolean get() = false
    fun checkForUpdate(onResult: (Result<AppUpdateInfo?>) -> Unit) {
        onResult(Result.success(null))
    }
    fun loadPreferences(): Map<String, String> = emptyMap()
    fun savePreferences(values: Map<String, String>): SaveResult =
        SaveResult.Failure("Preference storage is unavailable in this host.")
    fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult
    fun open(): LoadResult
    fun exportPng(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult
}

data class LocalVersionEntry(
    val id: String,
    val label: String,
    val createdAtEpochMillis: Long,
    val branch: String = "Main",
    val parentId: String? = null,
)

data class ImportedImage(val name: String, val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height <= 16_000_000)
        require(argb.size.toLong() == width.toLong() * height)
    }
}

object UnavailableEditorFileActions : EditorFileActions {
    private const val MESSAGE = "Local file access is unavailable in this host."
    override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>) = SaveResult.Failure(MESSAGE)
    override fun open() = LoadResult.Failure(MESSAGE)
    override fun exportPng(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>) = SaveResult.Failure(MESSAGE)
}
