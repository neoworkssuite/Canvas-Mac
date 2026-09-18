package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.renderer.TileStore

/** Shows an individual layer, even when hidden, against a transparency checkerboard. */
internal fun DrawScope.drawLayerPreview(
    layer: Layer, width: Int, height: Int, store: TileStore, images: TileImageCache,
) {
    val scale = minOf(size.width / width, size.height / height)
    val w = width * scale
    val h = height * scale
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    clipRect(left, top, left + w, top + h) {
        val cell = 6.dp.toPx()
        for (y in 0..(h / cell).toInt()) for (x in 0..(w / cell).toInt()) {
            drawRect(if ((x + y) % 2 == 0) Color(0xFFE2E2E2) else Color(0xFFBDBDBD),
                Offset(left + x * cell, top + y * cell), Size(cell, cell))
        }
        withTransform({ translate(left, top); scale(scale, scale, Offset.Zero) }) {
            (layer.payload as? LayerPayload.Raster)?.tileAddresses?.forEach { key ->
                store.read(key)?.let { drawImage(images.image(key, it), Offset(key.x * 256f, key.y * 256f)) }
            }
        }
    }
}
