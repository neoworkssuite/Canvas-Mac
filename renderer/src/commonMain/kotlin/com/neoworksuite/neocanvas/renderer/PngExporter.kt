package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.SaveResult
import kotlin.math.max
import kotlin.math.min

/** A flattened RGBA8 image that can be written as a standards-compliant PNG. */
class PngImage(val width: Int, val height: Int, rgba: ByteArray) {
    val rgba: ByteArray = rgba.copyOf()

    init {
        require(width > 0 && height > 0) { "PNG dimensions must be positive." }
        require(this.rgba.size == width * height * 4) { "PNG pixels do not match its dimensions." }
    }

    fun rgbaAt(x: Int, y: Int): ByteArray {
        require(x in 0 until width && y in 0 until height) { "Pixel is outside the PNG." }
        val offset = (y * width + x) * 4
        return rgba.copyOfRange(offset, offset + 4)
    }

    fun encode(): ByteArray = PngEncoder.encode(width, height, rgba)
}

/** Host-owned output boundary; it deliberately has no network or account capability. */
fun interface PngTarget {
    fun write(bytes: ByteArray)
}

/** Composites visible raster layers at document resolution and writes a local PNG when requested. */
object PngExporter {
    fun export(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, target: PngTarget): SaveResult = try {
        target.write(render(document, tiles).encode())
        SaveResult.Success
    } catch (error: Exception) {
        SaveResult.Failure("Could not export PNG: ${error.message ?: "unknown output error"}")
    }

    fun render(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): PngImage {
        val output = ByteArray(document.width * document.height * 4)
        val groupsById = document.groups.associateBy { it.id }
        document.layers.forEachIndexed { index, layer ->
            val group = layer.groupId?.let(groupsById::get)
            val effectiveOpacity = layer.opacity * (group?.opacity ?: 1f)
            if (!layer.visible || group?.visible == false || effectiveOpacity <= 0f) return@forEachIndexed
            val raster = layer.payload as? LayerPayload.Raster ?: return@forEachIndexed
            val clippingBase = if (layer.clipping && index > 0) document.layers[index - 1] else null
            val clippingRaster = clippingBase?.payload as? LayerPayload.Raster
            raster.tileAddresses.forEach { address ->
                val sourcePixels = tiles[address] ?: return@forEach
                require(sourcePixels.size == TileFormat.BYTES_PER_TILE) { "Tile $address is not 256×256 RGBA." }
                val maskedPixels = applyLayerMask(sourcePixels, layer.mask, address, tiles)
                val pixels = if (layer.clipping) {
                    val basePixels = clippingRaster?.let {
                        val raw = tiles[TileAddress(clippingBase!!.id, address.x, address.y)]
                        raw?.let { applyLayerMask(it, clippingBase.mask, address, tiles) }
                    }
                    clipAlpha(maskedPixels, basePixels)
                } else maskedPixels
                compositeTile(output, document.width, document.height, address, pixels, effectiveOpacity, layer.blendMode)
            }
        }
        return PngImage(document.width, document.height, output)
    }

    private fun applyLayerMask(
        source: ByteArray,
        mask: com.neoworksuite.neocanvas.core.model.LayerMask?,
        address: TileAddress,
        tiles: Map<TileAddress, ByteArray>,
    ): ByteArray {
        if (mask == null || !mask.enabled) return source
        val maskPixels = tiles[TileAddress(mask.id, address.x, address.y)]
        if (maskPixels == null && !mask.inverted) return source
        val output = source.copyOf()
        var offset = 0
        while (offset < output.size) {
            val rawMask = maskPixels?.get(offset)?.toInt()?.and(255) ?: 255
            val maskValue = if (mask.inverted) 255 - rawMask else rawMask
            val sourceAlpha = output[offset + 3].toInt() and 255
            val maskedAlpha = (sourceAlpha * maskValue + 127) / 255
            output[offset + 3] = maskedAlpha.toByte()
            if (maskedAlpha == 0) {
                output[offset] = 0
                output[offset + 1] = 0
                output[offset + 2] = 0
            }
            offset += 4
        }
        return output
    }

    private fun clipAlpha(source: ByteArray, mask: ByteArray?): ByteArray {
        if (mask == null) return ByteArray(source.size)
        val output = source.copyOf()
        var offset = 0
        while (offset < output.size) {
            val sourceAlpha = output[offset + 3].toInt() and 255
            val maskAlpha = mask[offset + 3].toInt() and 255
            val clippedAlpha = (sourceAlpha * maskAlpha + 127) / 255
            output[offset + 3] = clippedAlpha.toByte()
            if (clippedAlpha == 0) {
                output[offset] = 0
                output[offset + 1] = 0
                output[offset + 2] = 0
            }
            offset += 4
        }
        return output
    }

    private fun compositeTile(output: ByteArray, outputWidth: Int, outputHeight: Int, address: TileAddress, tile: ByteArray,
        opacity: Float, blendMode: com.neoworksuite.neocanvas.core.model.LayerBlendMode) {
        val startX = address.x * TILE_SIZE_PIXELS
        val startY = address.y * TILE_SIZE_PIXELS
        val fromX = max(0, startX)
        val fromY = max(0, startY)
        val toX = min(outputWidth, startX + TILE_SIZE_PIXELS)
        val toY = min(outputHeight, startY + TILE_SIZE_PIXELS)
        if (fromX >= toX || fromY >= toY) return

        for (y in fromY until toY) for (x in fromX until toX) {
            val tileOffset = ((y - startY) * TILE_SIZE_PIXELS + (x - startX)) * 4
            val outputOffset = (y * outputWidth + x) * 4
            LayerCompositor.compositePixel(output, outputOffset, tile, tileOffset, opacity, blendMode)
        }
    }
}

private object PngEncoder {
    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

    fun encode(width: Int, height: Int, pixels: ByteArray): ByteArray {
        val rows = Bytes().apply {
            repeat(height) { y -> byte(0); bytes(pixels, y * width * 4, width * 4) }
        }.toByteArray()
        return Bytes().apply {
            bytes(signature)
            chunk("IHDR", Bytes().apply { intBig(width); intBig(height); byte(8); byte(6); byte(0); byte(0); byte(0) }.toByteArray())
            chunk("IDAT", zlibStore(rows))
            chunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun Bytes.chunk(type: String, data: ByteArray) {
        intBig(data.size); bytes(type.encodeToByteArray()); bytes(data); intBig(crc32(type.encodeToByteArray() + data))
    }

    private fun zlibStore(data: ByteArray): ByteArray = Bytes().apply {
        byte(0x78); byte(0x01)
        var offset = 0
        do {
            val count = min(65_535, data.size - offset)
            byte(if (offset + count == data.size) 1 else 0)
            byte(count); byte(count ushr 8); byte(count.inv()); byte(count.inv() ushr 8)
            bytes(data, offset, count); offset += count
        } while (offset < data.size)
        intBig(adler32(data))
    }.toByteArray()

    private fun crc32(data: ByteArray): Int {
        var value = -1
        data.forEach { byte ->
            value = value xor (byte.toInt() and 0xff)
            repeat(8) { value = if ((value and 1) != 0) (value ushr 1) xor 0xedb88320.toInt() else value ushr 1 }
        }
        return value.inv()
    }

    private fun adler32(data: ByteArray): Int {
        var a = 1; var b = 0
        data.forEach { byte -> a = (a + (byte.toInt() and 0xff)) % 65_521; b = (b + a) % 65_521 }
        return (b shl 16) or a
    }

    private class Bytes {
        private val data = ArrayList<Byte>()
        fun byte(value: Int) { data += value.toByte() }
        fun bytes(value: ByteArray, offset: Int = 0, length: Int = value.size - offset) { repeat(length) { data += value[offset + it] } }
        fun intBig(value: Int) { byte(value ushr 24); byte(value ushr 16); byte(value ushr 8); byte(value) }
        fun toByteArray(): ByteArray = data.toByteArray()
    }
}
