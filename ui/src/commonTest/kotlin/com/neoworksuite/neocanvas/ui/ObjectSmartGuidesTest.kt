package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectSmartGuidesTest {
    @Test
    fun smart_guides_report_canvas_edges_and_centres_only_inside_tolerance() {
        val left = LayerPayload.ShapeObject(
            kind = ShapeKind.Rectangle,
            x = 0f,
            y = 40f,
            width = 80f,
            height = 60f,
        )
        val leftGuides = editableObjectSmartGuides(left, 400, 300, tolerance = .5f)
        assertEquals(0f, leftGuides.verticalX)
        assertNull(leftGuides.horizontalY)

        val centred = left.copy(x = 160f, y = 120f)
        val centreGuides = editableObjectSmartGuides(centred, 400, 300, tolerance = .5f)
        assertEquals(200f, centreGuides.verticalX)
        assertEquals(150f, centreGuides.horizontalY)

        val free = left.copy(x = 12f, y = 17f)
        val freeGuides = editableObjectSmartGuides(free, 400, 300, tolerance = 4f)
        assertNull(freeGuides.verticalX)
        assertNull(freeGuides.horizontalY)
    }

    @Test
    fun smart_guides_use_rotated_visual_bounds() {
        val rotated = LayerPayload.TextObject(
            text = "Guide",
            x = 160f,
            y = 100f,
            width = 80f,
            height = 100f,
            rotationDegrees = 90f,
        )
        val guides = editableObjectSmartGuides(rotated, 400, 300, tolerance = .5f)
        assertEquals(200f, guides.verticalX)
        assertEquals(150f, guides.horizontalY)
    }
}
