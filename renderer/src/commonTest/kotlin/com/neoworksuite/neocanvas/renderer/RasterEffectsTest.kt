package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterEffectsTest {
    private fun store(): TileStore {
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        for (y in 20..44) for (x in 20..44) {
            val i = (y * TILE_SIZE_PIXELS + x) * 4
            bytes[i] = (x * 5).coerceIn(0, 255).toByte()
            bytes[i + 1] = (y * 5).coerceIn(0, 255).toByte()
            bytes[i + 2] = 120.toByte()
            bytes[i + 3] = 255.toByte()
        }
        return TileStore(mapOf(TileKey("a", 0, 0) to bytes))
    }

    @Test fun professional_fx_are_neutral_at_zero_and_change_pixels_at_strength() {
        val types = listOf(
            RasterEffectType.Sharpen,
            RasterEffectType.Noise,
            RasterEffectType.Bloom,
            RasterEffectType.Halftone,
            RasterEffectType.ChromaticAberration,
        )
        types.forEach { type ->
            val base = store()
            val before = base.read(TileKey("a", 0, 0))!!
            val zero = RasterEffects.apply(base, "a", 128, 128, type, RasterEffectSettings(amount = 0f))
            val previewZero = zero.previewTile(TileKey("a", 0, 0), base)!!
            assertTrue(before.contentEquals(previewZero), "$type must be neutral at zero")
            val full = RasterEffects.apply(base, "a", 128, 128, type, RasterEffectSettings(amount = .85f))
            val preview = full.previewTile(TileKey("a", 0, 0), base)!!
            assertFalse(before.contentEquals(preview), "$type should change pixels")
        }
    }
}
