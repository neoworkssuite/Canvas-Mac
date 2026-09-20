package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.TileAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class PngExporterTest {
    @Test fun multiply_and_screen_blend_modes_are_used_during_export() {
        val base = tile(100.toByte(), 150.toByte(), 200.toByte(), 255.toByte())
        val top = tile(128.toByte(), 128.toByte(), 128.toByte(), 255.toByte())
        fun pixel(mode: LayerBlendMode): ByteArray {
            val document = CanvasDocument("blend", 1, 1, listOf(
                Layer("base", "Base", payload = LayerPayload.Raster(setOf(TileAddress("base", 0, 0)))),
                Layer("top", "Top", payload = LayerPayload.Raster(setOf(TileAddress("top", 0, 0))), blendMode = mode),
            ))
            return PngExporter.render(document, mapOf(TileAddress("base", 0, 0) to base, TileAddress("top", 0, 0) to top)).rgbaAt(0, 0)
        }
        val multiply = pixel(LayerBlendMode.Multiply)
        val screen = pixel(LayerBlendMode.Screen)
        assertEquals(50, multiply[0].toInt() and 255)
        assertEquals(178, screen[0].toInt() and 255)
    }

    @Test fun clipping_mask_uses_alpha_of_layer_below() {
        val base = tile(10, 20, 30, 0)
        val baseOpaque = base.copyOf().also {
            it[3] = 255.toByte()
        }
        val top = tile(255.toByte(), 0, 0, 255.toByte())
        val document = CanvasDocument("clip", 2, 1, listOf(
            Layer("base", "Base", payload = LayerPayload.Raster(setOf(TileAddress("base", 0, 0)))),
            Layer("top", "Top", payload = LayerPayload.Raster(setOf(TileAddress("top", 0, 0))), clipping = true),
        ))
        val basePixels = ByteArray(TileFormat.BYTES_PER_TILE)
        baseOpaque.copyInto(basePixels)
        basePixels[7] = 0
        val image = PngExporter.render(
            document,
            mapOf(
                TileAddress("base", 0, 0) to basePixels,
                TileAddress("top", 0, 0) to top,
            ),
        )
        assertEquals(255, image.rgbaAt(0, 0)[0].toInt() and 255)
        assertEquals(0, image.rgbaAt(1, 0)[3].toInt() and 255)
    }

    @Test fun extended_blend_modes_render_without_falling_back_to_normal() {
        val base = tile(80, 120, 180, 255.toByte())
        val top = tile(180.toByte(), 80, 40, 255.toByte())
        val document = CanvasDocument("blend-more", 1, 1, listOf(
            Layer("base", "Base", payload = LayerPayload.Raster(setOf(TileAddress("base", 0, 0)))),
            Layer("top", "Top", payload = LayerPayload.Raster(setOf(TileAddress("top", 0, 0))), blendMode = LayerBlendMode.Difference),
        ))
        val result = PngExporter.render(document, mapOf(
            TileAddress("base", 0, 0) to base,
            TileAddress("top", 0, 0) to top,
        )).rgbaAt(0, 0)
        assertEquals(100, result[0].toInt() and 255)
    }

    @Test
    fun png_dimensions_match_document_and_hidden_layers_are_excluded() {
        val red = tile(255.toByte(), 0, 0, 255.toByte())
        val blue = tile(0, 0, 255.toByte(), 255.toByte())
        val document = CanvasDocument(
            id = "document",
            width = 100,
            height = 100,
            layers = listOf(
                Layer("red", "Red", payload = LayerPayload.Raster(setOf(TileAddress("red", 0, 0)))),
                Layer("blue", "Blue", visible = false, payload = LayerPayload.Raster(setOf(TileAddress("blue", 0, 0)))),
            ),
        )

        val image = PngExporter.render(document, mapOf(TileAddress("red", 0, 0) to red, TileAddress("blue", 0, 0) to blue))

        assertEquals(100, image.width)
        assertEquals(100, image.height)
        assertEquals(255, image.rgbaAt(0, 0)[0].toInt() and 0xff)
        assertEquals(0, image.rgbaAt(0, 0)[2].toInt() and 0xff)
    }

    private fun tile(r: Byte, g: Byte, b: Byte, a: Byte): ByteArray =
        ByteArray(TILE_SIZE_PIXELS * TILE_SIZE_PIXELS * 4).also { pixels ->
            for (index in pixels.indices step 4) {
                pixels[index] = r; pixels[index + 1] = g; pixels[index + 2] = b; pixels[index + 3] = a
            }
        }
}
