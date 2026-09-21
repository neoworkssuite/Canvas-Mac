package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterLiquifyTest {
    private fun storeWithBlock(
        layerId: String = "paint",
        left: Int = 12,
        top: Int = 12,
        right: Int = 20,
        bottom: Int = 20,
    ): TileStore {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        for (y in top..bottom) for (x in left..right) {
            val offset = (y * TILE_SIZE_PIXELS + x) * 4
            bytes[offset] = 220.toByte()
            bytes[offset + 1] = 40
            bytes[offset + 2] = 30
            bytes[offset + 3] = 255.toByte()
        }
        return TileStore(mapOf(TileKey(layerId, 0, 0) to bytes))
    }

    private fun alphaAt(store: TileStore, x: Int, y: Int, layerId: String = "paint"): Int {
        val tile = store.read(TileKey(layerId, x / TILE_SIZE_PIXELS, y / TILE_SIZE_PIXELS)) ?: return 0
        val offset = ((y % TILE_SIZE_PIXELS) * TILE_SIZE_PIXELS + x % TILE_SIZE_PIXELS) * 4 + 3
        return tile[offset].toInt() and 255
    }

    @Test
    fun push_warp_drags_content_in_stroke_direction() {
        val store = storeWithBlock()
        val patch = RasterLiquify.stroke(
            existing = store,
            layerId = "paint",
            points = listOf(RasterPoint(16f, 16f), RasterPoint(28f, 16f)),
            size = 22f,
            strength = 1f,
            mode = LiquifyMode.Push,
            canvasWidth = 64,
            canvasHeight = 64,
        )
        assertFalse(patch.keys.isEmpty())

        val warped = TileStore(store.snapshot())
        warped.applyPatch(patch)
        assertTrue((21..28).any { x -> alphaAt(warped, x, 16) > 0 })
    }

    @Test
    fun pinch_and_expand_generate_distinct_spatial_warps() {
        val store = storeWithBlock(left = 10, top = 10, right = 22, bottom = 22)
        val pinch = RasterLiquify.stroke(
            store, "paint", listOf(RasterPoint(16f, 16f)),
            size = 28f, strength = .9f, mode = LiquifyMode.Pinch,
            canvasWidth = 64, canvasHeight = 64,
        )
        val expand = RasterLiquify.stroke(
            store, "paint", listOf(RasterPoint(16f, 16f)),
            size = 28f, strength = .9f, mode = LiquifyMode.Expand,
            canvasWidth = 64, canvasHeight = 64,
        )
        assertFalse(pinch.keys.isEmpty())
        assertFalse(expand.keys.isEmpty())

        val pinched = TileStore(store.snapshot()).apply { applyPatch(pinch) }
        val expanded = TileStore(store.snapshot()).apply { applyPatch(expand) }
        assertTrue(
            (6..26).any { x -> alphaAt(pinched, x, 16) != alphaAt(expanded, x, 16) },
        )
    }

    @Test
    fun liquify_respects_selection_acceptance() {
        val store = storeWithBlock()
        val before = store.snapshot()
        val patch = RasterLiquify.stroke(
            existing = store,
            layerId = "paint",
            points = listOf(RasterPoint(16f, 16f), RasterPoint(28f, 16f)),
            size = 24f,
            strength = 1f,
            mode = LiquifyMode.Push,
            canvasWidth = 64,
            canvasHeight = 64,
            acceptsPixel = { x, _ -> x >= 16 },
        )
        val warped = TileStore(before).apply { applyPatch(patch) }
        for (y in 0 until 32) for (x in 0 until 16) {
            assertTrue(alphaAt(warped, x, y) == alphaAt(store, x, y))
        }
    }

    @Test
    fun zero_strength_and_single_point_push_are_noops() {
        val store = storeWithBlock()
        assertTrue(RasterLiquify.stroke(
            store, "paint", listOf(RasterPoint(16f, 16f), RasterPoint(20f, 16f)),
            20f, 0f, LiquifyMode.Push, 64, 64,
        ).keys.isEmpty())
        assertTrue(RasterLiquify.stroke(
            store, "paint", listOf(RasterPoint(16f, 16f)),
            20f, 1f, LiquifyMode.Push, 64, 64,
        ).keys.isEmpty())
    }
}
