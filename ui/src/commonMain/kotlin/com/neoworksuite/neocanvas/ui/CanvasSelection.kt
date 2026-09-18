package com.neoworksuite.neocanvas.ui

import kotlin.math.ceil
import kotlin.math.floor

enum class SelectionShape { Rectangle, Ellipse, Lasso }

/** Pixel selection bounds plus an optional non-rectangular membership test. Right/bottom are exclusive. */
data class CanvasSelection(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val shape: SelectionShape = SelectionShape.Rectangle,
    val points: List<DrawPoint> = emptyList(),
    val invertedRegion: CanvasSelection? = null,
) {
    fun contains(x: Int, y: Int): Boolean {
        if (x !in left until right || y !in top until bottom) return false
        invertedRegion?.let { return !it.contains(x, y) }
        return when (shape) {
            SelectionShape.Rectangle -> true
            SelectionShape.Ellipse -> {
                val cx = (left + right) / 2.0
                val cy = (top + bottom) / 2.0
                val rx = (right - left) / 2.0
                val ry = (bottom - top) / 2.0
                val dx = (x + .5 - cx) / rx
                val dy = (y + .5 - cy) / ry
                dx * dx + dy * dy <= 1.0
            }
            SelectionShape.Lasso -> pointInPolygon(x + .5f, y + .5f, points)
        }
    }

    companion object {
        fun ellipse(left: Int, top: Int, right: Int, bottom: Int) =
            CanvasSelection(left, top, right, bottom, SelectionShape.Ellipse)

        fun lasso(points: List<DrawPoint>): CanvasSelection? {
            if (points.size < 3) return null
            val left = floor(points.minOf { it.x }).toInt()
            val top = floor(points.minOf { it.y }).toInt()
            val right = ceil(points.maxOf { it.x }).toInt() + 1
            val bottom = ceil(points.maxOf { it.y }).toInt() + 1
            if (left >= right || top >= bottom) return null
            return CanvasSelection(left, top, right, bottom, SelectionShape.Lasso, points.toList())
        }
    }
}

private fun pointInPolygon(x: Float, y: Float, points: List<DrawPoint>): Boolean {
    var inside = false
    var previous = points.last()
    points.forEach { current ->
        if ((current.y > y) != (previous.y > y)) {
            val crossing = (previous.x - current.x) * (y - current.y) / (previous.y - current.y) + current.x
            if (x < crossing) inside = !inside
        }
        previous = current
    }
    return inside
}
