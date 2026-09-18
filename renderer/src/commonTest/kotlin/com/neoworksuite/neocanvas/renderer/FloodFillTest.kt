package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloodFillTest {
    @Test fun tolerance_includes_nearby_colours_but_stops_at_the_boundary() {
        val pixels = ByteArray(TileFormat.BYTES_PER_TILE)
        fun set(x: Int, red: Int) {
            val offset = x * 4
            pixels[offset] = red.toByte(); pixels[offset + 3] = 255.toByte()
        }
        set(0, 100); set(1, 108); set(2, 140)
        val store = TileStore(mapOf(TileKey("a", 0, 0) to pixels))
        store.applyPatch(FloodFill.fill(store, "a", 3, 1, 0, 0, RasterColor(255, 0, 0), tolerance = 10))
        val result = store.read(TileKey("a", 0, 0))!!
        assertEquals(255, result[0].toInt() and 255)
        assertEquals(255, result[4].toInt() and 255)
        assertEquals(140, result[8].toInt() and 255)
    }

    @Test fun fillCrossesTileBoundaryAndStopsAtDocumentEdge() {
        val store = TileStore()
        val patch = FloodFill.fill(store, "a", 258, 2, 0, 0, RasterColor(255, 0, 0))
        store.applyPatch(patch)
        assertEquals(2, store.keys.size)
        val tile = store.read(TileKey("a", 1, 0))!!
        assertEquals(255, tile[7].toInt() and 255)
        assertEquals(0, tile[11].toInt() and 255)
        assertTrue(FloodFill.fill(store, "a", 258, 2, 0, 0, RasterColor(255, 0, 0)).keys.isEmpty())
    }

    @Test fun fillCannotCrossSolidBoundaryOrChangeAnotherLayer() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        for (y in 0..3) bytes[(y * 256 + 2) * 4 + 3] = 255.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes, TileKey("b", 0, 0) to bytes))
        store.applyPatch(FloodFill.fill(store, "a", 5, 4, 0, 0, RasterColor(255, 0, 0)))
        val result = store.read(TileKey("a", 0, 0))!!
        assertEquals(255, result[0].toInt() and 255)
        assertEquals(0, result[3 * 4 + 3].toInt() and 255)
        assertTrue(bytes.contentEquals(store.read(TileKey("b", 0, 0))!!))
    }
}
