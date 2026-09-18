package com.neoworksuite.neocanvas.renderer

/** Causal smoothing: extending a stroke never changes its earlier filtered points. */
fun smoothStroke(points: List<RasterPoint>, amount: Float): List<RasterPoint> {
    require(amount.isFinite() && amount in 0f..1f)
    if (amount == 0f || points.size < 2) return points
    val response = 1f - amount * .85f
    var previous = points.first()
    return buildList {
        add(previous)
        points.drop(1).forEach { point ->
            previous = RasterPoint(previous.x + (point.x - previous.x) * response,
                previous.y + (point.y - previous.y) * response, point.pressure)
            add(previous)
        }
    }
}
