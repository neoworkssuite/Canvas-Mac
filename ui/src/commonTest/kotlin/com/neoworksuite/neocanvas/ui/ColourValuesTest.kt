package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColourValuesTest {
    @Test fun hex_accepts_rgb_and_rejects_invalid_input() {
        assertEquals("#12ABEF", colorHex(parseColorHex(" #12abef ")!!))
        assertEquals(Color.Black, parseColorHex("000000"))
        assertNull(parseColorHex("#FFFF"))
        assertNull(parseColorHex("#GG0000"))
        assertNull(parseColorHex(""))
    }
    @Test fun hsv_round_trip_preserves_colours_including_grey_and_black() {
        listOf(Color.Red, Color.Green, Color.Blue, Color.Black, Color.White, Color(.2f, .5f, .8f)).forEach {
            val hsv = colorHsv(it)
            val actual = Color.hsv(hsv.hue, hsv.saturation, hsv.value)
            assertTrue(kotlin.math.abs(actual.red - it.red) < .001f)
            assertTrue(kotlin.math.abs(actual.green - it.green) < .001f)
            assertTrue(kotlin.math.abs(actual.blue - it.blue) < .001f)
        }
    }
}
