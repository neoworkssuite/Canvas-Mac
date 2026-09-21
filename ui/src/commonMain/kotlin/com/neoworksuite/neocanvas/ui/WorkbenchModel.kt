package com.neoworksuite.neocanvas.ui

sealed interface WorkbenchItem {
    val id: String
    val x: Float
    val y: Float
    val width: Float
    val height: Float
    val locked: Boolean

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
        override val locked: Boolean = false,
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
        override val locked: Boolean = false,
    ) : WorkbenchItem

    data class ColourCard(
        override val id: String,
        val hex: String,
        override val x: Float,
        override val y: Float,
        override val width: Float = 220f,
        override val height: Float = 140f,
        override val locked: Boolean = false,
    ) : WorkbenchItem
}

internal fun WorkbenchItem.moved(dx: Float, dy: Float): WorkbenchItem = when (this) {
    is WorkbenchItem.Reference -> copy(x = x + dx, y = y + dy)
    is WorkbenchItem.Note -> copy(x = x + dx, y = y + dy)
    is WorkbenchItem.ColourCard -> copy(x = x + dx, y = y + dy)
}

internal fun WorkbenchItem.resized(factor: Float): WorkbenchItem {
    require(factor.isFinite() && factor > 0f)
    val nextWidth = (width * factor).coerceIn(90f, 1200f)
    val nextHeight = (height * factor).coerceIn(60f, 900f)
    return when (this) {
        is WorkbenchItem.Reference -> copy(width = nextWidth, height = nextHeight)
        is WorkbenchItem.Note -> copy(width = nextWidth, height = nextHeight)
        is WorkbenchItem.ColourCard -> copy(width = nextWidth, height = nextHeight)
    }
}

internal fun WorkbenchItem.withLocked(value: Boolean): WorkbenchItem = when (this) {
    is WorkbenchItem.Reference -> copy(locked = value)
    is WorkbenchItem.Note -> copy(locked = value)
    is WorkbenchItem.ColourCard -> copy(locked = value)
}

object WorkbenchCodec {
    private val magicV1 = "NCWB1".encodeToByteArray()
    private val magicV2 = "NCWB2".encodeToByteArray()

    fun encode(items: List<WorkbenchItem>): ByteArray {
        require(items.size <= 128) { "Workbench supports up to 128 items." }
        val out = WorkbenchWriter()
        out.bytes(magicV2)
        out.i32(items.size)
        items.forEach { item ->
            when (item) {
                is WorkbenchItem.Reference -> {
                    out.u8(1); out.string(item.id); out.string(item.name.take(120))
                    out.f32(item.x); out.f32(item.y); out.f32(item.width); out.f32(item.height); out.bool(item.locked)
                    out.i32(item.pixelWidth); out.i32(item.pixelHeight)
                    require(item.argb.size <= 4_000_000) { "Workbench reference is too large." }
                    out.i32(item.argb.size)
                    item.argb.forEach(out::i32)
                }
                is WorkbenchItem.Note -> {
                    out.u8(2); out.string(item.id); out.string(item.text.take(2_000))
                    out.f32(item.x); out.f32(item.y); out.f32(item.width); out.f32(item.height); out.bool(item.locked)
                }
                is WorkbenchItem.ColourCard -> {
                    out.u8(3); out.string(item.id); out.string(item.hex.take(16))
                    out.f32(item.x); out.f32(item.y); out.f32(item.width); out.f32(item.height); out.bool(item.locked)
                }
            }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): List<WorkbenchItem> {
        if (bytes.isEmpty()) return emptyList()
        require(bytes.size <= 80_000_000) { "Workbench data is too large." }
        val input = WorkbenchReader(bytes)
        val magic = input.bytes(5)
        val version = when {
            magic.contentEquals(magicV2) -> 2
            magic.contentEquals(magicV1) -> 1
            else -> error("Unsupported Workbench data.")
        }
        val count = input.i32()
        require(count in 0..128) { "Workbench item count is invalid." }
        val result = List(count) {
            when (input.u8()) {
                1 -> {
                    val id = input.string()
                    val name = input.string()
                    val x = input.f32(); val y = input.f32(); val width = input.f32(); val height = input.f32()
                    val locked = if (version >= 2) input.bool() else false
                    val pixelWidth = input.i32(); val pixelHeight = input.i32()
                    val size = input.i32()
                    require(pixelWidth > 0 && pixelHeight > 0 && size == pixelWidth * pixelHeight && size <= 4_000_000)
                    WorkbenchItem.Reference(
                        id, name, pixelWidth, pixelHeight, IntArray(size) { input.i32() },
                        x, y, width, height, locked,
                    )
                }
                2 -> WorkbenchItem.Note(
                    input.string(), input.string(),
                    input.f32(), input.f32(), input.f32(), input.f32(),
                    if (version >= 2) input.bool() else false,
                )
                3 -> WorkbenchItem.ColourCard(
                    input.string(), input.string(),
                    input.f32(), input.f32(), input.f32(), input.f32(),
                    if (version >= 2) input.bool() else false,
                )
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
    fun bool(value: Boolean) = u8(if (value) 1 else 0)
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
    fun bool(): Boolean = when (val value = u8()) {
        0 -> false
        1 -> true
        else -> error("Workbench boolean is invalid: " + value)
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
