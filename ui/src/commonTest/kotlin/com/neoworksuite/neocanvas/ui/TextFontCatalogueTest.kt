package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextFontCatalogueTest {
    @Test fun catalogue_names_are_unique_and_search_matches_name_or_category() {
        assertEquals(neoCanvasTextFonts.size, neoCanvasTextFonts.map { it.name.lowercase() }.distinct().size)
        assertEquals(listOf("Noto Sans"), filteredTextFonts("international").map { it.name })
        assertTrue(filteredTextFonts("mono").map { it.name }.containsAll(listOf("Mono", "JetBrains Mono")))
    }
}
