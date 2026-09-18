package com.neoworksuite.neocanvas.brushes

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Guardrail against allocating an unbounded sample list for malformed input. */
const val MAX_INTERPOLATED_SAMPLES: Int = 100_000

/**
 * Generates samples after [from], with no gap greater than [spacing], and
 * always returns [to] as the last sample.  The caller retains [from] as the
 * already-emitted sample for a continuous stroke.
 */
fun interpolate(from: StrokeSample, to: StrokeSample, spacing: Float): List<StrokeSample> {
    require(spacing.isFinite() && spacing > 0f) { "Stroke spacing must be positive and finite." }

    val deltaX = to.x.toDouble() - from.x.toDouble()
    val deltaY = to.y.toDouble() - from.y.toDouble()
    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
    if (distance == 0.0) return listOf(to)
    require(distance.isFinite()) { "Stroke sample distance must be finite." }

    val requestedSampleCount = ceil(distance / spacing.toDouble())
    require(requestedSampleCount.isFinite() && requestedSampleCount <= MAX_INTERPOLATED_SAMPLES.toDouble()) {
        "Stroke interpolation exceeds $MAX_INTERPOLATED_SAMPLES samples."
    }

    val exactSpacingPositionCount = floor(distance / spacing.toDouble()).toInt()
    val samples = ArrayList<StrokeSample>(requestedSampleCount.toInt())
    for (index in 1..exactSpacingPositionCount) {
        val fraction = (index.toDouble() * spacing.toDouble() / distance).toFloat()
        if (fraction >= 1f) {
            samples += to
            break
        }
        samples += StrokeSample(
            x = lerp(from.x, to.x, fraction),
            y = lerp(from.y, to.y, fraction),
            timestampMillis = lerp(from.timestampMillis, to.timestampMillis, fraction),
            pressure = lerp(from.pressure, to.pressure, fraction),
            tiltX = lerp(from.tiltX, to.tiltX, fraction),
            tiltY = lerp(from.tiltY, to.tiltY, fraction),
            pointerKind = to.pointerKind,
        )
    }
    if (samples.isEmpty() || samples.last() != to) {
        samples += to
    }
    return samples
}

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

private fun lerp(from: Long, to: Long, fraction: Float): Long =
    (from.toDouble() + (to.toDouble() - from.toDouble()) * fraction.toDouble()).toLong()
