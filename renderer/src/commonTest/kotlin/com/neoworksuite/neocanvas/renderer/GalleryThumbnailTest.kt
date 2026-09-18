package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryThumbnailTest {
    @Test fun wide_canvas_fits_inside_thumbnail_without_changing_aspect_ratio() {
        val image = GalleryThumbnail.render(CanvasDocument.blank(640, 320), emptyMap(), 320, 240)
        assertEquals(320, image.width)
        assertEquals(160, image.height)
    }
}
