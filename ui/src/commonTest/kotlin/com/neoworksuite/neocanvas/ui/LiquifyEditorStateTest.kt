package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.renderer.LiquifyMode
import com.neoworksuite.neocanvas.renderer.TileFormat
import com.neoworksuite.neocanvas.renderer.TileKey
import com.neoworksuite.neocanvas.renderer.TileStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiquifyEditorStateTest {
    private fun stateWithPixels(): EditorState {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        for (y in 10..22) for (x in 10..22) {
            val offset = (y * 256 + x) * 4
            bytes[offset] = 220.toByte()
            bytes[offset + 1] = 40
            bytes[offset + 2] = 30
            bytes[offset + 3] = 255.toByte()
        }
        val key = TileKey("paint", 0, 0)
        val document = CanvasDocument(
            id = "liquify-editor",
            width = 64,
            height = 64,
            layers = listOf(Layer("paint", "Paint", payload = LayerPayload.Raster(setOf(key)))),
        )
        return EditorState(
            DocumentHistory(document),
            tileStore = TileStore(mapOf(key to bytes)),
        ).apply {
            activeLayerId = "paint"
        }
    }

    @Test
    fun liquify_preview_and_commit_use_existing_raster_undo_pipeline() {
        val state = stateWithPixels()
        assertTrue(state.activateLiquifyTool())
        state.liquifyMode = LiquifyMode.Push
        state.liquifySize = 24f
        state.liquifyStrength = 1f
        val before = state.tileStore.snapshot()

        val preview = state.previewLiquify(
            listOf(DrawPoint(16f, 16f), DrawPoint(28f, 16f)),
            stabilize = false,
        )
        assertNotNull(preview)
        assertFalse(preview.keys.isEmpty())

        state.recordStroke(listOf(DrawPoint(16f, 16f), DrawPoint(28f, 16f)), stabilize = false)
        assertTrue(state.canUndo)
        assertTrue(state.undo())
        val restored = state.tileStore.snapshot()
        assertTrue(before.keys == restored.keys)
        assertTrue(before.all { (key, pixels) -> restored[key]?.contentEquals(pixels) == true })
    }

    @Test
    fun liquify_refuses_editable_object_layers() {
        val state = EditorState(DocumentHistory(CanvasDocument(
            id = "liquify-text",
            width = 100,
            height = 100,
            layers = listOf(Layer("text", "Text", payload = LayerPayload.TextObject("Text"))),
        )))
        state.activeLayerId = "text"

        assertFalse(state.activateLiquifyTool())
        assertTrue(state.statusMessage?.contains("raster layer") == true)
    }
}
