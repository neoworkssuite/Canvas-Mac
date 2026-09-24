package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

data class LineMetrics(val length: Float, val angleDegrees: Float)

fun lineMetrics(shape: LayerPayload.ShapeObject): LineMetrics {
    require(shape.kind == ShapeKind.Line) { "Line metrics require a line shape." }
    return LineMetrics(
        length = hypot(shape.width, shape.height),
        angleDegrees = normalizedAngle(atan2(shape.height, shape.width) * 180f / PI.toFloat()),
    )
}

fun lineWithLength(shape: LayerPayload.ShapeObject, length: Float): LayerPayload.ShapeObject {
    if (shape.kind != ShapeKind.Line || !length.isFinite() || length <= 0f) return shape
    val current = lineMetrics(shape)
    if (current.length <= 0f) return shape
    val scale = length / current.length
    return shape.copy(width = shape.width * scale, height = shape.height * scale)
}

fun lineWithAngle(
    shape: LayerPayload.ShapeObject,
    angleDegrees: Float,
    snap: Boolean = shape.angleSnapping,
): LayerPayload.ShapeObject {
    if (shape.kind != ShapeKind.Line || !angleDegrees.isFinite()) return shape
    val length = lineMetrics(shape).length
    if (length <= 0f) return shape
    val angle = snappedAngle(angleDegrees, snap)
    val radians = angle * PI.toFloat() / 180f
    return shape.copy(
        width = cos(radians) * length,
        height = sin(radians) * length,
    )
}

fun lineWithEndpoint(
    shape: LayerPayload.ShapeObject,
    moveStart: Boolean,
    x: Float,
    y: Float,
): LayerPayload.ShapeObject {
    if (shape.kind != ShapeKind.Line || !x.isFinite() || !y.isFinite()) return shape
    val endX = shape.x + shape.width
    val endY = shape.y + shape.height
    val next = if (moveStart) {
        shape.copy(x = x, y = y, width = endX - x, height = endY - y)
    } else {
        shape.copy(width = x - shape.x, height = y - shape.y)
    }
    return if (next.width == 0f && next.height == 0f) shape else next
}

fun reversedLine(shape: LayerPayload.ShapeObject): LayerPayload.ShapeObject {
    if (shape.kind != ShapeKind.Line) return shape
    return shape.copy(
        x = shape.x + shape.width,
        y = shape.y + shape.height,
        width = -shape.width,
        height = -shape.height,
        startMarker = shape.endMarker,
        endMarker = shape.startMarker,
    )
}

internal fun normalizedAngle(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

internal fun snappedAngle(degrees: Float, enabled: Boolean): Float {
    val normalized = normalizedAngle(degrees)
    return if (enabled) normalizedAngle((normalized / 15f).roundToInt() * 15f) else normalized
}
