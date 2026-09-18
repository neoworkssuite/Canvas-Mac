package com.neoworksuite.neocanvas.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.neoworksuite.neocanvas.brushes.BrushDefinition
import com.neoworksuite.neocanvas.renderer.RasterColor
import com.neoworksuite.neocanvas.renderer.RasterPoint
import com.neoworksuite.neocanvas.renderer.Rasterizer
import com.neoworksuite.neocanvas.renderer.TileKey
import com.neoworksuite.neocanvas.renderer.TileStore

class BrushTestPadState(val width: Int = 480, val height: Int = 180) {
    private val tiles = TileStore()
    var revision: Int by mutableIntStateOf(0)
        private set

    val isEmpty: Boolean get() = tiles.keys.isEmpty()

    fun draw(points: List<RasterPoint>, brush: BrushDefinition, color: RasterColor, size: Float, opacity: Float) {
        if (points.isEmpty()) return
        val patch = Rasterizer.stroke(
            existing = tiles,
            layerId = PAD_LAYER_ID,
            points = points,
            color = color,
            size = size,
            opacity = opacity,
            mode = brush.mode,
            canvasWidth = width,
            canvasHeight = height,
            brush = brush,
        )
        if (tiles.applyPatch(patch).isNotEmpty()) revision++
    }

    fun clear() {
        if (tiles.keys.isEmpty()) return
        tiles.restore(emptyMap())
        revision++
    }

    internal fun snapshot(): Map<TileKey, ByteArray> = tiles.snapshot()

    private companion object { const val PAD_LAYER_ID = "brush-test-pad" }
}
