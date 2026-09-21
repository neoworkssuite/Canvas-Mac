package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectArrangeSelectionStateTest {
    @Test
    fun marquee_selection_filters_locked_objects() {
        val document = CanvasDocument(
            id = "marquee-state",
            width = 300,
            height = 200,
            layers = listOf(
                Layer("free", "Free", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Rectangle, x = 0f, y = 0f, width = 50f, height = 50f,
                )),
                Layer("locked", "Locked", locked = true, payload = LayerPayload.TextObject(
                    text = "Locked", x = 60f, y = 0f, width = 80f, height = 50f,
                )),
            ),
        )
        val state = EditorState(DocumentHistory(document))

        assertEquals(1, state.setObjectArrangeSelection(setOf("free", "locked")))
        assertEquals(setOf("free"), state.selectedObjectLayerIds)
        assertEquals("1 object marked for Arrange", state.statusMessage)
    }
}
