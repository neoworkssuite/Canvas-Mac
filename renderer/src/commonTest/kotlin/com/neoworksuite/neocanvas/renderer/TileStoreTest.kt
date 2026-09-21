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
    fun layer_snapshot_remove_and_restore_are_lossless_and_isolated() {
        val a0 = TileAddress("layer-a", 0, 0)
        val a1 = TileAddress("layer-a", 1, 0)
        val b0 = TileAddress("layer-b", 0, 0)
        val red = ByteArray(TileFormat.BYTES_PER_TILE).apply { this[0] = 99; this[3] = 255.toByte() }
        val green = ByteArray(TileFormat.BYTES_PER_TILE).apply { this[1] = 77; this[3] = 255.toByte() }
        val blue = ByteArray(TileFormat.BYTES_PER_TILE).apply { this[2] = 55; this[3] = 255.toByte() }
        val store = TileStore(mapOf(a0 to red, a1 to green, b0 to blue))

        val sleeping = store.snapshotLayer("layer-a")
        sleeping.getValue(a0)[0] = 1

        assertEquals(2, store.residentTileCount("layer-a"))
        assertEquals(3L * TileFormat.BYTES_PER_TILE, store.estimatedResidentBytes)
        assertEquals(setOf(a0, a1), store.removeLayer("layer-a"))
        assertEquals(0, store.residentTileCount("layer-a"))
        assertContentEquals(blue, store.read(b0))

        val cleanSnapshot = mapOf(a0 to red, a1 to green)
        store.restoreLayer("layer-a", cleanSnapshot)

        assertContentEquals(red, store.read(a0))
        assertContentEquals(green, store.read(a1))
        assertContentEquals(blue, store.read(b0))
    }

    @Test
    fun dormant_layer_codec_round_trips_and_detects_corruption() {
        val key = TileAddress("sleeping", -1, 3)
        val pixels = ByteArray(TileFormat.BYTES_PER_TILE).apply {
            this[0] = 42
            this[lastIndex] = 7
        }

        val encoded = DormantLayerCodec.encode("sleeping", mapOf(key to pixels))
        val decoded = DormantLayerCodec.decode(encoded)

        assertEquals("sleeping", decoded.layerId)
        assertEquals(setOf(key), decoded.tiles.keys)
        assertContentEquals(pixels, decoded.tiles.getValue(key))

        val corrupted = encoded.copyOf().also { it[it.lastIndex - 8] = (it[it.lastIndex - 8].toInt() xor 1).toByte() }
        kotlin.test.assertFailsWith<IllegalArgumentException> { DormantLayerCodec.decode(corrupted) }
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
