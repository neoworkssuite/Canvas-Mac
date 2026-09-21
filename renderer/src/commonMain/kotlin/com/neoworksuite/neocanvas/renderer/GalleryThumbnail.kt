package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import kotlin.math.min

object GalleryThumbnail {
    fun render(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, maxWidth: Int = 480, maxHeight: Int = 360): PngImage {
        require(maxWidth > 0 && maxHeight > 0)
        val source = PngExporter.render(document, tiles, allowTextPlaceholder = true)
        val scale = min(1.0, min(maxWidth.toDouble() / source.width, maxHeight.toDouble() / source.height))
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        if (width == source.width && height == source.height) return source
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val sourceX = ((x + .5) * source.width / width).toInt().coerceAtMost(source.width - 1)
            val sourceY = ((y + .5) * source.height / height).toInt().coerceAtMost(source.height - 1)
            val from = (sourceY * source.width + sourceX) * 4
            val to = (y * width + x) * 4
            source.rgba.copyInto(output, to, from, from + 4)
        }
        return PngImage(width, height, output)
    }
}
