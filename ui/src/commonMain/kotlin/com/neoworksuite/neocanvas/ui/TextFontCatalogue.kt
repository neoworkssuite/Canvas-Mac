package com.neoworksuite.neocanvas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font
import com.neoworksuite.neocanvas.ui.resources.Res
import com.neoworksuite.neocanvas.ui.resources.caveat_variable
import com.neoworksuite.neocanvas.ui.resources.inter_variable
import com.neoworksuite.neocanvas.ui.resources.jetbrains_mono_variable
import com.neoworksuite.neocanvas.ui.resources.lora_variable
import com.neoworksuite.neocanvas.ui.resources.noto_sans_variable
import com.neoworksuite.neocanvas.ui.resources.playfair_display_variable

internal data class TextFontChoice(
    val name: String,
    val category: String,
    val bundled: Boolean = true,
)

internal val neoCanvasTextFonts = listOf(
    TextFontChoice("System", "System", bundled = false),
    TextFontChoice("Sans", "System", bundled = false),
    TextFontChoice("Serif", "System", bundled = false),
    TextFontChoice("Mono", "System", bundled = false),
    TextFontChoice("Inter", "Modern"),
    TextFontChoice("Noto Sans", "International"),
    TextFontChoice("Lora", "Editorial"),
    TextFontChoice("Playfair Display", "Display"),
    TextFontChoice("Caveat", "Handwriting"),
    TextFontChoice("JetBrains Mono", "Monospaced"),
)

internal fun filteredTextFonts(query: String): List<TextFontChoice> {
    val term = query.trim()
    return if (term.isEmpty()) neoCanvasTextFonts else neoCanvasTextFonts.filter {
        it.name.contains(term, ignoreCase = true) || it.category.contains(term, ignoreCase = true)
    }
}

@Composable
internal fun rememberNeoCanvasFontFamilies(): Map<String, FontFamily> {
    val inter = FontFamily(Font(Res.font.inter_variable))
    val notoSans = FontFamily(Font(Res.font.noto_sans_variable))
    val lora = FontFamily(Font(Res.font.lora_variable))
    val playfair = FontFamily(Font(Res.font.playfair_display_variable))
    val caveat = FontFamily(Font(Res.font.caveat_variable))
    val jetBrains = FontFamily(Font(Res.font.jetbrains_mono_variable))
    return remember(inter, notoSans, lora, playfair, caveat, jetBrains) {
        mapOf(
            "inter" to inter,
            "noto sans" to notoSans,
            "lora" to lora,
            "playfair display" to playfair,
            "caveat" to caveat,
            "jetbrains mono" to jetBrains,
        )
    }
}

internal fun editableTextFontFamily(name: String, bundled: Map<String, FontFamily>): FontFamily =
    bundled[name.trim().lowercase()] ?: when (name.trim().lowercase()) {
        "sans", "sans-serif", "sans serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono", "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
