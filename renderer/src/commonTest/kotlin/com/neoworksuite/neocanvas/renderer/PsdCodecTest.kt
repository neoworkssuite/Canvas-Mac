package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.TileAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class PsdCodecTest {
    @Test
    fun compatibility_report_describes_preserved_raster_document_and_lock_notice_inputs() {
        val address = TileAddress("paint", 0, 0)
        val tile = ByteArray(TileFormat.BYTES_PER_TILE).apply {
            this[0] = 100
            this[3] = 255.toByte()
        }
        val document = CanvasDocument(
            id = "preflight",
            width = 64,
            height = 32,
            layers = listOf(
                Layer(
                    id = "paint",
                    name = "Paint",
                    visible = false,
                    payload = LayerPayload.Raster(setOf(address)),
                    locked = true,
                    alphaLocked = true,
                    clipping = true,
                    blendMode = LayerBlendMode.Multiply,
                ),
            ),
        )

        val report = PsdCodec.analyzeExport(document, mapOf(address to tile))

        assertTrue(report.canExport)
        assertEquals(1, report.layerCount)
        assertEquals(1, report.hiddenLayerCount)
        assertEquals(1, report.clippingLayerCount)
        assertEquals(1, report.alphaLockedLayerCount)
        assertEquals(1, report.lockedLayerCount)
        assertTrue(report.estimatedRawPixelBytes > 0)
        assertTrue(report.blockingIssues.isEmpty())
    }

    @Test
    fun editable_objects_are_reported_and_rejected_by_psd_v1_export() {
        val document = CanvasDocument(
            id = "psd-object",
            width = 64,
            height = 64,
            layers = listOf(
                Layer(
                    "text",
                    "Text",
                    payload = LayerPayload.TextObject("Hello", x = 4f, y = 4f, width = 40f, height = 20f),
                ),
            ),
        )
        val report = PsdCodec.analyzeExport(document, emptyMap())
        assertFalse(report.canExport)
        assertEquals(1, report.editableObjectLayerCount)
        assertTrue(report.blockingIssues.any { "raster layers only" in it })

        val flattenable = PsdCodec.analyzeExport(
            document,
            emptyMap(),
            canFlattenEditableObjects = true,
        )
        assertTrue(flattenable.canExport)
        assertEquals(1, flattenable.editableObjectLayerCount)
        assertTrue(flattenable.blockingIssues.isEmpty())
        assertFailsWith<IllegalArgumentException> { PsdCodec.encode(document, emptyMap()) }
    }

    @Test
    fun layered_round_trip_preserves_raster_layers_and_supported_metadata() {
        val baseId = "base"
        val topId = "top"
        val baseTile = tile().also {
            pixel(it, 1, 1, 10, 20, 30, 255)
            pixel(it, 2, 1, 40, 50, 60, 255)
        }
        val topTile = tile().also {
            pixel(it, 2, 1, 200, 100, 50, 180)
        }
        val baseAddress = TileAddress(baseId, 0, 0)
        val topAddress = TileAddress(topId, 0, 0)
        val document = CanvasDocument(
            id = "psd-test",
            width = 8,
            height = 6,
            layers = listOf(
                Layer(
                    id = baseId,
                    name = "Background",
                    opacity = .8f,
                    payload = LayerPayload.Raster(setOf(baseAddress)),
                    alphaLocked = true,
                    blendMode = LayerBlendMode.Multiply,
                ),
                Layer(
                    id = topId,
                    name = "Paint Ω",
                    visible = false,
                    opacity = .55f,
                    payload = LayerPayload.Raster(setOf(topAddress)),
                    clipping = true,
                    blendMode = LayerBlendMode.Screen,
                ),
            ),
        )
        val encoded = PsdCodec.encode(document, mapOf(baseAddress to baseTile, topAddress to topTile))
        val decoded = PsdCodec.decode(encoded)

        assertEquals(8, decoded.document.width)
        assertEquals(6, decoded.document.height)
        assertEquals(listOf("Background", "Paint Ω"), decoded.document.layers.map { it.name })
        assertEquals(LayerBlendMode.Multiply, decoded.document.layers[0].blendMode)
        assertTrue(decoded.document.layers[0].alphaLocked)
        assertEquals(.8f, decoded.document.layers[0].opacity, .01f)
        assertFalse(decoded.document.layers[1].visible)
        assertTrue(decoded.document.layers[1].clipping)
        assertEquals(LayerBlendMode.Screen, decoded.document.layers[1].blendMode)

        val baseLayer = decoded.document.layers[0]
        val baseBytes = decoded.tiles[(baseLayer.payload as LayerPayload.Raster).tileAddresses.single()]!!
        assertEquals(10, channel(baseBytes, 1, 1, 0))
        assertEquals(20, channel(baseBytes, 1, 1, 1))
        assertEquals(30, channel(baseBytes, 1, 1, 2))
    }

    @Test
    fun flattened_rle_rgb_psd_imports_as_one_layer() {
        val bytes = minimalRlePsd(
            width = 3,
            height = 1,
            red = byteArrayOf(10, 20, 30),
            green = byteArrayOf(40, 50, 60),
            blue = byteArrayOf(70, 80, 90),
        )
        val decoded = PsdCodec.decode(bytes)

        assertEquals(1, decoded.document.layers.size)
        assertEquals("PSD Composite", decoded.document.layers.single().name)
        val tile = decoded.tiles.values.single()
        assertEquals(20, channel(tile, 1, 0, 0))
        assertEquals(50, channel(tile, 1, 0, 1))
        assertEquals(80, channel(tile, 1, 0, 2))
        assertEquals(255, alpha(tile, 1, 0))
    }

    @Test
    fun rejects_non_rgb_or_non_8_bit_psd() {
        assertFailsWith<IllegalArgumentException> { PsdCodec.decode(minimalRawHeader(depth = 16, mode = 3)) }
        assertFailsWith<IllegalArgumentException> { PsdCodec.decode(minimalRawHeader(depth = 8, mode = 4)) }
    }

    private fun minimalRawHeader(depth: Int, mode: Int): ByteArray = Writer().apply {
        ascii("8BPS"); u16(1); zeros(6); u16(3); u32(1); u32(1); u16(depth); u16(mode)
        u32(0); u32(0); u32(0)
        u16(0)
        u8(0); u8(0); u8(0)
    }.bytes()

    private fun minimalRlePsd(
        width: Int,
        height: Int,
        red: ByteArray,
        green: ByteArray,
        blue: ByteArray,
    ): ByteArray {
        require(height == 1)
        val rows = listOf(red, green, blue).map { plane ->
            byteArrayOf((plane.size - 1).toByte()) + plane
        }
        return Writer().apply {
            ascii("8BPS"); u16(1); zeros(6); u16(3); u32(height); u32(width); u16(8); u16(3)
            u32(0); u32(0); u32(0)
            u16(1)
            rows.forEach { u16(it.size) }
            rows.forEach(::raw)
        }.bytes()
    }

    private class Writer {
        private val data = mutableListOf<Byte>()
        fun u8(value: Int) { data += value.toByte() }
        fun u16(value: Int) { u8(value ushr 8); u8(value) }
        fun u32(value: Int) { u8(value ushr 24); u8(value ushr 16); u8(value ushr 8); u8(value) }
        fun zeros(count: Int) = repeat(count) { u8(0) }
        fun ascii(value: String) = value.forEach { u8(it.code) }
        fun raw(value: ByteArray) { value.forEach { data += it } }
        fun bytes() = data.toByteArray()
    }

    private fun tile() = ByteArray(TileFormat.BYTES_PER_TILE)

    private fun pixel(bytes: ByteArray, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * TILE_SIZE_PIXELS + x) * 4
        bytes[offset] = r.toByte()
        bytes[offset + 1] = g.toByte()
        bytes[offset + 2] = b.toByte()
        bytes[offset + 3] = a.toByte()
    }

    private fun channel(bytes: ByteArray, x: Int, y: Int, channel: Int): Int =
        bytes[(y * TILE_SIZE_PIXELS + x) * 4 + channel].toInt() and 255

    private fun alpha(bytes: ByteArray, x: Int, y: Int): Int = channel(bytes, x, y, 3)
}
