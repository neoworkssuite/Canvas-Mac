package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.TileAddress

/** Flattens two adjacent raster layers into the lower layer and removes the upper layer's tiles. */
object RasterLayerMerge {
    fun mergeDown(store: TileStore, lower: Layer, upper: Layer): RasterPatch {
        val lowerRaster = lower.payload as? LayerPayload.Raster ?: return RasterPatch.of(emptyMap())
        val upperRaster = upper.payload as? LayerPayload.Raster ?: return RasterPatch.of(emptyMap())
        val coordinates = (lowerRaster.tileAddresses + upperRaster.tileAddresses).mapTo(linkedSetOf()) { it.x to it.y }
        val replacements = linkedMapOf<TileKey, ByteArray>()
        val removals = upperRaster.tileAddresses.toMutableSet()
        coordinates.forEach { (tileX, tileY) ->
            val output = ByteArray(TileFormat.BYTES_PER_TILE)
            fun composite(layer: Layer, address: TileAddress) {
                if (!layer.visible || layer.opacity <= 0f) return
                val pixels = store.read(address) ?: return
                for (offset in pixels.indices step 4) {
                    LayerCompositor.compositePixel(output, offset, pixels, offset, layer.opacity, layer.blendMode)
                }
            }
            val lowerAddress = TileAddress(lower.id, tileX, tileY)
            val upperAddress = TileAddress(upper.id, tileX, tileY)
            composite(lower, lowerAddress)
            composite(upper, upperAddress)
            if (output.any { it.toInt() != 0 }) replacements[lowerAddress] = output
            else if (lowerAddress in lowerRaster.tileAddresses) removals += lowerAddress
        }
        return RasterPatch.of(replacements, removals)
    }
}
