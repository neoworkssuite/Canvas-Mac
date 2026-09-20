package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.renderer.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticSelectionTest {
    private fun pixel(state: EditorState, x: Int, y: Int): IntArray {
        val layer = state.activeLayerId!!
        val bytes = state.tileStore.read(TileKey(layer, x / 256, y / 256)) ?: return intArrayOf(0, 0, 0, 0)
        val index = ((y % 256) * 256 + x % 256) * 4
        return IntArray(4) { channel -> bytes[index + channel].toInt() and 255 }
    }

    private fun preparedState(): EditorState {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(8, 8)))
        state.insertImage(
            ImportedImage(
                "Strip",
                4,
                1,
                intArrayOf(
                    0xFFFF0000.toInt(),
                    0xFFF02000.toInt(),
                    0xFF0000FF.toInt(),
                    0xFFFF0000.toInt(),
                ),
            ),
        )
        state.cancelTransform()
        state.clearSelection()
        state.selectionMode = SelectionShape.Automatic
        return state
    }

    @Test
    fun automatic_selection_uses_active_layer_tolerance_and_connectivity() {
        val state = preparedState()
        state.automaticSelectionTolerancePercent = 0
        state.selectArea(listOf(DrawPoint(2.2f, 3.2f)))
        assertTrue(state.selection!!.contains(2, 3))
        assertFalse(state.selection!!.contains(3, 3))
        assertFalse(state.selection!!.contains(5, 3))

        state.automaticSelectionTolerancePercent = 16
        state.selectArea(listOf(DrawPoint(2.2f, 3.2f)))
        assertTrue(state.selection!!.contains(2, 3))
        assertTrue(state.selection!!.contains(3, 3))
        assertFalse(state.selection!!.contains(4, 3))
        assertFalse(state.selection!!.contains(5, 3))
        assertEquals(SelectionShape.Automatic, state.selection!!.shape)
    }

    @Test
    fun automatic_selection_clear_and_move_respect_the_pixel_mask() {
        val state = preparedState()
        state.automaticSelectionTolerancePercent = 0
        state.selectArea(listOf(DrawPoint(2.2f, 3.2f)))
        state.moveSelection(0, 1)

        assertEquals(0, pixel(state, 2, 3)[3])
        assertEquals(255, pixel(state, 2, 4)[3])
        assertEquals(255, pixel(state, 3, 3)[3])
        assertEquals(SelectionShape.Automatic, state.selection!!.shape)
        assertTrue(state.selection!!.contains(2, 4))
        assertFalse(state.selection!!.contains(3, 4))

        state.clearSelectedPixels()
        assertEquals(0, pixel(state, 2, 4)[3])
        assertEquals(255, pixel(state, 3, 3)[3])
        assertTrue(state.undo())
        assertEquals(255, pixel(state, 2, 4)[3])
    }
}
