package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterSelectionTest {
    private fun storeWithPixels(vararg pixels: Triple<Int, Int, Int>): TileStore {
        val layer = "layer"
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        pixels.forEach { (x, y, argb) ->
            val index = (y * TILE_SIZE_PIXELS + x) * 4
            bytes[index] = (argb ushr 16).toByte()
            bytes[index + 1] = (argb ushr 8).toByte()
            bytes[index + 2] = argb.toByte()
            bytes[index + 3] = (argb ushr 24).toByte()
        }
        return TileStore(mapOf(TileKey(layer, 0, 0) to bytes))
    }

    @Test
    fun automatic_selection_is_connected_and_honours_tolerance() {
        val store = storeWithPixels(
            Triple(1, 1, 0xFFFF0000.toInt()),
            Triple(2, 1, 0xFFF02000.toInt()),
            Triple(3, 1, 0xFF0000FF.toInt()),
            Triple(5, 1, 0xFFFF0000.toInt()),
        )

        val exact = RasterSelection.connectedColour(store, "layer", 8, 4, 1, 1, 0)!!
        assertTrue(exact.contains(1, 1))
        assertFalse(exact.contains(2, 1))
        assertFalse(exact.contains(5, 1))

        val tolerant = RasterSelection.connectedColour(store, "layer", 8, 4, 1, 1, 40)!!
        assertTrue(tolerant.contains(1, 1))
        assertTrue(tolerant.contains(2, 1))
        assertFalse(tolerant.contains(3, 1))
        assertFalse(tolerant.contains(5, 1))
    }

    @Test
    fun raster_selection_region_translates_flips_rotates_and_scales_membership() {
        val region = RasterSelectionRegion.fromPredicate(0, 0, 2, 2) { x, y -> x == 0 && y == 0 }!!
        val moved = region.translated(3, 4)
        assertTrue(moved.contains(3, 4))
        assertFalse(moved.contains(4, 4))

        val flipped = region.flipped(horizontal = true)
        assertTrue(flipped.contains(1, 0))
        assertFalse(flipped.contains(0, 0))

        val rotated = region.rotatedClockwise(0, 0)
        assertTrue(rotated.contains(1, 0))
        assertFalse(rotated.contains(0, 0))

        val scaled = region.transformed(0, 0, 4, 4, 0f)
        assertTrue(scaled.contains(0, 0))
        assertTrue(scaled.contains(1, 1))
        assertFalse(scaled.contains(3, 3))
    }
}
