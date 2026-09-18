package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class VerticalRailTest {
    @Test
    fun vertical_slider_maps_top_to_max_and_bottom_to_min() {
        assertEquals(96f, verticalValueFromY(0f, 200f, 1f..96f))
        assertEquals(1f, verticalValueFromY(200f, 200f, 1f..96f))
        assertEquals(48.5f, verticalValueFromY(100f, 200f, 1f..96f))
    }

    @Test
    fun vertical_slider_clamps_pointer_outside_track() {
        assertEquals(1f, verticalValueFromY(400f, 200f, 1f..96f))
        assertEquals(96f, verticalValueFromY(-20f, 200f, 1f..96f))
    }
}
