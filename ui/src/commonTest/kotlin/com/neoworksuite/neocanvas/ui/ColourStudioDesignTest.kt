package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColourStudioDesignTest {
    @Test
    fun starter_palettes_are_named_square_swatch_collections() {
        assertEquals(listOf("Essential", "Portrait", "Landscape", "Neo Neon"), starterColourPalettes.map { it.name })
        assertTrue(starterColourPalettes.all { it.colours.size == 8 })
        assertTrue(starterColourPalettes.flatMap { it.colours }.all { parseColorHex(it) != null })
        assertEquals(6f, paletteSwatchCornerRadius.value)
    }

    @Test
    fun colour_studio_uses_a_compact_desktop_window() {
        val regular = colourStudioBounds(compact = false)
        assertEquals(.36f, regular.widthFraction)
        assertEquals(.66f, regular.heightFraction)
        assertEquals(380f, regular.maxWidth.value)
        assertEquals(520f, regular.maxHeight.value)

        val compact = colourStudioBounds(compact = true)
        assertEquals(.94f, compact.widthFraction)
        assertEquals(.60f, compact.heightFraction)
    }
}
