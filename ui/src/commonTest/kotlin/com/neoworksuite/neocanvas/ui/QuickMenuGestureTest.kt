package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickMenuGestureTest {
    @Test
    fun quick_menu_hold_is_reserved_for_non_painting_finger_on_pencil_tools() {
        assertTrue(shouldArmQuickMenu(
            isStylus = false,
            fingerPaintingEnabled = false,
            tool = Tool.Brush,
            objectArrangePicking = false,
        ))
        assertTrue(shouldArmQuickMenu(false, false, Tool.Eraser, false))
        assertTrue(shouldArmQuickMenu(false, false, Tool.Smudge, false))
        assertTrue(shouldArmQuickMenu(false, false, Tool.Liquify, false))

        assertFalse(shouldArmQuickMenu(true, false, Tool.Brush, false))
        assertFalse(shouldArmQuickMenu(false, true, Tool.Brush, false))
        assertFalse(shouldArmQuickMenu(false, false, Tool.Pan, false))
        assertFalse(shouldArmQuickMenu(false, false, Tool.Brush, true))
    }

    @Test
    fun stylus_and_inverted_eraser_are_both_pen_input() {
        assertTrue(PointerType.Stylus.isPenPointer())
        assertTrue(PointerType.Eraser.isPenPointer())
        assertFalse(PointerType.Mouse.isPenPointer())
        assertFalse(PointerType.Touch.isPenPointer())
    }
}
