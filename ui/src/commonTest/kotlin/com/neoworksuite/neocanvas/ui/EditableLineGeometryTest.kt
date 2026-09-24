package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.LineMarker
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditableLineGeometryTest {
    @Test
    fun length_and_angle_are_derived_in_all_quadrants() {
        val first = lineMetrics(line(3f, 4f))
        assertEquals(5f, first.length, .001f)
        assertEquals(53.1301f, first.angleDegrees, .001f)
        assertEquals(225f, lineMetrics(line(-4f, -4f)).angleDegrees, .001f)
        assertEquals(315f, lineMetrics(line(4f, -4f)).angleDegrees, .001f)
    }

    @Test
    fun invalid_length_or_angle_preserves_the_line() {
        val original = line(3f, 4f)
        assertEquals(original, lineWithLength(original, Float.NaN))
        assertEquals(original, lineWithLength(original, 0f))
        assertEquals(original, lineWithAngle(original, Float.POSITIVE_INFINITY))
    }

    @Test
    fun length_angle_and_endpoint_edits_keep_the_document_geometry_contract() {
        val original = line(3f, 4f)
        val lengthened = lineWithLength(original, 10f)
        assertEquals(6f, lengthened.width, .001f)
        assertEquals(8f, lengthened.height, .001f)

        val angled = lineWithAngle(original, 44f, snap = true)
        assertEquals(45f, lineMetrics(angled).angleDegrees, .001f)
        assertEquals(5f, lineMetrics(angled).length, .001f)

        val movedStart = lineWithEndpoint(original, moveStart = true, x = 1f, y = 2f)
        assertEquals(1f, movedStart.x)
        assertEquals(2f, movedStart.y)
        assertEquals(2f, movedStart.width)
        assertEquals(2f, movedStart.height)
        assertEquals(3f, movedStart.x + movedStart.width)
        assertEquals(4f, movedStart.y + movedStart.height)
    }

    @Test
    fun reversing_swaps_endpoints_and_markers_without_changing_length() {
        val original = line(3f, 4f).copy(
            x = 10f,
            y = 20f,
            startMarker = LineMarker.Arrow,
            endMarker = LineMarker.None,
        )
        val reversed = reversedLine(original)
        assertEquals(13f, reversed.x)
        assertEquals(24f, reversed.y)
        assertEquals(-3f, reversed.width)
        assertEquals(-4f, reversed.height)
        assertEquals(LineMarker.None, reversed.startMarker)
        assertEquals(LineMarker.Arrow, reversed.endMarker)
        assertTrue(kotlin.math.abs(lineMetrics(original).length - lineMetrics(reversed).length) < .001f)
    }

    private fun line(width: Float, height: Float) = LayerPayload.ShapeObject(
        kind = ShapeKind.Line,
        width = width,
        height = height,
        fillArgb = null,
        strokeArgb = 0xff000000.toInt(),
        strokeWidth = 2f,
    )
}
