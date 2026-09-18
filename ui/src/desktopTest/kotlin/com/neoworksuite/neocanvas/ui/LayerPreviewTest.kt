package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.renderer.TileFormat
import com.neoworksuite.neocanvas.renderer.TileKey
import com.neoworksuite.neocanvas.renderer.TileStore
import kotlin.test.Test
import kotlin.test.assertEquals

class LayerPreviewTest {
    @Test fun fits_artwork_without_stretching_and_ignores_other_layers() {
        val key = TileKey("art", 0, 0)
        val other = TileKey("other", 0, 0)
        val red = ByteArray(TileFormat.BYTES_PER_TILE)
        for (i in red.indices step 4) { red[i] = (-1).toByte(); red[i + 3] = (-1).toByte() }
        val store = TileStore(mapOf(key to red, other to red))
        val layer = Layer("art", "Art", visible = false, payload = LayerPayload.Raster(setOf(key)))
        val image = ImageBitmap(64, 64)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(64f, 64f)) {
            drawLayerPreview(layer, 512, 256, store, TileImageCache())
        }
        val pixels = image.toPixelMap()
        // The 2:1 document occupies y=16..47; only its left half has red artwork.
        assertEquals(0f, pixels[8, 8].alpha)
        assertEquals(1f, pixels[8, 24].red, .01f)
        assertEquals(0f, pixels[8, 24].green, .01f)
        assertEquals(1f, pixels[48, 24].alpha)
        assertEquals(pixels[48, 24].red, pixels[48, 24].green, .01f)
        assertEquals(0f, pixels[8, 56].alpha)
    }
}
