package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

internal fun colorHex(color: Color): String = "#" + listOf(color.red, color.green, color.blue)
    .joinToString("") { (it * 255).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0') }.uppercase()

internal fun parseColorHex(text: String): Color? {
    val value = text.trim().removePrefix("#")
    if (value.length != 6 || value.any { it !in "0123456789abcdefABCDEF" }) return null
    val rgb = value.toLong(16)
    return Color(0xFF000000L or rgb)
}

internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

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
