package com.neoworksuite.neocanvas.renderer

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class LiquifyMode { Push, Pinch, Expand }

/**
 * Local spatial warp for raster artwork.
 *
 * Each dab samples from the state before that dab and writes a sparse tile patch. This avoids
 * feedback smearing inside one brush stamp while still allowing a stroke to accumulate naturally.
 */
object RasterLiquify {
    fun stroke(
        existing: TileStore,
        layerId: String,
        points: List<RasterPoint>,
        size: Float,
        strength: Float,
        mode: LiquifyMode,
        canvasWidth: Int,
        canvasHeight: Int,
        acceptsPixel: (Int, Int) -> Boolean = { _, _ -> true },
    ): RasterPatch {
        require(layerId.isNotBlank())
        require(size.isFinite() && size > 0f)
        require(strength in 0f..1f)
        require(canvasWidth > 0 && canvasHeight > 0)
        if (points.isEmpty() || strength <= 0f) return RasterPatch.of(emptyMap())
        if (mode == LiquifyMode.Push && points.size < 2) return RasterPatch.of(emptyMap())

        val source = existing.snapshot().filterKeys { it.layerId == layerId }
        val working = linkedMapOf<TileKey, ByteArray>()

        fun key(x: Int, y: Int) = TileKey(layerId, tileCoordinate(x), tileCoordinate(y))

        fun index(x: Int, y: Int, tileKey: TileKey): Int {
            val localX = x - tileKey.x * TILE_SIZE_PIXELS
            val localY = y - tileKey.y * TILE_SIZE_PIXELS
            return (localY * TILE_SIZE_PIXELS + localX) * 4
        }

        fun pixel(x: Int, y: Int): IntArray {
            if (x !in 0 until canvasWidth || y !in 0 until canvasHeight) {
                return intArrayOf(0, 0, 0, 0)
            }
            val tileKey = key(x, y)
            val bytes = working[tileKey] ?: source[tileKey] ?: return intArrayOf(0, 0, 0, 0)
            val offset = index(x, y, tileKey)
            return intArrayOf(
                bytes[offset].toInt() and 255,
                bytes[offset + 1].toInt() and 255,
                bytes[offset + 2].toInt() and 255,
                bytes[offset + 3].toInt() and 255,
            )
        }

        fun sample(x: Float, y: Float): IntArray {
            val sx = x.coerceIn(0f, (canvasWidth - 1).toFloat())
            val sy = y.coerceIn(0f, (canvasHeight - 1).toFloat())
            val x0 = floor(sx).toInt()
            val y0 = floor(sy).toInt()
            val x1 = min(canvasWidth - 1, x0 + 1)
            val y1 = min(canvasHeight - 1, y0 + 1)
            val tx = sx - x0
            val ty = sy - y0
            val p00 = pixel(x0, y0)
            val p10 = pixel(x1, y0)
            val p01 = pixel(x0, y1)
            val p11 = pixel(x1, y1)
            return IntArray(4) { channel ->
                val top = p00[channel] * (1f - tx) + p10[channel] * tx
                val bottom = p01[channel] * (1f - tx) + p11[channel] * tx
                (top * (1f - ty) + bottom * ty).toInt().coerceIn(0, 255)
            }
        }

        fun dab(cx: Float, cy: Float, ux: Float, uy: Float, pressure: Float) {
            val safePressure = pressure.coerceIn(.05f, 1f)
            val radius = max(.75f, size * safePressure * .5f)
            val left = max(0, floor(cx - radius).toInt())
            val top = max(0, floor(cy - radius).toInt())
            val right = min(canvasWidth - 1, ceil(cx + radius).toInt())
            val bottom = min(canvasHeight - 1, ceil(cy + radius).toInt())
            if (left > right || top > bottom) return

            val pending = linkedMapOf<TileKey, ByteArray>()

            fun writable(x: Int, y: Int): Pair<ByteArray, Int> {
                val tileKey = key(x, y)
                val bytes = pending.getOrPut(tileKey) {
                    working[tileKey]?.copyOf() ?: source[tileKey]?.copyOf()
                    ?: ByteArray(TileFormat.BYTES_PER_TILE)
                }
                return bytes to index(x, y, tileKey)
            }

            for (y in top..bottom) for (x in left..right) {
                if (!acceptsPixel(x, y)) continue
                val vx = x + .5f - cx
                val vy = y + .5f - cy
                val distance = sqrt(vx * vx + vy * vy)
                if (distance > radius) continue
                val normalized = (distance / radius).coerceIn(0f, 1f)
                val falloff = (1f - normalized) * (1f - normalized)
                if (falloff <= .0001f) continue

                val amount = strength * safePressure * falloff
                val sourcePoint = when (mode) {
                    LiquifyMode.Push -> {
                        val displacement = radius * .72f * amount
                        Pair(x + .5f - ux * displacement, y + .5f - uy * displacement)
                    }
                    LiquifyMode.Pinch -> {
                        val factor = 1f + .72f * amount
                        Pair(cx + vx * factor, cy + vy * factor)
                    }
                    LiquifyMode.Expand -> {
                        val factor = (1f - .72f * amount).coerceAtLeast(.12f)
                        Pair(cx + vx * factor, cy + vy * factor)
                    }
                }
                val sampled = sample(sourcePoint.first, sourcePoint.second)
                val current = pixel(x, y)
                val mix = (falloff * safePressure).coerceIn(0f, 1f)
                val (bytes, offset) = writable(x, y)
                for (channel in 0..3) {
                    bytes[offset + channel] = (
                        current[channel] * (1f - mix) + sampled[channel] * mix
                    ).toInt().coerceIn(0, 255).toByte()
                }
            }
            pending.forEach { (tileKey, bytes) -> working[tileKey] = bytes }
        }

        when {
            points.size == 1 -> {
                val point = points.single()
                dab(point.x, point.y, 0f, 0f, point.pressure)
            }
            else -> {
                val spacing = max(1f, size * .16f)
                points.zipWithNext().forEach { (from, to) ->
                    val dx = to.x - from.x
                    val dy = to.y - from.y
                    val distance = sqrt(dx * dx + dy * dy)
                    if (distance <= .001f) {
                        if (mode != LiquifyMode.Push) dab(to.x, to.y, 0f, 0f, to.pressure)
                        return@forEach
                    }
                    val ux = dx / distance
                    val uy = dy / distance
                    val steps = max(1, ceil(distance / spacing).toInt())
                    for (step in 1..steps) {
                        val t = step / steps.toFloat()
                        dab(
                            cx = from.x + dx * t,
                            cy = from.y + dy * t,
                            ux = ux,
                            uy = uy,
                            pressure = from.pressure + (to.pressure - from.pressure) * t,
                        )
                    }
                }
            }
        }

        val replacements = linkedMapOf<TileKey, ByteArray>()
        val removals = linkedSetOf<TileKey>()
        working.forEach { (tileKey, bytes) ->
            if (source[tileKey]?.contentEquals(bytes) == true) return@forEach
            if ((3 until bytes.size step 4).all { (bytes[it].toInt() and 255) == 0 }) removals += tileKey
            else replacements[tileKey] = bytes
        }
        return RasterPatch.of(replacements, removals)
    }
}
