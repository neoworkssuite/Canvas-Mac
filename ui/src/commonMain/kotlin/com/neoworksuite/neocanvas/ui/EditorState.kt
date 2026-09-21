package com.neoworksuite.neocanvas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.neoworksuite.neocanvas.brushes.BrushDefinition
import com.neoworksuite.neocanvas.brushes.BuiltInBrushes
import com.neoworksuite.neocanvas.core.model.AddRasterLayer
import com.neoworksuite.neocanvas.core.model.ApplyRasterPatch
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DeleteLayer
import com.neoworksuite.neocanvas.core.model.CropCanvas
import com.neoworksuite.neocanvas.core.model.DocumentCommand
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.DuplicateLayer
import com.neoworksuite.neocanvas.core.model.MoveLayer
import com.neoworksuite.neocanvas.core.model.RenameLayer
import com.neoworksuite.neocanvas.core.model.SetLayerOpacity
import com.neoworksuite.neocanvas.core.model.SetLayerVisibility
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.SetLayerAlphaLocked
import com.neoworksuite.neocanvas.core.model.SetLayerBlendMode
import com.neoworksuite.neocanvas.core.model.SetLayerClipping
import com.neoworksuite.neocanvas.core.model.MergeRasterLayerDown
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import com.neoworksuite.neocanvas.renderer.RasterColor
import com.neoworksuite.neocanvas.renderer.RasterPoint
import com.neoworksuite.neocanvas.renderer.Rasterizer
import com.neoworksuite.neocanvas.renderer.RasterSelection
import com.neoworksuite.neocanvas.renderer.TileKey
import com.neoworksuite.neocanvas.renderer.TileStore

enum class Tool { Brush, Eraser, Smudge, Pan, Fill, Eyedropper, Select, MoveSelection }
enum class InspectorPanel { Layers, Brushes, Colors, Effects }
enum class PendingDocumentAction { New, Open, Close }
data class DrawPoint(val x: Float, val y: Float, val pressure: Float = 1f)

/**
 * Shared editor coordinator. Pixels live in [tileStore], while every visible document mutation
 * is recorded in [history]. Tile snapshots pair with history entries so undo/redo restores both
 * metadata and raster pixels without exposing renderer types to the core module.
 */
class EditorState(
    val history: DocumentHistory,
    private val fileActions: EditorFileActions = UnavailableEditorFileActions,
    val tileStore: TileStore = TileStore(),
) {
    private var documentRevision by mutableIntStateOf(0)
    private var editVersion by mutableIntStateOf(0)
    private var savedVersion by mutableIntStateOf(0)
    private var nextVersion = 0
    private val undoVersions = mutableListOf<Int>()
    private val redoVersions = mutableListOf<Int>()
    val hasUnsavedChanges: Boolean get() = editVersion != savedVersion
    val supportsSaveAs: Boolean get() = fileActions.supportsSaveAs
    val supportsLocalLibrary: Boolean get() = fileActions.supportsLocalLibrary
    val supportsVersions: Boolean get() = fileActions.supportsVersions

    var versionsVisible by mutableStateOf(false)
        private set
    var versions: List<LocalVersionEntry> by mutableStateOf(emptyList())
        private set
    var versionError: String? by mutableStateOf(null)
        private set

    fun openVersions() {
        if (!supportsVersions) {
            statusMessage = "Local version history is unavailable on this device"
            return
        }
        if (inspectorVisible) hideInspector()
        settingsVisible = false
        refreshVersions()
        versionsVisible = true
    }

    fun closeVersions() {
        versionsVisible = false
        versionError = null
    }

    fun createVersion(label: String): Boolean {
        if (!supportsVersions) return false
        val clean = label.trim()
        if (clean.isEmpty()) {
            versionError = "Give this milestone a name."
            return false
        }
        val result = try {
            fileActions.createVersion(clean, document, tilesForDocument())
        } catch (error: Exception) {
            SaveResult.Failure(error.message ?: "Could not create local version")
        }
        return if (result == SaveResult.Success) {
            versionError = null
            refreshVersions()
            statusMessage = "Saved version: $clean"
            true
        } else {
            versionError = (result as? SaveResult.Failure)?.message ?: "Could not create local version"
            false
        }
    }

    fun restoreVersion(versionId: String): Boolean {
        if (!supportsVersions || versions.none { it.id == versionId }) return false
        val safety = try {
            fileActions.createVersion("Before restore", document, tilesForDocument())
        } catch (error: Exception) {
            SaveResult.Failure(error.message ?: "Could not create safety version")
        }
        if (safety != SaveResult.Success) {
            versionError = (safety as? SaveResult.Failure)?.message
                ?: "Could not create the safety version, so restore was cancelled."
            return false
        }

        val result = try {
            fileActions.loadVersion(document.id, versionId)
        } catch (error: Exception) {
            LoadResult.Failure(error.message ?: "Could not load local version")
        }
        return when (result) {
            is LoadResult.Success -> {
                clearSelection()
                resetView()
                history.reset(result.document)
                tileStore.restore(result.tiles)
                undoTileStates.clear()
                redoTileStates.clear()
                markCleanDocument()
                editVersion = ++nextVersion
                activeLayerId = document.layers.lastOrNull()?.id
                documentRevision++
                versionError = null
                refreshVersions()
                statusMessage = "Restored local version — current work was kept as Before restore"
                true
            }
            is LoadResult.Failure -> {
                versionError = result.message
                false
            }
            is LoadResult.Corrupt -> {
                versionError = result.message
                false
            }
            is LoadResult.Incompatible -> {
                versionError = result.message
                false
            }
        }
    }

    fun deleteVersion(versionId: String): Boolean {
        if (!supportsVersions || versions.none { it.id == versionId }) return false
        val result = try {
            fileActions.deleteVersion(document.id, versionId)
        } catch (error: Exception) {
            SaveResult.Failure(error.message ?: "Could not delete local version")
        }
        return if (result == SaveResult.Success) {
            versionError = null
            refreshVersions()
            statusMessage = "Deleted local version"
            true
        } else {
            versionError = (result as? SaveResult.Failure)?.message ?: "Could not delete local version"
            false
        }
    }

    private fun refreshVersions() {
        versions = try {
            fileActions.listVersions(document.id).sortedByDescending { it.createdAtEpochMillis }
        } catch (error: Exception) {
            versionError = error.message ?: "Could not read local versions"
            emptyList()
        }
    }
    var localDocuments: List<String>? by mutableStateOf(null)
        private set
    var namingLocalCopy by mutableStateOf(false)
        private set
    var libraryError: String? by mutableStateOf(null)
        private set
    fun closeLocalLibrary() { localDocuments = null; namingLocalCopy = false; libraryError = null }
    fun saveNamedCopy(name: String) {
        if (!namingLocalCopy) return
        val result = try { fileActions.saveNamedCopy(name, document, tilesForDocument()) }
            catch (error: Exception) { SaveResult.Failure(error.message ?: "Unable to save copy") }
        if (result == SaveResult.Success) {
            savedVersion = editVersion
            closeLocalLibrary()
            statusMessage = "Saved local copy: ${name.trim()}"
        } else if (result is SaveResult.Failure) libraryError = result.message
    }
    fun openLocalDocument(name: String) {
        if (name !in localDocuments.orEmpty()) return
        val result = try { fileActions.openLocalDocument(name) }
            catch (error: Exception) { LoadResult.Failure(error.message ?: "Unable to open document") }
        acceptOpenResult(result)
        if (result is LoadResult.Success) closeLocalLibrary() else libraryError = statusMessage
    }
    fun openFromGallery(name: String): Boolean {
        val result = try { fileActions.openLocalDocument(name) }
            catch (error: Exception) { LoadResult.Failure(error.message ?: "Unable to open document") }
        acceptOpenResult(result)
        return result is LoadResult.Success
    }
    fun importDocument(): Boolean {
        val result = try { fileActions.open() }
            catch (error: Exception) { LoadResult.Failure(error.message ?: "Unable to import document") }
        acceptOpenResult(result)
        return result is LoadResult.Success
    }

    val supportsPsdImport: Boolean get() = fileActions.supportsPsdImport
    val supportsPsdExport: Boolean get() = fileActions.supportsPsdExport

    fun importPsd() {
        val targetDocument = document.id
        fileActions.importPsd { result ->
            result.fold(
                onSuccess = { imported ->
                    if (imported != null && document.id == targetDocument) acceptPsdImport(imported)
                },
                onFailure = { error ->
                    statusMessage = "Could not import PSD: " + (error.message ?: "unknown PSD error")
                },
            )
        }
    }

    fun exportPsd(): Boolean {
        val result = try { fileActions.exportPsd(document, tilesForDocument()) }
            catch (error: Exception) { SaveResult.Failure(error.message ?: "Could not export PSD") }
        applySaveResult(result, "Exported layered Photoshop PSD")
        return result == SaveResult.Success
    }

    private fun acceptPsdImport(imported: com.neoworksuite.neocanvas.renderer.PsdImportResult) {
        clearSelection()
        resetView()
        history.reset(imported.document)
        tileStore.restore(imported.tiles)
        undoTileStates.clear()
        redoTileStates.clear()
        fileActions.resetDocumentTarget()
        markCleanDocument()
        editVersion = ++nextVersion
        activeLayerId = document.layers.lastOrNull()?.id
        documentRevision++
        statusMessage = if (imported.warnings.isEmpty()) {
            "Imported layered PSD — save as NeoCanvas to keep editing"
        } else {
            "Imported PSD with " + imported.warnings.size + " compatibility notice(s)"
        }
    }
    fun saveAs(): Boolean {
        if (fileActions.supportsLocalLibrary) { namingLocalCopy = true; libraryError = null; return false }
        val result = fileActions.saveAs(document, tilesForDocument())
        applySaveResult(result, "Saved local document copy")
        if (result == SaveResult.Success) savedVersion = editVersion
        return result == SaveResult.Success
    }
    var pendingDocumentAction: PendingDocumentAction? by mutableStateOf(null)
        private set
    var documentActionError: String? by mutableStateOf(null)
        private set
    private var closeAfterConfirmation: (() -> Unit)? = null
    var newCanvasDialogVisible by mutableStateOf(false)
    private var requestedCanvasSize: Pair<Int, Int>? = null
    var recoveryChecking by mutableStateOf(true)
        private set
    var recoveryCandidate: LoadResult? by mutableStateOf(null)
        private set
    private var lastRecoveryVersion = -1

    suspend fun checkRecovery() {
        if (!recoveryChecking) return
        recoveryCandidate = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try { if (fileActions.supportsRecovery) fileActions.loadRecovery() else null }
            catch (error: Exception) { LoadResult.Failure("Could not read recovery copy: ${error.message}") }
        }
        recoveryChecking = false
    }
    fun dismissRecovery() { recoveryCandidate = null }
    fun restoreRecovery() {
        val recovered = recoveryCandidate as? LoadResult.Success ?: return
        // Startup recovery must never silently replace work created in this session.
        if (hasUnsavedChanges) { statusMessage = "Save your current artwork before recovering another canvas"; return }
        history.reset(recovered.document)
        fileActions.resetDocumentTarget()
        tileStore.restore(recovered.tiles)
        undoTileStates.clear(); redoTileStates.clear()
        markCleanDocument()
        editVersion = ++nextVersion
        activeLayerId = document.layers.lastOrNull()?.id
        clearSelection(); resetView()
        documentRevision++
        recoveryCandidate = null
        statusMessage = "Recovered local snapshot — save it to keep this version"
    }
    suspend fun autosaveRecovery() {
        if (!fileActions.supportsRecovery || recoveryChecking || recoveryCandidate != null ||
            !hasUnsavedChanges || editVersion == lastRecoveryVersion) return
        val version = editVersion
        val snapshot = document
        val tiles = tilesForDocument()
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try { fileActions.saveRecovery(snapshot, tiles) }
            catch (error: Exception) { SaveResult.Failure("Could not write recovery copy: ${error.message}") }
        }
        if (result == SaveResult.Success) lastRecoveryVersion = version
        else if (result is SaveResult.Failure) statusMessage = "Autosave failed: ${result.message}. Save your artwork manually."
    }
    private val undoTileStates = mutableListOf<Map<TileKey, ByteArray>>()
    private val redoTileStates = mutableListOf<Map<TileKey, ByteArray>>()

    var activeLayerId: String? by mutableStateOf(history.current.layers.lastOrNull()?.id)
    var brush: BrushDefinition by mutableStateOf(BuiltInBrushes.pencil)
    private var primaryColor: Color by mutableStateOf(Color(0xFF1B1C20))
    var previousColor: Color by mutableStateOf(primaryColor)
        private set
    var secondaryColor: Color by mutableStateOf(Color.White)
        private set
    var recentColors: List<String> by mutableStateOf(emptyList())
        private set
    var color: Color
        get() = primaryColor
        set(value) {
            if (value == primaryColor) return
            previousColor = primaryColor
            primaryColor = value
            val hex = colorHex(value)
            recentColors = (listOf(hex) + recentColors.filterNot { it == hex }).take(12)
        }
    var brushSize: Float by mutableFloatStateOf(BuiltInBrushes.pencil.baseSize)
    var fillTolerance: Int by mutableIntStateOf(0)
    var automaticSelectionTolerancePercent: Int by mutableIntStateOf(12)
    var brushOpacity: Float by mutableFloatStateOf(1f)
    var smudgeStrength: Float by mutableFloatStateOf(.65f)
    var stabilization: Float by mutableFloatStateOf(0f)
    var symmetry: com.neoworksuite.neocanvas.renderer.DrawingSymmetry by mutableStateOf(com.neoworksuite.neocanvas.renderer.DrawingSymmetry.None)
    var tool: Tool by mutableStateOf(Tool.Brush)
    var selection: CanvasSelection? by mutableStateOf(null)
        private set
    var selectionMode: SelectionShape by mutableStateOf(SelectionShape.Rectangle)
    var selectionCombineMode: SelectionCombineMode by mutableStateOf(SelectionCombineMode.Replace)
    var transformSession: TransformSession? by mutableStateOf(null)
        private set
    var transformSnapping: Boolean by mutableStateOf(false)

    var effectPreviewPatch: com.neoworksuite.neocanvas.renderer.RasterPatch? by mutableStateOf(null)
        private set
    var effectPreviewType: com.neoworksuite.neocanvas.renderer.RasterEffectType? by mutableStateOf(null)
        private set
    var effectPreviewSettings: com.neoworksuite.neocanvas.renderer.RasterEffectSettings by mutableStateOf(
        com.neoworksuite.neocanvas.renderer.RasterEffectSettings(amount = 0f),
    )
        private set
    private var effectPreviewLayerId: String? = null

    fun clearSelection() { transformSession = null; selection = null }

    fun activateTransformTool() {
        if (transformSession != null) {
            tool = Tool.MoveSelection
            return
        }
        if (selection == null) selectLayerArtwork()
        if (selection != null) beginTransform()
    }

    fun beginTransform(): Boolean {
        val bounds = selection ?: return false
        if (bounds.invertedRegion != null) {
            statusMessage = "Invert selections cannot be transformed as one rectangular object"
            return false
        }
        val layerId = activeLayerId ?: return false
        val layer = document.layers.firstOrNull { it.id == layerId && it.visible && !it.locked }
        if (layer == null) {
            statusMessage = "Select an unlocked visible layer before transforming"
            return false
        }
        transformSession = TransformSession(bounds)
        tool = Tool.MoveSelection
        statusMessage = "Transform active — drag artwork or handles, then Apply"
        return true
    }
    fun updateTransform(
        translationX: Float = transformSession?.translationX ?: 0f,
        translationY: Float = transformSession?.translationY ?: 0f,
        scale: Float = transformSession?.scale ?: 1f,
        scaleX: Float = transformSession?.scaleX ?: 1f,
        scaleY: Float = transformSession?.scaleY ?: 1f,
        rotationDegrees: Float = transformSession?.rotationDegrees ?: 0f,
    ) {
        val current = transformSession ?: return
        if (!translationX.isFinite() || !translationY.isFinite() || !scale.isFinite() ||
            !scaleX.isFinite() || !scaleY.isFinite() || scale <= 0f || scaleX <= 0f || scaleY <= 0f ||
            !rotationDegrees.isFinite()) return
        val tx = if (transformSnapping) kotlin.math.round(translationX / 8f) * 8f else translationX
        val ty = if (transformSnapping) kotlin.math.round(translationY / 8f) * 8f else translationY
        val rotation = if (transformSnapping) kotlin.math.round(rotationDegrees / 15f) * 15f else rotationDegrees
        transformSession = current.copy(
            translationX = tx,
            translationY = ty,
            scale = scale.coerceIn(.02f, 50f),
            scaleX = scaleX.coerceIn(.05f, 20f),
            scaleY = scaleY.coerceIn(.05f, 20f),
            rotationDegrees = rotation,
        )
    }

    fun scaleTransformAxis(horizontal: Boolean, factor: Float) {
        require(factor.isFinite() && factor > 0f)
        val current = transformSession ?: return
        if (horizontal) updateTransform(scaleX = current.scaleX * factor)
        else updateTransform(scaleY = current.scaleY * factor)
    }
    fun resetTransform() {
        val current = transformSession ?: return
        transformSession = current.copy(
            translationX = 0f,
            translationY = 0f,
            scale = 1f,
            scaleX = 1f,
            scaleY = 1f,
            rotationDegrees = 0f,
        )
        statusMessage = "Transform reset"
    }

    fun fitTransformToCanvas() {
        val current = transformSession ?: return
        val sourceWidth = (current.sourceBounds.right - current.sourceBounds.left).coerceAtLeast(1)
        val sourceHeight = (current.sourceBounds.bottom - current.sourceBounds.top).coerceAtLeast(1)
        val rotated = com.neoworksuite.neocanvas.renderer.RasterMove.rotatedSize(
            sourceWidth,
            sourceHeight,
            current.rotationDegrees,
        )
        val fitScale = minOf(
            document.width.toFloat() / rotated.first.coerceAtLeast(1),
            document.height.toFloat() / rotated.second.coerceAtLeast(1),
        ).coerceIn(.02f, 50f)
        val sourceCenterX = (current.sourceBounds.left + current.sourceBounds.right) / 2f
        val sourceCenterY = (current.sourceBounds.top + current.sourceBounds.bottom) / 2f
        transformSession = current.copy(
            translationX = document.width / 2f - sourceCenterX,
            translationY = document.height / 2f - sourceCenterY,
            scale = fitScale,
            scaleX = 1f,
            scaleY = 1f,
        )
        statusMessage = "Transform fitted to canvas"
    }
    private fun transformPatch(): Pair<com.neoworksuite.neocanvas.renderer.RasterPatch, CanvasSelection>? {
        val session = transformSession ?: return null
        val layerId = activeLayerId ?: return null
        if (document.layers.none { it.id == layerId && it.visible && !it.locked }) return null
        val target = session.targetBounds(document.width, document.height) ?: return null
        val patch = com.neoworksuite.neocanvas.renderer.RasterMove.move(
            tileStore, layerId, session.sourceBounds.left, session.sourceBounds.top,
            session.sourceBounds.right, session.sourceBounds.bottom,
            target.left - session.sourceBounds.left, target.top - session.sourceBounds.top,
            document.width, document.height,
            resizedWidth = target.right - target.left, resizedHeight = target.bottom - target.top,
            sampling = if (smoothResizing) com.neoworksuite.neocanvas.renderer.ResizeSampling.Smooth
                else com.neoworksuite.neocanvas.renderer.ResizeSampling.Pixel,
            degrees = session.rotationDegrees,
            acceptsSourcePixel = session.sourceBounds::contains,
        )
        return patch to session.sourceBounds.transformedTo(target, session.rotationDegrees)
    }
    fun previewTransform(): com.neoworksuite.neocanvas.renderer.RasterPatch? = transformPatch()?.first
    fun previewTransformSelection(): CanvasSelection? = transformPatch()?.second
    fun cancelTransform() {
        if (transformSession == null) return
        transformSession = null
        statusMessage = "Transform cancelled"
    }
    fun applyTransform(): Boolean {
        val (patch, target) = transformPatch() ?: run {
            statusMessage = "Transform is outside the canvas or too large"
            return false
        }
        if (patch.keys.isNotEmpty()) {
            val layerId = activeLayerId ?: return false
            val before = tileStore.snapshot()
            tileStore.applyPatch(patch)
            execute(ApplyRasterPatch(layerId, tileStore.keys - before.keys, before.keys - tileStore.keys), before)
        }
        selection = target
        transformSession = null
        statusMessage = "Transform applied"
        return true
    }
    fun selectLayerArtwork() {
        val id = activeLayerId ?: return
        val layer = document.layers.firstOrNull { it.id == id && it.visible } ?: return
        val raster = layer.payload as? LayerPayload.Raster ?: return
        var left = document.width
        var top = document.height
        var right = 0
        var bottom = 0
        raster.tileAddresses.forEach { key ->
            val bytes = tileStore.read(key) ?: return@forEach
            for (index in 3 until bytes.size step 4) {
                if (bytes[index].toInt() == 0) continue
                val pixel = index / 4
                val x = key.x * 256 + pixel % 256
                val y = key.y * 256 + pixel / 256
                if (x !in 0 until document.width || y !in 0 until document.height) continue
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
            }
        }
        if (right <= left || bottom <= top) {
            clearSelection()
            statusMessage = "This layer has no artwork to select"
            return
        }
        selection = CanvasSelection(left, top, right, bottom)
        tool = Tool.MoveSelection
        statusMessage = "Layer artwork selected; drag to move or use transform controls"
    }

    fun previewSelectionMove(dx: Int, dy: Int): com.neoworksuite.neocanvas.renderer.RasterPatch? {
        val bounds = selection ?: return null
        val layer = activeLayerId ?: return null
        if (document.layers.none { it.id == layer && it.visible && !it.locked }) return null
        val moveX = dx.coerceIn(-bounds.left, document.width - bounds.right)
        val moveY = dy.coerceIn(-bounds.top, document.height - bounds.bottom)
        if (moveX == 0 && moveY == 0) return null
        return com.neoworksuite.neocanvas.renderer.RasterMove.move(
            tileStore, layer,
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            moveX, moveY, document.width, document.height,
            acceptsSourcePixel = bounds::contains,
        )
    }
    fun importImage() {
        val targetDocument = document.id
        fileActions.importImage { result ->
            result.fold(onSuccess = { image ->
                if (image != null && document.id == targetDocument) insertImage(image)
            }, onFailure = { statusMessage = "Could not import image: ${it.message}" })
        }
    }
    fun insertImage(image: ImportedImage) {
        val fit = minOf(1.0, document.width.toDouble() / image.width, document.height.toDouble() / image.height)
        val width = (image.width * fit).toInt().coerceAtLeast(1)
        val height = (image.height * fit).toInt().coerceAtLeast(1)
        val left = (document.width - width) / 2
        val top = (document.height - height) / 2
        val id = nextLayerId()
        val tiles = mutableMapOf<TileKey, ByteArray>()
        for (y in 0 until height) for (x in 0 until width) {
            val sx = ((x + .5) * image.width / width).toInt().coerceAtMost(image.width - 1)
            val sy = ((y + .5) * image.height / height).toInt().coerceAtMost(image.height - 1)
            val pixel = image.argb[sy * image.width + sx]
            if (pixel ushr 24 == 0) continue
            val tx = x + left
            val ty = y + top
            val bytes = tiles.getOrPut(TileKey(id, tx / 256, ty / 256)) { ByteArray(256 * 256 * 4) }
            val i = ((ty % 256) * 256 + tx % 256) * 4
            bytes[i] = (pixel ushr 16).toByte()
            bytes[i + 1] = (pixel ushr 8).toByte()
            bytes[i + 2] = pixel.toByte()
            bytes[i + 3] = (pixel ushr 24).toByte()
        }
        val before = tileStore.snapshot()
        tileStore.applyPatch(com.neoworksuite.neocanvas.renderer.RasterPatch.of(tiles))
        execute(com.neoworksuite.neocanvas.core.model.ImportRasterLayer(id, image.name.ifBlank { "Imported image" }, tiles.keys), before)
        activeLayerId = id
        selection = CanvasSelection(left, top, left + width, top + height)
        tool = Tool.MoveSelection
        resetView()
        beginTransform()
        statusMessage = "Image imported — transform active. Drag the image or its handles, then leave Transform when finished."
    }
    fun clearSelectedPixels() {
        val bounds = selection ?: return
        val layer = activeLayerId ?: return
        if (document.layers.none { it.id == layer && it.visible && !it.locked }) return
        val before = tileStore.snapshot()
        val replacements = mutableMapOf<TileKey, ByteArray>()
        val removals = mutableSetOf<TileKey>()
        before.filterKeys { it.layerId == layer }.forEach { (key, original) ->
            val left = maxOf(bounds.left, key.x * 256)
            val right = minOf(bounds.right, (key.x + 1) * 256)
            val top = maxOf(bounds.top, key.y * 256)
            val bottom = minOf(bounds.bottom, (key.y + 1) * 256)
            if (left >= right || top >= bottom) return@forEach
            val bytes = original.copyOf()
            for (y in top until bottom) for (x in left until right) {
                if (!bounds.contains(x, y)) continue
                val index = ((y % 256) * 256 + x % 256) * 4
                for (channel in 0..3) bytes[index + channel] = 0
            }
            if (bytes.contentEquals(original)) return@forEach
            if ((3 until bytes.size step 4).all { bytes[it].toInt() == 0 }) removals += key
            else replacements[key] = bytes
        }
        if (replacements.isEmpty() && removals.isEmpty()) return
        tileStore.applyPatch(com.neoworksuite.neocanvas.renderer.RasterPatch.of(replacements, removals))
        execute(ApplyRasterPatch(layer, tileStore.keys - before.keys, before.keys - tileStore.keys), before)
        statusMessage = "Cleared selected pixels"
    }
    var smoothResizing: Boolean by mutableStateOf(true)
    fun resizeSelection(factor: Float) {
        require(factor.isFinite() && factor > 0f)
        val bounds = selection ?: return
        val layer = activeLayerId ?: return
        if (document.layers.none { it.id == layer && it.visible && !it.locked }) return
        val newWidth = kotlin.math.round((bounds.right - bounds.left) * factor).toInt().coerceAtLeast(1)
        val newHeight = kotlin.math.round((bounds.bottom - bounds.top) * factor).toInt().coerceAtLeast(1)
        if (newWidth > document.width || newHeight > document.height) {
            statusMessage = "Resized selection would exceed the canvas. Select a smaller area."
            return
        }
        val left = ((bounds.left + bounds.right - newWidth) / 2).coerceIn(0, document.width - newWidth)
        val top = ((bounds.top + bounds.bottom - newHeight) / 2).coerceIn(0, document.height - newHeight)
        val patch = com.neoworksuite.neocanvas.renderer.RasterMove.move(tileStore, layer,
            bounds.left, bounds.top, bounds.right, bounds.bottom, left - bounds.left, top - bounds.top,
            document.width, document.height, resizedWidth = newWidth, resizedHeight = newHeight,
            sampling = if (smoothResizing) com.neoworksuite.neocanvas.renderer.ResizeSampling.Smooth
                else com.neoworksuite.neocanvas.renderer.ResizeSampling.Pixel,
            acceptsSourcePixel = bounds::contains,
        )
        if (patch.keys.isNotEmpty()) {
            val before = tileStore.snapshot()
            tileStore.applyPatch(patch)
            execute(ApplyRasterPatch(layer, tileStore.keys - before.keys, before.keys - tileStore.keys), before)
        }
        selection = bounds.transformedTo(CanvasSelection(left, top, left + newWidth, top + newHeight), 0f)
        statusMessage = "Resized selection to $newWidth × $newHeight pixels"
    }
    fun rotateSelection(degrees: Float = 90f) {
        val bounds = selection ?: return
        val layer = activeLayerId ?: return
        if (document.layers.none { it.id == layer && it.visible && !it.locked }) return
        val (newWidth, newHeight) = com.neoworksuite.neocanvas.renderer.RasterMove.rotatedSize(
            bounds.right - bounds.left, bounds.bottom - bounds.top, degrees)
        if (newWidth > document.width || newHeight > document.height) {
            statusMessage = "Rotated selection would exceed the canvas. Select a smaller area."
            return
        }
        val left = ((bounds.left + bounds.right - newWidth) / 2).coerceIn(0, document.width - newWidth)
        val top = ((bounds.top + bounds.bottom - newHeight) / 2).coerceIn(0, document.height - newHeight)
        val patch = com.neoworksuite.neocanvas.renderer.RasterMove.move(tileStore, layer,
            bounds.left, bounds.top, bounds.right, bounds.bottom, left - bounds.left, top - bounds.top,
            document.width, document.height, rotateClockwise = degrees == 90f,
            sampling = if (smoothResizing) com.neoworksuite.neocanvas.renderer.ResizeSampling.Smooth else com.neoworksuite.neocanvas.renderer.ResizeSampling.Pixel,
            degrees = if (degrees == 90f) 0f else degrees,
            acceptsSourcePixel = bounds::contains,
        )
        if (patch.keys.isNotEmpty()) {
            val before = tileStore.snapshot()
            tileStore.applyPatch(patch)
            execute(ApplyRasterPatch(layer, tileStore.keys - before.keys, before.keys - tileStore.keys), before)
        }
        val target = CanvasSelection(left, top, left + newWidth, top + newHeight)
        selection = if (degrees == 90f) bounds.rotatedClockwiseTo(target) else bounds.transformedTo(target, degrees)
        statusMessage = "Rotated selection ${degrees.toInt()}°"
    }
    fun flipSelection(horizontal: Boolean) {
        val bounds = selection ?: return
        val layer = activeLayerId ?: return
        if (document.layers.none { it.id == layer && it.visible && !it.locked }) return
        val patch = com.neoworksuite.neocanvas.renderer.RasterFlip.flip(
            tileStore, layer,
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            document.width, document.height, horizontal,
            acceptsSourcePixel = bounds::contains,
        )
        if (patch.keys.isEmpty()) return
        val before = tileStore.snapshot()
        tileStore.applyPatch(patch)
        execute(ApplyRasterPatch(layer, tileStore.keys - before.keys, before.keys - tileStore.keys), before)
        selection = bounds.flipped(horizontal)
        statusMessage = if (horizontal) "Flipped selection horizontally" else "Flipped selection vertically"
    }
    fun moveSelection(dx: Int, dy: Int) {
        val bounds = selection ?: return
        val layer = activeLayerId ?: return
        if (document.layers.none { it.id == layer && it.visible && !it.locked }) return
        val moveX = dx.coerceIn(-bounds.left, document.width - bounds.right)
        val moveY = dy.coerceIn(-bounds.top, document.height - bounds.bottom)
        val patch = previewSelectionMove(dx, dy) ?: return
        if (patch.keys.isEmpty()) return
        val before = tileStore.snapshot()
        tileStore.applyPatch(patch)
        execute(ApplyRasterPatch(layer, tileStore.keys - before.keys, before.keys - tileStore.keys), before)
        selection = bounds.translated(moveX, moveY)
        statusMessage = "Moved selected artwork on active layer"
    }
    fun selectRectangle(from: DrawPoint, to: DrawPoint) {
        val left = kotlin.math.floor(minOf(from.x, to.x)).toInt().coerceIn(0, document.width)
        val top = kotlin.math.floor(minOf(from.y, to.y)).toInt().coerceIn(0, document.height)
        val right = (kotlin.math.floor(maxOf(from.x, to.x)).toInt() + 1).coerceIn(0, document.width)
        val bottom = (kotlin.math.floor(maxOf(from.y, to.y)).toInt() + 1).coerceIn(0, document.height)
        val next = if (right > left && bottom > top) CanvasSelection(left, top, right, bottom) else null
        applySelection(next)
    }

    private fun applySelection(next: CanvasSelection?) {
        if (next == null) {
            if (selectionCombineMode == SelectionCombineMode.Replace) selection = null
            return
        }
        val current = selection
        selection = if (current == null || selectionCombineMode == SelectionCombineMode.Replace) next
            else current.combine(next, selectionCombineMode)
        transformSession = null
        statusMessage = when (selectionCombineMode) {
            SelectionCombineMode.Replace -> "Selection replaced"
            SelectionCombineMode.Add -> "Added to selection"
            SelectionCombineMode.Subtract -> "Subtracted from selection"
            SelectionCombineMode.Intersect -> "Intersected selection"
        }
    }

    fun selectArea(points: List<DrawPoint>) {
        if (points.isEmpty()) return
        if (selectionMode == SelectionShape.Automatic) {
            selectAutomatic(points.first())
            return
        }
        if (selectionMode == SelectionShape.Lasso) {
            val next = CanvasSelection.lasso(points)?.let {
                it.copy(left = it.left.coerceIn(0, document.width), top = it.top.coerceIn(0, document.height),
                    right = it.right.coerceIn(0, document.width), bottom = it.bottom.coerceIn(0, document.height))
            }
            applySelection(next)
            return
        }
        val from = points.first()
        val to = points.last()
        val left = kotlin.math.floor(minOf(from.x, to.x)).toInt().coerceIn(0, document.width)
        val top = kotlin.math.floor(minOf(from.y, to.y)).toInt().coerceIn(0, document.height)
        val right = (kotlin.math.floor(maxOf(from.x, to.x)).toInt() + 1).coerceIn(0, document.width)
        val bottom = (kotlin.math.floor(maxOf(from.y, to.y)).toInt() + 1).coerceIn(0, document.height)
        val next = if (right <= left || bottom <= top) null else if (selectionMode == SelectionShape.Ellipse)
            CanvasSelection.ellipse(left, top, right, bottom) else CanvasSelection(left, top, right, bottom)
        applySelection(next)
    }
    fun selectAutomatic(point: DrawPoint) {
        val layerId = activeLayerId ?: run {
            statusMessage = "Select a raster layer before using Automatic Selection"
            return
        }
        val layer = document.layers.firstOrNull { it.id == layerId && it.visible }
        if (layer?.payload !is LayerPayload.Raster) {
            statusMessage = "Automatic Selection needs a visible raster layer"
            return
        }
        val x = kotlin.math.floor(point.x).toInt()
        val y = kotlin.math.floor(point.y).toInt()
        val tolerance = automaticSelectionTolerancePercent.coerceIn(0, 100) * 255 / 100
        val region = RasterSelection.connectedColour(
            tileStore,
            layerId,
            document.width,
            document.height,
            x,
            y,
            tolerance,
        )
        applySelection(region?.let(CanvasSelection::automatic))
        if (region != null) {
            statusMessage = "Automatic selection • " +
                automaticSelectionTolerancePercent.coerceIn(0, 100) + "% tolerance"
        }
    }

    fun cropCanvasToSelection(): Boolean {
        val bounds = selection ?: run {
            statusMessage = "Make a selection before cropping the canvas"
            return false
        }
        if (bounds.invertedRegion != null || bounds.baseRegion != null || bounds.shape != SelectionShape.Rectangle) {
            statusMessage = "Crop Canvas currently requires one rectangular selection"
            return false
        }
        if (bounds.left == 0 && bounds.top == 0 && bounds.right == document.width && bounds.bottom == document.height) {
            statusMessage = "Selection already matches the full canvas"
            return false
        }

        val before = tileStore.snapshot()
        val cropped = com.neoworksuite.neocanvas.renderer.RasterCanvasCrop.crop(
            store = tileStore,
            layers = document.layers,
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
        )
        tileStore.restore(cropped.tiles)
        val addresses = cropped.tiles.keys.groupBy { it.layerId }.mapValues { (_, keys) ->
            keys.mapTo(linkedSetOf()) { TileAddress(it.layerId, it.x, it.y) }
        }
        execute(CropCanvas(cropped.width, cropped.height, addresses), before)
        clearSelection()
        resetView()
        statusMessage = "Cropped canvas to ${cropped.width} × ${cropped.height}"
        return true
    }

    fun invertSelection() {
        val current = selection ?: return
        selection = if (current.invertedRegion != null) current.invertedRegion
            else CanvasSelection(0, 0, document.width, document.height, invertedRegion = current)
        transformSession = null
        statusMessage = "Selection inverted"
    }
    var inspectorPanel: InspectorPanel by mutableStateOf(InspectorPanel.Layers)
    var inspectorVisible: Boolean by mutableStateOf(false)
    var settingsVisible: Boolean by mutableStateOf(false)

    // Workspace preferences. Settings UI owns these rather than scattering toggles across tool panels.
    var fingerPaintingEnabled: Boolean by mutableStateOf(true)
    var canvasRotationEnabled: Boolean by mutableStateOf(true)
    var autoRecoveryEnabled: Boolean by mutableStateOf(true)
    var showStatusMessages: Boolean by mutableStateOf(true)
    var quickShapeEnabled: Boolean by mutableStateOf(true)
    var gridGuideVisible: Boolean by mutableStateOf(false)
    var perspectiveGuideVisible: Boolean by mutableStateOf(false)
    var guideSpacing: Float by mutableFloatStateOf(128f)

    fun resetPreferences() {
        fingerPaintingEnabled = true
        canvasRotationEnabled = true
        autoRecoveryEnabled = true
        showStatusMessages = true
        quickShapeEnabled = true
        gridGuideVisible = false
        perspectiveGuideVisible = false
        guideSpacing = 128f
        automaticSelectionTolerancePercent = 12
        smoothResizing = true
        inspectorVisible = false
        settingsVisible = false
        persistPreferences()
        statusMessage = "NeoCanvas preferences reset"
    }

    fun persistPreferences() {
        runCatching {
            fileActions.savePreferences(
                mapOf(
                    "fingerPaintingEnabled" to fingerPaintingEnabled.toString(),
                    "canvasRotationEnabled" to canvasRotationEnabled.toString(),
                    "autoRecoveryEnabled" to autoRecoveryEnabled.toString(),
                    "showStatusMessages" to showStatusMessages.toString(),
                    "quickShapeEnabled" to quickShapeEnabled.toString(),
                    "gridGuideVisible" to gridGuideVisible.toString(),
                    "perspectiveGuideVisible" to perspectiveGuideVisible.toString(),
                    "guideSpacing" to guideSpacing.toString(),
                    "automaticSelectionTolerancePercent" to automaticSelectionTolerancePercent.coerceIn(0, 100).toString(),
                    "secondaryColor" to colorHex(secondaryColor),
                    "recentColors" to recentColors.joinToString(","),
                    "smoothResizing" to smoothResizing.toString(),
                ),
            )
        }
    }

    var zoom: Float by mutableFloatStateOf(1f)
    var panX: Float by mutableFloatStateOf(0f)
    var panY: Float by mutableFloatStateOf(0f)
    var viewRotationDegrees: Float by mutableFloatStateOf(0f)
    var statusMessage: String? by mutableStateOf(null)
    var palette: List<String> by mutableStateOf(emptyList())
        private set
    init {
        try {
            val preferences = fileActions.loadPreferences()
            fingerPaintingEnabled = preferences["fingerPaintingEnabled"]?.toBoolean() ?: fingerPaintingEnabled
            canvasRotationEnabled = preferences["canvasRotationEnabled"]?.toBoolean() ?: canvasRotationEnabled
            autoRecoveryEnabled = preferences["autoRecoveryEnabled"]?.toBoolean() ?: autoRecoveryEnabled
            showStatusMessages = preferences["showStatusMessages"]?.toBoolean() ?: showStatusMessages
            quickShapeEnabled = preferences["quickShapeEnabled"]?.toBoolean() ?: quickShapeEnabled
            gridGuideVisible = preferences["gridGuideVisible"]?.toBoolean() ?: gridGuideVisible
            perspectiveGuideVisible = preferences["perspectiveGuideVisible"]?.toBoolean() ?: perspectiveGuideVisible
            guideSpacing = preferences["guideSpacing"]?.toFloatOrNull()?.coerceIn(32f, 512f) ?: guideSpacing
            automaticSelectionTolerancePercent =
                preferences["automaticSelectionTolerancePercent"]?.toIntOrNull()?.coerceIn(0, 100)
                    ?: automaticSelectionTolerancePercent
            secondaryColor = preferences["secondaryColor"]?.let(::parseColorHex) ?: secondaryColor
            recentColors = preferences["recentColors"]
                ?.split(",")
                ?.mapNotNull { parseColorHex(it)?.let(::colorHex) }
                ?.distinct()
                ?.take(12)
                .orEmpty()
            smoothResizing = preferences["smoothResizing"]?.toBoolean() ?: smoothResizing
        } catch (_: Exception) {
            // Defaults remain active if a stored preference file cannot be read.
        }
        try {
            palette = fileActions.loadPalette().mapNotNull { parseColorHex(it)?.let(::colorHex) }.distinct().take(32)
        } catch (error: Exception) { statusMessage = "Could not load local palette: ${error.message}" }
    }
    fun loadBrushLibrarySnapshot(): ByteArray? =
        runCatching { fileActions.loadBrushLibrary() }.getOrNull()

    fun persistBrushLibrarySnapshot(bytes: ByteArray) {
        when (val result = fileActions.saveBrushLibrary(bytes)) {
            SaveResult.Success -> statusMessage = "Brush library saved locally"
            is SaveResult.Failure -> statusMessage = result.message
        }
    }

    fun addPaletteColor() {
        val hex = colorHex(color)
        if (hex in palette) { statusMessage = "Colour already in palette"; return }
        if (palette.size >= 32) { statusMessage = "Palette holds 32 colours. Remove one before adding another."; return }
        updatePalette(palette + hex)
    }
    fun removePaletteColor(hex: String) { updatePalette(palette - hex) }

    fun usePreviousColor() {
        val previous = previousColor
        color = previous
        statusMessage = "Restored previous colour"
    }

    fun setSecondaryFromPrimary() {
        secondaryColor = color
        persistPreferences()
        statusMessage = "Secondary colour updated"
    }

    fun swapPrimarySecondaryColors() {
        val current = color
        val secondary = secondaryColor
        color = secondary
        secondaryColor = current
        persistPreferences()
        statusMessage = "Swapped primary and secondary colours"
    }

    fun clearRecentColors() {
        recentColors = emptyList()
        persistPreferences()
        statusMessage = "Cleared recent colours"
    }

    private fun updatePalette(next: List<String>) {
        when (val result = fileActions.savePalette(next)) {
            SaveResult.Success -> { palette = next; statusMessage = "Palette saved locally" }
            is SaveResult.Failure -> { statusMessage = result.message }
        }
    }

    val document: CanvasDocument get() { documentRevision; return history.current }
    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    fun selectBrush(selection: BrushDefinition) {
        brush = selection
        tool = if (selection == BuiltInBrushes.eraser) Tool.Eraser else Tool.Brush
        brushSize = selection.baseSize
        brushOpacity = selection.opacity
    }
    fun activateTool(next: Tool) {
        if (inspectorVisible && inspectorPanel == InspectorPanel.Effects) hideInspector()
        tool = next
    }

    fun openSettings() {
        if (inspectorVisible && inspectorPanel == InspectorPanel.Effects) hideInspector()
        versionsVisible = false
        settingsVisible = true
    }

    fun showInspector(panel: InspectorPanel) {
        versionsVisible = false
        if (inspectorVisible && inspectorPanel == InspectorPanel.Effects && panel != InspectorPanel.Effects) {
            commitEffectPreview()
        }
        inspectorPanel = panel
        inspectorVisible = true
    }

    fun hideInspector(commitEffects: Boolean = true) {
        if (inspectorVisible && inspectorPanel == InspectorPanel.Effects) {
            if (commitEffects) commitEffectPreview() else cancelEffectPreview()
        }
        if (inspectorVisible && inspectorPanel == InspectorPanel.Colors) persistPreferences()
        inspectorVisible = false
    }

    fun toggleInspector(panel: InspectorPanel) {
        if (inspectorVisible && inspectorPanel == panel) hideInspector()
        else showInspector(panel)
    }

    fun addLayer() {
        val id = nextLayerId()
        execute(AddRasterLayer(id, "Layer ${document.layers.size + 1}"))
        activeLayerId = id
        statusMessage = "Added a new local layer"
    }
    fun deleteActiveLayer() {
        val id = activeLayerId ?: return
        if (document.layers.any { it.id == id && it.locked }) { statusMessage = "Unlock this layer before deleting it"; return }
        if (document.layers.size <= 1) { statusMessage = "Keep at least one drawing layer."; return }
        execute(DeleteLayer(id))
        activeLayerId = document.layers.lastOrNull()?.id
    }
    fun duplicateActiveLayer() {
        val source = activeLayerId ?: return
        val original = document.layers.firstOrNull { it.id == source } ?: return
        val id = nextLayerId()
        val command = DuplicateLayer(source, id, "${original.name} copy")
        val before = tileStore.snapshot()
        tileStore.copyTiles(command.rasterTileCopies(document))
        execute(command, before)
        activeLayerId = id
    }
    fun toggleLayerVisibility(id: String) { document.layers.find { it.id == id }?.let { execute(SetLayerVisibility(id, !it.visible)) } }
    fun toggleLayerLock(id: String) {
        document.layers.find { it.id == id }?.let {
            execute(com.neoworksuite.neocanvas.core.model.SetLayerLocked(id, !it.locked))
            statusMessage = if (it.locked) "Layer unlocked" else "Layer locked — unlock it to edit pixels or delete it"
        }
    }
    fun toggleLayerAlphaLock(id: String) {
        document.layers.find { it.id == id }?.let {
            execute(SetLayerAlphaLocked(id, !it.alphaLocked))
            statusMessage = if (it.alphaLocked) "Alpha unlocked" else "Alpha locked — paint stays inside existing pixels"
        }
    }
    fun toggleLayerClipping(id: String) {
        val index = document.layers.indexOfFirst { it.id == id }
        if (index < 0) return
        val layer = document.layers[index]
        if (!layer.clipping && index == 0) {
            statusMessage = "Clipping masks need a layer underneath"
            return
        }
        execute(SetLayerClipping(id, !layer.clipping))
        statusMessage = if (layer.clipping) "Clipping mask disabled" else "Clipping mask enabled"
    }

    fun setLayerBlendMode(id: String, blendMode: LayerBlendMode) {
        execute(SetLayerBlendMode(id, blendMode))
        statusMessage = "Blend mode: ${blendMode.name}"
    }
    fun mergeActiveLayerDown() {
        val sourceId = activeLayerId ?: return
        val sourceIndex = document.layers.indexOfFirst { it.id == sourceId }
        if (sourceIndex <= 0) { statusMessage = "There is no layer below to merge into"; return }
        val source = document.layers[sourceIndex]
        val destination = document.layers[sourceIndex - 1]
        if (source.locked || destination.locked) { statusMessage = "Unlock both layers before merging"; return }
        val before = tileStore.snapshot()
        tileStore.applyPatch(com.neoworksuite.neocanvas.renderer.RasterLayerMerge.mergeDown(tileStore, destination, source))
        val destinationAddresses = tileStore.keys.filterTo(linkedSetOf()) { it.layerId == destination.id }
        execute(MergeRasterLayerDown(source.id, destination.id, destinationAddresses), before)
        activeLayerId = destination.id
        clearSelection()
        statusMessage = "Merged ${source.name} down into ${destination.name}"
    }
    fun setLayerOpacity(id: String, opacity: Float) { execute(SetLayerOpacity(id, opacity.coerceIn(0f, 1f))) }
    fun renameLayer(id: String, name: String) { if (name.isNotBlank()) execute(RenameLayer(id, name.trim())) }
    fun moveLayer(id: String, index: Int) { execute(MoveLayer(id, index.coerceIn(0, document.layers.lastIndex))) }
    /** Moves the active layer by one or more rows in the top-to-bottom Layers panel. */
    fun reorderActiveLayerInDisplay(displayDelta: Int): Boolean {
        val id = activeLayerId ?: return false
        val sourceIndex = document.layers.indexOfFirst { it.id == id }
        if (sourceIndex < 0) return false
        val targetIndex = sourceIndex - displayDelta
        if (targetIndex !in document.layers.indices || targetIndex == sourceIndex) return false
        execute(MoveLayer(id, targetIndex))
        statusMessage = "Reordered layer"
        return true
    }

    /** Rasterizes one completed gesture into sparse tiles and commits its address patch to history. */
    fun recordStroke(points: List<DrawPoint>, stabilize: Boolean = true) {
        val layerId = activeLayerId ?: return
        if (points.isEmpty() || tool !in listOf(Tool.Brush, Tool.Eraser, Tool.Smudge)) return
        val patch = if (tool == Tool.Smudge) previewSmudge(points, stabilize) else previewStroke(points, stabilize)
        if (patch == null) return
        val before = tileStore.snapshot()
        if (tileStore.applyPatch(patch).isEmpty()) return
        val currentKeys = tileStore.keys
        execute(ApplyRasterPatch(layerId, currentKeys - before.keys, before.keys - currentKeys), before)
    }

    fun previewStroke(
        points: List<DrawPoint>,
        stabilize: Boolean = true,
    ): com.neoworksuite.neocanvas.renderer.RasterPatch? {
        val layerId = activeLayerId ?: return null
        if (points.isEmpty() || tool !in listOf(Tool.Brush, Tool.Eraser)) return null
        val activeLayer = document.layers.firstOrNull { it.id == layerId && it.visible && !it.locked } ?: return null
        return Rasterizer.stroke(
            existing = tileStore,
            layerId = layerId,
            points = points.map { RasterPoint(it.x, it.y, normalizedPressure(it.pressure)) }.let { rasterPoints ->
                if (stabilize) com.neoworksuite.neocanvas.renderer.smoothStroke(rasterPoints, stabilization)
                else rasterPoints
            },
            color = RasterColor((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()),
            size = brushSize,
            opacity = brushOpacity,
            mode = if (tool == Tool.Eraser) com.neoworksuite.neocanvas.brushes.BrushMode.ERASE else com.neoworksuite.neocanvas.brushes.BrushMode.PAINT,
            canvasWidth = document.width,
            canvasHeight = document.height,
            acceptsPixel = { x, y -> selection?.contains(x, y) ?: true },
            brush = if (tool == Tool.Eraser && brush.mode != com.neoworksuite.neocanvas.brushes.BrushMode.ERASE) BuiltInBrushes.eraser else brush,
            symmetry = symmetry,
            alphaLocked = activeLayer.alphaLocked,
        )
    }

    fun previewSmudge(
        points: List<DrawPoint>,
        stabilize: Boolean = true,
    ): com.neoworksuite.neocanvas.renderer.RasterPatch? {
        val layerId = activeLayerId ?: return null
        if (points.size < 2 || tool != Tool.Smudge) return null
        val activeLayer = document.layers.firstOrNull { it.id == layerId && it.visible && !it.locked } ?: return null
        if (activeLayer.payload !is LayerPayload.Raster) return null
        val rasterPoints = points.map {
            RasterPoint(it.x, it.y, normalizedPressure(it.pressure))
        }.let {
            if (stabilize) com.neoworksuite.neocanvas.renderer.smoothStroke(it, stabilization) else it
        }
        return com.neoworksuite.neocanvas.renderer.RasterSmudge.stroke(
            existing = tileStore,
            layerId = layerId,
            points = rasterPoints,
            size = brushSize,
            strength = smudgeStrength.coerceIn(0f, 1f),
            canvasWidth = document.width,
            canvasHeight = document.height,
            acceptsPixel = { x, y -> selection?.contains(x, y) ?: true },
        )
    }

    fun undo(): Boolean {
        if (!history.undo()) return false
        redoVersions += editVersion
        editVersion = undoVersions.removeLastOrNull() ?: 0
        val previousTiles = undoTileStates.removeLastOrNull() ?: emptyMap()
        redoTileStates += tileStore.snapshot()
        tileStore.restore(previousTiles)
        afterHistoryMove()
        return true
    }
    fun redo(): Boolean {
        if (!history.redo()) return false
        undoVersions += editVersion
        editVersion = redoVersions.removeLastOrNull() ?: 0
        val nextTiles = redoTileStates.removeLastOrNull() ?: emptyMap()
        undoTileStates += tileStore.snapshot()
        tileStore.restore(nextTiles)
        afterHistoryMove()
        return true
    }
    fun previewEffect(
        type: com.neoworksuite.neocanvas.renderer.RasterEffectType,
        settings: com.neoworksuite.neocanvas.renderer.RasterEffectSettings,
    ): Boolean {
        val layerId = activeLayerId ?: run {
            statusMessage = "Select a layer before adjusting an effect"
            cancelEffectPreview(silent = true)
            return false
        }
        val layer = document.layers.firstOrNull { it.id == layerId && it.visible && !it.locked } ?: run {
            statusMessage = "Select an unlocked visible layer before adjusting an effect"
            cancelEffectPreview(silent = true)
            return false
        }
        if (layer.payload !is LayerPayload.Raster) {
            cancelEffectPreview(silent = true)
            return false
        }

        effectPreviewLayerId = layerId
        effectPreviewType = type
        effectPreviewSettings = settings
        effectPreviewPatch = com.neoworksuite.neocanvas.renderer.RasterEffects.apply(
            store = tileStore,
            layerId = layerId,
            canvasWidth = document.width,
            canvasHeight = document.height,
            type = type,
            settings = settings,
            gradientHighlight = RasterColor(
                (color.red * 255).toInt().coerceIn(0, 255),
                (color.green * 255).toInt().coerceIn(0, 255),
                (color.blue * 255).toInt().coerceIn(0, 255),
            ),
        )
        return true
    }

    fun adjustEffectPreviewPrimary(deltaFraction: Float) {
        val type = effectPreviewType ?: return
        if (!deltaFraction.isFinite() || deltaFraction == 0f) return
        val current = effectPreviewSettings
        val nextAmount = when (type) {
            com.neoworksuite.neocanvas.renderer.RasterEffectType.Blur,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.MotionBlur ->
                (current.amount + deltaFraction).coerceIn(0f, 1f)

            com.neoworksuite.neocanvas.renderer.RasterEffectType.HueSaturation,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.ColourBalance,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.Curves ->
                (current.amount + deltaFraction * 2f).coerceIn(-1f, 1f)

            com.neoworksuite.neocanvas.renderer.RasterEffectType.GradientMap,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.Grayscale,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.Invert -> return

            com.neoworksuite.neocanvas.renderer.RasterEffectType.Sharpen,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.Noise,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.Bloom,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.Halftone,
            com.neoworksuite.neocanvas.renderer.RasterEffectType.ChromaticAberration ->
                (current.amount + deltaFraction).coerceIn(0f, 1f)
        }
        previewEffect(type, current.copy(amount = nextAmount))
    }

    fun commitEffectPreview(): Boolean {
        val patch = effectPreviewPatch ?: return false
        val type = effectPreviewType ?: return false
        val layerId = effectPreviewLayerId ?: return false
        if (activeLayerId != layerId) {
            cancelEffectPreview(silent = true)
            return false
        }

        val before = tileStore.snapshot()
        val changed = tileStore.applyPatch(patch)
        clearEffectPreviewState()
        if (changed.isEmpty()) return false

        execute(
            ApplyRasterPatch(layerId, tileStore.keys - before.keys, before.keys - tileStore.keys),
            before,
        )
        statusMessage = effectAppliedMessage(type)
        return true
    }

    fun cancelEffectPreview(silent: Boolean = false) {
        val hadPreview = effectPreviewPatch != null
        clearEffectPreviewState()
        if (hadPreview && !silent) statusMessage = "Adjustment cancelled"
    }

    private fun clearEffectPreviewState() {
        effectPreviewPatch = null
        effectPreviewType = null
        effectPreviewLayerId = null
    }

    fun applyEffect(
        type: com.neoworksuite.neocanvas.renderer.RasterEffectType,
        settings: com.neoworksuite.neocanvas.renderer.RasterEffectSettings,
    ): Boolean {
        cancelEffectPreview(silent = true)
        if (!previewEffect(type, settings)) return false
        return commitEffectPreview()
    }

    private fun effectAppliedMessage(type: com.neoworksuite.neocanvas.renderer.RasterEffectType): String = when (type) {
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Blur -> "Blur applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.MotionBlur -> "Motion blur applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.HueSaturation -> "Hue / Saturation applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.ColourBalance -> "Colour balance applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Curves -> "Curves applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.GradientMap -> "Gradient map applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Sharpen -> "Sharpen applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Noise -> "Noise applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Bloom -> "Bloom applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Halftone -> "Halftone applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.ChromaticAberration -> "Chromatic aberration applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Grayscale -> "Grayscale applied"
        com.neoworksuite.neocanvas.renderer.RasterEffectType.Invert -> "Invert applied"
    }

    fun zoomBy(multiplier: Float) { zoom = (zoom * multiplier).coerceIn(.20f, 6f) }
    fun rotateViewBy(degrees: Float) {
        if (!degrees.isFinite()) return
        viewRotationDegrees = normalizeViewRotation(viewRotationDegrees + degrees)
    }
    fun zoomAt(multiplier: Float, pointerX: Float, pointerY: Float, centerX: Float, centerY: Float) {
        val previousZoom = zoom
        zoomBy(multiplier)
        val ratio = zoom / previousZoom
        panX = pointerX - centerX - (pointerX - centerX - panX) * ratio
        panY = pointerY - centerY - (pointerY - centerY - panY) * ratio
    }
    fun applyPointTool(point: DrawPoint) {
        val x = point.x.toInt()
        val y = point.y.toInt()
        if (x !in 0 until document.width || y !in 0 until document.height) return
        if (tool == Tool.Fill) {
            val layer = activeLayerId ?: return
            val activeLayer = document.layers.firstOrNull { it.id == layer && it.visible && !it.locked } ?: return
            val patch = com.neoworksuite.neocanvas.renderer.FloodFill.fill(
                tileStore, layer, document.width, document.height, x, y,
                RasterColor((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()),
                acceptsPixel = { px, py -> selection?.contains(px, py) ?: true },
                alphaLocked = activeLayer.alphaLocked,
                tolerance = fillTolerance,
            )
            if (patch.keys.isEmpty()) return
            val before = tileStore.snapshot()
            tileStore.applyPatch(patch)
            execute(ApplyRasterPatch(layer, tileStore.keys - before.keys, before.keys - tileStore.keys), before)
            statusMessage = "Filled connected colour on active layer"
        } else if (tool == Tool.Eyedropper) {
            var r = NeoCanvasColors.paper.red
            var g = NeoCanvasColors.paper.green
            var b = NeoCanvasColors.paper.blue
            document.layers.filter { it.visible }.forEach { layer ->
                val bytes = tileStore.read(TileKey(layer.id, x / 256, y / 256)) ?: return@forEach
                val i = ((y % 256) * 256 + x % 256) * 4
                val alpha = (bytes[i + 3].toInt() and 255) / 255f * layer.opacity
                r = (bytes[i].toInt() and 255) / 255f * alpha + r * (1f - alpha)
                g = (bytes[i + 1].toInt() and 255) / 255f * alpha + g * (1f - alpha)
                b = (bytes[i + 2].toInt() and 255) / 255f * alpha + b * (1f - alpha)
            }
            color = Color(r, g, b)
            tool = Tool.Brush
            statusMessage = "Sampled visible colour"
        }
    }
    fun resetView() {
        zoom = 1f
        panX = 0f
        panY = 0f
        viewRotationDegrees = 0f
    }

    fun save(): Boolean {
        val result = fileActions.save(document, tilesForDocument())
        applySaveResult(result, "Saved locally")
        if (result == SaveResult.Success) savedVersion = editVersion
        return result == SaveResult.Success
    }
    fun exportPng() = applySaveResult(fileActions.exportPng(document, tilesForDocument()), "Exported PNG locally")
    fun newDocument(width: Int = document.width, height: Int = document.height): Boolean {
        if (width !in 1..8192 || height !in 1..8192 || width.toLong() * height > 16_000_000) {
            statusMessage = "Choose dimensions from 1–8192 pixels, up to 16 million pixels total"
            return false
        }
        requestedCanvasSize = width to height
        newCanvasDialogVisible = false
        if (hasUnsavedChanges) { pendingDocumentAction = PendingDocumentAction.New; return true }
        createNewDocument()
        return true
    }
    fun requestClose(onConfirmed: () -> Unit) {
        if (!hasUnsavedChanges) { onConfirmed(); return }
        closeAfterConfirmation = onConfirmed
        pendingDocumentAction = PendingDocumentAction.Close
    }
    fun cancelDocumentAction() {
        requestedCanvasSize = null
        pendingDocumentAction = null
        documentActionError = null
        closeAfterConfirmation = null
    }
    fun saveAndContinue() {
        if (pendingDocumentAction == null) return
        if (save()) discardAndContinue() else documentActionError = statusMessage
    }
    fun discardAndContinue() {
        val action = pendingDocumentAction ?: return
        pendingDocumentAction = null
        documentActionError = null
        val close = closeAfterConfirmation
        closeAfterConfirmation = null
        when (action) {
            PendingDocumentAction.New -> createNewDocument()
            PendingDocumentAction.Open -> openDocument()
            PendingDocumentAction.Close -> close?.invoke()
        }
    }
    private fun markCleanDocument() {
        undoVersions.clear(); redoVersions.clear()
        editVersion = ++nextVersion
        savedVersion = editVersion
    }
    private fun createNewDocument() {
        val (width, height) = requestedCanvasSize ?: (document.width to document.height)
        requestedCanvasSize = null
        fileActions.resetDocumentTarget()
        clearSelection()
        resetView()
        history.reset(CanvasDocument.blank(width, height).copy(
            layers = listOf(Layer("layer-1", "Sketch", payload = LayerPayload.Raster())),
        ))
        tileStore.restore(emptyMap())
        undoTileStates.clear(); redoTileStates.clear()
        markCleanDocument()
        activeLayerId = "layer-1"
        documentRevision++
        statusMessage = "New local canvas"
    }
    fun open() {
        if (hasUnsavedChanges) { pendingDocumentAction = PendingDocumentAction.Open; return }
        openDocument()
    }
    private fun openDocument() {
        if (fileActions.supportsLocalLibrary) {
            libraryError = null
            localDocuments = try { fileActions.listLocalDocuments() } catch (error: Exception) {
                libraryError = error.message ?: "Unable to list local documents"
                emptyList()
            }
            return
        }
        acceptOpenResult(fileActions.open())
    }
    private fun acceptOpenResult(result: LoadResult) {
        when (result) {
            is LoadResult.Success -> {
                clearSelection()
                resetView()
                history.reset(result.document)
                tileStore.restore(result.tiles)
                undoTileStates.clear(); redoTileStates.clear()
                markCleanDocument()
                activeLayerId = document.layers.lastOrNull()?.id
                documentRevision++
                statusMessage = "Opened local NeoCanvas document"
            }
            is LoadResult.Failure -> statusMessage = result.message
            is LoadResult.Corrupt -> statusMessage = "Could not open: ${result.message}"
            is LoadResult.Incompatible -> statusMessage = "Unsupported document: ${result.message}"
        }
    }

    /** Excludes cached tiles made orphaned by layer deletion; packages require exact tile addresses. */
    fun tilesForDocument(): Map<TileAddress, ByteArray> {
        val valid = document.layers.flatMap { layer ->
            (layer.payload as? com.neoworksuite.neocanvas.core.model.LayerPayload.Raster)?.tileAddresses.orEmpty()
        }.toSet()
        return tileStore.snapshot().filterKeys { it in valid }
    }

    private fun applySaveResult(result: SaveResult, success: String) {
        statusMessage = when (result) {
            SaveResult.Success -> success
            is SaveResult.Failure -> buildString { append(result.message); result.recoveryPath?.let { append(" Recovery copy: $it") } }
        }
    }
    private fun execute(command: DocumentCommand, tilesBefore: Map<TileKey, ByteArray> = tileStore.snapshot()) {
        history.execute(command)
        undoVersions += editVersion
        redoVersions.clear()
        editVersion = ++nextVersion
        undoTileStates += tilesBefore
        redoTileStates.clear()
        documentRevision++
    }
    private fun afterHistoryMove() {
        clearSelection()
        documentRevision++
        activeLayerId = activeLayerId?.takeIf { id -> document.layers.any { it.id == id } } ?: document.layers.lastOrNull()?.id
    }
    private fun nextLayerId(): String {
        var ordinal = document.layers.size + 1
        while (document.layers.any { it.id == "layer-$ordinal" }) ordinal++
        return "layer-$ordinal"
    }
}

fun normalizedPressure(reported: Float?): Float = reported?.takeIf { it in 0f..1f } ?: 1f


internal fun normalizeViewRotation(degrees: Float): Float {
    if (!degrees.isFinite()) return 0f
    var normalized = degrees % 360f
    if (normalized > 180f) normalized -= 360f
    if (normalized <= -180f) normalized += 360f
    return normalized
}
