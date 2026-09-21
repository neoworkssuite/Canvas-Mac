package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObjectArrangeLifecycleTest {
    @Test
    fun marked_objects_duplicate_as_new_marked_set_then_delete_atomically() {
        val initial = CanvasDocument(
            id = "arrange-lifecycle",
            width = 300,
            height = 200,
            layers = listOf(
                Layer("base", "Base", payload = LayerPayload.Raster()),
                Layer("text", "Text", payload = LayerPayload.TextObject(
                    text = "Title", x = 20f, y = 20f, width = 100f, height = 40f,
                )),
                Layer("shape", "Shape", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Rectangle, x = 140f, y = 20f, width = 60f, height = 50f,
                )),
            ),
        )
        val state = EditorState(DocumentHistory(initial))
        assertTrue(state.toggleObjectArrangeSelection("text"))
        assertTrue(state.toggleObjectArrangeSelection("shape"))

        assertTrue(state.duplicateSelectedObjects())
        assertEquals(5, state.document.layers.size)
        assertEquals(2, state.selectedObjectCount)
        val duplicateIds = state.selectedObjectLayerIds
        assertTrue(duplicateIds.all { id -> state.document.layers.any { it.id == id && it.name.endsWith(" copy") } })

        assertTrue(state.deleteSelectedObjects())
        assertEquals(3, state.document.layers.size)
        assertEquals(0, state.selectedObjectCount)
        assertTrue(state.undo())
        assertEquals(5, state.document.layers.size)
    }

    @Test
    fun deleting_all_remaining_marked_layers_is_refused() {
        val state = EditorState(DocumentHistory(CanvasDocument(
            id = "arrange-delete-guard",
            width = 100,
            height = 100,
            layers = listOf(Layer(
                "only",
                "Only",
                payload = LayerPayload.TextObject("Only"),
            )),
        )))
        assertTrue(state.toggleObjectArrangeSelection("only"))
        assertFalse(state.deleteSelectedObjects())
        assertEquals("Keep at least one drawing layer.", state.statusMessage)
    }
}
