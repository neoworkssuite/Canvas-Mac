package com.neoworksuite.neocanvas.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class QuickShapeType(val label: String) {
    Line("Line"),
    Circle("Circle"),
    Triangle("Triangle"),
    Square("Square"),
}

internal data class QuickShapeResult(
    val type: QuickShapeType,
    val points: List<DrawPoint>,
)

/**
 * Recognises deliberately simple held brush strokes and returns a clean replacement path.
 *
 * The recogniser intentionally supports only the four predictable shapes exposed by NeoCanvas.
 * Scribbles and open curves return null rather than being aggressively changed into geometry.
 */
internal fun detectQuickShape(points: List<DrawPoint>): QuickShapeResult? {
    val clean = removeNearDuplicates(points)
    if (clean.size < 4) return null

    val minX = clean.minOf { it.x }
    val maxX = clean.maxOf { it.x }
    val minY = clean.minOf { it.y }
    val maxY = clean.maxOf { it.y }
    val diagonal = hypot((maxX - minX).toDouble(), (maxY - minY).toDouble()).toFloat()
    if (!diagonal.isFinite() || diagonal < 12f) return null

    val pressure = clean.map { it.pressure }.average().toFloat().coerceIn(.05f, 1f)
    val start = clean.first()
    val end = clean.last()
    val closureDistance = distance(start, end)
    val isClosed = closureDistance <= diagonal * .30f

    if (!isClosed) {
        val lineLength = distance(start, end)
        if (lineLength < diagonal * .72f) return null
        val maximumDeviation = clean.maxOf { distanceToInfiniteLine(it, start, end) }
        if (maximumDeviation > diagonal * .13f) return null
        return QuickShapeResult(
            QuickShapeType.Line,
            interpolatePolyline(listOf(start.copy(pressure = pressure), end.copy(pressure = pressure)), pressure),
        )
    }

    val cornerIndices = strongCornerIndices(clean)
    if (cornerIndices.size == 3) {
        val corners = cornerIndices.map { clean[it].copy(pressure = pressure) }
        return QuickShapeResult(
            QuickShapeType.Triangle,
            interpolatePolyline(corners + corners.first(), pressure),
        )
    }

    if (cornerIndices.size == 4) {
        val roughCorners = cornerIndices.map { clean[it] }
        return QuickShapeResult(
            QuickShapeType.Square,
            perfectSquare(roughCorners, pressure),
        )
    }

    // Closed strokes without three/four decisive corners become circles only when they
    // surround their centre reasonably evenly. This prevents loops and scribbles snapping.
    val centerX = (minX + maxX) / 2f
    val centerY = (minY + maxY) / 2f
    val radialDistances = clean.map { hypot((it.x - centerX).toDouble(), (it.y - centerY).toDouble()).toFloat() }
    val radialMean = radialDistances.average().toFloat()
    if (radialMean <= 1f) return null
    val radialDeviation = sqrt(
        radialDistances.sumOf { value ->
            val d = value - radialMean
            (d * d).toDouble()
        } / radialDistances.size,
    ).toFloat() / radialMean
    if (radialDeviation > .28f) return null

    val radius = (((maxX - minX) + (maxY - minY)) / 4f).coerceAtLeast(6f)
    val circle = buildList {
        val samples = 72
        for (i in 0..samples) {
            val angle = i.toFloat() / samples * (PI.toFloat() * 2f)
            add(
                DrawPoint(
                    x = centerX + cos(angle) * radius,
                    y = centerY + sin(angle) * radius,
                    pressure = pressure,
                ),
            )
        }
    }
    return QuickShapeResult(QuickShapeType.Circle, circle)
}

private fun removeNearDuplicates(points: List<DrawPoint>): List<DrawPoint> {
    if (points.isEmpty()) return emptyList()
    return buildList {
        add(points.first())
        points.drop(1).forEach { point ->
            if (distance(last(), point) >= 1.5f) add(point)
        }
    }
}

private fun strongCornerIndices(points: List<DrawPoint>): List<Int> {
    val count = points.size
    if (count < 8) return emptyList()
    val step = (count / 36).coerceAtLeast(2)
    val strengths = FloatArray(count)

    for (index in points.indices) {
        val before = points[(index - step + count) % count]
        val current = points[index]
        val after = points[(index + step) % count]
        val ax = current.x - before.x
        val ay = current.y - before.y
        val bx = after.x - current.x
        val by = after.y - current.y
        val aLength = hypot(ax.toDouble(), ay.toDouble()).toFloat()
        val bLength = hypot(bx.toDouble(), by.toDouble()).toFloat()
        if (aLength < 2f || bLength < 2f) continue
        val dot = ((ax * bx + ay * by) / (aLength * bLength)).coerceIn(-1f, 1f)
        strengths[index] = acos(dot)
    }

    val candidates = points.indices
        .filter { strengths[it] >= .95f } // about 54 degrees: circles stay below this at the chosen sampling span.
        .filter { index ->
            val radius = step.coerceAtLeast(2)
            (-radius..radius).all { delta ->
                delta == 0 || strengths[index] >= strengths[(index + delta + count) % count]
            }
        }
        .sortedByDescending { strengths[it] }

    val minimumSeparation = (count / 9).coerceAtLeast(2)
    val selected = mutableListOf<Int>()
    candidates.forEach { candidate ->
        val separated = selected.all { existing ->
            val raw = abs(candidate - existing)
            minOf(raw, count - raw) >= minimumSeparation
        }
        if (separated) selected += candidate
        if (selected.size == 5) return@forEach
    }

    return when (selected.size) {
        3, 4 -> selected.sorted()
        else -> emptyList()
    }
}

private fun perfectSquare(corners: List<DrawPoint>, pressure: Float): List<DrawPoint> {
    require(corners.size == 4)
    val centerX = corners.map { it.x }.average().toFloat()
    val centerY = corners.map { it.y }.average().toFloat()
    val side = corners.indices.map { index ->
        distance(corners[index], corners[(index + 1) % corners.size])
    }.average().toFloat().coerceAtLeast(4f)

    // Use the first detected side for orientation, but force all four sides/every angle exact.
    val dx = corners[1].x - corners[0].x
    val dy = corners[1].y - corners[0].y
    val orientation = atan2(dy, dx)
    val ux = cos(orientation)
    val uy = sin(orientation)
    val vx = -uy
    val vy = ux
    val half = side / 2f

    fun corner(u: Float, v: Float) = DrawPoint(
        x = centerX + ux * u + vx * v,
        y = centerY + uy * u + vy * v,
        pressure = pressure,
    )

    val exact = listOf(
        corner(-half, -half),
        corner(half, -half),
        corner(half, half),
        corner(-half, half),
    )
    return interpolatePolyline(exact + exact.first(), pressure)
}

private fun interpolatePolyline(vertices: List<DrawPoint>, pressure: Float): List<DrawPoint> {
    if (vertices.size < 2) return vertices
    return buildList {
        add(vertices.first().copy(pressure = pressure))
        vertices.zipWithNext().forEach { (from, to) ->
            val length = distance(from, to)
            val steps = (length / 6f).toInt().coerceIn(1, 512)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                add(
                    DrawPoint(
                        x = from.x + (to.x - from.x) * t,
                        y = from.y + (to.y - from.y) * t,
                        pressure = pressure,
                    ),
                )
            }
        }
    }
}

private fun distance(a: DrawPoint, b: DrawPoint): Float =
    hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()

private fun distanceToInfiniteLine(point: DrawPoint, a: DrawPoint, b: DrawPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val denominator = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(.001f)
    return abs(dy * point.x - dx * point.y + b.x * a.y - b.y * a.x) / denominator
}
