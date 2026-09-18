package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.renderer.RasterMove
import kotlin.math.roundToInt

data class TransformSession(
    val sourceBounds: CanvasSelection,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
) {
    init {
        require(translationX.isFinite() && translationY.isFinite())
        require(scale.isFinite() && scale > 0f)
        require(rotationDegrees.isFinite())
    }

    fun targetBounds(canvasWidth: Int, canvasHeight: Int): CanvasSelection? {
        val sourceWidth = sourceBounds.right - sourceBounds.left
        val sourceHeight = sourceBounds.bottom - sourceBounds.top
        val rotated = RasterMove.rotatedSize(sourceWidth, sourceHeight, rotationDegrees)
        val width = (rotated.first * scale).roundToInt().coerceAtLeast(1)
        val height = (rotated.second * scale).roundToInt().coerceAtLeast(1)
        if (width > canvasWidth || height > canvasHeight) return null
        val centerX = (sourceBounds.left + sourceBounds.right) / 2f + translationX
        val centerY = (sourceBounds.top + sourceBounds.bottom) / 2f + translationY
        val left = (centerX - width / 2f).roundToInt().coerceIn(0, canvasWidth - width)
        val top = (centerY - height / 2f).roundToInt().coerceIn(0, canvasHeight - height)
        return CanvasSelection(left, top, left + width, top + height)
    }
}
