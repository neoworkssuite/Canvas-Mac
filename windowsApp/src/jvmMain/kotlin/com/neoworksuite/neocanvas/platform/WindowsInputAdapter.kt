package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.brushes.PointerKind
import com.neoworksuite.neocanvas.brushes.StrokeSample
import com.neoworksuite.neocanvas.ui.normalizedPressure

/** Converts Windows mouse/pen events supplied by the desktop host into shared brush input. */
object WindowsInputAdapter {
    fun sample(x: Float, y: Float, timeMillis: Long, pressure: Float?, pen: Boolean): StrokeSample = StrokeSample(
        x = x,
        y = y,
        timestampMillis = timeMillis,
        pressure = normalizedPressure(pressure),
        pointerKind = if (pen) PointerKind.STYLUS else PointerKind.MOUSE,
    )
}
