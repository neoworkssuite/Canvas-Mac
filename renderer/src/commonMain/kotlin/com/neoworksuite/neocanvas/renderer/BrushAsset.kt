package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.brushes.BrushAssetRef

data class BrushAsset(
    val id: String,
    val width: Int,
    val height: Int,
    val coverage: ByteArray,
) {
    init {
        require(id.isNotBlank()) { "Brush asset id must not be blank." }
        require(width in 1..512 && height in 1..512) { "Brush asset dimensions must be between 1 and 512." }
        require(coverage.size == width * height) { "Brush asset coverage does not match its dimensions." }
    }

    val nonEmptyRows: List<IntRange?> = List(height) { y ->
        val first = (0 until width).firstOrNull { x -> coverage[y * width + x].toInt() and 255 != 0 }
        val last = (width - 1 downTo 0).firstOrNull { x -> coverage[y * width + x].toInt() and 255 != 0 }
        if (first == null || last == null) null else first..last
    }

    override fun equals(other: Any?): Boolean = other is BrushAsset &&
        id == other.id && width == other.width && height == other.height && coverage.contentEquals(other.coverage)

    override fun hashCode(): Int = 31 * (31 * (31 * id.hashCode() + width) + height) + coverage.contentHashCode()
}

fun interface BrushAssetResolver {
    fun resolve(ref: BrushAssetRef): BrushAsset?
}
