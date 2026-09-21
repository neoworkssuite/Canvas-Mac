package com.neoworksuite.neocanvas.ui

sealed interface WorkbenchItem {
    val id: String
    val x: Float
    val y: Float
    val width: Float
    val height: Float

    data class Reference(
        override val id: String,
        val name: String,
        val pixelWidth: Int,
        val pixelHeight: Int,
        val argb: IntArray,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
    ) : WorkbenchItem {
        init {
            require(id.isNotBlank())
            require(pixelWidth > 0 && pixelHeight > 0)
            require(argb.size == pixelWidth * pixelHeight)
            require(width > 0f && height > 0f)
        }
    }

    data class Note(
        override val id: String,
        val text: String,
        override val x: Float,
        override val y: Float,
        override val width: Float = 320f,
        override val height: Float = 180f,
    ) : WorkbenchItem

    data class ColourCard(
        override val id: String,
        val hex: String,
        override val x: Float,
        override val y: Float,
        override val width: Float = 220f,
        override val height: Float = 140f,
    ) : WorkbenchItem
}

internal fun WorkbenchItem.moved(dx: Float, dy: Float): WorkbenchItem = when (this) {
    is WorkbenchItem.Reference -> copy(x = x + dx, y = y + dy)
    is WorkbenchItem.Note -> copy(x = x + dx, y = y + dy)
    is WorkbenchItem.ColourCard -> copy(x = x + dx, y = y + dy)
}

object WorkbenchCodec {
    private val magic = "NCWB1".encodeToByteArray()

    fun encode(items: List<WorkbenchItem>): ByteArray {
        require(items.size <= 128) { "Workbench supports up to 128 items." }
        val out = WorkbenchWriter()
        out.bytes(magic)
        out.i32(items.size)
        items.forEach { item ->
            when (item) {
                is WorkbenchItem.Reference -> {
                    out.u8(1); out.string(item.id); out.string(item.name.take(120))
                    out.f32(item.x); out.f32(item.y); out.f32(item.width); out.f32(item.height)
                    out.i32(item.pixelWidth); out.i32(item.pixelHeight)
                    require(item.argb.size <= 4_000_000) { "Workbench reference is too large." }
                    out.i32(item.argb.size)
                    item.argb.forEach(out::i32)
                }
                is WorkbenchItem.Note -> {
                    out.u8(2); out.string(item.id); out.string(item.text.take(2_000))
                    out.f32(item.x); out.f32(item.y); out.f32(item.width); out.f32(item.height)
                }
                is WorkbenchItem.ColourCard -> {
                    out.u8(3); out.string(item.id); out.string(item.hex.take(16))
                    out.f32(item.x); out.f32(item.y); out.f32(item.width); out.f32(item.height)
                }
            }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): List<WorkbenchItem> {
        if (bytes.isEmpty()) return emptyList()
        require(bytes.size <= 80_000_000) { "Workbench data is too large." }
        val input = WorkbenchReader(bytes)
        require(input.bytes(magic.size).contentEquals(magic)) { "Unsupported Workbench data." }
        val count = input.i32()
        require(count in 0..128) { "Workbench item count is invalid." }
        val result = List(count) {
            when (input.u8()) {
                1 -> {
                    val id = input.string()
                    val name = input.string()
                    val x = input.f32(); val y = input.f32(); val width = input.f32(); val height = input.f32()
                    val pixelWidth = input.i32(); val pixelHeight = input.i32()
                    val size = input.i32()
                    require(pixelWidth > 0 && pixelHeight > 0 && size == pixelWidth * pixelHeight && size <= 4_000_000)
                    WorkbenchItem.Reference(id, name, pixelWidth, pixelHeight, IntArray(size) { input.i32() }, x, y, width, height)
                }
                2 -> WorkbenchItem.Note(input.string(), input.string(), input.f32(), input.f32(), input.f32(), input.f32())
                3 -> WorkbenchItem.ColourCard(input.string(), input.string(), input.f32(), input.f32(), input.f32(), input.f32())
                else -> error("Unknown Workbench item.")
            }
        }
        require(input.remaining == 0) { "Workbench data has trailing bytes." }
        return result
    }
}

private class WorkbenchWriter {
    private val data = ArrayList<Byte>()
    fun u8(value: Int) { data += value.toByte() }
    fun i32(value: Int) { u8(value ushr 24); u8(value ushr 16); u8(value ushr 8); u8(value) }
    fun f32(value: Float) = i32(value.toBits())
    fun bytes(value: ByteArray) { value.forEach { data += it } }
    fun string(value: String) {
        val encoded = value.encodeToByteArray()
        require(encoded.size <= 16_384)
        i32(encoded.size); bytes(encoded)
    }
    fun toByteArray() = data.toByteArray()
}

private class WorkbenchReader(private val data: ByteArray) {
    private var position = 0
    val remaining get() = data.size - position
    fun u8(): Int {
        require(remaining >= 1) { "Workbench data is truncated." }
        return data[position++].toInt() and 255
    }
    fun i32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
    fun f32(): Float = Float.fromBits(i32()).also { require(it.isFinite()) }
    fun bytes(count: Int): ByteArray {
        require(count >= 0 && remaining >= count) { "Workbench data is truncated." }
        return data.copyOfRange(position, position + count).also { position += count }
    }
    fun string(): String {
        val count = i32()
        require(count in 0..16_384)
        return bytes(count).decodeToString(throwOnInvalidSequence = true)
    }
}
