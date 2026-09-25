package com.neoworksuite.neocanvas.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfExporterTest {
    @Test
    fun encodes_a_single_page_rgb_pdf() {
        val image = PngImage(
            width = 2,
            height = 1,
            rgba = byteArrayOf(
                255.toByte(), 0, 0, 255.toByte(),
                0, 0, 255.toByte(), 128.toByte(),
            ),
        )

        val bytes = PdfExporter.encode(image)
        val text = bytes.decodeToString(throwOnInvalidSequence = false)

        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/Width 2 /Height 1"))
        assertTrue(text.contains("/ColorSpace /DeviceRGB"))
        assertTrue(text.contains("xref\n0 6"))
        assertTrue(text.endsWith("%%EOF\n"))

        val streamHeader = "/BitsPerComponent 8 /Length 6 >>\nstream\n".encodeToByteArray()
        val start = bytes.indexOfSubsequence(streamHeader) + streamHeader.size
        assertTrue(start >= streamHeader.size)
        assertEquals(255.toByte(), bytes[start])
        assertEquals(0.toByte(), bytes[start + 1])
        assertEquals(0.toByte(), bytes[start + 2])
        assertTrue((bytes[start + 3].toInt() and 0xff) in 126..128)
        assertTrue((bytes[start + 4].toInt() and 0xff) in 126..128)
        assertEquals(255.toByte(), bytes[start + 5])
    }
}

private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
    if (needle.isEmpty()) return 0
    for (start in 0..size - needle.size) {
        var matches = true
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) {
                matches = false
                break
            }
        }
        if (matches) return start
    }
    return -1
}
