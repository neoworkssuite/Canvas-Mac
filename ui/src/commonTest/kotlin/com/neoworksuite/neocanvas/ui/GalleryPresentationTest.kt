package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GalleryPresentationTest {
    @Test
    fun gallery_has_no_kids_destination_or_placeholder_copy() {
        assertFalse("Kids" in galleryPrimaryActionLabels())
        assertEquals("Create a canvas. Everything stays local.", galleryEmptyStateMessage())
    }
}
