package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.TileAddress

data class PsdImportResult(
    val document: CanvasDocument,
    val tiles: Map<TileAddress, ByteArray>,
    val warnings: List<String> = emptyList(),
)

data class PsdCompatibilityReport(
    val width: Int,
    val height: Int,
    val layerCount: Int,
    val hiddenLayerCount: Int,
    val clippingLayerCount: Int,
    val alphaLockedLayerCount: Int,
    val lockedLayerCount: Int,
    val estimatedRawPixelBytes: Long,
    val canExport: Boolean,
    val blockingIssues: List<String>,
)

/**
 * Photoshop PSD v1 interoperability for NeoCanvas.
 *
 * V1 targets the portable subset NeoCanvas can represent faithfully:
 * RGB, 8 bits/channel, raster layers and Photoshop Raw/RLE channel compression.
 */
object PsdCodec {
    private const val MAX_PIXELS = 16_000_000L
    private const val MAX_DIMENSION = 8_192
    private const val MAX_LAYERS = 512
    private const val RGB_MODE = 3
    private const val DEPTH_8 = 8
    private const val SIGNATURE = "8BPS"
    private const val BLEND_SIGNATURE = "8BIM"

    fun analyzeExport(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ): PsdCompatibilityReport {
        val issues = mutableListOf<String>()
        if (document.width !in 1..30_000 || document.height !in 1..30_000) {
            issues += "PSD dimensions must be between 1 and 30,000 pixels."
        }
        if (document.layers.size > MAX_LAYERS) {
            issues += "PSD export supports up to " + MAX_LAYERS + " layers."
        }
        if (document.layers.any { it.payload !is LayerPayload.Raster }) {
            issues += "PSD V1 export currently supports raster layers only."
        }

        var layerPixels = 0L
        document.layers.forEach { layer ->
            val bounds = findBounds(document, layer, tiles)
            layerPixels += bounds.width.toLong() * bounds.height.toLong()
        }
        val mergedPixels = document.width.toLong() * document.height.toLong()
        val estimated = layerPixels * 4L + mergedPixels * 4L

        return PsdCompatibilityReport(
            width = document.width,
            height = document.height,
            layerCount = document.layers.size,
            hiddenLayerCount = document.layers.count { !it.visible },
            clippingLayerCount = document.layers.count { it.clipping },
            alphaLockedLayerCount = document.layers.count { it.alphaLocked },
            lockedLayerCount = document.layers.count { it.locked },
            estimatedRawPixelBytes = estimated,
            canExport = issues.isEmpty(),
            blockingIssues = issues,
        )
    }

    fun encode(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): ByteArray {
        require(document.width in 1..30_000 && document.height in 1..30_000) {
            "PSD dimensions must be between 1 and 30,000 pixels."
        }
        require(document.layers.size <= MAX_LAYERS) { "PSD export supports up to " + MAX_LAYERS + " layers." }

        val merged = PngExporter.render(document, tiles).rgba
        val hasMergedAlpha = (3 until merged.size step 4).any { (merged[it].toInt() and 255) != 255 }
        val encodedLayers = document.layers.asReversed().map { layer ->
            PsdEncodedLayer(layer, findBounds(document, layer, tiles))
        }

        val layerInfo = PsdWriter()
        if (encodedLayers.isNotEmpty()) {
            layerInfo.i16(if (hasMergedAlpha) -encodedLayers.size else encodedLayers.size)
            encodedLayers.forEach { encoded -> writeLayerRecord(layerInfo, encoded) }
            encodedLayers.forEach { encoded -> writeLayerChannels(layerInfo, encoded, tiles) }
        }
        layerInfo.padTo(2)

        val layerAndMask = PsdWriter()
        layerAndMask.u32(layerInfo.size)
        layerAndMask.bytes(layerInfo.toByteArray())
        layerAndMask.u32(0)

        return PsdWriter().apply {
            ascii(SIGNATURE)
            u16(1)
            zeros(6)
            u16(if (hasMergedAlpha) 4 else 3)
            u32(document.height)
            u32(document.width)
            u16(DEPTH_8)
            u16(RGB_MODE)
            u32(0)
            u32(0)
            u32(layerAndMask.size)
            bytes(layerAndMask.toByteArray())
            writeMergedImage(this, merged, document.width, document.height, hasMergedAlpha)
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): PsdImportResult {
        require(bytes.size >= 26) { "PSD is too small to contain a valid header." }
        val reader = PsdReader(bytes)
        require(reader.ascii(4) == SIGNATURE) { "This file is not a Photoshop PSD." }
        require(reader.u16() == 1) { "PSB and non-v1 Photoshop documents are not supported yet." }
        reader.skip(6)
        val channels = reader.u16()
        val height = reader.u32Int()
        val width = reader.u32Int()
        val depth = reader.u16()
        val mode = reader.u16()

        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_PIXELS) {
            "PSD is too large for NeoCanvas. Use up to 8192 pixels per side and 16 million pixels total."
        }
        require(depth == DEPTH_8) { "NeoCanvas PSD V1 currently supports 8-bit/channel documents." }
        require(mode == RGB_MODE) { "NeoCanvas PSD V1 currently supports RGB Photoshop documents." }
        require(channels in 3..56) { "PSD has an invalid channel count." }

        reader.skipSection32("color mode")
        reader.skipSection32("image resources")

        val warnings = mutableListOf<String>()
        val layerAndMaskLength = reader.u32Int()
        val layerAndMask = reader.subReader(layerAndMaskLength)
        var layerCountWasNegative = false
        var decodedLayers: List<DecodedPsdLayer> = emptyList()

        if (layerAndMask.remaining >= 4) {
            val layerInfoLength = layerAndMask.u32Int()
            if (layerInfoLength > 0) {
                val layerInfo = layerAndMask.subReader(layerInfoLength)
                if (layerInfo.remaining >= 2) {
                    val signedCount = layerInfo.i16()
                    layerCountWasNegative = signedCount < 0
                    val layerCount = kotlin.math.abs(signedCount.toInt())
                    require(layerCount <= MAX_LAYERS) { "PSD has too many layers for NeoCanvas." }
                    val records = List(layerCount) { layerIndex ->
                        readLayerRecord(layerInfo, layerIndex, warnings)
                    }
                    decodedLayers = records.map { record ->
                        readLayerChannels(layerInfo, record)
                    }
                }
            }
        }

        val composite = if (reader.remaining >= 2) {
            runCatching {
                readComposite(reader, width, height, channels, layerCountWasNegative || decodedLayers.isEmpty())
            }.getOrNull()
        } else null

        if (decodedLayers.isEmpty()) {
            val rgba = composite ?: error("PSD contains no readable layer or merged image data.")
            val layerId = "psd-layer-1"
            val layerTiles = rgbaToTiles(layerId, width, height, 0, 0, width, height, rgba)
            val layer = Layer(
                id = layerId,
                name = "PSD Composite",
                payload = LayerPayload.Raster(layerTiles.keys.toSet()),
            )
            return PsdImportResult(
                document = CanvasDocument.blank(width, height).copy(layers = listOf(layer)),
                tiles = layerTiles,
                warnings = warnings,
            )
        }

        val tiles = linkedMapOf<TileAddress, ByteArray>()
        val topToBottom = decodedLayers.mapIndexed { layerIndex, source ->
            val layerId = "psd-layer-" + (layerIndex + 1)
            val layerTiles = channelsToTiles(layerId, width, height, source)
            tiles.putAll(layerTiles)
            Layer(
                id = layerId,
                name = source.record.name.ifBlank { "Layer " + (layerIndex + 1) },
                visible = source.record.visible,
                opacity = source.record.opacity / 255f,
                payload = LayerPayload.Raster(layerTiles.keys.toSet()),
                alphaLocked = source.record.alphaLocked,
                clipping = source.record.clipping,
                blendMode = source.record.blendMode,
            )
        }

        return PsdImportResult(
            document = CanvasDocument.blank(width, height).copy(layers = topToBottom.asReversed()),
            tiles = tiles,
            warnings = warnings.distinct(),
        )
    }

    private fun writeLayerRecord(writer: PsdWriter, encoded: PsdEncodedLayer) {
        val bounds = encoded.bounds
        val planeSize = bounds.width.toLong() * bounds.height.toLong()
        require(planeSize <= Int.MAX_VALUE.toLong()) { "Layer is too large for PSD export." }

        writer.i32(bounds.top)
        writer.i32(bounds.left)
        writer.i32(bounds.bottom)
        writer.i32(bounds.right)
        writer.u16(4)
        listOf(-1, 0, 1, 2).forEach { channelId ->
            writer.i16(channelId)
            writer.u32(2L + planeSize)
        }
        writer.ascii(BLEND_SIGNATURE)
        writer.ascii(blendKey(encoded.layer.blendMode))
        writer.u8((encoded.layer.opacity.coerceIn(0f, 1f) * 255f + .5f).toInt())
        writer.u8(if (encoded.layer.clipping) 1 else 0)
        var flags = 0x08
        if (encoded.layer.alphaLocked) flags = flags or 0x01
        if (!encoded.layer.visible) flags = flags or 0x02
        writer.u8(flags)
        writer.u8(0)

        val extra = PsdWriter().apply {
            u32(0)
            u32(0)
            pascal4(encoded.layer.name)
            val unicode = PsdWriter().apply { unicodeString(encoded.layer.name) }.toByteArray()
            ascii(BLEND_SIGNATURE)
            ascii("luni")
            u32(unicode.size)
            bytes(unicode)
            padTo(2)
        }.toByteArray()
        writer.u32(extra.size)
        writer.bytes(extra)
    }

    private fun writeLayerChannels(
        writer: PsdWriter,
        encoded: PsdEncodedLayer,
        tiles: Map<TileAddress, ByteArray>,
    ) {
        listOf(3, 0, 1, 2).forEach { rgbaChannel ->
            writer.u16(0)
            val bounds = encoded.bounds
            for (y in bounds.top until bounds.bottom) {
                for (x in bounds.left until bounds.right) {
                    writer.u8(layerChannelAt(encoded.layer.id, tiles, x, y, rgbaChannel))
                }
            }
        }
    }

    private fun writeMergedImage(
        writer: PsdWriter,
        rgba: ByteArray,
        width: Int,
        height: Int,
        includeAlpha: Boolean,
    ) {
        writer.u16(0)
        val channelCount = if (includeAlpha) 4 else 3
        for (channel in 0 until channelCount) {
            for (pixel in 0 until width * height) {
                if (channel == 3) {
                    writer.u8(rgba[pixel * 4 + 3].toInt() and 255)
                } else if (includeAlpha) {
                    val alpha = (rgba[pixel * 4 + 3].toInt() and 255) / 255f
                    val source = rgba[pixel * 4 + channel].toInt() and 255
                    val matte = (source * alpha + 255f * (1f - alpha) + .5f).toInt().coerceIn(0, 255)
                    writer.u8(matte)
                } else {
                    writer.u8(rgba[pixel * 4 + channel].toInt() and 255)
                }
            }
        }
    }

    private fun readLayerRecord(
        reader: PsdReader,
        layerIndex: Int,
        warnings: MutableList<String>,
    ): PsdLayerRecord {
        val top = reader.i32()
        val left = reader.i32()
        val bottom = reader.i32()
        val right = reader.i32()
        require(bottom >= top && right >= left) { "PSD layer has invalid bounds." }
        val channelCount = reader.u16()
        require(channelCount in 0..56) { "PSD layer has an invalid channel count." }
        val channelRecords = List(channelCount) {
            PsdChannelRecord(reader.i16().toInt(), reader.u32Int())
        }
        require(reader.ascii(4) == BLEND_SIGNATURE) { "PSD layer blend signature is invalid." }
        val blendKey = reader.ascii(4)
        val blendMode = blendMode(blendKey)
        if (blendMode == null) {
            warnings += "Layer " + (layerIndex + 1) + ": unsupported blend mode '" + blendKey + "' imported as Normal."
        }
        val opacity = reader.u8()
        val clipping = reader.u8() != 0
        val flags = reader.u8()
        reader.u8()

        val extraLength = reader.u32Int()
        val extra = reader.subReader(extraLength)
        if (extra.remaining >= 4) {
            val maskLength = extra.u32Int()
            extra.skip(maskLength)
        }
        if (extra.remaining >= 4) {
            val rangesLength = extra.u32Int()
            extra.skip(rangesLength)
        }
        var name = if (extra.remaining > 0) extra.pascal4() else "Layer " + (layerIndex + 1)

        while (extra.remaining >= 12) {
            val signature = extra.ascii(4)
            val key = extra.ascii(4)
            val length = extra.u32Int()
            if (signature != "8BIM" && signature != "8B64") {
                warnings += "Layer '" + name + "': stopped reading unknown PSD layer metadata."
                break
            }
            val data = extra.subReader(length)
            if (key == "luni" && data.remaining >= 4) {
                name = data.unicodeString().ifBlank { name }
            } else if (key in rasterisedFeatureKeys) {
                warnings += "Layer '" + name + "': Photoshop feature '" + key + "' imported as raster pixels."
            }
            if ((length and 1) != 0 && extra.remaining > 0) extra.skip(1)
        }

        return PsdLayerRecord(
            top = top,
            left = left,
            bottom = bottom,
            right = right,
            channels = channelRecords,
            name = name,
            visible = (flags and 0x02) == 0,
            alphaLocked = (flags and 0x01) != 0,
            clipping = clipping,
            opacity = opacity,
            blendMode = blendMode ?: LayerBlendMode.Normal,
        )
    }

    private fun readLayerChannels(reader: PsdReader, record: PsdLayerRecord): DecodedPsdLayer {
        val width = record.right - record.left
        val height = record.bottom - record.top
        val expected = width.toLong() * height.toLong()
        require(expected <= Int.MAX_VALUE.toLong()) { "PSD layer is too large." }
        val decoded = linkedMapOf<Int, ByteArray>()

        record.channels.forEach { channel ->
            require(channel.length >= 2) { "PSD channel data is truncated." }
            val channelReader = reader.subReader(channel.length)
            val compression = channelReader.u16()
            val data = when (compression) {
                0 -> channelReader.bytes(expected.toInt())
                1 -> decodeRlePlane(channelReader, width, height)
                2, 3 -> throw IllegalArgumentException(
                    "This PSD uses ZIP-compressed layer data. NeoCanvas PSD V1 supports Raw and RLE/PackBits layers."
                )
                else -> throw IllegalArgumentException("PSD uses unknown layer compression " + compression + ".")
            }
            decoded[channel.id] = data
        }
        return DecodedPsdLayer(record, decoded)
    }

    private fun readComposite(
        reader: PsdReader,
        width: Int,
        height: Int,
        channelCount: Int,
        useFourthAsAlpha: Boolean,
    ): ByteArray {
        val compression = reader.u16()
        val planeSize = width * height
        val planes = ArrayList<ByteArray>(channelCount)
        when (compression) {
            0 -> repeat(channelCount) { planes += reader.bytes(planeSize) }
            1 -> {
                val rowLengths = Array(channelCount) { IntArray(height) }
                for (channel in 0 until channelCount) {
                    for (row in 0 until height) rowLengths[channel][row] = reader.u16()
                }
                for (channel in 0 until channelCount) {
                    val plane = ByteArray(planeSize)
                    for (row in 0 until height) {
                        val encoded = reader.bytes(rowLengths[channel][row])
                        decodePackBitsRow(encoded, plane, row * width, width)
                    }
                    planes += plane
                }
            }
            2, 3 -> throw IllegalArgumentException(
                "This PSD uses ZIP-compressed merged data. NeoCanvas PSD V1 supports Raw and RLE/PackBits."
            )
            else -> throw IllegalArgumentException("PSD uses unknown merged-image compression " + compression + ".")
        }

        val rgba = ByteArray(planeSize * 4)
        for (pixel in 0 until planeSize) {
            rgba[pixel * 4] = planes.getOrNull(0)?.get(pixel) ?: 0
            rgba[pixel * 4 + 1] = planes.getOrNull(1)?.get(pixel) ?: rgba[pixel * 4]
            rgba[pixel * 4 + 2] = planes.getOrNull(2)?.get(pixel) ?: rgba[pixel * 4]
            rgba[pixel * 4 + 3] =
                if (useFourthAsAlpha && planes.size >= 4) planes[3][pixel] else 255.toByte()
        }
        return rgba
    }

    private fun decodeRlePlane(reader: PsdReader, width: Int, height: Int): ByteArray {
        val lengths = IntArray(height) { reader.u16() }
        val output = ByteArray(width * height)
        for (row in 0 until height) {
            decodePackBitsRow(reader.bytes(lengths[row]), output, row * width, width)
        }
        return output
    }

    private fun decodePackBitsRow(encoded: ByteArray, output: ByteArray, outputOffset: Int, width: Int) {
        var input = 0
        var written = 0
        while (input < encoded.size && written < width) {
            val rawHeader = encoded[input++].toInt() and 255
            val signed = if (rawHeader > 127) rawHeader - 256 else rawHeader
            when {
                signed in 0..127 -> {
                    val count = signed + 1
                    require(input + count <= encoded.size && written + count <= width) {
                        "PSD PackBits literal packet exceeds its row."
                    }
                    encoded.copyInto(output, outputOffset + written, input, input + count)
                    input += count
                    written += count
                }
                signed in -127..-1 -> {
                    val count = 1 - signed
                    require(input < encoded.size && written + count <= width) {
                        "PSD PackBits repeat packet exceeds its row."
                    }
                    val value = encoded[input++]
                    repeat(count) { output[outputOffset + written++] = value }
                }
                else -> Unit
            }
        }
        require(written == width) {
            "PSD PackBits row decoded to " + written + " pixels; expected " + width + "."
        }
    }

    private fun channelsToTiles(
        layerId: String,
        documentWidth: Int,
        documentHeight: Int,
        source: DecodedPsdLayer,
    ): Map<TileAddress, ByteArray> {
        val record = source.record
        val width = record.right - record.left
        val height = record.bottom - record.top
        if (width == 0 || height == 0) return emptyMap()
        val alpha = source.channels[-1]
        val red = source.channels[0]
        val green = source.channels[1]
        val blue = source.channels[2]
        val tiles = linkedMapOf<TileAddress, ByteArray>()

        for (localY in 0 until height) for (localX in 0 until width) {
            val x = record.left + localX
            val y = record.top + localY
            if (x !in 0 until documentWidth || y !in 0 until documentHeight) continue
            val sourceIndex = localY * width + localX
            val a = alpha?.get(sourceIndex)?.toInt()?.and(255) ?: 255
            if (a == 0) continue
            val address = TileAddress(layerId, x / TILE_SIZE_PIXELS, y / TILE_SIZE_PIXELS)
            val tile = tiles.getOrPut(address) { ByteArray(TileFormat.BYTES_PER_TILE) }
            val tileOffset = ((y % TILE_SIZE_PIXELS) * TILE_SIZE_PIXELS + x % TILE_SIZE_PIXELS) * 4
            tile[tileOffset] = red?.get(sourceIndex) ?: 0
            tile[tileOffset + 1] = green?.get(sourceIndex) ?: tile[tileOffset]
            tile[tileOffset + 2] = blue?.get(sourceIndex) ?: tile[tileOffset]
            tile[tileOffset + 3] = a.toByte()
        }
        return tiles
    }

    private fun rgbaToTiles(
        layerId: String,
        documentWidth: Int,
        documentHeight: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        rgba: ByteArray,
    ): Map<TileAddress, ByteArray> {
        val width = right - left
        val tiles = linkedMapOf<TileAddress, ByteArray>()
        for (y in top until bottom) for (x in left until right) {
            if (x !in 0 until documentWidth || y !in 0 until documentHeight) continue
            val sourceOffset = ((y - top) * width + (x - left)) * 4
            val alpha = rgba[sourceOffset + 3].toInt() and 255
            if (alpha == 0) continue
            val address = TileAddress(layerId, x / TILE_SIZE_PIXELS, y / TILE_SIZE_PIXELS)
            val tile = tiles.getOrPut(address) { ByteArray(TileFormat.BYTES_PER_TILE) }
            val tileOffset = ((y % TILE_SIZE_PIXELS) * TILE_SIZE_PIXELS + x % TILE_SIZE_PIXELS) * 4
            for (channel in 0..3) tile[tileOffset + channel] = rgba[sourceOffset + channel]
        }
        return tiles
    }

    private fun findBounds(
        document: CanvasDocument,
        layer: Layer,
        tiles: Map<TileAddress, ByteArray>,
    ): PsdBounds {
        val raster = layer.payload as? LayerPayload.Raster ?: return PsdBounds(0, 0, 0, 0)
        var left = document.width
        var top = document.height
        var right = 0
        var bottom = 0
        raster.tileAddresses.forEach { address ->
            val bytes = tiles[address] ?: return@forEach
            for (index in 3 until bytes.size step 4) {
                if ((bytes[index].toInt() and 255) == 0) continue
                val pixel = index / 4
                val x = address.x * TILE_SIZE_PIXELS + pixel % TILE_SIZE_PIXELS
                val y = address.y * TILE_SIZE_PIXELS + pixel / TILE_SIZE_PIXELS
                if (x !in 0 until document.width || y !in 0 until document.height) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
            }
        }
        return if (right <= left || bottom <= top) PsdBounds(0, 0, 0, 0)
        else PsdBounds(left, top, right, bottom)
    }

    private fun layerChannelAt(
        layerId: String,
        tiles: Map<TileAddress, ByteArray>,
        x: Int,
        y: Int,
        rgbaChannel: Int,
    ): Int {
        val address = TileAddress(layerId, x / TILE_SIZE_PIXELS, y / TILE_SIZE_PIXELS)
        val tile = tiles[address] ?: return 0
        val offset = ((y % TILE_SIZE_PIXELS) * TILE_SIZE_PIXELS + x % TILE_SIZE_PIXELS) * 4 + rgbaChannel
        return tile[offset].toInt() and 255
    }

    private fun blendKey(mode: LayerBlendMode): String = when (mode) {
        LayerBlendMode.Normal -> "norm"
        LayerBlendMode.Multiply -> "mul "
        LayerBlendMode.Screen -> "scrn"
        LayerBlendMode.Overlay -> "over"
        LayerBlendMode.Darken -> "dark"
        LayerBlendMode.Lighten -> "lite"
        LayerBlendMode.ColorDodge -> "div "
        LayerBlendMode.ColorBurn -> "idiv"
        LayerBlendMode.SoftLight -> "sLit"
        LayerBlendMode.HardLight -> "hLit"
        LayerBlendMode.Difference -> "diff"
        LayerBlendMode.Exclusion -> "smud"
        LayerBlendMode.Add -> "lddg"
        LayerBlendMode.Subtract -> "fsub"
    }

    private fun blendMode(key: String): LayerBlendMode? = when (key) {
        "norm", "pass" -> LayerBlendMode.Normal
        "mul " -> LayerBlendMode.Multiply
        "scrn" -> LayerBlendMode.Screen
        "over" -> LayerBlendMode.Overlay
        "dark" -> LayerBlendMode.Darken
        "lite" -> LayerBlendMode.Lighten
        "div " -> LayerBlendMode.ColorDodge
        "lddg" -> LayerBlendMode.Add
        "idiv" -> LayerBlendMode.ColorBurn
        "sLit" -> LayerBlendMode.SoftLight
        "hLit" -> LayerBlendMode.HardLight
        "diff" -> LayerBlendMode.Difference
        "smud" -> LayerBlendMode.Exclusion
        "fsub" -> LayerBlendMode.Subtract
        else -> null
    }

    private val rasterisedFeatureKeys = setOf(
        "TySh", "SoLd", "SoCo", "GdFl", "PtFl", "brit", "levl", "curv", "expA",
        "vibA", "hue ", "hue2", "blnc", "blwh", "phfl", "mixr", "clrL", "nvrt",
        "post", "thrs", "grdm", "selc", "lrFX", "lfx2",
    )
}

private data class PsdBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

private data class PsdEncodedLayer(val layer: Layer, val bounds: PsdBounds)
private data class PsdChannelRecord(val id: Int, val length: Int)

private data class PsdLayerRecord(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val channels: List<PsdChannelRecord>,
    val name: String,
    val visible: Boolean,
    val alphaLocked: Boolean,
    val clipping: Boolean,
    val opacity: Int,
    val blendMode: LayerBlendMode,
)

private data class DecodedPsdLayer(
    val record: PsdLayerRecord,
    val channels: Map<Int, ByteArray>,
)

private class PsdReader(private val data: ByteArray) {
    private var position = 0
    val remaining: Int get() = data.size - position

    fun u8(): Int {
        require(remaining >= 1) { "PSD ended unexpectedly." }
        return data[position++].toInt() and 255
    }

    fun u16(): Int = (u8() shl 8) or u8()
    fun i16(): Short = u16().toShort()

    fun i32(): Int =
        (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    fun u32Int(): Int {
        val value = i32().toLong() and 0xffffffffL
        require(value <= Int.MAX_VALUE.toLong()) { "PSD section is too large." }
        return value.toInt()
    }

    fun bytes(count: Int): ByteArray {
        require(count >= 0 && remaining >= count) { "PSD section is truncated." }
        val result = data.copyOfRange(position, position + count)
        position += count
        return result
    }

    fun ascii(count: Int): String = bytes(count).joinToString("") { byte ->
        (byte.toInt() and 255).toChar().toString()
    }

    fun skip(count: Int) {
        require(count >= 0 && remaining >= count) { "PSD section is truncated." }
        position += count
    }

    fun subReader(count: Int): PsdReader = PsdReader(bytes(count))

    fun skipSection32(label: String) {
        val length = u32Int()
        require(length <= remaining) { "PSD " + label + " section is truncated." }
        skip(length)
    }

    fun pascal4(): String {
        val start = position
        val length = u8()
        val raw = bytes(length)
        while ((position - start) % 4 != 0) skip(1)
        return raw.joinToString("") { byte ->
            val value = byte.toInt() and 255
            if (value in 32..126) value.toChar().toString() else "?"
        }
    }

    fun unicodeString(): String {
        val count = u32Int()
        require(count <= remaining / 2) { "PSD Unicode layer name is truncated." }
        val chars = CharArray(count)
        repeat(count) { chars[it] = u16().toChar() }
        return chars.concatToString().trimEnd('\u0000')
    }
}

private class PsdWriter(initialCapacity: Int = 1024) {
    private var buffer = ByteArray(initialCapacity.coerceAtLeast(32))
    var size: Int = 0
        private set

    fun u8(value: Int) {
        ensure(1)
        buffer[size++] = value.toByte()
    }

    fun u16(value: Int) {
        u8(value ushr 8)
        u8(value)
    }

    fun i16(value: Int) = u16(value and 0xffff)
    fun u32(value: Int) = u32(value.toLong())

    fun u32(value: Long) {
        require(value in 0..0xffffffffL) { "PSD length is out of range." }
        u8((value ushr 24).toInt())
        u8((value ushr 16).toInt())
        u8((value ushr 8).toInt())
        u8(value.toInt())
    }

    fun i32(value: Int) {
        u8(value ushr 24)
        u8(value ushr 16)
        u8(value ushr 8)
        u8(value)
    }

    fun bytes(value: ByteArray) {
        ensure(value.size)
        value.copyInto(buffer, size)
        size += value.size
    }

    fun ascii(value: String) {
        require(value.all { it.code in 0..127 }) { "PSD signature must be ASCII." }
        value.forEach { u8(it.code) }
    }

    fun zeros(count: Int) = repeat(count) { u8(0) }

    fun padTo(alignment: Int) {
        while (size % alignment != 0) u8(0)
    }

    fun pascal4(value: String) {
        val start = size
        val ascii = value.take(255).map { if (it.code in 32..126) it.code else '?'.code }
        u8(ascii.size)
        ascii.forEach(::u8)
        while ((size - start) % 4 != 0) u8(0)
    }

    fun unicodeString(value: String) {
        u32(value.length)
        value.forEach { u16(it.code) }
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
        val needed = size + extra
        if (needed <= buffer.size) return
        var next = buffer.size
        while (next < needed) next = (next * 2).coerceAtLeast(needed)
        buffer = buffer.copyOf(next)
    }
}
