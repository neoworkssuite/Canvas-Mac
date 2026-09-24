package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.geometry.CornerRadius
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
            when (val payload = layer.payload) {
                is LayerPayload.Raster -> payload.tileAddresses.forEach { key ->
                    store.read(key)?.let { drawImage(images.image(key, it), Offset(key.x * 256f, key.y * 256f)) }
                }
                is LayerPayload.TextObject -> {
                    val color = Color(payload.colorArgb)
                    drawRect(color.copy(alpha = .16f), Offset(payload.x, payload.y), Size(payload.width, payload.height))
                    val lineHeight = (payload.height / 5f).coerceAtLeast(2f)
                    repeat(3) { line ->
                        val y = payload.y + lineHeight * (line + 1)
                        drawLine(
                            color,
                            Offset(payload.x + payload.width * .12f, y),
                            Offset(payload.x + payload.width * (if (line == 2) .65f else .88f), y),
                            lineHeight * .16f,
                        )
                    }
                }
                is LayerPayload.ShapeObject -> {
                    val fill = payload.fillArgb?.let(::Color)
                    val stroke = payload.strokeArgb?.let(::Color)
                    withTransform({
                        rotate(payload.rotationDegrees, Offset(payload.x + payload.width / 2f, payload.y + payload.height / 2f))
                    }) {
                        when (payload.kind) {
                            com.neoworksuite.neocanvas.core.model.ShapeKind.Rectangle -> {
                                val radius = payload.cornerRadius.coerceIn(
                                    0f,
                                    minOf(kotlin.math.abs(payload.width), kotlin.math.abs(payload.height)) / 2f,
                                )
                                fill?.let {
                                    drawRoundRect(
                                        it,
                                        Offset(payload.x, payload.y),
                                        Size(payload.width, payload.height),
                                        CornerRadius(radius, radius),
                                    )
                                }
                                stroke?.let {
                                    drawRoundRect(
                                        it,
                                        Offset(payload.x, payload.y),
                                        Size(payload.width, payload.height),
                                        CornerRadius(radius, radius),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(payload.strokeWidth),
                                    )
                                }
                            }
                            com.neoworksuite.neocanvas.core.model.ShapeKind.Ellipse -> {
                                fill?.let { drawOval(it, Offset(payload.x, payload.y), Size(payload.width, payload.height)) }
                                stroke?.let { drawOval(it, Offset(payload.x, payload.y), Size(payload.width, payload.height),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(payload.strokeWidth)) }
                            }
                            com.neoworksuite.neocanvas.core.model.ShapeKind.Line -> {
                                val color = stroke ?: fill ?: Color.Black
                                drawStyledLine(payload, color)
                            }
                        }
                    }
                }
            }
        }
    }
}
