package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RasterMoveTest {
    @Test fun angled_rotation_is_bounded_and_preserves_pigment_with_transparency() {
        assertEquals(6 to 4, RasterMove.rotatedSize(4, 6, 90f))
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        for (y in 4..7) for (x in 4..7) {
            val i = (y * 256 + x) * 4
            bytes[i] = 255.toByte(); bytes[i + 3] = 128.toByte()
        }
        for (angle in listOf(-15f, 15f)) {
            val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
            store.applyPatch(RasterMove.move(store, "a", 4, 4, 8, 8, 0, 0, 16, 16,
                sampling = ResizeSampling.Smooth, degrees = angle))
            val result = store.read(TileKey("a", 0, 0))!!
            assertTrue(!result.contentEquals(bytes))
            for (i in result.indices step 4) if (result[i + 3].toInt() != 0) {
                assertEquals(255, result[i].toInt() and 255)
                assertTrue((result[i + 3].toInt() and 255) <= 128)
            }
        }
    }
    @Test fun smoothResizeBlendsAcrossTileBoundaryWithoutTransparentColourBleed() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[255 * 4] = 255.toByte()
        bytes[255 * 4 + 3] = 255.toByte()
        val transparent = ByteArray(TileFormat.BYTES_PER_TILE)
        transparent[2] = 255.toByte() // Invisible blue must not contaminate red.
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes, TileKey("a", 1, 0) to transparent))
        store.applyPatch(RasterMove.move(store, "a", 255, 0, 257, 1, 0, 0, 512, 8,
            resizedWidth = 4, resizedHeight = 1, sampling = ResizeSampling.Smooth))
        val result = store.read(TileKey("a", 1, 0))!!
        assertEquals(255, result[0].toInt() and 255)
        assertEquals(0, result[2].toInt() and 255)
        assertEquals(191, result[3].toInt() and 255)
        assertEquals(64, result[7].toInt() and 255)
    }

    @Test fun smoothReductionAveragesOpaqueNeighbours() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[0] = 255.toByte(); bytes[3] = 255.toByte()
        bytes[6] = 255.toByte(); bytes[7] = 255.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
        store.applyPatch(RasterMove.move(store, "a", 0, 0, 2, 1, 0, 0, 8, 8,
            resizedWidth = 1, resizedHeight = 1, sampling = ResizeSampling.Smooth))
        val result = store.read(TileKey("a", 0, 0))!!
        assertEquals(128, result[0].toInt() and 255)
        assertEquals(128, result[2].toInt() and 255)
        assertEquals(255, result[3].toInt() and 255)
    }
    @Test fun enlargementRepeatsPixelsWithoutHolesAndPreservesAlpha() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[0] = 100; bytes[3] = 128.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
        store.applyPatch(RasterMove.move(store, "a", 0, 0, 1, 1, 0, 0, 8, 8,
            resizedWidth = 2, resizedHeight = 2))
        val result = store.read(TileKey("a", 0, 0))!!
        for (y in 0..1) for (x in 0..1) {
            assertEquals(100, result[(y * 256 + x) * 4].toInt() and 255)
            assertEquals(128, result[(y * 256 + x) * 4 + 3].toInt() and 255)
        }
        assertEquals(0, result[2 * 4 + 3].toInt())
    }

    @Test fun shrinkingUsesPixelCentresAndClearsOldFootprint() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[3] = 255.toByte()
        bytes[(256 + 1) * 4 + 1] = 120
        bytes[(256 + 1) * 4 + 3] = 255.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
        store.applyPatch(RasterMove.move(store, "a", 0, 0, 2, 2, 0, 0, 8, 8,
            resizedWidth = 1, resizedHeight = 1))
        val result = store.read(TileKey("a", 0, 0))!!
        assertEquals(120, result[1].toInt() and 255)
        assertEquals(0, result[(256 + 1) * 4 + 3].toInt())
    }
    @Test fun clockwiseRotationMapsNonSquareRectangleWithoutLosingAlpha() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        // Two coloured pixels at opposite corners of a 2 x 3 rectangle.
        bytes[0] = 100; bytes[3] = 128.toByte()
        val last = (2 * 256 + 1) * 4
        bytes[last + 1] = 120; bytes[last + 3] = 255.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
        store.applyPatch(RasterMove.move(store, "a", 0, 0, 2, 3, 0, 0, 8, 8, true))
        val result = store.read(TileKey("a", 0, 0))!!
        assertEquals(100, result[2 * 4].toInt() and 255)
        assertEquals(128, result[2 * 4 + 3].toInt() and 255)
        assertEquals(120, result[256 * 4 + 1].toInt() and 255)
        assertEquals(0, result[last + 3].toInt())
    }
    @Test fun overlappingMoveReadsOriginalPixelsAndLeavesOtherLayersAlone() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[0] = 255.toByte(); bytes[3] = 255.toByte()
        bytes[5] = 255.toByte(); bytes[7] = 255.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes, TileKey("b", 0, 0) to bytes))
        store.applyPatch(RasterMove.move(store, "a", 0, 0, 2, 1, 1, 0, 512, 512))
        val actual = store.read(TileKey("a", 0, 0))!!
        assertEquals(0, actual[3].toInt())
        assertEquals(255, actual[4].toInt() and 255)
        assertEquals(255, actual[9].toInt() and 255)
        assertTrue(bytes.contentEquals(store.read(TileKey("b", 0, 0))!!))
    }

    @Test fun moveAcrossTilesRemovesEmptySourceTile() {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        bytes[3] = 255.toByte()
        val store = TileStore(mapOf(TileKey("a", 0, 0) to bytes))
        store.applyPatch(RasterMove.move(store, "a", 0, 0, 1, 1, 256, 0, 512, 512))
        assertEquals(setOf(TileKey("a", 1, 0)), store.keys)
        assertEquals(255, store.read(TileKey("a", 1, 0))!![3].toInt() and 255)
    }
}
