package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TiffExporterTest {
    @Test
    fun encodes_baseline_rgba_tiff_with_unassociated_alpha() {
        val rgba = byteArrayOf(
            255.toByte(), 0, 0, 255.toByte(),
            0, 128.toByte(), 255.toByte(), 64,
        )
        val bytes = TiffExporter.encode(PngImage(2, 1, rgba))

        assertEquals('I'.code, bytes[0].toInt() and 255)
        assertEquals('I'.code, bytes[1].toInt() and 255)
        assertEquals(42, u16(bytes, 2))
        assertEquals(8, u32(bytes, 4))

        val entryCount = u16(bytes, 8)
        assertEquals(15, entryCount)
        val entries = (0 until entryCount).associate { index ->
            val offset = 10 + index * 12
            u16(bytes, offset) to offset
        }

        assertEquals(2, u32(bytes, entries.getValue(256) + 8))
        assertEquals(1, u32(bytes, entries.getValue(257) + 8))
        assertEquals(1, u16(bytes, entries.getValue(259) + 8))
        assertEquals(2, u16(bytes, entries.getValue(262) + 8))
        assertEquals(4, u16(bytes, entries.getValue(277) + 8))
        assertEquals(2, u16(bytes, entries.getValue(338) + 8))

        val bitsOffset = u32(bytes, entries.getValue(258) + 8)
        assertContentEquals(listOf(8, 8, 8, 8), (0 until 4).map { u16(bytes, bitsOffset + it * 2) })

        val pixelOffset = u32(bytes, entries.getValue(273) + 8)
        assertEquals(rgba.size, u32(bytes, entries.getValue(279) + 8))
        assertTrue(pixelOffset + rgba.size <= bytes.size)
        assertContentEquals(rgba, bytes.copyOfRange(pixelOffset, pixelOffset + rgba.size))
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8) or
            ((bytes[offset + 2].toInt() and 255) shl 16) or
            ((bytes[offset + 3].toInt() and 255) shl 24)
}
