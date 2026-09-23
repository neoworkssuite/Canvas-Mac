package com.neoworksuite.neocanvas.renderer

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class StampMaskSampler(
    private val asset: BrushAsset,
    private val scaleX: Float,
    private val scaleY: Float,
    private val angleRadians: Float,
) {
    init {
        require(scaleX > 0f && scaleY > 0f)
    }

    /** Samples bilinear coverage at stamp-local coordinates where each unscaled axis is -1..1. */
    fun coverage(localX: Float, localY: Float): Float {
        val c = cos(angleRadians)
        val s = sin(angleRadians)
        val rotatedX = (localX * c + localY * s) / scaleX
        val rotatedY = (-localX * s + localY * c) / scaleY
        if (rotatedX !in -1f..1f || rotatedY !in -1f..1f) return 0f
        val px = (rotatedX + 1f) * .5f * (asset.width - 1)
        val py = (rotatedY + 1f) * .5f * (asset.height - 1)
        val x0 = floor(px).toInt().coerceIn(0, asset.width - 1)
        val y0 = floor(py).toInt().coerceIn(0, asset.height - 1)
        val x1 = (x0 + 1).coerceAtMost(asset.width - 1)
        val y1 = (y0 + 1).coerceAtMost(asset.height - 1)
        val fx = px - x0
        val fy = py - y0
        fun value(x: Int, y: Int) = (asset.coverage[y * asset.width + x].toInt() and 255) / 255f
        val top = value(x0, y0) * (1f - fx) + value(x1, y0) * fx
        val bottom = value(x0, y1) * (1f - fx) + value(x1, y1) * fx
        return top * (1f - fy) + bottom * fy
    }
}

data class StampWorkMetrics(
    val samples: Int,
    val subStamps: Int,
    val maskPixelsVisited: Long,
    val lastPoint: RasterPoint?,
)
