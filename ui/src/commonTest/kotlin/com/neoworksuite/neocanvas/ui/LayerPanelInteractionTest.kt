package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayerPanelInteractionTest {
    @Test fun dragging_a_displayed_layer_down_reorders_it_and_undo_restores_the_stack() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer() // layer-1, bottom
        state.addLayer() // layer-2
        state.addLayer() // layer-3, top and active

        assertTrue(state.reorderActiveLayerInDisplay(1))

        assertEquals(listOf("layer-1", "layer-3", "layer-2"), state.document.layers.map { it.id })
        assertTrue(state.undo())
        assertEquals(listOf("layer-1", "layer-2", "layer-3"), state.document.layers.map { it.id })
    }

    @Test fun dragging_past_the_display_edge_is_a_no_op() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.addLayer()
        val original = state.document

        assertEquals(false, state.reorderActiveLayerInDisplay(-1))
        assertEquals(original, state.document)
    }

    @Test fun only_the_handle_can_reorder_a_layer() {
        assertTrue(allowsLayerReorder(LayerDragRegion.Handle))
        assertEquals(false, allowsLayerReorder(LayerDragRegion.Body))
        assertEquals(false, allowsLayerReorder(LayerDragRegion.Controls))
    }
    @Test fun selected_layer_controls_remain_actions_not_reorder_regions() {
        assertEquals(false, allowsLayerReorder(LayerDragRegion.Controls))
        assertTrue(allowsLayerReorder(LayerDragRegion.Handle))
    }
}

