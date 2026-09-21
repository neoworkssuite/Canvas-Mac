package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FourFingerCanvasModeTest {
    @Test
    fun four_finger_shortcut_requires_a_quick_stable_touch_gesture() {
        assertTrue(isFourFingerCanvasToggle(
            maxTouchCount = 4,
            durationMillis = 220,
            touchTravel = 18f,
            touchSlop = 10f,
            stylusSeen = false,
        ))
        assertFalse(isFourFingerCanvasToggle(3, 220, 18f, 10f, false))
        assertFalse(isFourFingerCanvasToggle(4, 700, 18f, 10f, false))
        assertFalse(isFourFingerCanvasToggle(4, 220, 100f, 10f, false))
        assertFalse(isFourFingerCanvasToggle(4, 220, 18f, 10f, true))
    }

    @Test
    fun canvas_only_mode_toggles_and_explains_how_to_exit() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(128, 128, "fullscreen")))
        assertFalse(state.canvasOnlyMode)

        state.toggleCanvasOnlyMode()
        assertTrue(state.canvasOnlyMode)
        assertTrue(state.statusMessage?.contains("four-finger") == true)

        state.toggleCanvasOnlyMode()
        assertFalse(state.canvasOnlyMode)
    }
}
