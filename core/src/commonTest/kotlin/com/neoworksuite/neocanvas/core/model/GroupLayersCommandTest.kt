package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupLayersCommandTest {
    private fun layer(id: String, groupId: String? = null) =
        Layer(id, id.uppercase(), payload = LayerPayload.Raster(), groupId = groupId)

    @Test
    fun grouping_multiple_layers_is_one_undoable_document_change() {
        val initial = CanvasDocument(
            id = "group-batch",
            width = 100,
            height = 100,
            layers = listOf(layer("a"), layer("b"), layer("c")),
        )
        val history = DocumentHistory(initial)

        history.execute(GroupLayers("g-new", "Marked objects", setOf("a", "b")))

        assertEquals("g-new", history.current.layers.first { it.id == "a" }.groupId)
        assertEquals("g-new", history.current.layers.first { it.id == "b" }.groupId)
        assertNull(history.current.layers.first { it.id == "c" }.groupId)
        assertEquals(listOf("g-new"), history.current.groups.map(LayerGroup::id))
        assertTrue(history.undo())
        assertEquals(initial, history.current)
    }

    @Test
    fun regroup_and_ungroup_only_remove_groups_that_become_empty() {
        val initial = CanvasDocument(
            id = "regroup-batch",
            width = 100,
            height = 100,
            layers = listOf(
                layer("a", "old"),
                layer("b", "old"),
                layer("c", "keep"),
            ),
            groups = listOf(
                LayerGroup("old", "Old"),
                LayerGroup("keep", "Keep"),
            ),
        )

        val regrouped = GroupLayers("new", "New", setOf("a", "b")).apply(initial)
        assertEquals(setOf("keep", "new"), regrouped.groups.mapTo(linkedSetOf(), LayerGroup::id))
        assertTrue(regrouped.layers.filter { it.id in setOf("a", "b") }.all { it.groupId == "new" })

        val partiallyUngrouped = UngroupLayers(setOf("a")).apply(regrouped)
        assertTrue(partiallyUngrouped.groups.any { it.id == "new" })
        assertNull(partiallyUngrouped.layers.first { it.id == "a" }.groupId)

        val fullyUngrouped = UngroupLayers(setOf("b")).apply(partiallyUngrouped)
        assertTrue(fullyUngrouped.groups.none { it.id == "new" })
        assertTrue(fullyUngrouped.groups.any { it.id == "keep" })
        assertEquals("keep", fullyUngrouped.layers.first { it.id == "c" }.groupId)
    }
}
