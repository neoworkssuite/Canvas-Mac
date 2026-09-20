package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload

data class CroppedCanvas(
    val width: Int,
    val height: Int,
    val tiles: Map<TileKey, ByteArray>,
)

/** Crops every raster layer to one document-space rectangle and rebases surviving pixels to (0, 0). */
object RasterCanvasCrop {
    fun crop(
        store: TileStore,
        layers: List<Layer>,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): CroppedCanvas {
        require(left >= 0 && top >= 0 && right > left && bottom > top)
        val width = right - left
        val height = bottom - top
        val snapshot = store.snapshot()
        val output = linkedMapOf<TileKey, ByteArray>()

        fun sourcePixel(layerId: String, x: Int, y: Int): IntArray? {
            val key = TileKey(layerId, tileCoordinate(x), tileCoordinate(y))
            val bytes = snapshot[key] ?: return null
            val localX = x - key.x * TILE_SIZE_PIXELS
            val localY = y - key.y * TILE_SIZE_PIXELS
            val offset = (localY * TILE_SIZE_PIXELS + localX) * 4
            if ((bytes[offset + 3].toInt() and 255) == 0) return null
            return intArrayOf(
                bytes[offset].toInt() and 255,
                bytes[offset + 1].toInt() and 255,
                bytes[offset + 2].toInt() and 255,
                bytes[offset + 3].toInt() and 255,
            )
        }

        layers.forEach { layer ->
            if (layer.payload !is LayerPayload.Raster) return@forEach
            for (targetY in 0 until height) for (targetX in 0 until width) {
                val pixel = sourcePixel(layer.id, left + targetX, top + targetY) ?: continue
                val key = TileKey(layer.id, tileCoordinate(targetX), tileCoordinate(targetY))
                val bytes = output.getOrPut(key) { ByteArray(TileFormat.BYTES_PER_TILE) }
                val localX = targetX - key.x * TILE_SIZE_PIXELS
                val localY = targetY - key.y * TILE_SIZE_PIXELS
                val offset = (localY * TILE_SIZE_PIXELS + localX) * 4
                bytes[offset] = pixel[0].toByte()
                bytes[offset + 1] = pixel[1].toByte()
                bytes[offset + 2] = pixel[2].toByte()
                bytes[offset + 3] = pixel[3].toByte()
            }
        }
        return CroppedCanvas(width, height, output)
    }
}
