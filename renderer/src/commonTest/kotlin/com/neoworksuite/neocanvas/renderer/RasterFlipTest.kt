package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RasterFlipTest {
    @Test fun horizontalFlipCrossesTilesAndPreservesOutsidePixelsAndOtherLayers() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[3] = 255.toByte()
        bytes[254 * 4] = 100
        bytes[254 * 4 + 3] = 128.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes, TileKey("b", 0, 0) to bytes))
        store.applyPatch(RasterFlip.flip(store, "a", 254, 0, 258, 1, 512, 2, true))
        assertEquals(0, store.read(TileKey("a", 0, 0))!![254 * 4 + 3].toInt())
        assertEquals(255, store.read(TileKey("a", 0, 0))!![3].toInt() and 255)
        assertEquals(128, store.read(TileKey("a", 1, 0))!![7].toInt() and 255)
        assertTrue(bytes.contentEquals(store.read(TileKey("b", 0, 0))!!))
        store.applyPatch(RasterFlip.flip(store, "a", 254, 0, 258, 1, 512, 2, true))
        assertTrue(bytes.contentEquals(store.read(TileKey("a", 0, 0))!!))
        assertEquals(null, store.read(TileKey("a", 1, 0)))
    }

    @Test fun verticalFlipMovesPixelToOppositeRow() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[3] = 255.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
        store.applyPatch(RasterFlip.flip(store, "a", 0, 0, 1, 3, 3, 3, false))
        val result = store.read(TileKey("a", 0, 0))!!
        assertEquals(0, result[3].toInt())
        assertEquals(255, result[(2 * 256) * 4 + 3].toInt() and 255)
    }
}
