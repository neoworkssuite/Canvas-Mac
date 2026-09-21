package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerMask
import com.neoworksuite.neocanvas.core.model.LayerPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjectArrangeDuplicateIdSafetyTest {
    @Test
    fun duplicate_marked_objects_skips_ids_already_used_by_masks() {
        val state = EditorState(
            DocumentHistory(
                CanvasDocument(
                    id = "duplicate-id-safety",
                    width = 200,
                    height = 120,
                    layers = listOf(
                        Layer("base", "Base", payload = LayerPayload.Raster()),
                        Layer(
                            "text",
                            "Text",
                            payload = LayerPayload.TextObject("Text"),
                            mask = LayerMask("layer-3"),
                        ),
                    ),
                ),
            ),
        )
        assertTrue(state.toggleObjectArrangeSelection("text"))
        assertTrue(state.duplicateSelectedObjects())
        assertEquals(setOf("layer-4"), state.selectedObjectLayerIds)
        assertTrue(state.document.layers.any { it.id == "layer-4" && it.mask?.id == "layer-4-mask" })
    }
}
