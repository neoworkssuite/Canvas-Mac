package com.neoworksuite.neocanvas.renderer

/**
 * Lossless on-disk representation for one hibernated raster layer.
 *
 * It intentionally stores complete RGBA tiles rather than document metadata.
 * Layer metadata remains owned by CanvasDocument, so waking a layer cannot
 * change names, order, opacity, visibility, clipping or blend mode.
 */
data class DormantLayerSnapshot(
    val layerId: String,
    val tiles: Map<TileKey, ByteArray>,
)

object DormantLayerCodec {
    private val magic = "NCDL1".encodeToByteArray()
    private const val MAX_TILES = 4096

    fun encode(layerId: String, tiles: Map<TileKey, ByteArray>): ByteArray {
        require(layerId.isNotBlank()) { "Layer id must not be blank." }
        require(tiles.size <= MAX_TILES) { "Dormant layer has too many tiles." }
        require(tiles.keys.all { it.layerId == layerId }) { "Dormant layer contains another layer's tile." }

        val body = DormantWriter()
        body.string(layerId)
        body.i32(tiles.size)
        tiles.entries.sortedWith(compareBy({ it.key.y }, { it.key.x })).forEach { (key, pixels) ->
            require(pixels.size == TileFormat.BYTES_PER_TILE) { "Dormant tile has the wrong byte length." }
            body.i32(key.x)
            body.i32(key.y)
            body.bytes(pixels)
        }
        val payload = body.toByteArray()

        return DormantWriter().apply {
            bytes(magic)
            i32(payload.size)
            bytes(payload)
            i32(crc32(payload))
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): DormantLayerSnapshot {
        require(bytes.size >= magic.size + 8) { "Dormant layer file is truncated." }
        val input = DormantReader(bytes)
        require(input.bytes(magic.size).contentEquals(magic)) { "Unsupported dormant layer file." }
        val payloadLength = input.i32()
        require(payloadLength >= 0 && payloadLength <= input.remaining - 4) { "Dormant layer length is invalid." }
        val payload = input.bytes(payloadLength)
        val checksum = input.i32()
        require(input.remaining == 0) { "Dormant layer file has trailing data." }
        require(crc32(payload) == checksum) { "Dormant layer checksum failed." }

        val body = DormantReader(payload)
        val layerId = body.string()
        require(layerId.isNotBlank()) { "Dormant layer id is blank." }
        val count = body.i32()
        require(count in 0..MAX_TILES) { "Dormant layer tile count is invalid." }

        val tiles = linkedMapOf<TileKey, ByteArray>()
        repeat(count) {
            val key = TileKey(layerId, body.i32(), body.i32())
            require(tiles.put(key, body.bytes(TileFormat.BYTES_PER_TILE)) == null) {
                "Dormant layer contains a duplicate tile address."
            }
        }
        require(body.remaining == 0) { "Dormant layer payload has trailing data." }
        return DormantLayerSnapshot(layerId, tiles)
    }

    private fun crc32(data: ByteArray): Int {
        var value = -1
        data.forEach { byte ->
            value = value xor (byte.toInt() and 0xff)
            repeat(8) {
                value = if ((value and 1) != 0) (value ushr 1) xor 0xedb88320.toInt() else value ushr 1
            }
        }
        return value.inv()
    }
}

private class DormantWriter {
    private val data = ArrayList<Byte>()
    fun i32(value: Int) {
        data += (value ushr 24).toByte()
        data += (value ushr 16).toByte()
        data += (value ushr 8).toByte()
        data += value.toByte()
    }
    fun bytes(value: ByteArray) { value.forEach { data += it } }
    fun string(value: String) {
        val encoded = value.encodeToByteArray()
        require(encoded.size <= 1024) { "Dormant layer id is too long." }
        i32(encoded.size)
        bytes(encoded)
    }
    fun toByteArray(): ByteArray = data.toByteArray()
}

private class DormantReader(private val data: ByteArray) {
    private var offset = 0
    val remaining: Int get() = data.size - offset

    fun i32(): Int {
        require(remaining >= 4) { "Dormant layer file is truncated." }
        val result =
            ((data[offset].toInt() and 255) shl 24) or
                ((data[offset + 1].toInt() and 255) shl 16) or
                ((data[offset + 2].toInt() and 255) shl 8) or
                (data[offset + 3].toInt() and 255)
        offset += 4
        return result
    }

    fun bytes(count: Int): ByteArray {
        require(count >= 0 && remaining >= count) { "Dormant layer file is truncated." }
        return data.copyOfRange(offset, offset + count).also { offset += count }
    }

    fun string(): String {
        val size = i32()
        require(size in 0..1024) { "Dormant layer id length is invalid." }
        return bytes(size).decodeToString(throwOnInvalidSequence = true)
    }
}
