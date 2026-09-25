package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.brushes.PointerKind
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsInputAdapterTest {
    @Test
    fun mouse_defaults_to_full_pressure_and_mouse_kind() {
        val sample = WindowsInputAdapter.sample(1f, 2f, 3L, pressure = null, pen = false)
        assertEquals(PointerKind.MOUSE, sample.pointerKind)
        assertEquals(1f, sample.pressure)
    }

    @Test
    fun pen_preserves_valid_pressure_and_stylus_kind() {
        val sample = WindowsInputAdapter.sample(1f, 2f, 3L, pressure = .37f, pen = true)
        assertEquals(PointerKind.STYLUS, sample.pointerKind)
        assertEquals(.37f, sample.pressure)
    }

    @Test
    fun invalid_pressure_falls_back_safely() {
        assertEquals(1f, WindowsInputAdapter.sample(0f, 0f, 0L, -1f, true).pressure)
        assertEquals(1f, WindowsInputAdapter.sample(0f, 0f, 0L, 2f, true).pressure)
        assertEquals(1f, WindowsInputAdapter.sample(0f, 0f, 0L, Float.NaN, true).pressure)
    }
}
