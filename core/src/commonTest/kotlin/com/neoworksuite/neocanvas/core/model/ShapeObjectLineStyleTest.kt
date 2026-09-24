package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShapeObjectLineStyleTest {
    @Test
    fun line_defaults_are_backward_compatible() {
        val line = LayerPayload.ShapeObject(
            kind = ShapeKind.Line,
            width = 10f,
            height = 0f,
            fillArgb = null,
            strokeArgb = 0xff000000.toInt(),
            strokeWidth = 1f,
        )

        assertEquals(LineStyle.Solid, line.lineStyle)
        assertEquals(LineCap.Round, line.lineCap)
        assertEquals(LineMarker.None, line.startMarker)
        assertEquals(LineMarker.None, line.endMarker)
        assertEquals(true, line.angleSnapping)
    }

    @Test
    fun non_finite_geometry_and_negative_stroke_are_rejected() {
        assertFailsWith<IllegalArgumentException> {
            LayerPayload.ShapeObject(
                kind = ShapeKind.Line,
                width = Float.NaN,
                height = 1f,
                fillArgb = null,
                strokeArgb = 0xff000000.toInt(),
                strokeWidth = 1f,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LayerPayload.ShapeObject(
                kind = ShapeKind.Line,
                width = 1f,
                height = 0f,
                fillArgb = null,
                strokeArgb = 0xff000000.toInt(),
                strokeWidth = -1f,
            )
        }
    }
}
