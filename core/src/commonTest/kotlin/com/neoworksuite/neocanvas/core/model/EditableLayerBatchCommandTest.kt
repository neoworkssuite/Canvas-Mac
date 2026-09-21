package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditableLayerBatchCommandTest {
    @Test
    fun duplicate_editable_layers_preserves_order_group_and_is_one_undo_step() {
        val initial = CanvasDocument(
            id = "duplicate-batch",
            width = 300,
            height = 200,
            groups = listOf(LayerGroup("g1", "Objects")),
            layers = listOf(
                Layer("base", "Base", payload = LayerPayload.Raster()),
                Layer("text", "Text", groupId = "g1", payload = LayerPayload.TextObject(
                    text = "Title", x = 20f, y = 30f, width = 100f, height = 40f,
                )),
                Layer("shape", "Shape", groupId = "g1", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Rectangle, x = 140f, y = 30f, width = 60f, height = 50f,
                )),
            ),
        )
        val history = DocumentHistory(initial)
        val command = DuplicateEditableLayers(
            mapOf("text" to "text-copy", "shape" to "shape-copy"),
        )

        history.execute(command)

        assertEquals(listOf("base", "text", "text-copy", "shape", "shape-copy"), history.current.layers.map(Layer::id))
        val copiedText = history.current.layers.first { it.id == "text-copy" }
        assertEquals("g1", copiedText.groupId)
        val copiedTextPayload = copiedText.payload as LayerPayload.TextObject
        assertEquals(40f, copiedTextPayload.x)
        assertEquals(50f, copiedTextPayload.y)
        assertTrue(history.undo())
        assertEquals(initial, history.current)
    }

    @Test
    fun deleting_layers_cleans_empty_groups_and_keeps_one_layer() {
        val initial = CanvasDocument(
            id = "delete-batch",
            width = 100,
            height = 100,
            groups = listOf(LayerGroup("g1", "Objects")),
            layers = listOf(
                Layer("base", "Base", payload = LayerPayload.Raster()),
                Layer("a", "A", groupId = "g1", payload = LayerPayload.TextObject("A")),
                Layer("b", "B", groupId = "g1", payload = LayerPayload.TextObject("B")),
            ),
        )

        val deleted = DeleteLayers(setOf("a", "b")).apply(initial)
        assertEquals(listOf("base"), deleted.layers.map(Layer::id))
        assertTrue(deleted.groups.isEmpty())
    }
}
