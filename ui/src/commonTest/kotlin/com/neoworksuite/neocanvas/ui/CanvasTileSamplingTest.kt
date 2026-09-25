package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.FilterQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasTileSamplingTest {
    @Test
    fun live_canvas_uses_non_interpolating_tile_sampling_to_prevent_faint_seams() {
        assertEquals(FilterQuality.None, liveCanvasTileFilterQuality())
    }
}
