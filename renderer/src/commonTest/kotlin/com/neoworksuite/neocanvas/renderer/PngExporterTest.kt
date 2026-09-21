package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PngExporterTest {
    @Test fun multiply_and_screen_blend_modes_are_used_during_export() {
        val base = tile(100, 150, 200, 255)
        val top = tile(128, 128, 128, 255)
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

    @Test
    fun layer_mask_and_group_visibility_affect_flattened_output() {
        val paintAddress = TileAddress("paint", 0, 0)
        val maskAddress = TileAddress("paint-mask", 0, 0)
        val paint = tile(255, 0, 0, 255)
        val mask = tile(255, 255, 255, 255).also {
            it[0] = 0
            it[1] = 0
            it[2] = 0
        }
        val visibleDocument = CanvasDocument(
            "mask",
            2,
            1,
            layers = listOf(
                Layer(
                    "paint",
                    "Paint",
                    payload = LayerPayload.Raster(setOf(paintAddress)),
                    groupId = "group",
                    mask = com.neoworksuite.neocanvas.core.model.LayerMask(
                        "paint-mask",
                        setOf(maskAddress),
                    ),
                ),
            ),
            groups = listOf(
                com.neoworksuite.neocanvas.core.model.LayerGroup(
                    "group",
                    "Character",
                    opacity = .5f,
                ),
            ),
        )
        val rendered = PngExporter.render(
            visibleDocument,
            mapOf(paintAddress to paint, maskAddress to mask),
        )
        assertEquals(0, rendered.rgbaAt(0, 0)[3].toInt() and 255)
        assertEquals(128, rendered.rgbaAt(1, 0)[3].toInt() and 255)

        val hidden = visibleDocument.copy(
            groups = listOf(
                com.neoworksuite.neocanvas.core.model.LayerGroup(
                    "group",
                    "Character",
                    visible = false,
                ),
            ),
        )
        assertEquals(
            0,
            PngExporter.render(
                hidden,
                mapOf(paintAddress to paint, maskAddress to mask),
            ).rgbaAt(1, 0)[3].toInt() and 255,
        )
    }

    @Test fun clipping_mask_uses_alpha_of_layer_below() {
        val base = tile(10, 20, 30, 0)
        val baseOpaque = base.copyOf().also {
            it[3] = 255.toByte()
        }
        val top = tile(255, 0, 0, 255)
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
        val base = tile(80, 120, 180, 255)
        val top = tile(180, 80, 40, 255)
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
    fun text_placeholders_are_available_only_for_thumbnail_rendering() {
        val document = CanvasDocument(
            "text-thumb",
            120,
            80,
            layers = listOf(
                Layer(
                    "text",
                    "Text",
                    payload = LayerPayload.TextObject(
                        "NeoCanvas",
                        fontSize = 28f,
                        colorArgb = 0xffff0000.toInt(),
                        x = 20f,
                        y = 20f,
                        width = 80f,
                        height = 40f,
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalStateException> {
            PngExporter.render(document, emptyMap())
        }

        val preview = PngExporter.render(document, emptyMap(), allowTextPlaceholder = true)
        assertEquals(120, preview.width)
        assertEquals(80, preview.height)
        kotlin.test.assertTrue((3 until preview.rgba.size step 4).any { (preview.rgba[it].toInt() and 255) > 0 })
    }

    @Test
    fun font_aware_text_rasterizer_flattens_editable_text_for_export() {
        val document = CanvasDocument(
            "text-export",
            4,
            4,
            layers = listOf(
                Layer(
                    "text",
                    "Text",
                    opacity = .5f,
                    payload = LayerPayload.TextObject(
                        "Neo",
                        x = 0f,
                        y = 0f,
                        width = 4f,
                        height = 4f,
                    ),
                ),
            ),
        )
        var requestedSize = 0 to 0
        val image = PngExporter.render(
            document,
            emptyMap(),
            textRasterizer = TextRasterizer { _, width, height ->
                requestedSize = width to height
                ByteArray(width * height * 4).apply {
                    this[0] = 255.toByte()
                    this[3] = 255.toByte()
                }
            },
        )

        assertEquals(4 to 4, requestedSize)
        val first = image.rgbaAt(0, 0)
        assertEquals(255, first[0].toInt() and 255)
        assertEquals(128, first[3].toInt() and 255)
        assertEquals(0, image.rgbaAt(1, 0)[3].toInt() and 255)
    }

    @Test
    fun editable_shapes_flatten_into_png_and_text_never_disappears_silently() {
        val shapeDocument = CanvasDocument(
            "shape-png",
            32,
            32,
            layers = listOf(
                Layer(
                    "shape",
                    "Shape",
                    payload = LayerPayload.ShapeObject(
                        kind = ShapeKind.Rectangle,
                        x = 8f,
                        y = 8f,
                        width = 16f,
                        height = 12f,
                        fillArgb = 0xffff0000.toInt(),
                    ),
                ),
            ),
        )
        val rendered = PngExporter.render(shapeDocument, emptyMap())
        val inside = rendered.rgbaAt(12, 12)
        val outside = rendered.rgbaAt(2, 2)
        assertEquals(255, inside[0].toInt() and 255)
        assertEquals(255, inside[3].toInt() and 255)
        assertEquals(0, outside[3].toInt() and 255)

        val textDocument = CanvasDocument(
            "text-png",
            32,
            32,
            layers = listOf(
                Layer(
                    "text",
                    "Text",
                    payload = LayerPayload.TextObject(
                        "Hello",
                        x = 2f,
                        y = 2f,
                        width = 24f,
                        height = 10f,
                    ),
                ),
            ),
        )
        assertFailsWith<IllegalStateException> {
            PngExporter.render(textDocument, emptyMap())
        }
    }

    @Test
    fun png_dimensions_match_document_and_hidden_layers_are_excluded() {
        val red = tile(255, 0, 0, 255)
        val blue = tile(0, 0, 255, 255)
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

    private fun tile(r: Int, g: Int, b: Int, a: Int): ByteArray =
        ByteArray(TILE_SIZE_PIXELS * TILE_SIZE_PIXELS * 4).also { pixels ->
            for (index in pixels.indices step 4) {
                pixels[index] = r.coerceIn(0, 255).toByte()
                pixels[index + 1] = g.coerceIn(0, 255).toByte()
                pixels[index + 2] = b.coerceIn(0, 255).toByte()
                pixels[index + 3] = a.coerceIn(0, 255).toByte()
            }
        }
}
