package com.neoworksuite.neocanvas.core.store

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.TileAddress
import kotlin.math.min

/** Version-one, local-only ZIP package codec. It uses store-only ZIP and PNG entries for portability. */
object NeoCanvasPackage {
    const val FORMAT_VERSION = 1
    const val TILE_SIZE_PIXELS = 256
    const val RGBA_TILE_BYTES = TILE_SIZE_PIXELS * TILE_SIZE_PIXELS * 4

    fun write(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, thumbnailPng: ByteArray = transparentThumbnail()): ByteArray {
        val expectedTiles = document.layers.flatMap { layer ->
            val raster = layer.payload as? LayerPayload.Raster
                ?: throw IllegalArgumentException("Unsupported layer payload in v1 package.")
            raster.tileAddresses
        }.toSet()
        require(tiles.keys == expectedTiles) { "Package tile buffers must exactly match the document tile addresses." }
        tiles.forEach { (_, pixels) -> require(pixels.size == RGBA_TILE_BYTES) { "Every tile must be 256×256 RGBA pixels." } }

        val members = linkedMapOf<String, ByteArray>()
        members["manifest.json"] = manifest(document).encodeToByteArray()
        document.layers.forEach { layer ->
            val raster = layer.payload as LayerPayload.Raster
            raster.tileAddresses.sortedWith(compareBy(TileAddress::y, TileAddress::x)).forEach { address ->
                members[tileMember(address)] = encodePng(TILE_SIZE_PIXELS, TILE_SIZE_PIXELS, tiles.getValue(address))
            }
        }
        members["thumb.png"] = thumbnailPng
        members["assets/"] = ByteArray(0)
        return ZipArchive.write(members)
    }

    fun read(bytes: ByteArray): LoadResult = try {
        readMembers(ZipArchive.read(bytes))
    } catch (error: PackageIncompatibleException) {
        LoadResult.Incompatible(error.message ?: "This NeoCanvas format is not supported.")
    } catch (error: Exception) {
        LoadResult.Corrupt(error.message ?: "The NeoCanvas package is corrupt.")
    }

    fun readThumbnail(bytes: ByteArray): ByteArray = ZipArchive.read(bytes)["thumb.png"]
        ?: throw IllegalArgumentException("thumb.png is missing.")

    /** Exposed for deterministic validation tests and for future import adapters that already unpack ZIP members. */
    fun readMembers(members: Map<String, ByteArray>): LoadResult = try {
        val manifestBytes = members["manifest.json"] ?: throw PackageCorruptException("manifest.json is missing.")
        if ("thumb.png" !in members) throw PackageCorruptException("thumb.png is missing.")
        if ("assets/" !in members) throw PackageCorruptException("assets/ is missing.")
        val root = JsonParser(manifestBytes.decodeToString()).parseObject()
        val version = root.int("formatVersion")
        if (version != FORMAT_VERSION) {
            throw PackageIncompatibleException("NeoCanvas format version $version is not supported.")
        }
        val documentObject = root.objectValue("document")
        val documentId = documentObject.string("id")
        val width = documentObject.int("width")
        val height = documentObject.int("height")
        val memberTiles = linkedMapOf<TileAddress, ByteArray>()
        val layers = root.array("layers").map { value ->
            val layerObject = value.asObject()
            val id = layerObject.string("id")
            val name = layerObject.string("name")
            val visible = layerObject.boolean("visible")
            val opacity = layerObject.float("opacity")
            if (layerObject.string("type") != "raster") throw PackageIncompatibleException("Unsupported layer type.")
            val addresses = linkedSetOf<TileAddress>()
            layerObject.array("tiles").forEach { tileValue ->
                val memberName = tileValue.asString()
                val address = addressForMember(id, memberName)
                if (!addresses.add(address)) throw PackageCorruptException("Duplicate tile address '$memberName'.")
                val png = members[memberName] ?: throw PackageCorruptException("Tile '$memberName' is missing.")
                memberTiles[address] = decodeTilePng(png)
            }
            Layer(id, name, visible, opacity, LayerPayload.Raster(addresses),
                locked = if ("locked" in layerObject.fields) layerObject.boolean("locked") else false,
                alphaLocked = if ("alphaLocked" in layerObject.fields) layerObject.boolean("alphaLocked") else false,
                blendMode = if ("blendMode" in layerObject.fields) {
                    runCatching { LayerBlendMode.valueOf(layerObject.string("blendMode")) }
                        .getOrElse { throw PackageIncompatibleException("Unsupported layer blend mode.") }
                } else LayerBlendMode.Normal)
        }
        if (layers.map(Layer::id).distinct().size != layers.size) throw PackageCorruptException("Layer ids must be unique.")
        val declaredMembers = memberTiles.keys.mapTo(linkedSetOf(), ::tileMember)
        members.keys.filter { it.startsWith("layers/") && it.endsWith(".png") }.forEach { name ->
            if (name !in declaredMembers) throw PackageCorruptException("Tile '$name' is not declared by the manifest.")
        }
        LoadResult.Success(CanvasDocument(documentId, width, height, layers), memberTiles)
    } catch (error: PackageIncompatibleException) {
        LoadResult.Incompatible(error.message ?: "This NeoCanvas format is not supported.")
    } catch (error: Exception) {
        LoadResult.Corrupt(error.message ?: "The NeoCanvas package is corrupt.")
    }

    fun transparentThumbnail(): ByteArray = encodePng(1, 1, ByteArray(4))

    private fun manifest(document: CanvasDocument): String = buildString {
        append("{\"formatVersion\":").append(FORMAT_VERSION)
        append(",\"document\":{\"id\":\"").append(json(document.id)).append("\",\"width\":")
        append(document.width).append(",\"height\":").append(document.height).append("},\"layers\":[")
        document.layers.forEachIndexed { index, layer ->
            if (index > 0) append(',')
            val raster = layer.payload as LayerPayload.Raster
            append("{\"id\":\"").append(json(layer.id)).append("\",\"name\":\"").append(json(layer.name))
            append("\",\"visible\":").append(layer.visible).append(",\"opacity\":").append(layer.opacity)
            append(",\"locked\":").append(layer.locked)
            append(",\"alphaLocked\":").append(layer.alphaLocked)
            append(",\"blendMode\":\"").append(layer.blendMode.name).append('"')
            append(",\"type\":\"raster\",\"tiles\":[")
            raster.tileAddresses.sortedWith(compareBy(TileAddress::y, TileAddress::x)).forEachIndexed { tileIndex, address ->
                if (tileIndex > 0) append(',')
                append('\"').append(tileMember(address)).append('\"')
            }
            append("]}")
        }
        append("]}")
    }

    private fun json(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }

    private fun tileMember(address: TileAddress): String = "layers/${address.layerId}/${address.x}-${address.y}.png"

    private fun addressForMember(layerId: String, member: String): TileAddress {
        val prefix = "layers/$layerId/"
        if (!member.startsWith(prefix) || !member.endsWith(".png")) throw PackageCorruptException("Invalid tile member '$member'.")
        val coordinates = member.removePrefix(prefix).removeSuffix(".png").split('-')
        val split = if (coordinates.size == 2) coordinates else {
            // Negative coordinates contain a leading empty component after splitting.
            val match = Regex("^(-?\\d+)-(-?\\d+)$").matchEntire(member.removePrefix(prefix).removeSuffix(".png"))
                ?: throw PackageCorruptException("Invalid tile member '$member'.")
            return TileAddress(layerId, match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
        return TileAddress(layerId, split[0].toIntOrNull() ?: throw PackageCorruptException("Invalid tile x."), split[1].toIntOrNull() ?: throw PackageCorruptException("Invalid tile y."))
    }
}

private class PackageIncompatibleException(message: String) : IllegalArgumentException(message)
private class PackageCorruptException(message: String) : IllegalArgumentException(message)

private fun encodePng(width: Int, height: Int, pixels: ByteArray): ByteArray {
    require(pixels.size == width * height * 4)
    val rows = ByteAccumulator()
    repeat(height) { y ->
        rows.byte(0)
        rows.bytes(pixels, y * width * 4, width * 4)
    }
    val output = ByteAccumulator().apply { bytes(PNG_SIGNATURE) }
    output.pngChunk("IHDR", ByteAccumulator().apply { intBig(width); intBig(height); byte(8); byte(6); byte(0); byte(0); byte(0) }.toByteArray())
    output.pngChunk("IDAT", zlibStore(rows.toByteArray()))
    output.pngChunk("IEND", ByteArray(0))
    return output.toByteArray()
}

private fun decodeTilePng(bytes: ByteArray): ByteArray {
    if (bytes.size < PNG_SIGNATURE.size || !bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
        throw PackageCorruptException("Tile is not a PNG file.")
    }
    var offset = PNG_SIGNATURE.size
    var width = -1
    var height = -1
    val idat = ByteAccumulator()
    while (offset + 12 <= bytes.size) {
        val length = bytes.intBig(offset)
        if (length < 0 || length > bytes.size - offset - 12) throw PackageCorruptException("Malformed PNG chunk.")
        val type = bytes.decodeToString(offset + 4, offset + 8)
        val data = bytes.copyOfRange(offset + 8, offset + 8 + length)
        val expectedCrc = bytes.intBig(offset + 8 + length)
        if (crc32(type.encodeToByteArray() + data) != expectedCrc) throw PackageCorruptException("PNG checksum failed.")
        when (type) {
            "IHDR" -> {
                if (data.size != 13) throw PackageCorruptException("Malformed PNG header.")
                width = data.intBig(0); height = data.intBig(4)
                if (data[8].toInt() != 8 || data[9].toInt() != 6 || data[10].toInt() != 0 || data[11].toInt() != 0 || data[12].toInt() != 0) {
                    throw PackageIncompatibleException("Tile PNG format is not RGBA8.")
                }
            }
            "IDAT" -> idat.bytes(data)
            "IEND" -> break
        }
        offset += 12 + length
    }
    if (width != NeoCanvasPackage.TILE_SIZE_PIXELS || height != NeoCanvasPackage.TILE_SIZE_PIXELS) throw PackageCorruptException("Tile is not 256×256 pixels.")
    val inflated = inflateStoreZlib(idat.toByteArray())
    val rowSize = width * 4
    if (inflated.size != height * (rowSize + 1)) throw PackageCorruptException("Unexpected tile pixel data length.")
    val pixels = ByteArray(NeoCanvasPackage.RGBA_TILE_BYTES)
    repeat(height) { y ->
        val source = y * (rowSize + 1)
        if (inflated[source].toInt() != 0) throw PackageIncompatibleException("Tile PNG filter is not supported.")
        inflated.copyInto(pixels, y * rowSize, source + 1, source + 1 + rowSize)
    }
    return pixels
}

private val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

private fun ByteAccumulator.pngChunk(type: String, data: ByteArray) {
    intBig(data.size); bytes(type.encodeToByteArray()); bytes(data); intBig(crc32(type.encodeToByteArray() + data))
}

private fun zlibStore(data: ByteArray): ByteArray {
    val output = ByteAccumulator().apply { byte(0x78); byte(0x01) }
    var offset = 0
    do {
        val size = min(65_535, data.size - offset)
        val final = offset + size == data.size
        output.byte(if (final) 1 else 0)
        output.byte(size and 0xff); output.byte(size ushr 8)
        output.byte((size.inv()) and 0xff); output.byte((size.inv()) ushr 8)
        output.bytes(data, offset, size)
        offset += size
    } while (offset < data.size)
    output.intBig(adler32(data))
    return output.toByteArray()
}

private fun inflateStoreZlib(data: ByteArray): ByteArray {
    if (data.size < 6 || data[0].toInt() != 0x78 || data[1].toInt() != 0x01) throw PackageIncompatibleException("Compressed tile PNG data is not supported.")
    var offset = 2
    val output = ByteAccumulator()
    var final: Boolean
    do {
        if (offset + 5 > data.size - 4) throw PackageCorruptException("Truncated compressed PNG data.")
        val header = data[offset++].toInt() and 0xff
        if ((header and 0xfe) != 0) throw PackageIncompatibleException("Compressed tile PNG data is not supported.")
        val size = (data[offset++].toInt() and 0xff) or ((data[offset++].toInt() and 0xff) shl 8)
        val inverse = (data[offset++].toInt() and 0xff) or ((data[offset++].toInt() and 0xff) shl 8)
        if (inverse != (size xor 0xffff) || offset + size > data.size - 4) throw PackageCorruptException("Malformed compressed PNG data.")
        output.bytes(data, offset, size); offset += size
        final = (header and 1) != 0
    } while (!final)
    val result = output.toByteArray()
    if (offset + 4 != data.size || data.intBig(offset) != adler32(result)) throw PackageCorruptException("PNG data checksum failed.")
    return result
}

private fun adler32(data: ByteArray): Int {
    var a = 1
    var b = 0
    data.forEach { byte -> a = (a + (byte.toInt() and 0xff)) % 65_521; b = (b + a) % 65_521 }
    return (b shl 16) or a
}

private object ZipArchive {
    fun write(members: Map<String, ByteArray>): ByteArray {
        val output = ByteAccumulator()
        val entries = members.map { (name, data) ->
            require(name.isNotBlank() && !name.startsWith('/') && ".." !in name.split('/')) { "Unsafe ZIP member name." }
            val offset = output.size
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            output.intLittle(0x04034b50); output.shortLittle(20); output.shortLittle(0); output.shortLittle(0)
            output.shortLittle(0); output.shortLittle(0); output.intLittle(crc); output.intLittle(data.size); output.intLittle(data.size)
            output.shortLittle(nameBytes.size); output.shortLittle(0); output.bytes(nameBytes); output.bytes(data)
            ZipEntry(name, data.size, crc, offset)
        }
        val centralOffset = output.size
        entries.forEach { entry ->
            val name = entry.name.encodeToByteArray()
            output.intLittle(0x02014b50); output.shortLittle(20); output.shortLittle(20); output.shortLittle(0); output.shortLittle(0)
            output.shortLittle(0); output.shortLittle(0); output.intLittle(entry.crc); output.intLittle(entry.size); output.intLittle(entry.size)
            output.shortLittle(name.size); output.shortLittle(0); output.shortLittle(0); output.shortLittle(0); output.shortLittle(0); output.intLittle(0); output.intLittle(entry.offset); output.bytes(name)
        }
        val centralSize = output.size - centralOffset
        output.intLittle(0x06054b50); output.shortLittle(0); output.shortLittle(0); output.shortLittle(entries.size); output.shortLittle(entries.size)
        output.intLittle(centralSize); output.intLittle(centralOffset); output.shortLittle(0)
        return output.toByteArray()
    }

    fun read(bytes: ByteArray): Map<String, ByteArray> {
        val members = linkedMapOf<String, ByteArray>()
        var offset = 0
        while (offset + 4 <= bytes.size && bytes.intLittle(offset) == 0x04034b50) {
            if (offset + 30 > bytes.size) throw PackageCorruptException("Truncated ZIP member.")
            val flags = bytes.shortLittle(offset + 6)
            val method = bytes.shortLittle(offset + 8)
            val crc = bytes.intLittle(offset + 14)
            val compressedSize = bytes.intLittle(offset + 18)
            val size = bytes.intLittle(offset + 22)
            val nameSize = bytes.shortLittle(offset + 26)
            val extraSize = bytes.shortLittle(offset + 28)
            if (flags != 0 || method != 0 || compressedSize < 0 || size < 0 || compressedSize != size) throw PackageIncompatibleException("Compressed ZIP members are not supported.")
            val dataOffset = offset + 30 + nameSize + extraSize
            if (dataOffset < offset || dataOffset > bytes.size || size > bytes.size - dataOffset) throw PackageCorruptException("Malformed ZIP member.")
            val name = bytes.decodeToString(offset + 30, offset + 30 + nameSize)
            if (name.isBlank() || name.startsWith('/') || ".." in name.split('/')) throw PackageCorruptException("Unsafe ZIP member name.")
            val data = bytes.copyOfRange(dataOffset, dataOffset + size)
            if (crc32(data) != crc) throw PackageCorruptException("ZIP member checksum failed.")
            if (members.put(name, data) != null) throw PackageCorruptException("Duplicate ZIP member '$name'.")
            offset = dataOffset + size
        }
        if (members.isEmpty() || offset + 4 > bytes.size || bytes.intLittle(offset) != 0x02014b50) throw PackageCorruptException("Invalid ZIP package.")
        return members
    }
}

private data class ZipEntry(val name: String, val size: Int, val crc: Int, val offset: Int)

private class ByteAccumulator {
    private val values = ArrayList<Byte>()
    val size: Int get() = values.size
    fun byte(value: Int) { values += value.toByte() }
    fun bytes(value: ByteArray, offset: Int = 0, length: Int = value.size - offset) { repeat(length) { values += value[offset + it] } }
    fun shortLittle(value: Int) { byte(value); byte(value ushr 8) }
    fun intLittle(value: Int) { byte(value); byte(value ushr 8); byte(value ushr 16); byte(value ushr 24) }
    fun intBig(value: Int) { byte(value ushr 24); byte(value ushr 16); byte(value ushr 8); byte(value) }
    fun toByteArray(): ByteArray = values.toByteArray()
}

private fun ByteArray.shortLittle(offset: Int): Int = (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)
private fun ByteArray.intLittle(offset: Int): Int = shortLittle(offset) or (shortLittle(offset + 2) shl 16)
private fun ByteArray.intBig(offset: Int): Int = ((this[offset].toInt() and 0xff) shl 24) or ((this[offset + 1].toInt() and 0xff) shl 16) or ((this[offset + 2].toInt() and 0xff) shl 8) or (this[offset + 3].toInt() and 0xff)

private fun crc32(data: ByteArray): Int {
    var crc = -1
    data.forEach { value ->
        crc = crc xor (value.toInt() and 0xff)
        repeat(8) { crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xedb88320.toInt() else crc ushr 1 }
    }
    return crc.inv()
}

private sealed interface JsonValue {
    fun asObject(): JsonObject = this as? JsonObject ?: throw PackageCorruptException("Expected JSON object.")
    fun asString(): String = (this as? JsonString)?.value ?: throw PackageCorruptException("Expected JSON string.")
}
private data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue
private data class JsonArray(val values: List<JsonValue>) : JsonValue
private data class JsonString(val value: String) : JsonValue
private data class JsonNumber(val value: String) : JsonValue
private data class JsonBoolean(val value: Boolean) : JsonValue
private data object JsonNull : JsonValue

private fun JsonObject.value(key: String): JsonValue = fields[key] ?: throw PackageCorruptException("Manifest field '$key' is missing.")
private fun JsonObject.objectValue(key: String): JsonObject = value(key).asObject()
private fun JsonObject.array(key: String): List<JsonValue> = (value(key) as? JsonArray)?.values ?: throw PackageCorruptException("Manifest field '$key' must be an array.")
private fun JsonObject.string(key: String): String = value(key).asString()
private fun JsonObject.boolean(key: String): Boolean = (value(key) as? JsonBoolean)?.value ?: throw PackageCorruptException("Manifest field '$key' must be a boolean.")
private fun JsonObject.int(key: String): Int = (value(key) as? JsonNumber)?.value?.toIntOrNull() ?: throw PackageCorruptException("Manifest field '$key' must be an integer.")
private fun JsonObject.float(key: String): Float = (value(key) as? JsonNumber)?.value?.toFloatOrNull()?.takeIf { it.isFinite() } ?: throw PackageCorruptException("Manifest field '$key' must be a number.")

private class JsonParser(private val source: String) {
    private var index = 0
    fun parseObject(): JsonObject = parseValue().asObject().also { skipWhitespace(); if (index != source.length) throw PackageCorruptException("Unexpected JSON content.") }
    private fun parseValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObjectValue()
            '[' -> parseArray()
            '\"' -> JsonString(parseString())
            't' -> literal("true", JsonBoolean(true))
            'f' -> literal("false", JsonBoolean(false))
            'n' -> literal("null", JsonNull)
            '-', in '0'..'9' -> JsonNumber(parseNumber())
            else -> throw PackageCorruptException("Invalid JSON value.")
        }
    }
    private fun parseObjectValue(): JsonObject {
        expect('{'); skipWhitespace(); val fields = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonObject(fields)
        do { skipWhitespace(); val key = parseString(); skipWhitespace(); expect(':'); val value = parseValue(); if (fields.put(key, value) != null) throw PackageCorruptException("Duplicate manifest field '$key'."); skipWhitespace() } while (consume(','))
        expect('}'); return JsonObject(fields)
    }
    private fun parseArray(): JsonArray {
        expect('['); skipWhitespace(); val values = mutableListOf<JsonValue>()
        if (consume(']')) return JsonArray(values)
        do { values += parseValue(); skipWhitespace() } while (consume(','))
        expect(']'); return JsonArray(values)
    }
    private fun parseString(): String {
        expect('\"'); val value = StringBuilder()
        while (index < source.length) {
            when (val character = source[index++]) {
                '\"' -> return value.toString()
                '\\' -> when (val escaped = next()) { '\"', '\\', '/' -> value.append(escaped); 'b' -> value.append('\b'); 'f' -> value.append('\u000c'); 'n' -> value.append('\n'); 'r' -> value.append('\r'); 't' -> value.append('\t'); else -> throw PackageCorruptException("Unsupported JSON escape.") }
                else -> value.append(character)
            }
        }
        throw PackageCorruptException("Unterminated JSON string.")
    }
    private fun parseNumber(): String { val start = index; if (consume('-')) Unit; if (peek() == '0') index++ else { if (peek() !in '1'..'9') throw PackageCorruptException("Invalid JSON number."); while (peek() in '0'..'9') index++ }; if (consume('.')) { if (peek() !in '0'..'9') throw PackageCorruptException("Invalid JSON number."); while (peek() in '0'..'9') index++ }; if (peek() == 'e' || peek() == 'E') { index++; if (peek() == '+' || peek() == '-') index++; if (peek() !in '0'..'9') throw PackageCorruptException("Invalid JSON number."); while (peek() in '0'..'9') index++ }; return source.substring(start, index) }
    private fun literal(text: String, value: JsonValue): JsonValue { if (!source.startsWith(text, index)) throw PackageCorruptException("Invalid JSON literal."); index += text.length; return value }
    private fun skipWhitespace() { while (index < source.length && source[index] in " \n\r\t") index++ }
    private fun peek(): Char = source.getOrNull(index) ?: '\u0000'
    private fun next(): Char = source.getOrNull(index++) ?: throw PackageCorruptException("Truncated JSON escape.")
    private fun expect(character: Char) { if (!consume(character)) throw PackageCorruptException("Expected '$character' in JSON.") }
    private fun consume(character: Char): Boolean = if (peek() == character) { index++; true } else false
}
