package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.brushes.*
import kotlin.test.*

class StampMaskSamplerTest {
    private val diamond = BrushAsset(
        id = "diamond",
        width = 5,
        height = 5,
        coverage = byteArrayOf(
            0, 0, 255.toByte(), 0, 0,
            0, 255.toByte(), 255.toByte(), 255.toByte(), 0,
            255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            0, 255.toByte(), 255.toByte(), 255.toByte(), 0,
            0, 0, 255.toByte(), 0, 0,
        ),
    )

    @Test fun transformed_sampler_preserves_diamond_centre_and_rejects_corners() {
        val sampler = StampMaskSampler(diamond, scaleX = 1f, scaleY = .5f, angleRadians = 0f)
        assertEquals(1f, sampler.coverage(0f, 0f), .001f)
        assertEquals(0f, sampler.coverage(.95f, .95f), .001f)
        assertTrue(sampler.coverage(0f, .35f) > 0f)
    }

    @Test fun asset_precomputes_empty_rows_and_validates_storage() {
        val sparse = BrushAsset("sparse", 3, 3, byteArrayOf(0, 0, 0, 0, 1, 0, 0, 0, 0))
        assertEquals(listOf(null, 1..1, null), sparse.nonEmptyRows)
        assertFailsWith<IllegalArgumentException> { BrushAsset("bad", 3, 3, ByteArray(8)) }
    }

    @Test fun v2_mask_rendering_is_repeatable_and_symmetry_reuses_variation() {
        val ref = BrushAssetRef("diamond", "0".repeat(64))
        val brush = BuiltInBrushes.ink.copy(
            version = 2,
            stamp = BrushStamp(shape = ref, stampCount = 3, scatterAcross = .4f, angleMode = StampAngleMode.DirectionJitter),
        )
        val resolver = BrushAssetResolver { if (it == ref) diamond else null }
        fun render(symmetry: DrawingSymmetry): ByteArray {
            val store = TileStore()
            store.applyPatch(Rasterizer.stroke(
                store, "a", listOf(RasterPoint(20.5f, 40.5f), RasterPoint(59.5f, 40.5f)),
                RasterColor(30, 60, 90), 18f, 1f, BrushMode.PAINT, 80, 80,
                brush = brush, symmetry = symmetry, assetResolver = resolver,
            ))
            return store.read(TileKey("a", 0, 0))!!
        }
        assertContentEquals(render(DrawingSymmetry.None), render(DrawingSymmetry.None))
        val mirrored = render(DrawingSymmetry.Vertical)
        for (y in 0 until 80) for (x in 0 until 40) {
            val left = mirrored[(y * 256 + x) * 4 + 3]
            val right = mirrored[(y * 256 + (79 - x)) * 4 + 3]
            assertEquals(left, right, "alpha at $x,$y")
        }
    }

    @Test fun planner_caps_substamps_and_preserves_the_endpoint() {
        val brush = BuiltInBrushes.ink.copy(version = 2, stamp = BrushStamp(stampCount = 8, spacingRatio = .2f))
        val metrics = planStampWork(listOf(RasterPoint(0f, 10f), RasterPoint(2_000f, 10f)), 80f, brush)
        assertTrue(metrics.subStamps <= metrics.samples * 8)
        assertEquals(RasterPoint(2_000f, 10f), metrics.lastPoint)
    }
}
