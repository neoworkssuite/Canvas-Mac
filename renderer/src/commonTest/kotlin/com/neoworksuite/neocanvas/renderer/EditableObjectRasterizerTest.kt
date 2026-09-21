package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EditableObjectRasterizerTest {
    @Test
    fun editable_text_and_shapes_become_separate_raster_layers_for_interchange() {
        val source = CanvasDocument(
            id = "objects-to-psd",
            width = 32,
            height = 32,
            layers = listOf(
                Layer(
                    "shape",
                    "Badge",
                    opacity = .75f,
                    payload = LayerPayload.ShapeObject(
                        kind = ShapeKind.Rectangle,
                        x = 8f,
                        y = 8f,
                        width = 12f,
                        height = 10f,
                        fillArgb = 0xffff0000.toInt(),
                    ),
                ),
                Layer(
                    "text",
                    "Title",
                    opacity = .6f,
                    payload = LayerPayload.TextObject(
                        "Neo",
                        x = 2f,
                        y = 2f,
                        width = 20f,
                        height = 10f,
                    ),
                ),
            ),
        )
        val flattened = EditableObjectRasterizer.rasterize(
            source,
            emptyMap(),
            TextRasterizer { _, width, height ->
                ByteArray(width * height * 4).apply {
                    val offset = (3 * width + 3) * 4
                    this[offset + 1] = 255.toByte()
                    this[offset + 3] = 255.toByte()
                }
            },
        )

        assertIs<LayerPayload.ShapeObject>(source.layers[0].payload)
        assertIs<LayerPayload.TextObject>(source.layers[1].payload)
        assertTrue(flattened.document.layers.all { it.payload is LayerPayload.Raster })
        assertEquals(listOf("Badge", "Title"), flattened.document.layers.map { it.name })
        assertEquals(.75f, flattened.document.layers[0].opacity)
        assertEquals(.6f, flattened.document.layers[1].opacity)
        assertTrue(flattened.tiles.keys.any { it.layerId == "shape" })
        assertTrue(flattened.tiles.keys.any { it.layerId == "text" })

        val encoded = PsdCodec.encode(flattened.document, flattened.tiles)
        val decoded = PsdCodec.decode(encoded)
        assertEquals(listOf("Badge", "Title"), decoded.document.layers.map { it.name })
        assertTrue(decoded.document.layers.all { it.payload is LayerPayload.Raster })
    }
}
