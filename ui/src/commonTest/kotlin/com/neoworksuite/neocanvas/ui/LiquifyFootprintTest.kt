package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class LiquifyFootprintTest {
    @Test
    fun footprint_radius_tracks_size_and_normalized_pressure() {
        assertEquals(50f, liquifyFootprintRadius(100f, 1f), .001f)
        assertEquals(25f, liquifyFootprintRadius(100f, .5f), .001f)
        assertEquals(50f, liquifyFootprintRadius(100f, 2f), .001f)
    }

    @Test
    fun footprint_keeps_a_visible_minimum_radius() {
        assertEquals(.75f, liquifyFootprintRadius(.1f, .05f), .001f)
    }
}
