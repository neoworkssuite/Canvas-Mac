package com.neoworksuite.neocanvas.renderer

/** Exact-colour, four-connected fill on one layer. Tile copies are cached per operation. */
object FloodFill {
    fun fill(store: TileStore, layer: String, width: Int, height: Int, x: Int, y: Int, color: RasterColor,
        acceptsPixel: (Int, Int) -> Boolean = { _, _ -> true },
        alphaLocked: Boolean = false,
        tolerance: Int = 0,
    ): RasterPatch {
        require(tolerance in 0..255)
        if (x !in 0 until width || y !in 0 until height) return RasterPatch.of(emptyMap())
        val tiles = mutableMapOf<TileKey, ByteArray>()
        val changed = mutableSetOf<TileKey>()
        fun tile(px: Int, py: Int): ByteArray = tiles.getOrPut(TileKey(layer, px / 256, py / 256)) {
            store.read(TileKey(layer, px / 256, py / 256)) ?: ByteArray(TileFormat.BYTES_PER_TILE)
        }
        fun offset(px: Int, py: Int) = ((py % 256) * 256 + px % 256) * 4
        val start = tile(x, y)
        val startOffset = offset(x, y)
        val target = (0..3).map { start[startOffset + it] }
        if (alphaLocked && (target[3].toInt() and 255) == 0) return RasterPatch.of(emptyMap())
        val replacement = listOf(color.red.toByte(), color.green.toByte(), color.blue.toByte(),
            if (alphaLocked) target[3] else 255.toByte())
        if (target == replacement) return RasterPatch.of(emptyMap())
        fun matches(px: Int, py: Int): Boolean {
            if (px !in 0 until width || py !in 0 until height) return false
            if (!acceptsPixel(px, py)) return false
            val bytes = tile(px, py)
            val i = offset(px, py)
            return (0..3).all { kotlin.math.abs((bytes[i + it].toInt() and 255) - (target[it].toInt() and 255)) <= tolerance }
        }
        // Scanline spans avoid a per-pixel queue on large blank documents.
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.addLast(x to y)
        while (pending.isNotEmpty()) {
            val (sx, sy) = pending.removeLast()
            if (!matches(sx, sy)) continue
            var left = sx
            while (matches(left - 1, sy)) left--
            var px = left
            var above = false
            var below = false
            while (matches(px, sy)) {
                val bytes = tile(px, sy)
                val i = offset(px, sy)
                for (channel in 0..3) bytes[i + channel] = replacement[channel]
                changed += TileKey(layer, px / 256, sy / 256)
                val nextAbove = matches(px, sy - 1)
                val nextBelow = matches(px, sy + 1)
                if (nextAbove && !above) pending.addLast(px to sy - 1)
                if (nextBelow && !below) pending.addLast(px to sy + 1)
                above = nextAbove
                below = nextBelow
                px++
            }
        }
        return RasterPatch.of(tiles.filterKeys { it in changed })
    }
}
