package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.TileAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileStoreTest {
    @Test
    fun x_256_is_stored_in_tile_one() {
        assertEquals(1, tileCoordinate(256))
    }

    @Test
    fun negative_pixels_use_the_tile_to_the_left() {
        assertEquals(-1, tileCoordinate(-1))
    }

    @Test
    fun applying_a_patch_returns_the_changed_keys_and_copies_its_pixels() {
        val key = TileAddress("layer-1", 1, 0)
        val suppliedPixels = ByteArray(TileFormat.BYTES_PER_TILE).apply {
            this[0] = 0x7f
            this[3] = 0xff.toByte()
        }
        val store = TileStore()

        val changed = store.applyPatch(RasterPatch.replace(key, suppliedPixels))
        suppliedPixels[0] = 0

        assertEquals(setOf(key), changed)
        assertContentEquals(byteArrayOf(0x7f, 0, 0, 0xff.toByte()), store.read(key)!!.copyOfRange(0, 4))
    }

    @Test
    fun removing_a_tile_from_a_patch_removes_it_from_the_store() {
        val key = TileAddress("layer-1", 0, 0)
        val store = TileStore()
        store.applyPatch(RasterPatch.replace(key, ByteArray(TileFormat.BYTES_PER_TILE)))

        val changed = store.applyPatch(RasterPatch.remove(key))

        assertEquals(setOf(key), changed)
        assertEquals(null, store.read(key))
        assertTrue(store.keys.isEmpty())
    }
}
