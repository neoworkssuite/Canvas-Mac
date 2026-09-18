package com.neoworksuite.neocanvas.renderer

enum class ResizeSampling { Pixel, Smooth }

/** Cut and transform from an immutable source; smooth sampling blends premultiplied RGBA. */
object RasterMove {
    fun rotatedSize(width: Int, height: Int, degrees: Float): Pair<Int, Int> {
        require(degrees.isFinite())
        val angle = degrees * kotlin.math.PI / 180.0
        val cosine = kotlin.math.abs(kotlin.math.cos(angle))
        val sine = kotlin.math.abs(kotlin.math.sin(angle))
        return kotlin.math.ceil(width * cosine + height * sine - 1e-9).toInt().coerceAtLeast(1) to
            kotlin.math.ceil(width * sine + height * cosine - 1e-9).toInt().coerceAtLeast(1)
    }
    fun move(store: TileStore, layer: String, left: Int, top: Int, right: Int, bottom: Int,
        dx: Int, dy: Int, width: Int, height: Int, rotateClockwise: Boolean = false,
        resizedWidth: Int? = null, resizedHeight: Int? = null,
        sampling: ResizeSampling = ResizeSampling.Pixel, degrees: Float = 0f): RasterPatch {
        require(left >= 0 && top >= 0 && right <= width && bottom <= height)
        require(left < right && top < bottom)
        require(degrees.isFinite() && !(rotateClockwise && degrees != 0f))
        val angledSize = rotatedSize(right - left, bottom - top, degrees)
        val rotatedWidth = if (rotateClockwise) bottom - top else angledSize.first
        val rotatedHeight = if (rotateClockwise) right - left else angledSize.second
        val targetWidth = resizedWidth ?: rotatedWidth
        val targetHeight = resizedHeight ?: rotatedHeight
        require(targetWidth > 0 && targetHeight > 0)
        require(left + dx >= 0 && top + dy >= 0 && left + dx + targetWidth <= width && top + dy + targetHeight <= height)
        if (dx == 0 && dy == 0 && !rotateClockwise && degrees == 0f && targetWidth == right - left && targetHeight == bottom - top) return RasterPatch.of(emptyMap())
        val source = store.snapshot().filterKeys { it.layerId == layer }
        val working = mutableMapOf<TileKey, ByteArray>()
        fun key(x: Int, y: Int) = TileKey(layer, x / 256, y / 256)
        fun index(x: Int, y: Int) = ((y % 256) * 256 + x % 256) * 4
        fun writable(k: TileKey) = working.getOrPut(k) { source[k]?.copyOf() ?: ByteArray(TileFormat.BYTES_PER_TILE) }
        for (y in top until bottom) for (x in left until right) {
            val k = key(x, y)
            val original = source[k] ?: continue
            val i = index(x, y)
            if (original[i + 3].toInt() == 0) continue
            val bytes = writable(k)
            for (c in 0..3) bytes[i + c] = 0
        }
        val sample = FloatArray(4)
        fun accumulate(rx: Int, ry: Int, weight: Float) {
            val x = if (rotateClockwise) left + ry else left + rx
            val y = if (rotateClockwise) bottom - 1 - rx else top + ry
            if (x !in left until right || y !in top until bottom) return
            val original = source[key(x, y)] ?: return
            val i = index(x, y)
            val alpha = (original[i + 3].toInt() and 255) / 255f * weight
            for (c in 0..2) sample[c] += (original[i + c].toInt() and 255) * alpha
            sample[3] += alpha
        }
        val angle = degrees * kotlin.math.PI / 180.0
        val cosine = kotlin.math.cos(angle)
        val sine = kotlin.math.sin(angle)
        for (oy in 0 until targetHeight) for (ox in 0 until targetWidth) {
            sample.fill(0f)
            if (degrees != 0f) {
                val tx = (ox + .5) * rotatedWidth / targetWidth - rotatedWidth / 2.0
                val ty = (oy + .5) * rotatedHeight / targetHeight - rotatedHeight / 2.0
                val fx = cosine * tx + sine * ty + (right - left) / 2.0 - .5
                val fy = -sine * tx + cosine * ty + (bottom - top) / 2.0 - .5
                if (sampling == ResizeSampling.Smooth) {
                    val x0 = kotlin.math.floor(fx).toInt()
                    val y0 = kotlin.math.floor(fy).toInt()
                    val wx = (fx - x0).toFloat()
                    val wy = (fy - y0).toFloat()
                    accumulate(x0, y0, (1f - wx) * (1f - wy))
                    accumulate(x0 + 1, y0, wx * (1f - wy))
                    accumulate(x0, y0 + 1, (1f - wx) * wy)
                    accumulate(x0 + 1, y0 + 1, wx * wy)
                } else accumulate(kotlin.math.floor(fx + .5).toInt(), kotlin.math.floor(fy + .5).toInt(), 1f)
            } else if (sampling == ResizeSampling.Smooth) {
                val fx = ((ox + .5) * rotatedWidth / targetWidth - .5).coerceIn(0.0, (rotatedWidth - 1).toDouble())
                val fy = ((oy + .5) * rotatedHeight / targetHeight - .5).coerceIn(0.0, (rotatedHeight - 1).toDouble())
                val x0 = fx.toInt()
                val y0 = fy.toInt()
                val x1 = (x0 + 1).coerceAtMost(rotatedWidth - 1)
                val y1 = (y0 + 1).coerceAtMost(rotatedHeight - 1)
                val wx = (fx - x0).toFloat()
                val wy = (fy - y0).toFloat()
                accumulate(x0, y0, (1f - wx) * (1f - wy))
                accumulate(x1, y0, wx * (1f - wy))
                accumulate(x0, y1, (1f - wx) * wy)
                accumulate(x1, y1, wx * wy)
            } else {
                val rx = ((ox + .5) * rotatedWidth / targetWidth).toInt().coerceAtMost(rotatedWidth - 1)
                val ry = ((oy + .5) * rotatedHeight / targetHeight).toInt().coerceAtMost(rotatedHeight - 1)
                accumulate(rx, ry, 1f)
            }
            val alpha = sample[3].coerceIn(0f, 1f)
            if (alpha == 0f) continue
            val tx = left + dx + ox
            val ty = top + dy + oy
            val bytes = writable(key(tx, ty))
            val j = index(tx, ty)
            val dstAlpha = (bytes[j + 3].toInt() and 255) / 255f
            val outAlpha = alpha + dstAlpha * (1f - alpha)
            for (c in 0..2) {
                val dst = bytes[j + c].toInt() and 255
                bytes[j + c] = ((sample[c] + dst * dstAlpha * (1f - alpha)) / outAlpha + .5f).toInt().coerceIn(0, 255).toByte()
            }
            bytes[j + 3] = (outAlpha * 255 + .5f).toInt().coerceIn(0, 255).toByte()
        }
        val changed = working.filter { (k, bytes) -> source[k]?.contentEquals(bytes) != true }
        val empty = changed.filterValues { bytes -> (3 until bytes.size step 4).all { bytes[it].toInt() == 0 } }.keys
        return RasterPatch.of(changed.filterKeys { it !in empty }, empty)
    }
}
