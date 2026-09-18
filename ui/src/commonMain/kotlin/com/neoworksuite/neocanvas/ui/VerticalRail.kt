package com.neoworksuite.neocanvas.ui

import kotlin.math.max

fun verticalValueFromY(y: Float, height: Float, range: ClosedFloatingPointRange<Float>): Float {
    if (height <= 0f) return range.start
    val fraction = (1f - y / max(height, 1f)).coerceIn(0f, 1f)
    return range.start + (range.endInclusive - range.start) * fraction
}
