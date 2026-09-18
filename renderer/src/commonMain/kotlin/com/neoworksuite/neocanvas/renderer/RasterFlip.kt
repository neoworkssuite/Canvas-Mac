package com.neoworksuite.neocanvas.renderer

/** Mirrors all RGBA pixels, including transparent ones, inside an exclusive rectangle. */
object RasterFlip {
    fun flip(store: TileStore, layer: String, left: Int, top: Int, right: Int, bottom: Int,
        width: Int, height: Int, horizontal: Boolean): RasterPatch {
        require(left >= 0 && top >= 0 && right <= width && bottom <= height)
        require(left < right && top < bottom)
        val source = store.snapshot().filterKeys { it.layerId == layer }
        val working = mutableMapOf<TileKey, ByteArray>()
        for (y in top until bottom) for (x in left until right) {
            val sx = if (horizontal) left + (right - 1 - x) else x
            val sy = if (horizontal) y else top + (bottom - 1 - y)
            val src = source[TileKey(layer, sx / 256, sy / 256)]
            val key = TileKey(layer, x / 256, y / 256)
            if (src == null && key !in source && key !in working) continue
            val dst = working.getOrPut(key) { source[key]?.copyOf() ?: ByteArray(TileFormat.BYTES_PER_TILE) }
            val i = ((sy % 256) * 256 + sx % 256) * 4
            val j = ((y % 256) * 256 + x % 256) * 4
            for (c in 0..3) dst[j + c] = src?.get(i + c) ?: 0.toByte()
        }
        val changed = working.filter { (key, bytes) -> source[key]?.contentEquals(bytes) != true }
        val empty = changed.filterValues { bytes -> (3 until bytes.size step 4).all { bytes[it].toInt() == 0 } }.keys
        return RasterPatch.of(changed.filterKeys { it !in empty }, empty)
    }
}
