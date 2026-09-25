package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.SaveResult

/** Local output boundary for flattened single-page PDF interchange files. */
fun interface PdfTarget {
    fun write(bytes: ByteArray)
}

/**
 * Small dependency-free PDF image exporter shared by desktop hosts.
 * Artwork is flattened to opaque RGB on white and embedded as a single image XObject.
 */
object PdfExporter {
    fun export(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
        textRasterizer: TextRasterizer? = null,
        target: PdfTarget,
    ): SaveResult = try {
        val image = PngExporter.render(document, tiles, textRasterizer = textRasterizer)
        target.write(encode(image))
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export PDF: " + (error.message ?: "unknown output error"))
    }

    fun encode(image: PngImage): ByteArray {
        val rgb = ByteArray(image.width * image.height * 3)
        var source = 0
        var target = 0
        while (source < image.rgba.size) {
            val alpha = image.rgba[source + 3].toInt() and 0xff
            val inverse = 255 - alpha
            val red = image.rgba[source].toInt() and 0xff
            val green = image.rgba[source + 1].toInt() and 0xff
            val blue = image.rgba[source + 2].toInt() and 0xff
            rgb[target] = ((red * alpha + 255 * inverse + 127) / 255).toByte()
            rgb[target + 1] = ((green * alpha + 255 * inverse + 127) / 255).toByte()
            rgb[target + 2] = ((blue * alpha + 255 * inverse + 127) / 255).toByte()
            source += 4
            target += 3
        }

        val content = "q\n${image.width} 0 0 ${image.height} 0 0 cm\n/Im0 Do\nQ\n".encodeToByteArray()
        val out = ByteAccumulator()
        val offsets = IntArray(6)

        out.ascii("%PDF-1.4\n% NeoCanvas\n")

        offsets[1] = out.size
        out.ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets[2] = out.size
        out.ascii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets[3] = out.size
        out.ascii(
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${image.width} ${image.height}] " +
                "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
        )

        offsets[4] = out.size
        out.ascii(
            "4 0 obj\n<< /Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Length ${rgb.size} >>\nstream\n"
        )
        out.bytes(rgb)
        out.ascii("\nendstream\nendobj\n")

        offsets[5] = out.size
        out.ascii("5 0 obj\n<< /Length ${content.size} >>\nstream\n")
        out.bytes(content)
        out.ascii("endstream\nendobj\n")

        val xref = out.size
        out.ascii("xref\n0 6\n")
        out.ascii("0000000000 65535 f \n")
        for (id in 1..5) {
            out.ascii(offsets[id].toString().padStart(10, '0') + " 00000 n \n")
        }
        out.ascii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}

private class ByteAccumulator(initialCapacity: Int = 4096) {
    private var data = ByteArray(initialCapacity)
    var size: Int = 0
        private set

    fun ascii(value: String) = bytes(value.encodeToByteArray())

    fun bytes(value: ByteArray) {
        ensure(size + value.size)
        value.copyInto(data, destinationOffset = size)
        size += value.size
    }

    fun toByteArray(): ByteArray = data.copyOf(size)

    private fun ensure(required: Int) {
        if (required <= data.size) return
        var capacity = data.size.coerceAtLeast(1)
        while (capacity < required) capacity = (capacity * 2).coerceAtLeast(required)
        data = data.copyOf(capacity)
    }
}
