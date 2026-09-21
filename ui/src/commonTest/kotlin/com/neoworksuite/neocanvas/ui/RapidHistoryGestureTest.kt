package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RapidHistoryGestureTest {
    @Test
    fun rapid_history_hold_only_arms_for_stable_two_or_three_finger_gestures() {
        assertTrue(shouldArmRapidHistoryGesture(
            fingerCount = 2,
            touchTravel = 8f,
            touchSlop = 10f,
            stylusSeen = false,
            transformStarted = false,
        ))
        assertTrue(shouldArmRapidHistoryGesture(3, 12f, 10f, false, false))

        assertFalse(shouldArmRapidHistoryGesture(1, 0f, 10f, false, false))
        assertFalse(shouldArmRapidHistoryGesture(4, 0f, 10f, false, false))
        assertFalse(shouldArmRapidHistoryGesture(2, 40f, 10f, false, false))
        assertFalse(shouldArmRapidHistoryGesture(2, 8f, 10f, true, false))
        assertFalse(shouldArmRapidHistoryGesture(2, 8f, 10f, false, true))
    }
}
