package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewCanvasDialogTest {
    @Test
    fun custom_canvas_size_accepts_supported_dimensions() {
        assertTrue(isValidCustomCanvasSize(3000, 2000))
        assertTrue(isValidCustomCanvasSize(4000, 4000))
    }

    @Test
    fun custom_canvas_size_rejects_invalid_edges_and_excessive_pixel_count() {
        assertFalse(isValidCustomCanvasSize(0, 100))
        assertFalse(isValidCustomCanvasSize(8193, 100))
        assertFalse(isValidCustomCanvasSize(5000, 4000))
    }
}
