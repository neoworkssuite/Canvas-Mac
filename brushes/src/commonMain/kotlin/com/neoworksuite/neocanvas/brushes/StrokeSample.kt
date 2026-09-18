package com.neoworksuite.neocanvas.brushes

/** The input device category supplied by an Android or Windows input adapter. */
enum class PointerKind {
    MOUSE,
    TOUCH,
    STYLUS,
}

/** A platform-neutral input point in document pixel coordinates. */
data class StrokeSample(
    val x: Float,
    val y: Float,
    val timestampMillis: Long,
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val pointerKind: PointerKind = PointerKind.MOUSE,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Stroke coordinates must be finite." }
        require(pressure.isFinite() && pressure in 0f..1f) { "Stroke pressure must be between 0 and 1." }
        require(tiltX.isFinite() && tiltY.isFinite()) { "Stroke tilt must be finite." }
    }
}
