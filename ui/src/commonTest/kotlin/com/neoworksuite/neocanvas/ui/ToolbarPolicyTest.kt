package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolbarPolicyTest {
    @Test
    fun colour_button_description_reports_active_colour_and_action() {
        assertEquals(
            "Colour #336699. Open Colour Studio",
            colourButtonDescription(Color(0xFF336699)),
        )
    }
}
