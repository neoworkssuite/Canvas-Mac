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
    val supportsSaveAs: Boolean get() = false
    fun resetDocumentTarget() {}
    fun saveAs(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("Save As is unavailable in this host.")
    val supportsRecovery: Boolean get() = false
    fun loadRecovery(): LoadResult? = null
    fun saveRecovery(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        SaveResult.Failure("Recovery storage is unavailable.")
    fun importImage(onResult: (Result<ImportedImage?>) -> Unit) {
        onResult(Result.failure(IllegalStateException("Image import is unavailable in this host.")))
    }
    fun loadPalette(): List<String> = emptyList()
    fun savePalette(colors: List<String>): SaveResult = SaveResult.Failure("Palette storage is unavailable in this host.")
    fun openExternalUrl(url: String): Boolean = false
    fun loadPreferences(): Map<String, String> = emptyMap()
    fun savePreferences(values: Map<String, String>): SaveResult =
        SaveResult.Failure("Preference storage is unavailable in this host.")
    fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult
    fun open(): LoadResult
    fun exportPng(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult
}

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
