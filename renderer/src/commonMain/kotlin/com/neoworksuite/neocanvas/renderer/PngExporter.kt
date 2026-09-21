package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
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

    fun render(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
        allowTextPlaceholder: Boolean = false,
    ): PngImage {
        val output = ByteArray(document.width * document.height * 4)
        val groupsById = document.groups.associateBy { it.id }
        document.layers.forEachIndexed { index, layer ->
            val group = layer.groupId?.let(groupsById::get)
            val effectiveOpacity = layer.opacity * (group?.opacity ?: 1f)
            if (!layer.visible || group?.visible == false || effectiveOpacity <= 0f) return@forEachIndexed
            when (val payload = layer.payload) {
                is LayerPayload.Raster -> {
                    val clippingBase = if (layer.clipping && index > 0) document.layers[index - 1] else null
                    val clippingRaster = clippingBase?.payload as? LayerPayload.Raster
                    payload.tileAddresses.forEach { address ->
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

                is LayerPayload.ShapeObject -> {
                    require(layer.mask == null && !layer.clipping) {
                        "Editable shapes with masks or clipping must be rasterized before PNG export."
                    }
                    compositeShape(
                        output,
                        document.width,
                        document.height,
                        payload,
                        effectiveOpacity,
                        layer.blendMode,
                    )
                }

                is LayerPayload.TextObject -> {
                    if (!allowTextPlaceholder) {
                        error(
                            "Editable text needs font-aware rasterization before PNG export. Keep the NeoCanvas file or rasterize the text layer.",
                        )
                    }
                    compositeTextPlaceholder(
                        output,
                        document.width,
                        document.height,
                        payload,
                        effectiveOpacity,
                        layer.blendMode,
                    )
                }
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

    private fun compositeTextPlaceholder(
        output: ByteArray,
        outputWidth: Int,
        outputHeight: Int,
        text: LayerPayload.TextObject,
        opacity: Float,
        blendMode: com.neoworksuite.neocanvas.core.model.LayerBlendMode,
    ) {
        val centerX = text.x + text.width / 2f
        val centerY = text.y + text.height / 2f
        val angle = text.rotationDegrees * kotlin.math.PI.toFloat() / 180f
        val cosA = kotlin.math.cos(angle)
        val sinA = kotlin.math.sin(angle)
        val source = ByteArray(4)
        val argb = text.colorArgb
        source[0] = (argb ushr 16).toByte()
        source[1] = (argb ushr 8).toByte()
        source[2] = argb.toByte()

        fun inverseLocal(px: Float, py: Float): Pair<Float, Float> {
            val dx = px - centerX
            val dy = py - centerY
            val lx = centerX + dx * cosA + dy * sinA - text.x
            val ly = centerY - dx * sinA + dy * cosA - text.y
            return lx to ly
        }

        val radius = kotlin.math.sqrt(text.width * text.width + text.height * text.height) / 2f + 2f
        val fromX = kotlin.math.floor(centerX - radius).toInt().coerceIn(0, outputWidth)
        val fromY = kotlin.math.floor(centerY - radius).toInt().coerceIn(0, outputHeight)
        val toX = kotlin.math.ceil(centerX + radius).toInt().coerceIn(0, outputWidth)
        val toY = kotlin.math.ceil(centerY + radius).toInt().coerceIn(0, outputHeight)
        val lineCount = (text.text.lineSequence().count().coerceIn(1, 4))
        val lineHeight = text.height / (lineCount + 2f)
        val strokeThickness = maxOf(1f, minOf(text.fontSize * .08f, lineHeight * .18f))
        val alphaBase = ((argb ushr 24) and 255) / 255f

        for (y in fromY until toY) {
            for (x in fromX until toX) {
                val (lx, ly) = inverseLocal(x + .5f, y + .5f)
                if (lx !in 0f..text.width || ly !in 0f..text.height) continue
                var covered = false
                for (line in 0 until lineCount) {
                    val lineY = lineHeight * (line + 1.5f)
                    val left = text.width * .08f
                    val right = text.width * if (line == lineCount - 1) .68f else .92f
                    if (lx in left..right && kotlin.math.abs(ly - lineY) <= strokeThickness / 2f) {
                        covered = true
                        break
                    }
                }
                if (!covered) continue
                source[3] = (255f * alphaBase * .48f + .5f).toInt().coerceIn(0, 255).toByte()
                LayerCompositor.compositePixel(
                    output,
                    (y * outputWidth + x) * 4,
                    source,
                    0,
                    opacity,
                    blendMode,
                )
            }
        }
    }

    private fun compositeShape(
        output: ByteArray,
        outputWidth: Int,
        outputHeight: Int,
        shape: LayerPayload.ShapeObject,
        opacity: Float,
        blendMode: com.neoworksuite.neocanvas.core.model.LayerBlendMode,
    ) {
        val strokeHalf = if (shape.strokeArgb != null) shape.strokeWidth / 2f else 0f
        val centerX = shape.x + shape.width / 2f
        val centerY = shape.y + shape.height / 2f
        val angle = shape.rotationDegrees * kotlin.math.PI.toFloat() / 180f
        val cosA = kotlin.math.cos(angle)
        val sinA = kotlin.math.sin(angle)

        fun rotatePoint(x: Float, y: Float): Pair<Float, Float> {
            val dx = x - centerX
            val dy = y - centerY
            return (centerX + dx * cosA - dy * sinA) to (centerY + dx * sinA + dy * cosA)
        }

        val rawLeft = minOf(shape.x, shape.x + shape.width) - strokeHalf - 2f
        val rawTop = minOf(shape.y, shape.y + shape.height) - strokeHalf - 2f
        val rawRight = maxOf(shape.x, shape.x + shape.width) + strokeHalf + 2f
        val rawBottom = maxOf(shape.y, shape.y + shape.height) + strokeHalf + 2f
        val corners = listOf(
            rotatePoint(rawLeft, rawTop),
            rotatePoint(rawRight, rawTop),
            rotatePoint(rawLeft, rawBottom),
            rotatePoint(rawRight, rawBottom),
        )
        val fromX = kotlin.math.floor(corners.minOf { it.first }).toInt().coerceIn(0, outputWidth)
        val fromY = kotlin.math.floor(corners.minOf { it.second }).toInt().coerceIn(0, outputHeight)
        val toX = kotlin.math.ceil(corners.maxOf { it.first }).toInt().coerceIn(0, outputWidth)
        val toY = kotlin.math.ceil(corners.maxOf { it.second }).toInt().coerceIn(0, outputHeight)
        if (fromX >= toX || fromY >= toY) return

        val inverseCos = cosA
        val inverseSin = -sinA
        val sampleOffsets = floatArrayOf(.25f, .75f)
        val source = ByteArray(4)

        fun localPoint(px: Float, py: Float): Pair<Float, Float> {
            val dx = px - centerX
            val dy = py - centerY
            val rx = centerX + dx * inverseCos - dy * inverseSin
            val ry = centerY + dx * inverseSin + dy * inverseCos
            return (rx - shape.x) to (ry - shape.y)
        }

        fun rectangleFill(lx: Float, ly: Float): Boolean =
            lx >= 0f && lx <= shape.width && ly >= 0f && ly <= shape.height

        fun rectangleStroke(lx: Float, ly: Float): Boolean {
            if (shape.strokeArgb == null) return false
            val half = shape.strokeWidth / 2f
            val outer = lx >= -half && lx <= shape.width + half && ly >= -half && ly <= shape.height + half
            if (!outer) return false
            val innerLeft = half
            val innerTop = half
            val innerRight = shape.width - half
            val innerBottom = shape.height - half
            val inner = innerRight > innerLeft && innerBottom > innerTop &&
                lx > innerLeft && lx < innerRight && ly > innerTop && ly < innerBottom
            return !inner
        }

        fun ellipseRadius(lx: Float, ly: Float): Float {
            val rx = shape.width / 2f
            val ry = shape.height / 2f
            if (rx <= 0f || ry <= 0f) return Float.POSITIVE_INFINITY
            val nx = (lx - rx) / rx
            val ny = (ly - ry) / ry
            return kotlin.math.sqrt(nx * nx + ny * ny)
        }

        fun ellipseFill(lx: Float, ly: Float): Boolean = ellipseRadius(lx, ly) <= 1f

        fun ellipseStroke(lx: Float, ly: Float): Boolean {
            if (shape.strokeArgb == null) return false
            val minRadius = minOf(kotlin.math.abs(shape.width), kotlin.math.abs(shape.height)) / 2f
            if (minRadius <= 0f) return false
            return kotlin.math.abs(ellipseRadius(lx, ly) - 1f) * minRadius <= shape.strokeWidth / 2f
        }

        fun lineStroke(lx: Float, ly: Float): Boolean {
            val vx = shape.width
            val vy = shape.height
            val length2 = vx * vx + vy * vy
            if (length2 <= 0f) return false
            val t = ((lx * vx + ly * vy) / length2).coerceIn(0f, 1f)
            val nearestX = t * vx
            val nearestY = t * vy
            val dx = lx - nearestX
            val dy = ly - nearestY
            return kotlin.math.sqrt(dx * dx + dy * dy) <= shape.strokeWidth.coerceAtLeast(1f) / 2f
        }

        fun compositeSolid(argb: Int, coverage: Float, destinationOffset: Int) {
            if (coverage <= 0f) return
            source[0] = (argb ushr 16).toByte()
            source[1] = (argb ushr 8).toByte()
            source[2] = argb.toByte()
            val alpha = (argb ushr 24) and 255
            source[3] = (alpha * coverage + .5f).toInt().coerceIn(0, 255).toByte()
            LayerCompositor.compositePixel(output, destinationOffset, source, 0, opacity, blendMode)
        }

        for (y in fromY until toY) {
            for (x in fromX until toX) {
                var fillHits = 0
                var strokeHits = 0
                for (oy in sampleOffsets) {
                    for (ox in sampleOffsets) {
                        val (lx, ly) = localPoint(x + ox, y + oy)
                        when (shape.kind) {
                            ShapeKind.Rectangle -> {
                                if (shape.fillArgb != null && rectangleFill(lx, ly)) fillHits++
                                if (rectangleStroke(lx, ly)) strokeHits++
                            }
                            ShapeKind.Ellipse -> {
                                if (shape.fillArgb != null && ellipseFill(lx, ly)) fillHits++
                                if (ellipseStroke(lx, ly)) strokeHits++
                            }
                            ShapeKind.Line -> if (lineStroke(lx, ly)) strokeHits++
                        }
                    }
                }
                val destinationOffset = (y * outputWidth + x) * 4
                shape.fillArgb?.let { compositeSolid(it, fillHits / 4f, destinationOffset) }
                shape.strokeArgb?.let { compositeSolid(it, strokeHits / 4f, destinationOffset) }
            }
        }
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
