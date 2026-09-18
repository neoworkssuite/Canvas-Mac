package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrokeSmoothingTest {
    @Test fun smoothing_reduces_jitter_and_keeps_pressure() {
        val points = listOf(RasterPoint(0f, 0f), RasterPoint(5f, 8f, .4f), RasterPoint(10f, -8f, .7f))
        val result = smoothStroke(points, 1f)
        assertEquals(points.first(), result.first())
        assertTrue(kotlin.math.abs(result[1].y) < kotlin.math.abs(points[1].y))
        assertTrue(kotlin.math.abs(result[2].y) < kotlin.math.abs(points[2].y))
        assertEquals(.7f, result.last().pressure)
        assertEquals(result.take(2), smoothStroke(points.take(2), 1f))
        assertEquals(points, smoothStroke(points, 0f))
    }
}
