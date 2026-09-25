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

    @Test fun imported_faces_merge_by_family_and_expose_styles_without_duplicates() {
        val choices = textFontChoices(listOf(
            ImportedFontFace("acme-regular", "Acme", "Regular", "Acme.ttf"),
            ImportedFontFace("acme-bold", "Acme", "Bold", "Acme-Bold.ttf"),
            ImportedFontFace("acme-bold-copy", "Acme", "Bold", "Copy.ttf"),
        ))
        val acme = choices.single { it.name == "Acme" }
        assertTrue(acme.imported)
        assertEquals(listOf("Bold", "Regular"), acme.styles.sorted())
    }

    @Test fun missing_font_keeps_requested_identity_and_resolves_to_system() {
        val resolved = resolveTextFontChoice("Removed Family", "Black", emptyList())
        assertTrue(resolved.missing)
        assertEquals("Removed Family", resolved.requestedFamily)
        assertEquals("Black", resolved.requestedStyle)
        assertEquals("System", resolved.renderFamily)
    }
}
