package com.neoworksuite.neocanvas.renderer

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class RasterSelectionEdge(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

/** Compact immutable pixel mask used by non-rectangular selections. */
class RasterSelectionRegion private constructor(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    private val bits: ByteArray,
    val outline: List<RasterSelectionEdge>,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    init {
        require(left < right && top < bottom)
        require(bits.size == maskByteCount(width, height))
    }

    fun contains(x: Int, y: Int): Boolean {
        if (x !in left until right || y !in top until bottom) return false
        val index = (y - top) * width + (x - left)
        return bitIsSet(bits, index)
    }

    fun translated(dx: Int, dy: Int): RasterSelectionRegion =
        RasterSelectionRegion(
            left + dx,
            top + dy,
            right + dx,
            bottom + dy,
            bits,
            outline.map { edge ->
                RasterSelectionEdge(edge.x1 + dx, edge.y1 + dy, edge.x2 + dx, edge.y2 + dy)
            },
        )

    fun flipped(horizontal: Boolean): RasterSelectionRegion =
        fromPredicate(left, top, right, bottom) { x, y ->
            val sourceX = if (horizontal) left + (right - 1 - x) else x
            val sourceY = if (horizontal) y else top + (bottom - 1 - y)
            contains(sourceX, sourceY)
        } ?: error("A non-empty selection cannot become empty when flipped")

    fun rotatedClockwise(targetLeft: Int, targetTop: Int): RasterSelectionRegion =
        fromPredicate(
            targetLeft,
            targetTop,
            targetLeft + height,
            targetTop + width,
        ) { x, y ->
            val ox = x - targetLeft
            val oy = y - targetTop
            contains(left + oy, bottom - 1 - ox)
        } ?: error("A non-empty selection cannot become empty when rotated")

    fun transformed(
        targetLeft: Int,
        targetTop: Int,
        targetWidth: Int,
        targetHeight: Int,
        degrees: Float,
    ): RasterSelectionRegion {
        require(targetWidth > 0 && targetHeight > 0)
        require(degrees.isFinite())
        val rotated = RasterMove.rotatedSize(width, height, degrees)
        val angle = degrees * kotlin.math.PI / 180.0
        val cosine = cos(angle)
        val sine = sin(angle)
        return fromPredicate(
            targetLeft,
            targetTop,
            targetLeft + targetWidth,
            targetTop + targetHeight,
        ) { x, y ->
            val ox = x - targetLeft
            val oy = y - targetTop
            val sourceX: Int
            val sourceY: Int
            if (degrees != 0f) {
                val tx = (ox + .5) * rotated.first / targetWidth - rotated.first / 2.0
                val ty = (oy + .5) * rotated.second / targetHeight - rotated.second / 2.0
                val fx = cosine * tx + sine * ty + width / 2.0 - .5
                val fy = -sine * tx + cosine * ty + height / 2.0 - .5
                sourceX = floor(fx + .5).toInt()
                sourceY = floor(fy + .5).toInt()
            } else {
                sourceX = ((ox + .5) * width / targetWidth).toInt()
                sourceY = ((oy + .5) * height / targetHeight).toInt()
            }
            sourceX in 0 until width && sourceY in 0 until height &&
                contains(left + sourceX, top + sourceY)
        } ?: error("A non-empty selection cannot become empty when transformed")
    }

    override fun equals(other: Any?): Boolean =
        other is RasterSelectionRegion &&
            left == other.left && top == other.top && right == other.right && bottom == other.bottom &&
            bits.contentEquals(other.bits)

    override fun hashCode(): Int {
        var result = left
        result = 31 * result + top
        result = 31 * result + right
        result = 31 * result + bottom
        result = 31 * result + bits.contentHashCode()
        return result
    }

    companion object {
        fun fromPredicate(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            predicate: (Int, Int) -> Boolean,
        ): RasterSelectionRegion? {
            require(left < right && top < bottom)
            val width = right - left
            val height = bottom - top
            val bits = ByteArray(maskByteCount(width, height))
            var selected = 0
            for (y in top until bottom) for (x in left until right) {
                if (!predicate(x, y)) continue
                bitSet(bits, (y - top) * width + (x - left))
                selected++
            }
            if (selected == 0) return null
            return RasterSelectionRegion(left, top, right, bottom, bits, buildOutline(left, top, width, height, bits))
        }

        private fun buildOutline(
            left: Int,
            top: Int,
            width: Int,
            height: Int,
            bits: ByteArray,
        ): List<RasterSelectionEdge> {
            fun selected(x: Int, y: Int): Boolean =
                x in 0 until width && y in 0 until height && bitIsSet(bits, y * width + x)

            val edges = mutableListOf<RasterSelectionEdge>()
            for (y in 0 until height) for (x in 0 until width) {
                if (!selected(x, y)) continue
                val ax = left + x
                val ay = top + y
                if (!selected(x, y - 1)) edges += RasterSelectionEdge(ax, ay, ax + 1, ay)
                if (!selected(x + 1, y)) edges += RasterSelectionEdge(ax + 1, ay, ax + 1, ay + 1)
                if (!selected(x, y + 1)) edges += RasterSelectionEdge(ax + 1, ay + 1, ax, ay + 1)
                if (!selected(x - 1, y)) edges += RasterSelectionEdge(ax, ay + 1, ax, ay)
            }
            return edges
        }
    }
}

/** Four-connected colour selection on one raster layer with per-channel tolerance. */
object RasterSelection {
    fun connectedColour(
        store: TileStore,
        layer: String,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        tolerance: Int,
    ): RasterSelectionRegion? {
        require(width > 0 && height > 0)
        require(tolerance in 0..255)
        if (x !in 0 until width || y !in 0 until height) return null
        val pixelCount = width.toLong() * height.toLong()
        require(pixelCount <= Int.MAX_VALUE.toLong()) { "Canvas is too large for automatic selection." }
        val selected = ByteArray(((pixelCount + 7L) / 8L).toInt())
        val rejected = ByteArray(selected.size)
        val emptyTile = ByteArray(TileFormat.BYTES_PER_TILE)
        val cache = mutableMapOf<TileKey, ByteArray>()

        fun tile(px: Int, py: Int): ByteArray {
            val key = TileKey(layer, px / TILE_SIZE_PIXELS, py / TILE_SIZE_PIXELS)
            return cache.getOrPut(key) { store.read(key) ?: emptyTile }
        }
        fun offset(px: Int, py: Int): Int =
            ((py % TILE_SIZE_PIXELS) * TILE_SIZE_PIXELS + px % TILE_SIZE_PIXELS) * 4
        fun pixelIndex(px: Int, py: Int): Int = py * width + px
        fun channels(px: Int, py: Int): IntArray {
            val bytes = tile(px, py)
            val index = offset(px, py)
            return IntArray(4) { channel -> bytes[index + channel].toInt() and 255 }
        }

        val target = channels(x, y)
        fun matches(px: Int, py: Int): Boolean {
            if (px !in 0 until width || py !in 0 until height) return false
            val index = pixelIndex(px, py)
            if (bitIsSet(selected, index) || bitIsSet(rejected, index)) return false
            val current = channels(px, py)
            val matches = if (target[3] == 0 && current[3] == 0) true
                else (0..3).all { channel -> kotlin.math.abs(current[channel] - target[channel]) <= tolerance }
            if (!matches) bitSet(rejected, index)
            return matches
        }

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.addLast(x to y)
        while (pending.isNotEmpty()) {
            val (seedX, seedY) = pending.removeLast()
            if (!matches(seedX, seedY)) continue
            var left = seedX
            while (matches(left - 1, seedY)) left--
            var px = left
            var above = false
            var below = false
            while (matches(px, seedY)) {
                bitSet(selected, pixelIndex(px, seedY))
                minX = minOf(minX, px)
                minY = minOf(minY, seedY)
                maxX = maxOf(maxX, px)
                maxY = maxOf(maxY, seedY)

                val nextAbove = matches(px, seedY - 1)
                val nextBelow = matches(px, seedY + 1)
                if (nextAbove && !above) pending.addLast(px to seedY - 1)
                if (nextBelow && !below) pending.addLast(px to seedY + 1)
                above = nextAbove
                below = nextBelow
                px++
            }
        }

        if (maxX < minX || maxY < minY) return null
        return RasterSelectionRegion.fromPredicate(minX, minY, maxX + 1, maxY + 1) { px, py ->
            bitIsSet(selected, pixelIndex(px, py))
        }
    }
}

private fun maskByteCount(width: Int, height: Int): Int {
    val count = width.toLong() * height.toLong()
    require(count in 1..Int.MAX_VALUE.toLong())
    return ((count + 7L) / 8L).toInt()
}

private fun bitIsSet(bits: ByteArray, index: Int): Boolean {
    val mask = 1 shl (index and 7)
    return (bits[index ushr 3].toInt() and mask) != 0
}

private fun bitSet(bits: ByteArray, index: Int) {
    val byteIndex = index ushr 3
    val mask = 1 shl (index and 7)
    bits[byteIndex] = (bits[byteIndex].toInt() or mask).toByte()
}
