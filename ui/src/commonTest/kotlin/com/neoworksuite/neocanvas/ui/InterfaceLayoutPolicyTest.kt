package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class InterfaceLayoutPolicyTest {
    @Test
    fun automatic_and_right_handed_modes_keep_rail_left_and_panels_right() {
        val expected = WorkspacePlacement(railAtStart = true, panelsAtEnd = true)
        assertEquals(expected, workspacePlacement(InterfaceSide.Automatic, compact = false))
        assertEquals(expected, workspacePlacement(InterfaceSide.Right, compact = false))
        assertEquals(expected, workspacePlacement(InterfaceSide.Right, compact = true))
    }

    @Test
    fun left_handed_mode_places_rail_right_and_panels_left() {
        val expected = WorkspacePlacement(railAtStart = false, panelsAtEnd = false)
        assertEquals(expected, workspacePlacement(InterfaceSide.Left, compact = false))
        assertEquals(expected, workspacePlacement(InterfaceSide.Left, compact = true))
    }
}
