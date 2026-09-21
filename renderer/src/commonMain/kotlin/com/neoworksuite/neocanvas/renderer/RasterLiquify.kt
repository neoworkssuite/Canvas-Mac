package com.neoworksuite.neocanvas.renderer

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class LiquifyMode(val displayName: String) {
    Push("Push"),
    Pinch("Pinch"),
    Expand("Expand"),
    TwirlLeft("Twirl Left"),
    TwirlRight("Twirl Right"),
    Smooth("Smooth"),
    Crystals("Crystals"),
    Edge("Edge"),
    Reconstruct("Reconstruct"),
}

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
        reference: Map<TileKey, ByteArray> = emptyMap(),
        acceptsPixel: (Int, Int) -> Boolean = { _, _ -> true },
    ): RasterPatch {
        require(layerId.isNotBlank())
        require(size.isFinite() && size > 0f)
        require(strength in 0f..1f)
        require(canvasWidth > 0 && canvasHeight > 0)
        if (points.isEmpty() || strength <= 0f) return RasterPatch.of(emptyMap())
        if (mode == LiquifyMode.Push && points.size < 2) return RasterPatch.of(emptyMap())
        if (mode == LiquifyMode.Reconstruct && reference.isEmpty()) return RasterPatch.of(emptyMap())
        require(reference.keys.all { it.layerId == layerId }) {
            "Liquify reference tiles must belong to the active layer."
        }

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

        fun sampleWith(reader: (Int, Int) -> IntArray, x: Float, y: Float): IntArray {
            val sx = x.coerceIn(0f, (canvasWidth - 1).toFloat())
            val sy = y.coerceIn(0f, (canvasHeight - 1).toFloat())
            val x0 = floor(sx).toInt()
            val y0 = floor(sy).toInt()
            val x1 = min(canvasWidth - 1, x0 + 1)
            val y1 = min(canvasHeight - 1, y0 + 1)
            val tx = sx - x0
            val ty = sy - y0
            val p00 = reader(x0, y0)
            val p10 = reader(x1, y0)
            val p01 = reader(x0, y1)
            val p11 = reader(x1, y1)
            return IntArray(4) { channel ->
                val top = p00[channel] * (1f - tx) + p10[channel] * tx
                val bottom = p01[channel] * (1f - tx) + p11[channel] * tx
                (top * (1f - ty) + bottom * ty).toInt().coerceIn(0, 255)
            }
        }

        fun sample(x: Float, y: Float): IntArray = sampleWith(::pixel, x, y)

        fun referencePixel(x: Int, y: Int): IntArray {
            if (x !in 0 until canvasWidth || y !in 0 until canvasHeight) {
                return intArrayOf(0, 0, 0, 0)
            }
            val tileKey = key(x, y)
            val bytes = reference[tileKey] ?: return intArrayOf(0, 0, 0, 0)
            val offset = index(x, y, tileKey)
            return intArrayOf(
                bytes[offset].toInt() and 255,
                bytes[offset + 1].toInt() and 255,
                bytes[offset + 2].toInt() and 255,
                bytes[offset + 3].toInt() and 255,
            )
        }

        fun referenceSample(x: Float, y: Float): IntArray =
            sampleWith(::referencePixel, x, y)

        fun intensity(pixel: IntArray): Float =
            (pixel[0] * .2126f + pixel[1] * .7152f + pixel[2] * .0722f) *
                (pixel[3] / 255f)

        fun noise01(x: Int, y: Int, salt: Int): Float {
            var value = x * 374761393 + y * 668265263 + salt * 69069
            value = (value xor (value ushr 13)) * 1274126177
            value = value xor (value ushr 16)
            return (value ushr 1) / Int.MAX_VALUE.toFloat()
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
                val sampled = when (mode) {
                    LiquifyMode.Push -> {
                        val displacement = radius * .72f * amount
                        sample(x + .5f - ux * displacement, y + .5f - uy * displacement)
                    }
                    LiquifyMode.Pinch -> {
                        val factor = 1f + .72f * amount
                        sample(cx + vx * factor, cy + vy * factor)
                    }
                    LiquifyMode.Expand -> {
                        val factor = (1f - .72f * amount).coerceAtLeast(.12f)
                        sample(cx + vx * factor, cy + vy * factor)
                    }
                    LiquifyMode.TwirlLeft,
                    LiquifyMode.TwirlRight -> {
                        val direction = if (mode == LiquifyMode.TwirlLeft) -1f else 1f
                        val angle = direction * .90f * amount
                        val cosA = cos(angle)
                        val sinA = sin(angle)
                        sample(
                            cx + vx * cosA - vy * sinA,
                            cy + vx * sinA + vy * cosA,
                        )
                    }
                    LiquifyMode.Smooth -> {
                        val reach = max(1f, radius * .12f)
                        val samples = listOf(
                            sample(x + .5f, y + .5f),
                            sample(x + .5f - reach, y + .5f),
                            sample(x + .5f + reach, y + .5f),
                            sample(x + .5f, y + .5f - reach),
                            sample(x + .5f, y + .5f + reach),
                        )
                        IntArray(4) { channel ->
                            samples.sumOf { it[channel] } / samples.size
                        }
                    }
                    LiquifyMode.Crystals -> {
                        val cellSize = max(2f, radius * .16f)
                        val cellX = floor((x + .5f) / cellSize).toInt()
                        val cellY = floor((y + .5f) / cellSize).toInt()
                        val angle = noise01(cellX, cellY, 17) * 6.2831855f
                        val scatter = radius * (.08f + .20f * noise01(cellX, cellY, 41)) * amount
                        sample(
                            x + .5f + cos(angle) * scatter,
                            y + .5f + sin(angle) * scatter,
                        )
                    }
                    LiquifyMode.Edge -> {
                        val step = (radius * .08f).coerceIn(1f, 4f)
                        val leftSample = sample(x + .5f - step, y + .5f)
                        val rightSample = sample(x + .5f + step, y + .5f)
                        val topSample = sample(x + .5f, y + .5f - step)
                        val bottomSample = sample(x + .5f, y + .5f + step)
                        val gx = intensity(rightSample) - intensity(leftSample)
                        val gy = intensity(bottomSample) - intensity(topSample)
                        val gradient = sqrt(gx * gx + gy * gy)
                        if (gradient <= .001f) {
                            sample(x + .5f, y + .5f)
                        } else {
                            val displacement = min(6f, radius * .18f) * amount
                            sample(
                                x + .5f - gx / gradient * displacement,
                                y + .5f - gy / gradient * displacement,
                            )
                        }
                    }
                    LiquifyMode.Reconstruct -> referenceSample(x + .5f, y + .5f)
                }
                val current = pixel(x, y)
                val mix = when (mode) {
                    LiquifyMode.Smooth, LiquifyMode.Reconstruct -> amount.coerceIn(0f, 1f)
                    else -> (falloff * safePressure).coerceIn(0f, 1f)
                }
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
