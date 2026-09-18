package com.neoworksuite.neocanvas.core.gallery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GalleryModelTest {
    private fun gallery() = GallerySnapshot(
        artworks = listOf(
            GalleryArtwork("a", "Alpha", 0),
            GalleryArtwork("b", "Beta", 1),
            GalleryArtwork("c", "Gamma", 2),
        ),
    )

    @Test
    fun reorder_normalizes_positions() {
        val moved = gallery().reorderArtwork("c", 0)
        assertEquals(listOf("c", "a", "b"), moved.artworks.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.artworks.map { it.order })
    }

    @Test
    fun stack_moves_selected_artworks_without_duplicating_them() {
        val stacked = gallery().createStack("Ideas", listOf("a", "c"), "stack-1")
        assertEquals(listOf("b"), stacked.rootArtworks.map { it.id })
        assertEquals(listOf("a", "c"), stacked.artworksIn("stack-1").map { it.id })
    }

    @Test
    fun gallery_rejects_duplicate_names_case_insensitively() {
        assertFailsWith<IllegalArgumentException> { gallery().renameArtwork("b", " alpha ") }
    }
}
