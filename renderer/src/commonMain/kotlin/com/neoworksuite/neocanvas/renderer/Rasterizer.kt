package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.brushes.BrushMode
import com.neoworksuite.neocanvas.brushes.BrushDefinition
import com.neoworksuite.neocanvas.brushes.BrushTip
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Platform-neutral brush input after the UI has mapped it into document pixels. */
data class RasterPoint(val x: Float, val y: Float, val pressure: Float = 1f)

enum class DrawingSymmetry { None, Vertical, Horizontal, Both }

/** Straight-alpha pigment used by the raster engine, independent of Compose or Android graphics. */
data class RasterColor(val red: Int, val green: Int, val blue: Int) {
    init { require(red in 0..255 && green in 0..255 && blue in 0..255) }
}

/**
 * Stamps a simple, original round brush into sparse 256px RGBA tiles. The returned patch is not
 * applied automatically: callers can pair it with one ApplyRasterPatch history command.
 */
object Rasterizer {
    fun stroke(
        existing: TileStore,
        layerId: String,
        points: List<RasterPoint>,
        color: RasterColor,
        size: Float,
        opacity: Float,
        mode: BrushMode,
        canvasWidth: Int,
        canvasHeight: Int,
        acceptsPixel: (Int, Int) -> Boolean = { _, _ -> true },
        brush: BrushDefinition? = null,
        symmetry: DrawingSymmetry = DrawingSymmetry.None,
        alphaLocked: Boolean = false,
    ): RasterPatch {
        require(size.isFinite() && size > 0f)
        require(opacity in 0f..1f)
        if (points.isEmpty()) return RasterPatch.of(emptyMap())

        val working = linkedMapOf<TileKey, ByteArray>()
        fun tile(key: TileKey): ByteArray = working.getOrPut(key) { existing.read(key) ?: ByteArray(TileFormat.BYTES_PER_TILE) }
        val stamps = interpolateStrokeStamps(points, size, brush)
        stamps.forEachIndexed { stampIndex, point ->
            val dynamics = brush?.dynamics
            val scatterRadius = size * (dynamics?.scatter ?: 0f) * .72f
            val jitterRadius = size * (dynamics?.jitter ?: 0f) * .24f
            val spread = scatterRadius + jitterRadius
            val movedPoint = if (spread > 0f) {
                val angle = noise01(stampIndex, point.x.toInt(), 17) * 6.2831855f
                val amount = spread * noise01(stampIndex, point.y.toInt(), 41)
                point.copy(x = point.x + cos(angle) * amount, y = point.y + sin(angle) * amount)
            } else point
            val mirrored = linkedSetOf(movedPoint)
            if (symmetry == DrawingSymmetry.Vertical || symmetry == DrawingSymmetry.Both)
                mirrored += movedPoint.copy(x = canvasWidth - movedPoint.x)
            if (symmetry == DrawingSymmetry.Horizontal || symmetry == DrawingSymmetry.Both)
                mirrored += movedPoint.copy(y = canvasHeight - movedPoint.y)
            if (symmetry == DrawingSymmetry.Both)
                mirrored += movedPoint.copy(x = canvasWidth - movedPoint.x, y = canvasHeight - movedPoint.y)
            mirrored.forEach { sample ->
                stamp(::tile, layerId, sample, color, size, opacity, mode, canvasWidth, canvasHeight, acceptsPixel, brush, alphaLocked)
            }
        }

        val replacements = linkedMapOf<TileKey, ByteArray>()
        val removals = linkedSetOf<TileKey>()
        working.forEach { (key, pixels) ->
            if (pixels.any { it.toInt() != 0 }) replacements[key] = pixels
            else if (existing.read(key) != null) removals += key
        }
        return RasterPatch.of(replacements, removals)
    }

    private fun interpolateStrokeStamps(
        points: List<RasterPoint>,
        size: Float,
        brush: BrushDefinition?,
    ): List<RasterPoint> {
        val configured = size * (brush?.let { it.spacing / it.baseSize } ?: .20f)
        val textured = brush?.let {
            it.tip in setOf(BrushTip.Spray, BrushTip.Chalk, BrushTip.DryPaint, BrushTip.Bristle,
                BrushTip.Water, BrushTip.Leaf, BrushTip.Grass, BrushTip.Bark) ||
                it.dynamics.scatter > .2f || it.dynamics.grain > .4f || it.dynamics.wetMix > .5f
        } == true
        // Large textured tips cover a broad area. A proportional floor avoids restamping almost
        // identical areas while preserving the dense sampling used by precision pens and pencils.
        val spacing = max(.5f, max(configured, if (textured && size >= 24f) size * .22f else 0f))
        val stamps = ArrayList<RasterPoint>()
        stamps += points.first()
        var distanceUntilStamp = spacing.toDouble()
        points.zipWithNext().forEach { (from, to) ->
            val dx = to.x - from.x
            val dy = to.y - from.y
            val distance = sqrt(dx.toDouble() * dx + dy.toDouble() * dy)
            if (distance == 0.0) return@forEach
            while (distanceUntilStamp <= distance && stamps.size < 100_000) {
                val fraction = distanceUntilStamp / distance
                stamps += RasterPoint((from.x.toDouble() + dx * fraction).toFloat(),
                    (from.y.toDouble() + dy * fraction).toFloat(),
                    (from.pressure.toDouble() + (to.pressure - from.pressure) * fraction).toFloat())
                distanceUntilStamp += spacing
            }
            distanceUntilStamp -= distance
        }
        if (stamps.last() != points.last()) stamps += points.last()
        return stamps
    }

    private fun stamp(
        tile: (TileKey) -> ByteArray,
        layerId: String,
        point: RasterPoint,
        color: RasterColor,
        size: Float,
        opacity: Float,
        mode: BrushMode,
        canvasWidth: Int,
        canvasHeight: Int,
        acceptsPixel: (Int, Int) -> Boolean,
        brush: BrushDefinition?,
        alphaLocked: Boolean,
    ) {
        val pressure = point.pressure.coerceIn(.05f, 1f)
        val sizePressure = 1f - (1f - pressure) * (brush?.pressureSize ?: 1f)
        val opacityPressure = 1f - (1f - pressure) * (brush?.pressureOpacity ?: 1f)
        val radius = max(.5f, size * sizePressure / 2f)
        val left = max(0, floor(point.x - radius).toInt())
        val top = max(0, floor(point.y - radius).toInt())
        val right = min(canvasWidth - 1, ceil(point.x + radius).toInt())
        val bottom = min(canvasHeight - 1, ceil(point.y + radius).toInt())
        if (left > right || top > bottom) return

        // Brush dynamics are constant for this stamp. Keep them out of the hot per-pixel loop.
        val dynamics = brush?.dynamics
        val tip = brush?.tip ?: BrushTip.Round
        val shapeRatio = dynamics?.shapeRatio ?: 1f
        val inverseShapeRatio = 1f / shapeRatio
        val hardness = dynamics?.hardness ?: 1f
        val grain = dynamics?.grain ?: 0f
        val wetMix = dynamics?.wetMix ?: 0f
        val rotation = dynamics?.rotation ?: 0f
        val angle = if (rotation == 0f) 0f
        else rotation * noise01(point.x.toInt(), point.y.toInt(), 73) * 6.2831855f
        val angleCos = if (angle == 0f) 1f else cos(angle)
        val angleSin = if (angle == 0f) 0f else sin(angle)
        val outerDistance = 1f + .5f / radius
        val flatHalfHeight = radius * shapeRatio * .42f
        val chalkThreshold = .16f + grain * .28f
        val wetRetain = 1f - wetMix * .28f
        val wetBlend = wetMix * .28f

        for (y in top..bottom) for (x in left..right) {
            if (!acceptsPixel(x, y)) continue
            val dx = x + .5f - point.x
            val dy = y + .5f - point.y
            val rotatedX = dx * angleCos - dy * angleSin
            val rotatedY = dx * angleSin + dy * angleCos
            val scaledY = rotatedY * inverseShapeRatio
            val distance = sqrt(rotatedX * rotatedX + scaledY * scaledY) / radius
            val edge = when {
                distance > outerDistance -> 0f
                hardness >= .999f -> (radius + .5f - distance * radius).coerceIn(0f, 1f)
                distance <= hardness -> 1f
                else -> ((outerDistance - distance) / (outerDistance - hardness)).coerceIn(0f, 1f)
            }
            val pixelNoise = noise01(x, y, 101)
            var coverage = when (tip) {
                BrushTip.Round -> if (brush == null) { if (distance <= 1f) 1f else 0f } else edge
                BrushTip.SoftRound -> (1f - distance * distance).coerceIn(0f, 1f).let { it * it * it }
                BrushTip.Flat -> if (abs(rotatedX) <= radius && abs(rotatedY) <= flatHalfHeight) edge else 0f
                BrushTip.Pencil -> if (edge > 0f) {
                    edge * (.20f + .65f * pixelNoise)
                } else 0f
                BrushTip.DryPaint -> {
                    val bristle = if ((y % 5) == 0) .15f else .75f
                    edge * bristle * (.25f + .75f * pixelNoise)
                }
                BrushTip.Bristle -> edge * (.18f + .82f * abs(sin(rotatedY * .72f))) * (.45f + .55f * pixelNoise)
                BrushTip.Chalk -> if (pixelNoise > chalkThreshold) edge * (.38f + .62f * pixelNoise) else 0f
                BrushTip.Water -> {
                    val soft = (1f - distance).coerceIn(0f, 1f)
                    val pooledEdge = (1f - abs(distance - .78f) * 5f).coerceIn(0f, 1f)
                    soft * .42f + pooledEdge * .38f
                }
                BrushTip.Spray -> if (distance <= 1f && pixelNoise > .58f) edge * pixelNoise else 0f
                BrushTip.Pixel -> if (abs(rotatedX) <= radius && abs(rotatedY) <= radius * shapeRatio) 1f else 0f
                BrushTip.Leaf -> {
                    val along = abs(rotatedX / radius)
                    val across = abs(scaledY / radius)
                    val leafEdge = (1f - along) * .72f
                    if (along <= 1f && across <= leafEdge) {
                        val vein = (1f - across * 8f).coerceIn(0f, 1f) * .22f
                        (edge * (.72f + vein))
                    } else 0f
                }
                BrushTip.Grass -> {
                    val vertical = (rotatedY / radius + 1f) * .5f
                    val bladeWidth = (.055f + (1f - vertical.coerceIn(0f, 1f)) * .055f) * radius
                    val bend = sin(vertical * 3.1415927f) * radius * .18f
                    val blade = minOf(abs(rotatedX - bend), abs(rotatedX + radius * .34f), abs(rotatedX - radius * .34f))
                    if (vertical in 0f..1f && blade <= bladeWidth) edge * (.62f + .38f * pixelNoise) else 0f
                }
                BrushTip.Bark -> {
                    val inside = abs(rotatedX) <= radius && abs(rotatedY) <= radius * shapeRatio
                    if (inside) {
                        val ridge = abs(sin(rotatedX * .31f + pixelNoise * 2.2f))
                        if (ridge > .34f) (.3f + ridge * .7f) * (1f - grain * .25f) else 0f
                    } else 0f
                }
            }
            coverage *= 1f - grain * (1f - pixelNoise) * .78f
            if (wetMix > 0f && distance <= 1f) {
                val bloom = (1f - distance * distance).coerceIn(0f, 1f) * (.32f + .68f * pixelNoise)
                coverage = coverage * wetRetain + bloom * wetBlend
            }
            val strength = opacity * opacityPressure * coverage
            if (strength <= 0f) continue
            val key = TileKey(layerId, tileCoordinate(x), tileCoordinate(y))
            val pixels = tile(key)
            val localX = x - key.x * TILE_SIZE_PIXELS
            val localY = y - key.y * TILE_SIZE_PIXELS
            val offset = (localY * TILE_SIZE_PIXELS + localX) * 4
            val originalAlpha = pixels[offset + 3]
            if (alphaLocked && (originalAlpha.toInt() and 255) == 0) continue
            if (mode == BrushMode.ERASE) {
                if (alphaLocked) continue
                val remaining = ((pixels[offset + 3].toInt() and 255) * (1f - strength)).toInt().coerceIn(0, 255)
                pixels[offset + 3] = remaining.toByte()
                if (remaining == 0) {
                    pixels[offset] = 0; pixels[offset + 1] = 0; pixels[offset + 2] = 0
                }
            } else {
                blend(pixels, offset, color, strength)
                if (alphaLocked) pixels[offset + 3] = originalAlpha
            }
        }
    }

    private fun blend(pixels: ByteArray, offset: Int, color: RasterColor, alpha: Float) {
        val sourceAlpha = alpha.coerceIn(0f, 1f)
        val destinationAlpha = (pixels[offset + 3].toInt() and 0xff) / 255f
        val outAlpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)
        fun channel(source: Int, destination: Byte): Byte {
            val dst = (destination.toInt() and 0xff) / 255f
            val out = if (outAlpha == 0f) 0f else (source / 255f * sourceAlpha + dst * destinationAlpha * (1f - sourceAlpha)) / outAlpha
            return (out * 255f).toInt().coerceIn(0, 255).toByte()
        }
        pixels[offset] = channel(color.red, pixels[offset])
        pixels[offset + 1] = channel(color.green, pixels[offset + 1])
        pixels[offset + 2] = channel(color.blue, pixels[offset + 2])
        pixels[offset + 3] = (outAlpha * 255f).toInt().coerceIn(0, 255).toByte()
    }

    private fun noise01(x: Int, y: Int, salt: Int): Float {
        var value = x * 374761393 + y * 668265263 + salt * 1442695041
        value = (value xor (value ushr 13)) * 1274126177
        return ((value xor (value ushr 16)).ushr(8) and 0x00ffffff) / 16777215f
    }

    internal fun plannedStrokeStampCount(points: List<RasterPoint>, size: Float, brush: BrushDefinition?): Int =
        if (points.isEmpty()) 0 else interpolateStrokeStamps(points, size, brush).size
}

internal fun plannedStrokeStampCount(points: List<RasterPoint>, size: Float, brush: BrushDefinition?): Int =
    Rasterizer.plannedStrokeStampCount(points, size, brush)
