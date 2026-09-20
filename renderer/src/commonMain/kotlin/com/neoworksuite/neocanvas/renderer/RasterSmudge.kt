package com.neoworksuite.neocanvas.renderer

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Directional pigment smudge. It drags existing pixels along the stroke without introducing new colour. */
object RasterSmudge {
    fun stroke(
        existing: TileStore,
        layerId: String,
        points: List<RasterPoint>,
        size: Float,
        strength: Float,
        canvasWidth: Int,
        canvasHeight: Int,
        acceptsPixel: (Int, Int) -> Boolean = { _, _ -> true },
    ): RasterPatch {
        require(size.isFinite() && size > 0f)
        require(strength in 0f..1f)
        if (points.size < 2 || strength <= 0f) return RasterPatch.of(emptyMap())

        val source = existing.snapshot().filterKeys { it.layerId == layerId }
        val working = linkedMapOf<TileKey, ByteArray>()

        fun key(x: Int, y: Int) = TileKey(layerId, tileCoordinate(x), tileCoordinate(y))
        fun index(x: Int, y: Int, tileKey: TileKey): Int {
            val localX = x - tileKey.x * TILE_SIZE_PIXELS
            val localY = y - tileKey.y * TILE_SIZE_PIXELS
            return (localY * TILE_SIZE_PIXELS + localX) * 4
        }
        fun readable(x: Int, y: Int): IntArray {
            if (x !in 0 until canvasWidth || y !in 0 until canvasHeight) return intArrayOf(0, 0, 0, 0)
            val k = key(x, y)
            val bytes = working[k] ?: source[k] ?: return intArrayOf(0, 0, 0, 0)
            val i = index(x, y, k)
            return intArrayOf(
                bytes[i].toInt() and 255,
                bytes[i + 1].toInt() and 255,
                bytes[i + 2].toInt() and 255,
                bytes[i + 3].toInt() and 255,
            )
        }
        fun writable(x: Int, y: Int): Pair<ByteArray, Int> {
            val k = key(x, y)
            val bytes = working.getOrPut(k) { source[k]?.copyOf() ?: ByteArray(TileFormat.BYTES_PER_TILE) }
            return bytes to index(x, y, k)
        }

        val spacing = max(1f, size * .16f)
        points.zipWithNext().forEach { (from, to) ->
            val dx = to.x - from.x
            val dy = to.y - from.y
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= .001f) return@forEach
            val ux = dx / distance
            val uy = dy / distance
            val steps = max(1, ceil(distance / spacing).toInt())
            for (step in 1..steps) {
                val t = step / steps.toFloat()
                val pressure = (from.pressure + (to.pressure - from.pressure) * t).coerceIn(.05f, 1f)
                val cx = from.x + dx * t
                val cy = from.y + dy * t
                val radius = max(.75f, size * pressure * .5f)
                val pickupDistance = radius * (.45f + strength * .55f)
                val left = max(0, floor(cx - radius).toInt())
                val top = max(0, floor(cy - radius).toInt())
                val right = min(canvasWidth - 1, ceil(cx + radius).toInt())
                val bottom = min(canvasHeight - 1, ceil(cy + radius).toInt())

                for (y in top..bottom) for (x in left..right) {
                    if (!acceptsPixel(x, y)) continue
                    val px = x + .5f - cx
                    val py = y + .5f - cy
                    val d = sqrt(px * px + py * py) / radius
                    if (d > 1f) continue

                    val sourceX = (x - ux * pickupDistance).toInt().coerceIn(0, canvasWidth - 1)
                    val sourceY = (y - uy * pickupDistance).toInt().coerceIn(0, canvasHeight - 1)
                    val dragged = readable(sourceX, sourceY)
                    if (dragged[3] == 0) continue
                    val current = readable(x, y)
                    val mix = (1f - d) * strength * pressure * .72f
                    if (mix <= .001f) continue

                    val (bytes, offset) = writable(x, y)
                    for (channel in 0..3) {
                        bytes[offset + channel] = (
                            current[channel] * (1f - mix) + dragged[channel] * mix
                        ).toInt().coerceIn(0, 255).toByte()
                    }
                }
            }
        }

        val replacements = linkedMapOf<TileKey, ByteArray>()
        val removals = linkedSetOf<TileKey>()
        working.forEach { (key, bytes) ->
            if (source[key]?.contentEquals(bytes) == true) return@forEach
            if ((3 until bytes.size step 4).all { (bytes[it].toInt() and 255) == 0 }) removals += key
            else replacements[key] = bytes
        }
        return RasterPatch.of(replacements, removals)
    }
}
