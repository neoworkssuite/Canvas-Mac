package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectArrangeStackOrderTest {
    @Test
    fun marked_objects_move_to_front_and_back_from_arrange_state() {
        val state = EditorState(DocumentHistory(CanvasDocument(
            id = "arrange-stack",
            width = 200,
            height = 100,
            layers = listOf(
                Layer("base", "Base", payload = LayerPayload.Raster()),
                Layer("a", "A", payload = LayerPayload.TextObject("A")),
                Layer("middle", "Middle", payload = LayerPayload.Raster()),
                Layer("b", "B", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Rectangle, width = 20f, height = 20f,
                )),
            ),
        )))
        assertTrue(state.toggleObjectArrangeSelection("a"))
        assertTrue(state.toggleObjectArrangeSelection("b"))

        assertTrue(state.moveSelectedObjectsToStackEdge(toFront = false))
        assertEquals(listOf("a", "b", "base", "middle"), state.document.layers.map(Layer::id))
        assertEquals("Moved marked objects to back", state.statusMessage)

        assertTrue(state.moveSelectedObjectsToStackEdge(toFront = true))
        assertEquals(listOf("base", "middle", "a", "b"), state.document.layers.map(Layer::id))
        assertEquals("Moved marked objects to front", state.statusMessage)
    }
}
