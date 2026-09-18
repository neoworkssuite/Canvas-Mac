package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.brushes.PointerKind
import com.neoworksuite.neocanvas.brushes.StrokeSample
import com.neoworksuite.neocanvas.ui.normalizedPressure

/** Converts Android pointer facts into the platform-neutral brush input record. */
object AndroidInputAdapter {
    fun sample(x: Float, y: Float, timeMillis: Long, pressure: Float?, stylus: Boolean): StrokeSample = StrokeSample(
        x = x,
        y = y,
        timestampMillis = timeMillis,
        pressure = normalizedPressure(pressure),
        pointerKind = if (stylus) PointerKind.STYLUS else PointerKind.TOUCH,
    )
}
