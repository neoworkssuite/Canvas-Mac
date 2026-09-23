package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.brushes.*
import kotlin.test.*

class BrushQualityTest {
    @Test fun pressure_can_be_disabled_independently_of_input_pressure() {
        val brush = BuiltInBrushes.ink.copy(pressureSize = 0f, pressureOpacity = 0f)
        val low = paint(listOf(RasterPoint(20f, 20f, .1f)), brush)
        val high = paint(listOf(RasterPoint(20f, 20f, 1f)), brush)
        assertTrue(low.contentEquals(high))
        assertFalse(paint(listOf(RasterPoint(20f, 20f, .1f)), BuiltInBrushes.ink).contentEquals(high))
    }
    @Test fun dry_paint_texture_is_repeatable_and_distinct_from_ink() {
        val points = listOf(RasterPoint(10f, 20f), RasterPoint(50f, 20f))
        val dry = paint(points, BuiltInBrushes.dryPaint)
        assertTrue(dry.contentEquals(paint(points, BuiltInBrushes.dryPaint)))
        assertFalse(dry.contentEquals(paint(points, BuiltInBrushes.ink)))
    }
    @Test fun grain_scatter_and_shape_dynamics_change_the_rendered_mark() {
        val points = listOf(RasterPoint(18f, 24f), RasterPoint(46f, 24f))
        val plain = BuiltInBrushes.ink.copy(
            tip = BrushTip.Round,
            dynamics = BrushDynamics(),
        )
        val expressive = plain.copy(
            dynamics = BrushDynamics(grain = .8f, scatter = .7f, rotation = .6f,
                shapeRatio = .35f, hardness = .35f, wetMix = .5f, jitter = .6f),
        )

        assertFalse(paint(points, plain).contentEquals(paint(points, expressive)))
        assertTrue(paint(points, expressive).contentEquals(paint(points, expressive)))
    }

    @Test fun spray_water_and_bristle_tips_have_distinct_deterministic_output() {
        val points = listOf(RasterPoint(16f, 30f), RasterPoint(48f, 30f))
        val base = BuiltInBrushes.ink.copy(dynamics = BrushDynamics(grain = .35f, wetMix = .4f))
        val spray = paint(points, base.copy(tip = BrushTip.Spray))
        val water = paint(points, base.copy(tip = BrushTip.Water))
        val bristle = paint(points, base.copy(tip = BrushTip.Bristle))

        assertFalse(spray.contentEquals(water))
        assertFalse(water.contentEquals(bristle))
        assertTrue(spray.contentEquals(paint(points, base.copy(tip = BrushTip.Spray))))
    }

    @Test fun leaf_grass_and_bark_tips_are_distinct_and_deterministic() {
        val points = listOf(RasterPoint(16f, 30f), RasterPoint(48f, 30f))
        val base = BuiltInBrushes.ink.copy(
            spacing = 4f,
            baseSize = 18f,
            dynamics = BrushDynamics(grain = .25f, rotation = .8f, shapeRatio = .55f),
        )
        val leaf = paint(points, base.copy(tip = BrushTip.Leaf))
        val grass = paint(points, base.copy(tip = BrushTip.Grass))
        val bark = paint(points, base.copy(tip = BrushTip.Bark))

        assertFalse(leaf.contentEquals(grass))
        assertFalse(grass.contentEquals(bark))
        assertTrue(leaf.contentEquals(paint(points, base.copy(tip = BrushTip.Leaf))))
    }

    @Test fun large_textured_brushes_use_a_bounded_adaptive_stamp_density() {
        val points = listOf(RasterPoint(0f, 20f), RasterPoint(1_000f, 20f))
        val precision = BuiltInBrushes.ink.copy(baseSize = 10f, spacing = 1f)
        val textured = precision.copy(
            tip = BrushTip.Leaf,
            dynamics = BrushDynamics(grain = .5f, scatter = .6f, rotation = .8f),
        )

        assertTrue(plannedStrokeStampCount(points, 120f, textured) <= 45)
        assertTrue(plannedStrokeStampCount(points, 6f, precision) > 100)
    }

    @Test fun pencil_and_pen_presets_keep_their_precision_spacing_at_large_sizes() {
        val points = listOf(RasterPoint(0f, 20f), RasterPoint(1_000f, 20f))
        val precisionPencil = BuiltInBrushes.inCategory("pencils").first { it.name == "Precision Pencil" }
        val leaf = precisionPencil.copy(
            categoryId = "foliage",
            tip = BrushTip.Leaf,
            dynamics = BrushDynamics(grain = .5f, scatter = .6f, rotation = .8f),
        )

        assertTrue(
            plannedStrokeStampCount(points, 120f, precisionPencil) >
                plannedStrokeStampCount(points, 120f, leaf),
        )
    }

    @Test fun rotated_bark_tip_keeps_pixels_in_its_rotated_corners() {
        val store = TileStore()
        val brush = BuiltInBrushes.ink.copy(
            tip = BrushTip.Bark,
            dynamics = BrushDynamics(rotation = .19901971f, shapeRatio = 1f),
        )
        store.applyPatch(
            Rasterizer.stroke(
                store, "a", listOf(RasterPoint(100f, 100f)), RasterColor(30, 60, 90),
                100f, 1f, BrushMode.PAINT, 200, 200, brush = brush,
            ),
        )

        val pixels = store.read(TileKey("a", 0, 0))!!
        assertTrue((pixels[(100 * 256 + 160) * 4 + 3].toInt() and 255) > 0)
    }
    private fun paint(points: List<RasterPoint>, brush: BrushDefinition): ByteArray {
        val store = TileStore()
        store.applyPatch(Rasterizer.stroke(store, "a", points, RasterColor(20, 40, 60),
            10f, .6f, BrushMode.PAINT, 64, 64, brush = brush))
        return store.read(TileKey("a", 0, 0))!!
    }
    @Test fun collinear_pointer_samples_do_not_change_ink_density() {
        val endpoints = listOf(RasterPoint(10f, 20f), RasterPoint(50f, 20f))
        val many = (10..50).map { RasterPoint(it.toFloat(), 20f) }
        val sparse = paint(endpoints, BuiltInBrushes.ink)
        val dense = paint(many, BuiltInBrushes.ink)
        sparse.indices.forEach { assertEquals(sparse[it], dense[it], "Pixel channel $it") }
    }
    @Test fun ink_has_antialiased_edges() {
        val pixels = paint(listOf(RasterPoint(20.5f, 20.5f)), BuiltInBrushes.ink)
        val center = pixels[(20 * 256 + 20) * 4 + 3].toInt() and 255
        val edge = pixels[(20 * 256 + 25) * 4 + 3].toInt() and 255
        assertTrue(edge in 1 until center)
    }
}
