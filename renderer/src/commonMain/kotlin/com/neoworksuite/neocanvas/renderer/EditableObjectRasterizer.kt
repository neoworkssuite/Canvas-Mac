package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.TileAddress
import kotlin.math.min

data class RasterizedExportDocument(
    val document: CanvasDocument,
    val tiles: Map<TileAddress, ByteArray>,
)

/**
 * Creates a temporary raster-only document for interchange formats such as PSD.
 * The source document is never mutated: editable Text/Shape payloads remain editable in NeoCanvas.
 */
object EditableObjectRasterizer {
    fun rasterize(
        document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
        textRasterizer: TextRasterizer? = null,
    ): RasterizedExportDocument {
        if (document.layers.all { it.payload is LayerPayload.Raster }) {
            return RasterizedExportDocument(document, tiles)
        }

        val outputTiles = tiles.toMutableMap()
        val outputLayers = document.layers.map { layer ->
            when (layer.payload) {
                is LayerPayload.Raster -> layer
                is LayerPayload.TextObject,
                is LayerPayload.ShapeObject -> {
                    require(layer.mask == null && !layer.clipping) {
                        "Editable objects with masks or clipping must be rasterized in NeoCanvas before interchange export."
                    }
                    outputTiles.keys.filter { it.layerId == layer.id }.forEach(outputTiles::remove)

                    val neutralLayer = layer.copy(
                        visible = true,
                        opacity = 1f,
                        locked = false,
                        alphaLocked = false,
                        clipping = false,
                        blendMode = LayerBlendMode.Normal,
                        groupId = null,
                        mask = null,
                    )
                    val isolated = document.copy(
                        layers = listOf(neutralLayer),
                        groups = emptyList(),
                    )
                    val image = PngExporter.render(
                        isolated,
                        emptyMap(),
                        textRasterizer = textRasterizer,
                    )
                    val raster = image.toTiles(layer.id)
                    outputTiles.putAll(raster.second)
                    layer.copy(
                        payload = LayerPayload.Raster(raster.first),
                        alphaLocked = false,
                        clipping = false,
                        mask = null,
                    )
                }
            }
        }

        return RasterizedExportDocument(
            document.copy(layers = outputLayers),
            outputTiles.toMap(),
        )
    }

    private fun PngImage.toTiles(layerId: String): Pair<Set<TileAddress>, Map<TileAddress, ByteArray>> {
        val addresses = linkedSetOf<TileAddress>()
        val result = linkedMapOf<TileAddress, ByteArray>()
        val columns = (width + TILE_SIZE_PIXELS - 1) / TILE_SIZE_PIXELS
        val rows = (height + TILE_SIZE_PIXELS - 1) / TILE_SIZE_PIXELS

        for (tileY in 0 until rows) {
            for (tileX in 0 until columns) {
                val startX = tileX * TILE_SIZE_PIXELS
                val startY = tileY * TILE_SIZE_PIXELS
                val copyWidth = min(TILE_SIZE_PIXELS, width - startX)
                val copyHeight = min(TILE_SIZE_PIXELS, height - startY)
                val tile = ByteArray(TileFormat.BYTES_PER_TILE)
                var hasVisiblePixel = false

                for (row in 0 until copyHeight) {
                    val sourceOffset = ((startY + row) * width + startX) * 4
                    val destinationOffset = row * TILE_SIZE_PIXELS * 4
                    rgba.copyInto(
                        tile,
                        destinationOffset,
                        sourceOffset,
                        sourceOffset + copyWidth * 4,
                    )
                    var alphaOffset = sourceOffset + 3
                    repeat(copyWidth) {
                        if ((rgba[alphaOffset].toInt() and 255) != 0) hasVisiblePixel = true
                        alphaOffset += 4
                    }
                }

                if (hasVisiblePixel) {
                    val address = TileAddress(layerId, tileX, tileY)
                    addresses += address
                    result[address] = tile
                }
            }
        }
        return addresses to result
    }
}
