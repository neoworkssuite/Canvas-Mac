package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.LayerBlendMode

/** Straight-alpha layer compositing shared by flattened export and destructive layer operations. */
object LayerCompositor {
    fun compositePixel(destination: ByteArray, destinationOffset: Int, source: ByteArray, sourceOffset: Int,
        opacity: Float, blendMode: LayerBlendMode) {
        val sourceAlpha = (source[sourceOffset + 3].toInt() and 255) / 255f * opacity.coerceIn(0f, 1f)
        if (sourceAlpha <= 0f) return
        val destinationAlpha = (destination[destinationOffset + 3].toInt() and 255) / 255f
        val outputAlpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)
        for (channel in 0..2) {
            val sourceColor = (source[sourceOffset + channel].toInt() and 255) / 255f
            val destinationColor = (destination[destinationOffset + channel].toInt() and 255) / 255f
            val blended = when (blendMode) {
                LayerBlendMode.Normal -> sourceColor
                LayerBlendMode.Multiply -> sourceColor * destinationColor
                LayerBlendMode.Screen -> 1f - (1f - sourceColor) * (1f - destinationColor)
                LayerBlendMode.Overlay -> if (destinationColor <= .5f) 2f * sourceColor * destinationColor
                    else 1f - 2f * (1f - sourceColor) * (1f - destinationColor)
            }
            val premultiplied = (1f - sourceAlpha) * destinationAlpha * destinationColor +
                (1f - destinationAlpha) * sourceAlpha * sourceColor +
                sourceAlpha * destinationAlpha * blended
            val output = if (outputAlpha == 0f) 0f else premultiplied / outputAlpha
            destination[destinationOffset + channel] = (output * 255f + .5f).toInt().coerceIn(0, 255).toByte()
        }
        destination[destinationOffset + 3] = (outputAlpha * 255f + .5f).toInt().coerceIn(0, 255).toByte()
    }
}
