package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.toPixelMap
import com.neoworksuite.neocanvas.renderer.TileFormat
import com.neoworksuite.neocanvas.renderer.TileKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNotSame

class TileImageCacheTest {
    @Test fun caches_unchanged_pixels_and_refreshes_after_edit_or_undo() {
        val cache = TileImageCache(1)
        val key = TileKey("layer", 0, 0)
        val pixels = ByteArray(TileFormat.BYTES_PER_TILE)
        pixels[0] = 255.toByte(); pixels[3] = 128.toByte()
        val first = cache.image(key, pixels)
        assertSame(first, cache.image(key, pixels.copyOf()))
        val map = first.toPixelMap()
        assertEquals(1f, map[0, 0].red, .01f)
        assertEquals(128f / 255, map[0, 0].alpha, .01f)
        assertEquals(0f, map[1, 0].alpha)
        val cleared = ByteArray(TileFormat.BYTES_PER_TILE)
        assertNotSame(first, cache.image(key, cleared))
        assertEquals(0f, cache.image(key, cleared).toPixelMap()[0, 0].alpha)
        assertEquals(128f / 255, cache.image(key, pixels).toPixelMap()[0, 0].alpha, .01f)
        val restored = cache.image(key, pixels)
        cache.image(TileKey("other", 0, 0), pixels)
        assertNotSame(restored, cache.image(key, pixels))
    }
}
