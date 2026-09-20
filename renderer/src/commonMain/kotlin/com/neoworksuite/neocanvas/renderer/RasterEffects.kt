package com.neoworksuite.neocanvas.renderer

import kotlin.math.roundToInt

enum class RasterEffectType {
    Blur,
    MotionBlur,
    HueSaturation,
    ColourBalance,
    Curves,
    GradientMap,
    Sharpen,
    Noise,
    Bloom,
    Halftone,
    ChromaticAberration,
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
                RasterEffectType.Blur -> {
                    val strength = settings.amount.coerceIn(0f, 1f)
                    if (strength <= .001f) source.copyOf()
                    else blurTile(snapshot, key, canvasWidth, canvasHeight,
                        radius = (strength * 10f).roundToInt().coerceAtLeast(1))
                }
                RasterEffectType.MotionBlur -> {
                    val strength = settings.amount.coerceIn(0f, 1f)
                    if (strength <= .001f) source.copyOf()
                    else motionBlurTile(snapshot, key, canvasWidth, canvasHeight,
                        distance = (strength * 24f).roundToInt().coerceAtLeast(1))
                }
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
                    val contrast = (1f + settings.amount.coerceIn(-1f, 1f) * 1.75f).coerceAtLeast(.10f)
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
                RasterEffectType.Sharpen -> sharpenTile(
                    snapshot = snapshot,
                    key = key,
                    width = canvasWidth,
                    height = canvasHeight,
                    amount = settings.amount.coerceIn(0f, 1f),
                )
                RasterEffectType.Noise -> noiseTile(
                    source = source,
                    key = key,
                    amount = settings.amount.coerceIn(0f, 1f),
                )
                RasterEffectType.Bloom -> bloomTile(
                    snapshot = snapshot,
                    key = key,
                    width = canvasWidth,
                    height = canvasHeight,
                    amount = settings.amount.coerceIn(0f, 1f),
                )
                RasterEffectType.Halftone -> halftoneTile(
                    source = source,
                    key = key,
                    amount = settings.amount.coerceIn(0f, 1f),
                )
                RasterEffectType.ChromaticAberration -> chromaticAberrationTile(
                    snapshot = snapshot,
                    key = key,
                    width = canvasWidth,
                    height = canvasHeight,
                    amount = settings.amount.coerceIn(0f, 1f),
                )
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

    private fun sharpenTile(
        snapshot: Map<TileKey, ByteArray>,
        key: TileKey,
        width: Int,
        height: Int,
        amount: Float,
    ): ByteArray {
        if (amount <= .001f) return snapshot[key]?.copyOf() ?: ByteArray(TileFormat.BYTES_PER_TILE)
        return sampledTile(snapshot, key, width, height) { x, y ->
            val center = pixel(snapshot, key.layerId, x, y, width, height) ?: intArrayOf(0, 0, 0, 0)
            val left = pixel(snapshot, key.layerId, x - 1, y, width, height) ?: center
            val right = pixel(snapshot, key.layerId, x + 1, y, width, height) ?: center
            val up = pixel(snapshot, key.layerId, x, y - 1, width, height) ?: center
            val down = pixel(snapshot, key.layerId, x, y + 1, width, height) ?: center
            val output = IntArray(4)
            for (channel in 0..2) {
                val blurred = (left[channel] + right[channel] + up[channel] + down[channel]) / 4f
                output[channel] = (center[channel] + (center[channel] - blurred) * amount * 2.4f)
                    .roundToInt().coerceIn(0, 255)
            }
            output[3] = center[3]
            output
        }
    }

    private fun noiseTile(source: ByteArray, key: TileKey, amount: Float): ByteArray {
        if (amount <= .001f) return source.copyOf()
        val output = source.copyOf()
        var pixelIndex = 0
        var offset = 0
        while (offset < output.size) {
            val alpha = output[offset + 3].toInt() and 255
            if (alpha != 0) {
                val noise = (noise01(key.x * 977 + pixelIndex, key.y * 991, 211) - .5f) * 2f
                val delta = noise * 72f * amount
                for (channel in 0..2) {
                    output[offset + channel] = ((source[offset + channel].toInt() and 255) + delta)
                        .roundToInt().coerceIn(0, 255).toByte()
                }
            }
            pixelIndex++
            offset += 4
        }
        return output
    }

    private fun bloomTile(
        snapshot: Map<TileKey, ByteArray>,
        key: TileKey,
        width: Int,
        height: Int,
        amount: Float,
    ): ByteArray {
        if (amount <= .001f) return snapshot[key]?.copyOf() ?: ByteArray(TileFormat.BYTES_PER_TILE)
        val radius = (1 + amount * 7f).roundToInt()
        return sampledTile(snapshot, key, width, height) { x, y ->
            val center = pixel(snapshot, key.layerId, x, y, width, height) ?: intArrayOf(0, 0, 0, 0)
            var sr = 0f
            var sg = 0f
            var sb = 0f
            var weight = 0f
            val offsets = intArrayOf(-radius, 0, radius)
            for (dy in offsets) for (dx in offsets) {
                val p = pixel(snapshot, key.layerId, x + dx, y + dy, width, height) ?: continue
                val luminance = (p[0] * .2126f + p[1] * .7152f + p[2] * .0722f) / 255f
                val glow = ((luminance - .52f) / .48f).coerceIn(0f, 1f)
                if (glow <= 0f) continue
                sr += p[0] * glow
                sg += p[1] * glow
                sb += p[2] * glow
                weight += glow
            }
            if (weight <= 0f) return@sampledTile center
            val mix = amount * .72f
            intArrayOf(
                (center[0] * (1f - mix) + (sr / weight).coerceAtMost(255f) * mix).roundToInt().coerceIn(0, 255),
                (center[1] * (1f - mix) + (sg / weight).coerceAtMost(255f) * mix).roundToInt().coerceIn(0, 255),
                (center[2] * (1f - mix) + (sb / weight).coerceAtMost(255f) * mix).roundToInt().coerceIn(0, 255),
                center[3],
            )
        }
    }

    private fun halftoneTile(source: ByteArray, key: TileKey, amount: Float): ByteArray {
        if (amount <= .001f) return source.copyOf()
        val output = source.copyOf()
        val cell = (12 - amount * 8f).roundToInt().coerceIn(4, 12)
        var pixelIndex = 0
        var offset = 0
        while (offset < output.size) {
            val alpha = source[offset + 3].toInt() and 255
            if (alpha != 0) {
                val localX = pixelIndex % TILE_SIZE_PIXELS
                val localY = pixelIndex / TILE_SIZE_PIXELS
                val globalX = key.x * TILE_SIZE_PIXELS + localX
                val globalY = key.y * TILE_SIZE_PIXELS + localY
                val r = source[offset].toInt() and 255
                val g = source[offset + 1].toInt() and 255
                val b = source[offset + 2].toInt() and 255
                val luminance = (r * .2126f + g * .7152f + b * .0722f) / 255f
                val cx = (globalX % cell) - cell / 2f
                val cy = (globalY % cell) - cell / 2f
                val distance = kotlin.math.sqrt(cx * cx + cy * cy)
                val radius = (1f - luminance) * cell * .58f
                val tone = if (distance <= radius) 0 else 255
                val mix = amount.coerceIn(0f, 1f)
                output[offset] = (r * (1f - mix) + tone * mix).roundToInt().coerceIn(0, 255).toByte()
                output[offset + 1] = (g * (1f - mix) + tone * mix).roundToInt().coerceIn(0, 255).toByte()
                output[offset + 2] = (b * (1f - mix) + tone * mix).roundToInt().coerceIn(0, 255).toByte()
            }
            pixelIndex++
            offset += 4
        }
        return output
    }

    private fun chromaticAberrationTile(
        snapshot: Map<TileKey, ByteArray>,
        key: TileKey,
        width: Int,
        height: Int,
        amount: Float,
    ): ByteArray {
        if (amount <= .001f) return snapshot[key]?.copyOf() ?: ByteArray(TileFormat.BYTES_PER_TILE)
        val shift = (1 + amount * 11f).roundToInt()
        return sampledTile(snapshot, key, width, height) { x, y ->
            val center = pixel(snapshot, key.layerId, x, y, width, height) ?: intArrayOf(0, 0, 0, 0)
            val red = pixel(snapshot, key.layerId, x + shift, y, width, height) ?: center
            val blue = pixel(snapshot, key.layerId, x - shift, y, width, height) ?: center
            intArrayOf(red[0], center[1], blue[2], center[3])
        }
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

    private fun noise01(x: Int, y: Int, salt: Int): Float {
        var value = x * 374761393 + y * 668265263 + salt * 1442695041
        value = (value xor (value ushr 13)) * 1274126177
        return ((value xor (value ushr 16)).ushr(8) and 0x00ffffff) / 16777215f
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
