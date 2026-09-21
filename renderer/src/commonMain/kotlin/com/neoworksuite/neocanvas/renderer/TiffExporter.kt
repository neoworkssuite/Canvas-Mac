package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.SaveResult

/** Local output boundary for flattened TIFF interchange files. */
fun interface TiffTarget {
    fun write(bytes: ByteArray)
}

/**
 * Baseline little-endian TIFF encoder for full-resolution RGBA8 artwork.
 *
 * The image is stored as one uncompressed strip with unassociated alpha, which keeps the output
 * lossless and broadly readable while avoiding a platform-specific codec dependency.
 */
object TiffExporter {
    fun export(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
        textRasterizer: TextRasterizer? = null,
        target: TiffTarget,
    ): SaveResult = try {
        val image = PngExporter.render(document, tiles, textRasterizer = textRasterizer)
        target.write(encode(image))
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export TIFF: " + (error.message ?: "unknown output error"))
    }

    fun encode(image: PngImage): ByteArray {
        val entryCount = 15
        val headerSize = 8
        val ifdSize = 2 + entryCount * 12 + 4
        val bitsOffset = headerSize + ifdSize
        val xResolutionOffset = bitsOffset + 8
        val yResolutionOffset = xResolutionOffset + 8
        val pixelOffset = yResolutionOffset + 8
        val pixelBytes = image.rgba.size
        val output = ByteArray(pixelOffset + pixelBytes)

        fun short(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
        }

        fun int(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
            output[offset + 2] = (value ushr 16).toByte()
            output[offset + 3] = (value ushr 24).toByte()
        }

        output[0] = 'I'.code.toByte()
        output[1] = 'I'.code.toByte()
        short(2, 42)
        int(4, headerSize)
        short(headerSize, entryCount)

        var entryOffset = headerSize + 2
        fun entry(tag: Int, type: Int, count: Int, value: Int, inlineShort: Boolean = false) {
            short(entryOffset, tag)
            short(entryOffset + 2, type)
            int(entryOffset + 4, count)
            if (inlineShort) {
                short(entryOffset + 8, value)
                short(entryOffset + 10, 0)
            } else {
                int(entryOffset + 8, value)
            }
            entryOffset += 12
        }

        entry(256, 4, 1, image.width)
        entry(257, 4, 1, image.height)
        entry(258, 3, 4, bitsOffset)
        entry(259, 3, 1, 1, inlineShort = true)
        entry(262, 3, 1, 2, inlineShort = true)
        entry(273, 4, 1, pixelOffset)
        entry(274, 3, 1, 1, inlineShort = true)
        entry(277, 3, 1, 4, inlineShort = true)
        entry(278, 4, 1, image.height)
        entry(279, 4, 1, pixelBytes)
        entry(282, 5, 1, xResolutionOffset)
        entry(283, 5, 1, yResolutionOffset)
        entry(284, 3, 1, 1, inlineShort = true)
        entry(296, 3, 1, 2, inlineShort = true)
        entry(338, 3, 1, 2, inlineShort = true)
        int(entryOffset, 0)

        repeat(4) { index -> short(bitsOffset + index * 2, 8) }
        int(xResolutionOffset, 72)
        int(xResolutionOffset + 4, 1)
        int(yResolutionOffset, 72)
        int(yResolutionOffset + 4, 1)
        image.rgba.copyInto(output, destinationOffset = pixelOffset)
        return output
    }
}
