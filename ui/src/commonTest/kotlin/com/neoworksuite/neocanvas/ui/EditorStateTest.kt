package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateTest {
    @Test fun merge_down_flattens_two_layers_and_undo_restores_both_with_pixels() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.color = Color.Red
        state.recordStroke(listOf(DrawPoint(4.5f, 4.5f)))
        state.addLayer()
        state.color = Color.Blue
        state.brushOpacity = .5f
        state.recordStroke(listOf(DrawPoint(4.5f, 4.5f)))
        val before = state.tileStore.snapshot()

        state.mergeActiveLayerDown()

        assertEquals(1, state.document.layers.size)
        assertEquals(state.document.layers.single().id, state.activeLayerId)
        assertTrue(state.undo())
        assertEquals(2, state.document.layers.size)
        assertEquals(before.keys, state.tileStore.keys)
        before.forEach { (key, pixels) -> assertTrue(pixels.contentEquals(state.tileStore.read(key))) }
    }

    @Test fun move_preview_matches_commit_and_does_not_change_document() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.insertImage(ImportedImage("Image", 2, 2, IntArray(4) { 0x80FF0000.toInt() }))
        val key = state.tileStore.keys.single()
        val original = state.tileStore.read(key)!!
        val preview = state.previewSelectionMove(3, 2)!!
        val expected = preview.previewTile(key, state.tileStore)!!
        assertTrue(original.contentEquals(state.tileStore.read(key)!!))
        state.moveSelection(3, 2)
        assertTrue(expected.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.undo())
        assertTrue(original.contentEquals(state.tileStore.read(key)!!))
    }

    @Test fun reselect_artwork_finds_visible_pixels_after_deselect() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.insertImage(ImportedImage("Image", 2, 2, intArrayOf(0xFF000000.toInt(), 0, 0, 0)))
        state.clearSelection()
        state.selectLayerArtwork()
        assertEquals(CanvasSelection(7, 7, 8, 8), state.selection)
        assertEquals(Tool.MoveSelection, state.tool)
    }
    @Test fun imported_image_is_centered_preserves_alpha_and_undoes_as_one_action() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(8, 8)))
        state.addLayer()
        val originalLayer = state.activeLayerId
        state.insertImage(ImportedImage("Photo", 2, 1, intArrayOf(0x80FF0000.toInt(), 0)))
        assertEquals(2, state.document.layers.size)
        assertEquals("Photo", state.document.layers.last().name)
        assertEquals(CanvasSelection(3, 3, 5, 4), state.selection)
        assertEquals(Tool.MoveSelection, state.tool)
        assertNotNull(state.transformSession)
        val key = state.tileStore.keys.single()
        val pixels = state.tileStore.read(key)!!
        val offset = (3 * 256 + 3) * 4
        assertEquals(255, pixels[offset].toInt() and 255)
        assertEquals(128, pixels[offset + 3].toInt() and 255)
        assertEquals(0, pixels[offset + 7].toInt())
        assertEquals(state.tileStore.keys, state.tilesForDocument().keys)
        state.rotateSelection()
        assertTrue(state.undo())
        assertTrue(pixels.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.undo())
        assertEquals(1, state.document.layers.size)
        assertEquals(originalLayer, state.activeLayerId)
        assertTrue(state.tileStore.keys.isEmpty())
        assertTrue(state.redo())
        assertTrue(pixels.contentEquals(state.tileStore.read(key)!!))
    }

    @Test fun imported_image_is_scaled_to_fit_without_changing_canvas() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(8, 8)))
        state.insertImage(ImportedImage("Wide", 32, 16, IntArray(32 * 16) { 0xFF0000FF.toInt() }))
        assertEquals(CanvasSelection(0, 2, 8, 6), state.selection)
        assertEquals(8, state.document.width)
        assertEquals(8, state.document.height)
    }
    @Test fun effect_preview_is_non_destructive_until_it_is_committed() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(8, 8)))
        state.insertImage(ImportedImage("Photo", 2, 2, IntArray(4) { 0xFF336699.toInt() }))
        state.cancelTransform()
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!.copyOf()

        assertTrue(
            state.previewEffect(
                com.neoworksuite.neocanvas.renderer.RasterEffectType.Invert,
                com.neoworksuite.neocanvas.renderer.RasterEffectSettings(amount = 1f),
            ),
        )
        assertNotNull(state.effectPreviewPatch)
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))

        assertTrue(state.commitEffectPreview())
        assertFalse(before.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.undo())
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
    }

    @Test fun cancelling_effect_preview_leaves_the_active_layer_unchanged() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(8, 8)))
        state.insertImage(ImportedImage("Photo", 2, 2, IntArray(4) { 0xFF336699.toInt() }))
        state.cancelTransform()
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!.copyOf()

        assertTrue(
            state.previewEffect(
                com.neoworksuite.neocanvas.renderer.RasterEffectType.Invert,
                com.neoworksuite.neocanvas.renderer.RasterEffectSettings(amount = 1f),
            ),
        )
        state.cancelEffectPreview()

        assertNull(state.effectPreviewPatch)
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
    }

    @Test fun crop_canvas_to_rectangular_selection_is_undoable() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(8, 8)))
        state.insertImage(ImportedImage("Crop", 2, 2, IntArray(4) { 0xFFFF0000.toInt() }))
        state.cancelTransform()
        val beforeTiles = state.tileStore.snapshot()
        assertEquals(8, state.document.width)
        assertEquals(8, state.document.height)
        assertEquals(CanvasSelection(3, 3, 5, 5), state.selection)

        assertTrue(state.cropCanvasToSelection())
        assertEquals(2, state.document.width)
        assertEquals(2, state.document.height)
        assertNull(state.selection)
        assertTrue(state.tileStore.keys.all { it.x == 0 && it.y == 0 })

        assertTrue(state.undo())
        assertEquals(8, state.document.width)
        assertEquals(8, state.document.height)
        beforeTiles.forEach { (key, pixels) ->
            assertTrue(pixels.contentEquals(state.tileStore.read(key)))
        }
    }

    @Test fun palette_normalizes_persists_and_retains_colours_on_save_failure() {
        var saved = listOf("#ff0000", "invalid", "#FF0000")
        var fail = false
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override fun loadPalette() = saved
            override fun savePalette(colors: List<String>): com.neoworksuite.neocanvas.core.store.SaveResult {
                if (fail) return com.neoworksuite.neocanvas.core.store.SaveResult.Failure("Disk full")
                saved = colors
                return com.neoworksuite.neocanvas.core.store.SaveResult.Success
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        assertEquals(listOf("#FF0000"), state.palette)
        state.color = Color.Blue
        state.addPaletteColor()
        assertEquals(listOf("#FF0000", "#0000FF"), saved)
        val reopened = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        assertEquals(saved, reopened.palette)
        fail = true
        state.removePaletteColor("#FF0000")
        assertEquals(saved, state.palette)
        assertEquals("Disk full", state.statusMessage)
    }

    @Test fun clear_pixels_removes_only_selected_area_and_is_undoable() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.tool = Tool.Fill
        state.applyPointTool(DrawPoint(0f, 0f))
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!
        state.selectRectangle(DrawPoint(0f, 0f), DrawPoint(3f, 3f))
        state.clearSelectedPixels()
        val after = state.tileStore.read(key)!!
        assertEquals(0, after[3].toInt())
        assertEquals(255, after[(5 * 256 + 5) * 4 + 3].toInt() and 255)
        assertTrue(state.undo())
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.redo())
        assertTrue(after.contentEquals(state.tileStore.read(key)!!))
    }
    @Test fun pointer_zoom_preserves_anchor_and_reverses_without_pan_drift() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(64, 64)))
        state.panX = 20f
        state.panY = -10f
        val beforeX = (150f - 300f - state.panX) / state.zoom
        val beforeY = (200f - 250f - state.panY) / state.zoom
        state.zoomAt(2f, 150f, 200f, 300f, 250f)
        assertEquals(beforeX, (150f - 300f - state.panX) / state.zoom, .001f)
        assertEquals(beforeY, (200f - 250f - state.panY) / state.zoom, .001f)
        state.zoomAt(.5f, 150f, 200f, 300f, 250f)
        assertEquals(20f, state.panX, .001f)
        assertEquals(-10f, state.panY, .001f)
        state.zoom = 6f
        state.zoomAt(2f, 150f, 200f, 300f, 250f)
        assertEquals(20f, state.panX, .001f)
    }
    @Test fun view_rotation_normalizes_and_reset_restores_fit_view() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(64, 64)))
        state.zoom = 2.5f
        state.panX = 120f
        state.panY = -48f
        state.rotateViewBy(190f)
        assertEquals(-170f, state.viewRotationDegrees, .001f)
        state.rotateViewBy(-30f)
        assertEquals(160f, state.viewRotationDegrees, .001f)

        state.resetView()

        assertEquals(1f, state.zoom, .001f)
        assertEquals(0f, state.panX, .001f)
        assertEquals(0f, state.panY, .001f)
        assertEquals(0f, state.viewRotationDegrees, .001f)
    }

    @Test fun live_brush_preview_matches_commit_without_mutating_document() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(64, 64)))
        state.addLayer()
        state.selectBrush(com.neoworksuite.neocanvas.brushes.BuiltInBrushes.softRound)
        val points = listOf(DrawPoint(12f, 12f), DrawPoint(20f, 18f))
        val preview = state.previewStroke(points)!!
        val key = preview.keys.first()
        val expected = preview.previewTile(key, state.tileStore)!!
        assertTrue(state.tileStore.keys.isEmpty())
        state.recordStroke(points)
        assertTrue(expected.contentEquals(state.tileStore.read(key)!!))
    }

    @Test fun eraser_preview_removes_tile_without_changing_stored_artwork() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(64, 64)))
        state.addLayer()
        state.selectBrush(com.neoworksuite.neocanvas.brushes.BuiltInBrushes.ink)
        val points = listOf(DrawPoint(12f, 12f))
        state.recordStroke(points)
        val key = state.tileStore.keys.single()
        val original = state.tileStore.read(key)!!
        state.tool = Tool.Eraser
        state.brushSize = 20f
        val preview = state.previewStroke(points)!!
        assertEquals(null, preview.previewTile(key, state.tileStore))
        assertTrue(original.contentEquals(state.tileStore.read(key)!!))
    }
    @Test fun resize_preserves_undo_and_rejects_oversized_results() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.recordStroke(listOf(DrawPoint(3f, 3f)))
        state.selectRectangle(DrawPoint(2f, 2f), DrawPoint(5f, 5f))
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!
        state.resizeSelection(2f)
        assertEquals(8, state.selection!!.right - state.selection!!.left)
        val bounds = state.selection
        val resized = state.tileStore.read(key)!!
        state.resizeSelection(4f)
        assertEquals(bounds, state.selection)
        assertTrue(resized.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.undo())
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.redo())
        assertTrue(resized.contentEquals(state.tileStore.read(key)!!))
    }
    @Test fun rotation_swaps_selection_dimensions_and_undo_restores_artwork() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.brushSize = 2f
        state.recordStroke(listOf(DrawPoint(3f, 3f)))
        state.selectRectangle(DrawPoint(2f, 2f), DrawPoint(5f, 7f))
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!
        state.rotateSelection()
        val bounds = state.selection!!
        assertEquals(6, bounds.right - bounds.left)
        assertEquals(4, bounds.bottom - bounds.top)
        val rotated = state.tileStore.read(key)!!
        assertFalse(before.contentEquals(rotated))
        assertTrue(state.undo())
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.redo())
        assertTrue(rotated.contentEquals(state.tileStore.read(key)!!))
    }

    @Test fun oversized_rotation_preserves_document_and_selection() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 4)))
        state.addLayer()
        state.recordStroke(listOf(DrawPoint(2f, 2f)))
        state.selectRectangle(DrawPoint(0f, 0f), DrawPoint(15f, 3f))
        val bounds = state.selection
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!
        state.rotateSelection()
        assertEquals(bounds, state.selection)
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
    }
    @Test fun flip_selection_preserves_pixels_through_undo_redo() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.brushSize = 2f
        state.recordStroke(listOf(DrawPoint(2f, 2f)))
        state.selectRectangle(DrawPoint(0f, 0f), DrawPoint(15f, 15f))
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!
        state.flipSelection(true)
        val after = state.tileStore.read(key)!!
        assertFalse(before.contentEquals(after))
        assertTrue(state.undo())
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.redo())
        assertTrue(after.contentEquals(state.tileStore.read(key)!!))
    }
    @Test fun selection_move_is_one_undoable_edit() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.selectRectangle(DrawPoint(2f, 2f), DrawPoint(3f, 3f))
        state.tool = Tool.Fill
        state.applyPointTool(DrawPoint(2f, 2f))
        val key = state.tileStore.keys.single()
        val before = state.tileStore.read(key)!!
        state.moveSelection(5, 0)
        assertEquals(7, state.selection!!.left)
        val moved = state.tileStore.read(key)!!
        assertEquals(0, moved[(2 * 256 + 2) * 4 + 3].toInt() and 255)
        assertEquals(255, moved[(2 * 256 + 7) * 4 + 3].toInt() and 255)
        assertTrue(state.undo())
        assertTrue(before.contentEquals(state.tileStore.read(key)!!))
        assertTrue(state.redo())
        assertTrue(moved.contentEquals(state.tileStore.read(key)!!))
    }
    @Test fun selection_limits_fill_and_fill_undo_restores_empty_layer() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.selectRectangle(DrawPoint(7f, 7f), DrawPoint(2f, 2f))
        state.tool = Tool.Fill
        state.color = Color.Red
        state.applyPointTool(DrawPoint(3f, 3f))
        val key = state.tileStore.keys.single()
        val pixels = state.tileStore.read(key)!!
        assertEquals(255, pixels[(3 * 256 + 3) * 4 + 3].toInt() and 255)
        assertEquals(0, pixels[(1 * 256 + 1) * 4 + 3].toInt() and 255)
        assertEquals(0, pixels[(8 * 256 + 8) * 4 + 3].toInt() and 255)
        assertTrue(state.undo())
        assertTrue(state.tileStore.keys.isEmpty())
        assertTrue(state.redo())
        assertTrue(pixels.contentEquals(state.tileStore.read(key)!!))
    }

    @Test fun selection_clips_brush_footprint_and_new_document_clears_selection() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.selectRectangle(DrawPoint(4f, 4f), DrawPoint(8f, 8f))
        state.brushSize = 20f
        state.recordStroke(listOf(DrawPoint(5f, 5f)))
        val bytes = state.tileStore.read(state.tileStore.keys.single())!!
        assertEquals(0, bytes[(3 * 256 + 5) * 4 + 3].toInt() and 255)
        assertTrue((bytes[(5 * 256 + 5) * 4 + 3].toInt() and 255) > 0)
        state.newDocument()
        state.discardAndContinue()
        assertEquals(null, state.selection)
    }

    @Test
    fun local_versions_create_and_restore_with_a_safety_snapshot() {
        val saved = mutableListOf<Pair<LocalVersionEntry, LoadResult.Success>>()
        var clock = 1000L
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsVersions = true
            override fun listVersions(documentId: String): List<LocalVersionEntry> =
                saved.map { it.first }.sortedByDescending { it.createdAtEpochMillis }

            override fun createVersion(
                label: String,
                document: CanvasDocument,
                tiles: Map<TileAddress, ByteArray>,
            ): SaveResult {
                val entry = LocalVersionEntry("v" + clock + ".neoversion", label, clock++)
                saved += entry to LoadResult.Success(document, tiles)
                return SaveResult.Success
            }

            override fun loadVersion(documentId: String, versionId: String): LoadResult =
                saved.firstOrNull { it.first.id == versionId }?.second
                    ?: LoadResult.Failure("Missing version")

            override fun deleteVersion(documentId: String, versionId: String): SaveResult {
                val removed = saved.removeAll { it.first.id == versionId }
                return if (removed) SaveResult.Success else SaveResult.Failure("Missing version")
            }
        }
        val initial = CanvasDocument(
            id = "version-test",
            width = 16,
            height = 16,
            layers = listOf(Layer("layer-1", "Sketch", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial), actions)

        assertTrue(state.createVersion("Sketch"))
        val sketchId = state.versions.single().id
        state.addLayer()
        assertTrue(state.hasUnsavedChanges)

        assertTrue(state.restoreVersion(sketchId))

        assertEquals(1, state.document.layers.size)
        assertTrue(state.hasUnsavedChanges)
        assertTrue(state.versions.any { it.label == "Before restore" })
        assertEquals("Restored local version — current work was kept as Before restore", state.statusMessage)
    }

    @Test
    fun psd_import_replaces_the_document_and_marks_it_unsaved() {
        val importedLayer = Layer("psd-layer-1", "PSD Paint", payload = LayerPayload.Raster())
        val imported = com.neoworksuite.neocanvas.renderer.PsdImportResult(
            CanvasDocument("psd", 24, 18, listOf(importedLayer)),
            emptyMap(),
        )
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsPsdImport = true
            override fun importPsd(onResult: (Result<com.neoworksuite.neocanvas.renderer.PsdImportResult?>) -> Unit) {
                onResult(Result.success(imported))
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(8, 8)), actions)

        state.importPsd()

        assertEquals(24, state.document.width)
        assertEquals(18, state.document.height)
        assertEquals("PSD Paint", state.document.layers.single().name)
        assertTrue(state.hasUnsavedChanges)
        assertEquals("psd-layer-1", state.activeLayerId)
    }

    @Test
    fun inspector_switches_between_layers_colours_and_brushes() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(100, 100)))

        assertEquals(InspectorPanel.Layers, state.inspectorPanel)
        state.showInspector(InspectorPanel.Brushes)
        assertEquals(InspectorPanel.Brushes, state.inspectorPanel)
        state.showInspector(InspectorPanel.Colors)
        assertEquals(InspectorPanel.Colors, state.inspectorPanel)
    }

    @Test
    fun mouse_input_defaults_to_full_pressure() {
        assertEquals(1f, normalizedPressure(null))
    }

    @Test
    fun invalid_pressure_defaults_to_full_pressure() {
        assertEquals(1f, normalizedPressure(1.5f))
    }

    @Test
    fun colour_history_tracks_recent_primary_and_secondary_colours() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(32, 32)))
        val original = state.color

        state.color = Color.Red
        state.color = Color.Blue

        assertEquals(Color.Red, state.previousColor)
        assertEquals(listOf("#0000FF", "#FF0000"), state.recentColors)
        state.setSecondaryFromPrimary()
        assertEquals(Color.Blue, state.secondaryColor)

        state.color = Color.Green
        state.swapPrimarySecondaryColors()
        assertEquals(Color.Blue, state.color)
        assertEquals(Color.Green, state.secondaryColor)

        state.usePreviousColor()
        assertEquals(Color.Green, state.color)
        assertTrue(original != state.color)
    }

    @Test
    fun eraser_keeps_selected_colour() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(100, 100)))
        state.color = Color.Red
        state.tool = Tool.Eraser
        assertEquals(Color.Red, state.color)
    }

    @Test
    fun adding_a_layer_updates_the_shared_document_history() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(100, 100)))
        state.addLayer()
        assertEquals(1, state.document.layers.size)
        assertTrue(state.undo())
        assertTrue(state.document.layers.isEmpty())
        assertFalse(state.undo())
    }

    @Test
    fun stroke_updates_tile_store_document_patch_and_visual_undo_history() {
        val state = EditorState(
            DocumentHistory(
                CanvasDocument(
                    id = "document",
                    width = 64,
                    height = 64,
                    layers = listOf(Layer("layer-1", "Ink", payload = LayerPayload.Raster())),
                ),
            ),
        )

        state.recordStroke(listOf(DrawPoint(12f, 12f)))

        val raster = state.document.layers.single().payload as LayerPayload.Raster
        assertTrue(raster.tileAddresses.isNotEmpty())
        assertTrue(state.tileStore.keys.isNotEmpty())
        assertTrue(state.undo())
        assertTrue((state.document.layers.single().payload as LayerPayload.Raster).tileAddresses.isEmpty())
        assertTrue(state.tileStore.keys.isEmpty())
        assertTrue(state.redo())
        assertTrue(state.tileStore.keys.isNotEmpty())
    }
}
