package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.geometry.Offset
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.renderer.TileFormat
import com.neoworksuite.neocanvas.renderer.TileKey
import com.neoworksuite.neocanvas.renderer.TileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkClipboardTest {
    @Test
    fun three_finger_horizontal_swipe_opens_clipboard_without_replacing_three_finger_tap() {
        assertTrue(isThreeFingerClipboardSwipe(
            maxTouchCount = 3,
            durationMillis = 420,
            start = Offset(40f, 80f),
            end = Offset(120f, 86f),
            touchSlop = 10f,
            stylusSeen = false,
        ))
        assertFalse(isThreeFingerClipboardSwipe(
            3, 220, Offset(40f, 80f), Offset(44f, 81f), 10f, false,
        ))
        assertFalse(isThreeFingerClipboardSwipe(
            2, 420, Offset(40f, 80f), Offset(120f, 86f), 10f, false,
        ))
    }

    @Test
    fun copy_and_paste_preserve_selected_raster_pixels_as_a_new_layer() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        fun paint(x: Int, y: Int, red: Int) {
            val offset = (y * 256 + x) * 4
            bytes[offset] = red.toByte()
            bytes[offset + 1] = 20
            bytes[offset + 2] = 30
            bytes[offset + 3] = 255.toByte()
        }
        paint(10, 10, 220)
        paint(20, 20, 180)
        val sourceKey = TileKey("paint", 0, 0)
        val state = EditorState(
            DocumentHistory(
                CanvasDocument(
                    id = "clipboard",
                    width = 64,
                    height = 64,
                    layers = listOf(Layer("paint", "Paint", payload = LayerPayload.Raster(setOf(sourceKey)))),
                ),
            ),
            tileStore = TileStore(mapOf(sourceKey to bytes)),
        )
        state.activeLayerId = "paint"
        state.selectRectangle(DrawPoint(8f, 8f), DrawPoint(14f, 14f))

        assertTrue(state.copySelectionToArtworkClipboard())
        assertTrue(state.hasArtworkClipboard)
        assertTrue(state.pasteArtworkClipboard())
        assertEquals(2, state.document.layers.size)
        assertEquals("Pasted artwork", state.document.layers.last().name)
        assertEquals(Tool.MoveSelection, state.tool)

        val pastedId = state.document.layers.last().id
        val pasted = state.tileStore.read(TileKey(pastedId, 0, 0))
        requireNotNull(pasted)
        val expectedX = 26
        val expectedY = 26
        val offset = (expectedY * 256 + expectedX) * 4
        assertEquals(220, pasted[offset].toInt() and 255)
        assertEquals(255, pasted[offset + 3].toInt() and 255)
    }
}
