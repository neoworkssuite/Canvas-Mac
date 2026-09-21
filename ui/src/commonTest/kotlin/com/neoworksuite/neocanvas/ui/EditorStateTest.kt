package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.LayerGroup
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun group_editor_controls_active_layer_membership_and_collapsed_state() {
        val initial = CanvasDocument(
            id = "groups-ui",
            width = 32,
            height = 32,
            layers = listOf(Layer("layer-1", "Paint", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial))

        assertTrue(state.addGroupFromActive())
        val group = state.document.groups.single()
        assertEquals(group.id, state.document.layers.single().groupId)

        state.toggleGroupCollapsed(group.id)
        state.toggleGroupVisibility(group.id)
        state.toggleGroupLocked(group.id)
        state.setGroupOpacity(group.id, .5f)

        val updated = state.document.groups.single()
        assertTrue(updated.collapsed)
        assertFalse(updated.visible)
        assertTrue(updated.locked)
        assertEquals(.5f, updated.opacity)

        state.setActiveLayerGroup(null)
        assertEquals(null, state.document.layers.single().groupId)
    }

    @Test
    fun mask_editing_changes_mask_pixels_without_changing_layer_pixels() {
        val paintAddress = TileAddress("layer-1", 0, 0)
        val paint = ByteArray(com.neoworksuite.neocanvas.renderer.TileFormat.BYTES_PER_TILE).apply {
            val offset = (12 * 256 + 12) * 4
            this[offset] = 255.toByte()
            this[offset + 3] = 255.toByte()
        }
        val initial = CanvasDocument(
            id = "mask-edit",
            width = 64,
            height = 64,
            layers = listOf(
                Layer("layer-1", "Paint", payload = LayerPayload.Raster(setOf(paintAddress))),
            ),
        )
        val state = EditorState(
            DocumentHistory(initial),
            tileStore = com.neoworksuite.neocanvas.renderer.TileStore(mapOf(paintAddress to paint)),
        )
        assertTrue(state.addMaskToActiveLayer())
        state.color = Color.Black
        state.brushSize = 4f
        state.recordStroke(listOf(DrawPoint(12f, 12f)))

        assertContentEquals(paint, state.tileStore.read(paintAddress))
        val mask = state.document.layers.single().mask!!
        val maskAddress = TileAddress(mask.id, 0, 0)
        val maskBytes = assertNotNull(state.tileStore.read(maskAddress))
        val offset = (12 * 256 + 12) * 4
        assertTrue((maskBytes[offset].toInt() and 255) < 255)
        assertTrue(maskAddress in state.tilesForDocument())
        val rendered = com.neoworksuite.neocanvas.renderer.PngExporter.render(
            state.document,
            state.tilesForDocument(),
        )
        assertTrue((rendered.rgbaAt(12, 12)[3].toInt() and 255) < 255)
    }

    @Test
    fun locked_group_blocks_layer_painting() {
        val initial = CanvasDocument(
            id = "group-lock",
            width = 64,
            height = 64,
            layers = listOf(
                Layer("layer-1", "Paint", payload = LayerPayload.Raster(), groupId = "group-1"),
            ),
            groups = listOf(LayerGroup("group-1", "Locked", locked = true)),
        )
        val state = EditorState(DocumentHistory(initial))
        state.recordStroke(listOf(DrawPoint(12f, 12f)))

        assertTrue(state.tileStore.keys.isEmpty())
        assertEquals("Unlock this group before editing its layers", state.statusMessage)
    }

    @Test
    fun deep_layers_sleep_hidden_pixels_and_wake_losslessly_on_visibility() {
        val dormant = mutableMapOf<Pair<String, String>, ByteArray>()
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsDeepLayers = true
            override fun loadDormantLayer(documentId: String, layerId: String): ByteArray? =
                dormant[documentId to layerId]
            override fun saveDormantLayer(documentId: String, layerId: String, bytes: ByteArray): SaveResult {
                dormant[documentId to layerId] = bytes
                return SaveResult.Success
            }
            override fun deleteDormantLayer(documentId: String, layerId: String): SaveResult {
                dormant.remove(documentId to layerId)
                return SaveResult.Success
            }
        }
        val address = TileAddress("hidden", 0, 0)
        val pixels = ByteArray(com.neoworksuite.neocanvas.renderer.TileFormat.BYTES_PER_TILE).apply {
            this[0] = 91
            this[3] = 255.toByte()
        }
        val document = CanvasDocument(
            id = "deep-test",
            width = 64,
            height = 64,
            layers = listOf(
                Layer("hidden", "Hidden", visible = false, payload = LayerPayload.Raster(setOf(address))),
                Layer("visible", "Visible", payload = LayerPayload.Raster()),
            ),
        )
        val state = EditorState(
            DocumentHistory(document),
            actions,
            com.neoworksuite.neocanvas.renderer.TileStore(mapOf(address to pixels)),
        )

        assertEquals(1, state.sleepHiddenLayers())
        assertTrue(state.isLayerDormant("hidden"))
        assertEquals(null, state.tileStore.read(address))
        assertContentEquals(pixels, state.tilesForDocument().getValue(address))

        state.toggleLayerVisibility("hidden")

        assertFalse(state.isLayerDormant("hidden"))
        assertTrue(state.document.layers.first { it.id == "hidden" }.visible)
        assertContentEquals(pixels, state.tileStore.read(address))
        assertTrue(dormant.isEmpty())
    }

    @Test
    fun undo_wakes_sleeping_layer_before_sparse_history_moves() {
        val dormant = mutableMapOf<Pair<String, String>, ByteArray>()
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsDeepLayers = true
            override fun loadDormantLayer(documentId: String, layerId: String): ByteArray? =
                dormant[documentId to layerId]
            override fun saveDormantLayer(documentId: String, layerId: String, bytes: ByteArray): SaveResult {
                dormant[documentId to layerId] = bytes
                return SaveResult.Success
            }
            override fun deleteDormantLayer(documentId: String, layerId: String): SaveResult {
                dormant.remove(documentId to layerId)
                return SaveResult.Success
            }
        }
        val initial = CanvasDocument(
            id = "deep-undo",
            width = 64,
            height = 64,
            layers = listOf(Layer("layer-1", "Paint", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial), actions)
        state.brushSize = 2f
        state.recordStroke(listOf(DrawPoint(12f, 12f)))
        val painted = state.tileStore.snapshot()
        state.toggleLayerVisibility("layer-1")
        assertEquals(1, state.sleepHiddenLayers())

        assertTrue(state.undo())

        assertFalse(state.isLayerDormant("layer-1"))
        assertTrue(state.document.layers.single().visible)
        painted.forEach { (key, bytes) -> assertContentEquals(bytes, state.tileStore.read(key)) }
    }

    @Test
    fun raster_history_retains_only_changed_tiles_and_metadata_retains_none() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(1024, 1024)))
        assertEquals(0L, state.retainedRasterHistoryBytes)

        state.addLayer()
        assertEquals(0L, state.retainedRasterHistoryBytes)

        val image = ImportedImage(
            "Large",
            512,
            512,
            IntArray(512 * 512) { 0xFF336699.toInt() },
        )
        state.insertImage(image)
        val afterImport = state.retainedRasterHistoryBytes
        assertEquals(4L * com.neoworksuite.neocanvas.renderer.TileFormat.BYTES_PER_TILE, afterImport)

        state.cancelTransform()
        state.activateTool(Tool.Brush)
        state.brushSize = 2f
        state.recordStroke(listOf(DrawPoint(400f, 400f)))

        assertEquals(
            afterImport + 2L * com.neoworksuite.neocanvas.renderer.TileFormat.BYTES_PER_TILE,
            state.retainedRasterHistoryBytes,
        )
    }

    @Test
    fun editable_recent_strokes_replay_later_strokes_after_an_earlier_edit() {
        val initial = CanvasDocument(
            id = "editable-strokes",
            width = 64,
            height = 64,
            layers = listOf(Layer("layer-1", "Paint", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial))
        state.selectBrush(com.neoworksuite.neocanvas.brushes.BuiltInBrushes.ink)
        state.brushSize = 4f

        state.color = Color.Red
        state.recordStroke(listOf(DrawPoint(10f, 10f)))
        state.color = Color.Blue
        state.recordStroke(listOf(DrawPoint(50f, 50f)))

        assertEquals(2, state.recentEditableStrokes.size)
        val firstId = state.recentEditableStrokes.first().id
        val key = com.neoworksuite.neocanvas.renderer.TileKey("layer-1", 0, 0)
        val beforeEdit = state.tileStore.read(key)!!.copyOf()
        val secondPixel = (50 * 256 + 50) * 4
        val laterStrokePixel = beforeEdit.copyOfRange(secondPixel, secondPixel + 4)

        state.color = Color.Green
        assertTrue(state.useCurrentColourForEditableStroke(firstId))

        val after = state.tileStore.read(key)!!
        assertContentEquals(laterStrokePixel, after.copyOfRange(secondPixel, secondPixel + 4))
        val firstPixel = (10 * 256 + 10) * 4
        assertTrue((after[firstPixel + 1].toInt() and 255) > (after[firstPixel].toInt() and 255))
    }

    @Test
    fun non_stroke_edit_closes_the_recent_stroke_chain() {
        val initial = CanvasDocument(
            id = "editable-reset",
            width = 64,
            height = 64,
            layers = listOf(Layer("layer-1", "Paint", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial))
        state.recordStroke(listOf(DrawPoint(12f, 12f)))
        assertEquals(1, state.recentEditableStrokes.size)

        state.addLayer()

        assertTrue(state.recentEditableStrokes.isEmpty())
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
    fun editable_text_and_shape_state_updates_are_metadata_only_and_undoable() {
        val initial = CanvasDocument(
            id = "objects-ui",
            width = 800,
            height = 600,
            layers = listOf(Layer("layer-1", "Paint", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial))

        state.addTextObject()
        val textId = state.activeLayerId!!
        assertTrue(state.objectEditorVisible)
        assertIs<LayerPayload.TextObject>(state.document.layers.last().payload)
        assertTrue(state.tileStore.keys.isEmpty())

        state.setActiveTextContent("NeoCanvas")
        state.setActiveTextAlignment(com.neoworksuite.neocanvas.core.model.TextAlignment.Right)
        state.rotateActiveObject(15f)
        assertEquals("NeoCanvas", state.activeTextObject!!.text)
        assertEquals(com.neoworksuite.neocanvas.core.model.TextAlignment.Right, state.activeTextObject!!.alignment)
        assertEquals(15f, state.activeTextObject!!.rotationDegrees)

        state.closeObjectEditor()
        state.addShapeObject(com.neoworksuite.neocanvas.core.model.ShapeKind.Ellipse)
        assertIs<LayerPayload.ShapeObject>(state.document.layers.last().payload)
        assertTrue(state.tileStore.keys.isEmpty())

        state.useCurrentColourForActiveObject(asStroke = true)
        state.setActiveShapeStrokeWidth(12f)
        state.scaleActiveObject(1.1f)
        assertEquals(12f, state.activeShapeObject!!.strokeWidth)

        assertTrue(state.undo())
        state.selectLayer(textId)
        assertTrue(state.openObjectEditor(textId))
    }

    @Test
    fun raster_tools_ignore_editable_object_layers() {
        val initial = CanvasDocument(
            id = "object-raster-guard",
            width = 400,
            height = 300,
            layers = listOf(
                Layer("text-1", "Text", payload = LayerPayload.TextObject(
                    "Hello", x = 20f, y = 20f, width = 200f, height = 80f,
                )),
            ),
        )
        val state = EditorState(DocumentHistory(initial))
        state.activeLayerId = "text-1"
        state.tool = Tool.Brush
        state.recordStroke(listOf(DrawPoint(40f, 40f)))
        assertTrue(state.tileStore.keys.isEmpty())
        assertIs<LayerPayload.TextObject>(state.document.layers.single().payload)
    }

    @Test
    fun workbench_items_persist_without_entering_document_exports() {
        var snapshot: ByteArray? = null
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsWorkbench = true
            override fun loadWorkbench(documentId: String): ByteArray? = snapshot
            override fun saveWorkbench(documentId: String, bytes: ByteArray): SaveResult {
                snapshot = bytes
                return SaveResult.Success
            }
        }
        val initial = CanvasDocument(
            id = "desk-test",
            width = 64,
            height = 64,
            layers = listOf(Layer("layer-1", "Sketch", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial), actions)
        val before = state.document
        assertTrue(state.addWorkbenchNote("Client wants warmer shadows"))
        state.color = Color.Red
        state.addWorkbenchColourCard()

        assertEquals(2, state.workbenchItems.size)
        assertEquals(before, state.document)
        assertTrue(state.tilesForDocument().isEmpty())
        assertNotNull(snapshot)

        val reopened = EditorState(DocumentHistory(initial), actions)
        assertEquals(2, reopened.workbenchItems.size)
        assertTrue(reopened.workbenchItems.any { it is WorkbenchItem.Note })
        assertTrue(reopened.workbenchItems.any { it is WorkbenchItem.ColourCard })
    }

    @Test
    fun workbench_codec_round_trips_reference_pixels_and_positions() {
        val encoded = WorkbenchCodec.encode(listOf(
            WorkbenchItem.Reference(
                id = "ref-1",
                name = "Mood",
                pixelWidth = 2,
                pixelHeight = 1,
                argb = intArrayOf(0xFFFF0000.toInt(), 0x800000FF.toInt()),
                x = 900f,
                y = 40f,
                width = 320f,
                height = 160f,
            ),
            WorkbenchItem.Note("note-1", "Remember texture", 40f, 700f),
        ))
        val decoded = WorkbenchCodec.decode(encoded)
        assertEquals(2, decoded.size)
        val reference = decoded[0] as WorkbenchItem.Reference
        assertEquals("Mood", reference.name)
        assertTrue(reference.argb.contentEquals(intArrayOf(0xFFFF0000.toInt(), 0x800000FF.toInt())))
        assertEquals(900f, reference.x)
        assertEquals("Remember texture", (decoded[1] as WorkbenchItem.Note).text)
    }

    @Test
    fun workbench_pro_lock_resize_and_colour_sampling_persist() {
        var snapshot: ByteArray? = null
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsWorkbench = true
            override fun loadWorkbench(documentId: String): ByteArray? = snapshot
            override fun saveWorkbench(documentId: String, bytes: ByteArray): SaveResult {
                snapshot = bytes
                return SaveResult.Success
            }
        }
        val initial = CanvasDocument(
            id = "desk-pro",
            width = 64,
            height = 64,
            layers = listOf(Layer("layer-1", "Sketch", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial), actions)
        state.color = Color.Red
        state.addWorkbenchColourCard()
        val id = state.workbenchItems.single().id
        val originalWidth = state.workbenchItems.single().width

        state.resizeWorkbenchItem(id, 1.25f)
        assertTrue(state.workbenchItems.single().width > originalWidth)
        state.toggleWorkbenchItemLocked(id)
        assertTrue(state.workbenchItems.single().locked)
        val lockedX = state.workbenchItems.single().x
        state.moveWorkbenchItem(id, 100f, 100f)
        assertEquals(lockedX, state.workbenchItems.single().x)

        state.color = Color.Blue
        assertTrue(state.useWorkbenchColour(id))
        assertEquals(Color.Red, state.color)

        val reopened = EditorState(DocumentHistory(initial), actions)
        assertTrue(reopened.workbenchItems.single().locked)
        assertTrue(reopened.workbenchItems.single().width > originalWidth)
    }

    @Test
    fun workbench_reference_rotate_duplicate_and_promote_are_isolated_until_promotion() {
        val initial = CanvasDocument(
            id = "desk-ref",
            width = 32,
            height = 32,
            layers = listOf(Layer("layer-1", "Sketch", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial))
        val reference = WorkbenchItem.Reference(
            id = "ref-test",
            name = "Mood",
            pixelWidth = 2,
            pixelHeight = 1,
            argb = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
            x = 100f,
            y = 40f,
            width = 200f,
            height = 100f,
        )
        val encoded = WorkbenchCodec.encode(listOf(reference))
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsWorkbench = true
            override fun loadWorkbench(documentId: String): ByteArray? = encoded
            override fun saveWorkbench(documentId: String, bytes: ByteArray): SaveResult = SaveResult.Success
        }
        val working = EditorState(DocumentHistory(initial), actions)
        val before = working.document

        assertTrue(working.rotateWorkbenchReference("ref-test"))
        val rotated = working.workbenchItems.first() as WorkbenchItem.Reference
        assertEquals(1, rotated.pixelWidth)
        assertEquals(2, rotated.pixelHeight)
        assertEquals(before, working.document)

        assertTrue(working.duplicateWorkbenchItem("ref-test"))
        assertEquals(2, working.workbenchItems.size)
        assertEquals(before, working.document)

        assertTrue(working.promoteWorkbenchReference("ref-test"))
        assertEquals(2, working.document.layers.size)
        assertEquals("Mood", working.document.layers.last().name)
        assertEquals(Tool.MoveSelection, working.tool)
        assertNotNull(working.transformSession)
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
        assertEquals("Restored Main version — current work was kept as Before restore", state.statusMessage)
    }

    @Test
    fun version_compare_is_read_only_and_reports_structure_changes() {
        val saved = mutableListOf<Pair<LocalVersionEntry, LoadResult.Success>>()
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsVersions = true
            override fun listVersions(documentId: String): List<LocalVersionEntry> = saved.map { it.first }
            override fun loadVersion(documentId: String, versionId: String): LoadResult =
                saved.firstOrNull { it.first.id == versionId }?.second
                    ?: LoadResult.Failure("Missing version")
        }

        val current = CanvasDocument(
            id = "compare-test",
            width = 64,
            height = 48,
            layers = listOf(
                Layer("layer-1", "Paint", payload = LayerPayload.Raster()),
                Layer("layer-2", "Details", payload = LayerPayload.Raster()),
            ),
        )
        val versionDocument = CanvasDocument(
            id = "compare-test",
            width = 32,
            height = 32,
            layers = listOf(Layer("layer-1", "Paint", payload = LayerPayload.Raster())),
        )
        val entry = LocalVersionEntry("1000__Sketch.neoversion", "Sketch", 1000L)
        saved += entry to LoadResult.Success(versionDocument, emptyMap())

        val state = EditorState(DocumentHistory(current), actions)
        state.openVersions()
        val before = state.document

        assertTrue(state.compareVersion(entry.id))

        val comparison = assertNotNull(state.versionComparison)
        assertEquals(before, state.document)
        assertEquals(64, comparison.currentWidth)
        assertEquals(48, comparison.currentHeight)
        assertEquals(32, comparison.versionWidth)
        assertEquals(32, comparison.versionHeight)
        assertEquals(2, comparison.currentLayerCount)
        assertEquals(1, comparison.versionLayerCount)
        assertTrue(comparison.dimensionsChanged)
        assertEquals(1, comparison.layerDelta)
    }

    @Test
    fun version_tree_branch_restores_source_and_persists_branch_start() {
        data class Stored(val entry: LocalVersionEntry, val load: LoadResult.Success)
        val saved = mutableListOf<Stored>()
        var clock = 2000L
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsVersions = true
            override val supportsVersionBranches = true
            override fun listVersions(documentId: String): List<LocalVersionEntry> =
                saved.map { it.entry }.sortedByDescending { it.createdAtEpochMillis }

            override fun createVersionOnBranch(
                label: String,
                branch: String,
                parentVersionId: String?,
                document: CanvasDocument,
                tiles: Map<TileAddress, ByteArray>,
            ): SaveResult {
                val id = (clock++).toString() + "~" + branch + "~root~" + label + ".neoversion"
                saved += Stored(
                    LocalVersionEntry(id, label, clock, branch, parentVersionId),
                    LoadResult.Success(document, tiles),
                )
                return SaveResult.Success
            }

            override fun loadVersion(documentId: String, versionId: String): LoadResult =
                saved.firstOrNull { it.entry.id == versionId }?.load
                    ?: LoadResult.Failure("Missing version")
        }

        val initial = CanvasDocument(
            id = "branch-test",
            width = 32,
            height = 32,
            layers = listOf(Layer("layer-1", "Sketch", payload = LayerPayload.Raster())),
        )
        val state = EditorState(DocumentHistory(initial), actions)

        assertTrue(state.createVersion("Sketch"))
        val source = state.versions.single()
        state.addLayer()
        assertEquals(2, state.document.layers.size)

        assertTrue(state.branchFromVersion(source.id, "Client B"))

        assertEquals("Client B", state.activeVersionBranch)
        assertEquals(1, state.document.layers.size)
        assertTrue(state.versions.any { it.branch == "Main" && it.label == "Before branch" })
        assertTrue(state.versions.any { it.branch == "Client B" && it.label == "Branch start" })
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
