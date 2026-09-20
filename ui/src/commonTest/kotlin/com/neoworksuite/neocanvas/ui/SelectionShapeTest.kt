package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import kotlin.test.*

class SelectionShapeTest {
    @Test fun ellipse_uses_curved_boundary_not_bounding_box() {
        val selection = CanvasSelection.ellipse(2, 2, 12, 10)
        assertTrue(selection.contains(7, 6))
        assertFalse(selection.contains(2, 2))
        assertTrue(selection.contains(7, 2))
    }
    @Test fun lasso_closes_polygon_and_rejects_outside_pixels() {
        val selection = CanvasSelection.lasso(listOf(DrawPoint(2f, 2f), DrawPoint(12f, 2f), DrawPoint(7f, 12f)))!!
        assertTrue(selection.contains(7, 5))
        assertFalse(selection.contains(2, 10))
        assertEquals(SelectionShape.Lasso, selection.shape)
    }
    @Test fun selection_add_subtract_and_intersect_combine_membership() {
        val a = CanvasSelection(0, 0, 8, 8)
        val b = CanvasSelection(4, 4, 12, 12)

        val added = a.combine(b, SelectionCombineMode.Add)!!
        assertTrue(added.contains(2, 2))
        assertTrue(added.contains(10, 10))

        val subtracted = a.combine(b, SelectionCombineMode.Subtract)!!
        assertTrue(subtracted.contains(2, 2))
        assertFalse(subtracted.contains(6, 6))

        val intersected = a.combine(b, SelectionCombineMode.Intersect)!!
        assertFalse(intersected.contains(2, 2))
        assertTrue(intersected.contains(6, 6))
    }

    @Test fun inverted_selection_flips_membership_inside_canvas() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.selectionMode = SelectionShape.Ellipse
        state.selectArea(listOf(DrawPoint(2f, 2f), DrawPoint(12f, 10f)))
        assertFalse(state.selection!!.contains(2, 2))
        state.invertSelection()
        assertTrue(state.selection!!.contains(2, 2))
        assertFalse(state.selection!!.contains(7, 6))
        assertEquals(CanvasSelection(0, 0, 16, 16), state.selection!!.copy(shape = SelectionShape.Rectangle, invertedRegion = null))
    }
}
