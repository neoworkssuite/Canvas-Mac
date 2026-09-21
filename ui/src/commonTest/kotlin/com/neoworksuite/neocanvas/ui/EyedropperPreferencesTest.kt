package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.renderer.TileFormat
import com.neoworksuite.neocanvas.renderer.TileKey
import com.neoworksuite.neocanvas.renderer.TileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EyedropperPreferencesTest {
    private fun colouredTile(red: Int, green: Int, blue: Int, x: Int = 10, y: Int = 10): ByteArray {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        val offset = (y * 256 + x) * 4
        bytes[offset] = red.toByte()
        bytes[offset + 1] = green.toByte()
        bytes[offset + 2] = blue.toByte()
        bytes[offset + 3] = 255.toByte()
        return bytes
    }

    private fun state(): EditorState {
        val lowerKey = TileKey("lower", 0, 0)
        val upperKey = TileKey("upper", 0, 0)
        val document = CanvasDocument(
            id = "eyedropper",
            width = 64,
            height = 64,
            layers = listOf(
                Layer("lower", "Lower", payload = LayerPayload.Raster(setOf(lowerKey))),
                Layer("upper", "Upper", payload = LayerPayload.Raster(setOf(upperKey))),
            ),
        )
        return EditorState(
            DocumentHistory(document),
            tileStore = TileStore(
                mapOf(
                    lowerKey to colouredTile(240, 20, 20),
                    upperKey to colouredTile(20, 40, 240),
                ),
            ),
        ).apply {
            activeLayerId = "lower"
        }
    }

    @Test
    fun merged_and_active_layer_sampling_are_configurable() {
        val state = state()

        state.eyedropperSampleMerged = true
        state.activateTool(Tool.Eyedropper)
        state.applyPointTool(DrawPoint(10f, 10f))
        assertTrue(state.color.blue > state.color.red)
        assertEquals(Tool.Brush, state.tool)

        state.eyedropperSampleMerged = false
        state.eyedropperReturnAfterSample = false
        state.activateTool(Tool.Eyedropper)
        state.applyPointTool(DrawPoint(10f, 10f))
        assertTrue(state.color.red > state.color.blue)
        assertEquals(Tool.Eyedropper, state.tool)
    }

    @Test
    fun merged_sampling_honours_layer_opacity() {
        val state = state()
        val upper = state.document.layers.last()
        state.setLayerOpacity(upper.id, 0f)
        state.eyedropperSampleMerged = true
        state.activateTool(Tool.Eyedropper)
        state.applyPointTool(DrawPoint(10f, 10f))

        assertTrue(state.color.red > state.color.blue)
    }
}
