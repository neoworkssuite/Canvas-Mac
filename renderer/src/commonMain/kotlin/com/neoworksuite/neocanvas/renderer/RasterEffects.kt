package com.neoworksuite.neocanvas.renderer

import kotlin.math.roundToInt

enum class RasterEffectType {
    Blur,
    MotionBlur,
    HueSaturation,
    ColourBalance,
    Curves,
    GradientMap,
    Grayscale,
    Invert,
}

data class RasterEffectSettings(
    val amount: Float = .5f,
    val secondary: Float = 0f,
    val tertiary: Float = 0f,
)

/** Destructive raster effects used by the first NeoCanvas FX panel. Every result is undoable by EditorState. */
object RasterEffects {
    fun apply(
        store: TileStore,
        layerId: String,
        canvasWidth: Int,
        canvasHeight: Int,
        type: RasterEffectType,
        settings: RasterEffectSettings,
        gradientHighlight: RasterColor = RasterColor(255, 255, 255),
    ): RasterPatch {
        val snapshot = store.snapshot()
        val keys = snapshot.keys.filter { it.layerId == layerId }
        if (keys.isEmpty()) return RasterPatch.of(emptyMap())

        val replacements = linkedMapOf<TileKey, ByteArray>()
        keys.forEach { key ->
            val source = snapshot[key] ?: return@forEach
            replacements[key] = when (type) {
                RasterEffectType.Blur -> blurTile(snapshot, key, canvasWidth, canvasHeight,
                    radius = (1 + settings.amount.coerceIn(0f, 1f) * 9f).roundToInt())
                RasterEffectType.MotionBlur -> motionBlurTile(snapshot, key, canvasWidth, canvasHeight,
                    distance = (2 + settings.amount.coerceIn(0f, 1f) * 22f).roundToInt())
                RasterEffectType.HueSaturation -> transform(source) { r, g, b, a ->
                    val hsv = rgbToHsv(r, g, b)
                    val hueShift = settings.secondary.coerceIn(-1f, 1f) * 180f
                    val saturationScale = 1f + settings.amount.coerceIn(-1f, 1f)
                    val valueScale = 1f + settings.tertiary.coerceIn(-1f, 1f)
                    val rgb = hsvToRgb(
                        (hsv.first + hueShift + 360f) % 360f,
                        (hsv.second * saturationScale).coerceIn(0f, 1f),
                        (hsv.third * valueScale).coerceIn(0f, 1f),
                    )
                    intArrayOf(rgb[0], rgb[1], rgb[2], a)
                }
                RasterEffectType.ColourBalance -> transform(source) { r, g, b, a ->
                    intArrayOf(
                        (r + settings.amount.coerceIn(-1f, 1f) * 96f).roundToInt().coerceIn(0, 255),
                        (g + settings.secondary.coerceIn(-1f, 1f) * 96f).roundToInt().coerceIn(0, 255),
                        (b + settings.tertiary.coerceIn(-1f, 1f) * 96f).roundToInt().coerceIn(0, 255),
                        a,
                    )
                }
                RasterEffectType.Curves -> transform(source) { r, g, b, a ->
                    val contrast = .25f + settings.amount.coerceIn(0f, 1f) * 2.75f
                    intArrayOf(curveChannel(r, contrast), curveChannel(g, contrast), curveChannel(b, contrast), a)
                }
                RasterEffectType.GradientMap -> {
                    val hr = gradientHighlight.red
                    val hg = gradientHighlight.green
                    val hb = gradientHighlight.blue
                    transform(source) { r, g, b, a ->
                        val luminance = ((r * .2126f + g * .7152f + b * .0722f) / 255f).coerceIn(0f, 1f)
                        intArrayOf((hr * luminance).roundToInt(), (hg * luminance).roundToInt(), (hb * luminance).roundToInt(), a)
                    }
                }
                RasterEffectType.Grayscale -> transform(source) { r, g, b, a ->
                    val y = (r * .2126f + g * .7152f + b * .0722f).roundToInt().coerceIn(0, 255)
                    intArrayOf(y, y, y, a)
                }
                RasterEffectType.Invert -> transform(source) { r, g, b, a ->
                    intArrayOf(255 - r, 255 - g, 255 - b, a)
                }
            }
        }
        return RasterPatch.of(replacements)
    }

    private fun transform(
        source: ByteArray,
        op: (r: Int, g: Int, b: Int, a: Int) -> IntArray,
    ): ByteArray {
        val output = source.copyOf()
        var i = 0
        while (i < output.size) {
            val a = source[i + 3].toInt() and 255
            if (a != 0) {
                val mapped = op(
                    source[i].toInt() and 255,
                    source[i + 1].toInt() and 255,
                    source[i + 2].toInt() and 255,
                    a,
                )
                output[i] = mapped[0].toByte()
                output[i + 1] = mapped[1].toByte()
                output[i + 2] = mapped[2].toByte()
                output[i + 3] = mapped[3].toByte()
            }
            i += 4
        }
        return output
    }

    private fun blurTile(
        snapshot: Map<TileKey, ByteArray>,
        key: TileKey,
        width: Int,
        height: Int,
        radius: Int,
    ): ByteArray = sampledTile(snapshot, key, width, height) { x, y ->
        var sr = 0
        var sg = 0
        var sb = 0
        var sa = 0
        var count = 0
        val offsets = intArrayOf(-radius, 0, radius)
        for (dy in offsets) for (dx in offsets) {
            val p = pixel(snapshot, key.layerId, x + dx, y + dy, width, height) ?: continue
            sr += p[0]; sg += p[1]; sb += p[2]; sa += p[3]; count++
        }
        if (count == 0) intArrayOf(0, 0, 0, 0)
        else intArrayOf(sr / count, sg / count, sb / count, sa / count)
    }

    private fun motionBlurTile(
        snapshot: Map<TileKey, ByteArray>,
        key: TileKey,
        width: Int,
        height: Int,
        distance: Int,
    ): ByteArray = sampledTile(snapshot, key, width, height) { x, y ->
        var sr = 0
        var sg = 0
        var sb = 0
        var sa = 0
        var count = 0
        for (step in -4..4) {
            val offset = (distance * step) / 4
            val p = pixel(snapshot, key.layerId, x + offset, y, width, height) ?: continue
            sr += p[0]; sg += p[1]; sb += p[2]; sa += p[3]; count++
        }
        if (count == 0) intArrayOf(0, 0, 0, 0)
        else intArrayOf(sr / count, sg / count, sb / count, sa / count)
    }

    private fun sampledTile(
        snapshot: Map<TileKey, ByteArray>,
        key: TileKey,
        width: Int,
        height: Int,
        sampler: (Int, Int) -> IntArray,
    ): ByteArray {
        val output = ByteArray(TileFormat.BYTES_PER_TILE)
        val startX = key.x * TILE_SIZE_PIXELS
        val startY = key.y * TILE_SIZE_PIXELS
        for (localY in 0 until TILE_SIZE_PIXELS) for (localX in 0 until TILE_SIZE_PIXELS) {
            val x = startX + localX
            val y = startY + localY
            if (x !in 0 until width || y !in 0 until height) continue
            val mapped = sampler(x, y)
            val i = (localY * TILE_SIZE_PIXELS + localX) * 4
            output[i] = mapped[0].toByte()
            output[i + 1] = mapped[1].toByte()
            output[i + 2] = mapped[2].toByte()
            output[i + 3] = mapped[3].toByte()
        }
        return output
    }

    private fun pixel(
        snapshot: Map<TileKey, ByteArray>,
        layerId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): IntArray? {
        if (x !in 0 until width || y !in 0 until height) return null
        val tx = tileCoordinate(x)
        val ty = tileCoordinate(y)
        val bytes = snapshot[TileKey(layerId, tx, ty)] ?: return intArrayOf(0, 0, 0, 0)
        val localX = x - tx * TILE_SIZE_PIXELS
        val localY = y - ty * TILE_SIZE_PIXELS
        val i = (localY * TILE_SIZE_PIXELS + localX) * 4
        return intArrayOf(
            bytes[i].toInt() and 255,
            bytes[i + 1].toInt() and 255,
            bytes[i + 2].toInt() and 255,
            bytes[i + 3].toInt() and 255,
        )
    }

    private fun curveChannel(value: Int, contrast: Float): Int {
        val n = value / 255f
        val curved = .5f + (n - .5f) * contrast
        return (curved.coerceIn(0f, 1f) * 255f).roundToInt()
    }

    private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / delta) % 6f)
            max == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        return Triple((hue + 360f) % 360f, if (max == 0f) 0f else delta / max, max)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): IntArray {
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val rgb = when (h) {
            in 0f..<60f -> floatArrayOf(c, x, 0f)
            in 60f..<120f -> floatArrayOf(x, c, 0f)
            in 120f..<180f -> floatArrayOf(0f, c, x)
            in 180f..<240f -> floatArrayOf(0f, x, c)
            in 240f..<300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return intArrayOf(
            ((rgb[0] + m) * 255f).roundToInt().coerceIn(0, 255),
            ((rgb[1] + m) * 255f).roundToInt().coerceIn(0, 255),
            ((rgb[2] + m) * 255f).roundToInt().coerceIn(0, 255),
        )
    }
}
