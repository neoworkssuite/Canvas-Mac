package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

internal fun colorHex(color: Color): String = "#" + listOf(color.red, color.green, color.blue)
    .joinToString("") { (it * 255).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0') }.uppercase()

internal fun parseColorHex(text: String): Color? {
    val value = text.trim().removePrefix("#")
    if (value.length != 6 || value.any { it !in "0123456789abcdefABCDEF" }) return null
    val rgb = value.toLong(16)
    return Color(0xFF000000L or rgb)
}

internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

internal enum class ColourStudioMode(val label: String) {
    Disc("DISC"), Classic("CLASSIC"), Harmony("HARMONY"), Value("VALUE"), Palettes("PALETTES");

    companion object {
        fun stored(value: String?): ColourStudioMode = entries.firstOrNull { it.name == value } ?: Disc
    }
}

internal class ColourDiscZoomState(initial: Float = 1f) {
    var scale: Float by mutableFloatStateOf(initial.coerceIn(1f, 2.5f))
        private set
    fun zoomBy(factor: Float) { scale = (scale * factor).coerceIn(1f, 2.5f) }
    fun reset() { scale = 1f }
}

/** Snaps only when a colour is already close to a useful artist target. */
internal fun snapColourDisc(hsv: Hsv): Hsv {
    val candidates = listOf(
        Hsv(hsv.hue, 0f, 1f), Hsv(hsv.hue, 0f, 0f), Hsv(hsv.hue, 0f, .5f),
        Hsv(hsv.hue, .5f, hsv.value), Hsv(hsv.hue, 1f, hsv.value),
    )
    fun distance(a: Hsv, b: Hsv): Float {
        val ds = a.saturation - b.saturation
        val dv = a.value - b.value
        return ds * ds + dv * dv
    }
    val nearest = candidates.minBy { distance(hsv, it) }
    return if (distance(hsv, nearest) <= .025f) nearest else hsv
}

internal data class ColourReticlePosition(val x: Float, val y: Float)

internal fun harmonyReticlePositions(hue: Float, harmony: ColourHarmony): List<ColourReticlePosition> =
    harmonyHues(hue, harmony).map { angle ->
        val radians = angle * kotlin.math.PI.toFloat() / 180f
        ColourReticlePosition(cos(radians), sin(radians))
    }

internal fun colorHsv(color: Color): Hsv {
    val hi = maxOf(color.red, color.green, color.blue)
    val lo = minOf(color.red, color.green, color.blue)
    val delta = hi - lo
    val hue = when {
        delta == 0f -> 0f
        hi == color.red -> 60f * ((color.green - color.blue) / delta)
        hi == color.green -> 60f * ((color.blue - color.red) / delta + 2f)
        else -> 60f * ((color.red - color.green) / delta + 4f)
    }
    return Hsv((hue + 360f) % 360f, if (hi == 0f) 0f else delta / hi, hi)
}


internal enum class ColourHarmony(val label: String) {
    Complementary("Complementary"),
    SplitComplementary("Split Complementary"),
    Analogous("Analogous"),
    Triadic("Triadic"),
    Tetradic("Tetradic"),
}

internal fun harmonyHues(hue: Float, harmony: ColourHarmony): List<Float> {
    val offsets = when (harmony) {
        ColourHarmony.Complementary -> listOf(0f, 180f)
        ColourHarmony.SplitComplementary -> listOf(0f, 150f, 210f)
        ColourHarmony.Analogous -> listOf(-30f, 0f, 30f)
        ColourHarmony.Triadic -> listOf(0f, 120f, 240f)
        ColourHarmony.Tetradic -> listOf(0f, 90f, 180f, 270f)
    }
    return offsets.map { offset -> ((hue + offset) % 360f + 360f) % 360f }
}
