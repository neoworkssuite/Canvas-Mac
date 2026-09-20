package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterSmudgeTest {
    @Test fun smudge_drags_existing_pigment_and_leaves_other_layers_untouched() {
        val a = ByteArray(TileFormat.BYTES_PER_TILE)
        val b = ByteArray(TileFormat.BYTES_PER_TILE)
        fun set(bytes: ByteArray, x: Int, y: Int, r: Int, g: Int, blue: Int, alpha: Int = 255) {
            val i = (y * TILE_SIZE_PIXELS + x) * 4
            bytes[i] = r.toByte()
            bytes[i + 1] = g.toByte()
            bytes[i + 2] = blue.toByte()
            bytes[i + 3] = alpha.toByte()
        }
        for (y in 20..28) for (x in 20..28) set(a, x, y, 255, 0, 0)
        set(b, 22, 22, 0, 255, 0)
        val store = TileStore(
            mapOf(
                TileKey("a", 0, 0) to a,
                TileKey("b", 0, 0) to b,
            ),
        )
        val beforeA = store.read(TileKey("a", 0, 0))!!
        val beforeB = store.read(TileKey("b", 0, 0))!!

        val patch = RasterSmudge.stroke(
            existing = store,
            layerId = "a",
            points = listOf(RasterPoint(24f, 24f, 1f), RasterPoint(42f, 24f, 1f)),
            size = 18f,
            strength = .85f,
            canvasWidth = 128,
            canvasHeight = 128,
        )
        store.applyPatch(patch)

        assertFalse(beforeA.contentEquals(store.read(TileKey("a", 0, 0))!!))
        assertTrue(beforeB.contentEquals(store.read(TileKey("b", 0, 0))!!))
    }

    @Test fun smudge_respects_selection_predicate() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        for (x in 10..20) {
            val i = (16 * TILE_SIZE_PIXELS + x) * 4
            bytes[i] = 255.toByte()
            bytes[i + 3] = 255.toByte()
        }
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
        val before = store.read(TileKey("a", 0, 0))!!
        val patch = RasterSmudge.stroke(
            existing = store,
            layerId = "a",
            points = listOf(RasterPoint(14f, 16f), RasterPoint(28f, 16f)),
            size = 12f,
            strength = .9f,
            canvasWidth = 64,
            canvasHeight = 64,
            acceptsPixel = { x, _ -> x < 18 },
        )
        store.applyPatch(patch)
        val after = store.read(TileKey("a", 0, 0))!!
        val outside = (16 * TILE_SIZE_PIXELS + 22) * 4
        assertTrue(before.copyOfRange(outside, outside + 4).contentEquals(after.copyOfRange(outside, outside + 4)))
    }
}
