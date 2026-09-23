package com.neoworksuite.neocanvas.brushes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StrokeInterpolatorTest {
    @Test
    fun interpolation_includes_end_point() {
        val samples = interpolate(sample(0f), sample(100f), 10f)

        assertEquals(100f, samples.last().x)
    }

    @Test
    fun interpolation_emits_exact_spacing_positions_before_the_endpoint() {
        val samples = interpolate(sample(0f), sample(25f), 10f)

        assertEquals(listOf(10f, 20f, 25f), samples.map(StrokeSample::x))
    }

    @Test
    fun interpolation_blends_pressure_and_timestamp_to_the_endpoint() {
        val from = StrokeSample(0f, 0f, 100L, 0.2f, 0f, 0f, PointerKind.STYLUS)
        val to = StrokeSample(10f, 0f, 200L, 0.8f, 1f, -1f, PointerKind.STYLUS)

        val samples = interpolate(from, to, 5f)

        assertEquals(0.5f, samples.first().pressure)
        assertEquals(150L, samples.first().timestampMillis)
        assertEquals(to, samples.last())
    }

    @Test
    fun non_positive_spacing_is_rejected() {
        assertFailsWith<IllegalArgumentException> { interpolate(sample(0f), sample(10f), 0f) }
    }

    @Test
    fun interpolation_rejects_more_than_the_supported_number_of_samples() {
        assertFailsWith<IllegalArgumentException> { interpolate(sample(0f), sample(100_001f), 1f) }
    }

    @Test
    fun built_in_catalog_preserves_the_original_brushes_and_dry_paint() {
        assertEquals(211, BuiltInBrushes.all.size)
        assertEquals(BuiltInBrushes.brushes, BuiltInBrushes.all)
        assertEquals(BrushMode.ERASE, BuiltInBrushes.eraser.mode)
    }

    private fun sample(x: Float) = StrokeSample(
        x = x,
        y = 0f,
        timestampMillis = x.toLong(),
        pressure = 1f,
        tiltX = 0f,
        tiltY = 0f,
        pointerKind = PointerKind.MOUSE,
    )
}
