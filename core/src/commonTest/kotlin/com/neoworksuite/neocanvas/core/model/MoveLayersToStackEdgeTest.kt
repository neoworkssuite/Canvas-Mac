package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoveLayersToStackEdgeTest {
    private fun layer(id: String) = Layer(id, id, payload = LayerPayload.Raster())

    @Test
    fun marked_layers_move_to_front_as_a_block_preserving_relative_order() {
        val initial = CanvasDocument(
            id = "stack-front",
            width = 100,
            height = 100,
            layers = listOf(layer("a"), layer("b"), layer("c"), layer("d"), layer("e")),
        )
        val history = DocumentHistory(initial)

        history.execute(MoveLayersToStackEdge(setOf("b", "d"), toFront = true))

        assertEquals(listOf("a", "c", "e", "b", "d"), history.current.layers.map(Layer::id))
        assertTrue(history.undo())
        assertEquals(initial, history.current)
    }

    @Test
    fun marked_layers_move_to_back_as_a_block_preserving_relative_order() {
        val initial = CanvasDocument(
            id = "stack-back",
            width = 100,
            height = 100,
            layers = listOf(layer("a"), layer("b"), layer("c"), layer("d")),
        )

        val moved = MoveLayersToStackEdge(setOf("b", "d"), toFront = false).apply(initial)

        assertEquals(listOf("b", "d", "a", "c"), moved.layers.map(Layer::id))
    }
}
