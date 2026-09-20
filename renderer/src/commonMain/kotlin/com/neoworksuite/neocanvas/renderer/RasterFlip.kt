package com.neoworksuite.neocanvas.renderer

/** Mirrors selected RGBA pixels inside an exclusive rectangle. */
object RasterFlip {
    fun flip(
        store: TileStore,
        layer: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        width: Int,
        height: Int,
        horizontal: Boolean,
        acceptsSourcePixel: (Int, Int) -> Boolean = { _, _ -> true },
    ): RasterPatch {
        require(left >= 0 && top >= 0 && right <= width && bottom <= height)
        require(left < right && top < bottom)
        val source = store.snapshot().filterKeys { it.layerId == layer }
        val working = mutableMapOf<TileKey, ByteArray>()
        fun key(x: Int, y: Int) = TileKey(layer, x / TILE_SIZE_PIXELS, y / TILE_SIZE_PIXELS)
        fun index(x: Int, y: Int) =
            ((y % TILE_SIZE_PIXELS) * TILE_SIZE_PIXELS + x % TILE_SIZE_PIXELS) * 4
        fun writable(k: TileKey) =
            working.getOrPut(k) { source[k]?.copyOf() ?: ByteArray(TileFormat.BYTES_PER_TILE) }

        // Cut the selected source pixels first, then place their mirrored snapshot.
        for (y in top until bottom) for (x in left until right) {
            if (!acceptsSourcePixel(x, y)) continue
            val original = source[key(x, y)] ?: continue
            val i = index(x, y)
            val bytes = writable(key(x, y))
            for (channel in 0..3) bytes[i + channel] = 0
        }

        for (y in top until bottom) for (x in left until right) {
            if (!acceptsSourcePixel(x, y)) continue
            val destinationX = if (horizontal) left + (right - 1 - x) else x
            val destinationY = if (horizontal) y else top + (bottom - 1 - y)
            val sourceBytes = source[key(x, y)]
            val destinationKey = key(destinationX, destinationY)
            if (sourceBytes == null && source[destinationKey] == null && destinationKey !in working) continue
            val destination = writable(destinationKey)
            val sourceIndex = index(x, y)
            val destinationIndex = index(destinationX, destinationY)
            for (channel in 0..3) destination[destinationIndex + channel] =
                sourceBytes?.get(sourceIndex + channel) ?: 0
        }

        val changed = working.filter { (key, bytes) -> source[key]?.contentEquals(bytes) != true }
        val empty = changed.filterValues { bytes ->
            (3 until bytes.size step 4).all { bytes[it].toInt() == 0 }
        }.keys
        return RasterPatch.of(changed.filterKeys { it !in empty }, empty)
    }
}
