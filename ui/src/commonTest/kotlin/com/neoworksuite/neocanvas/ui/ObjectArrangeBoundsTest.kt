package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectArrangeBoundsTest {
    @Test
    fun arrange_bounds_cover_all_marked_editable_objects() {
        val bounds = editableObjectArrangeBounds(listOf(
            LayerPayload.ShapeObject(
                kind = ShapeKind.Rectangle,
                x = 10f,
                y = 20f,
                width = 40f,
                height = 30f,
            ),
            LayerPayload.TextObject(
                text = "Text",
                x = 100f,
                y = 80f,
                width = 60f,
                height = 20f,
            ),
        ))
        requireNotNull(bounds)
        assertEquals(10f, bounds.left, .001f)
        assertEquals(20f, bounds.top, .001f)
        assertEquals(160f, bounds.right, .001f)
        assertEquals(100f, bounds.bottom, .001f)
    }

    @Test
    fun arrange_bounds_use_rotated_visual_corners() {
        val bounds = editableObjectArrangeBounds(listOf(
            LayerPayload.ShapeObject(
                kind = ShapeKind.Rectangle,
                x = 40f,
                y = 50f,
                width = 80f,
                height = 20f,
                rotationDegrees = 90f,
            ),
        ))
        requireNotNull(bounds)
        assertEquals(70f, bounds.left, .01f)
        assertEquals(20f, bounds.top, .01f)
        assertEquals(90f, bounds.right, .01f)
        assertEquals(100f, bounds.bottom, .01f)
        assertNull(editableObjectArrangeBounds(emptyList()))
    }
}
