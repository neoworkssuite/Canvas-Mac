package com.neoworksuite.neocanvas.core.model

sealed interface DocumentCommand {
    fun apply(document: CanvasDocument): CanvasDocument

    /**
     * Returns renderer-neutral tile copies needed before [apply] is committed.
     * The caller performs these in its tile store; core intentionally owns no pixels.
     */
    fun rasterTileCopies(document: CanvasDocument): Set<RasterTileCopy> = emptySet()
}

class CropCanvas(
    val width: Int,
    val height: Int,
    layerAddresses: Map<String, Set<TileAddress>>,
) : DocumentCommand {
    private val layerAddresses: Map<String, Set<TileAddress>> =
        layerAddresses.mapValues { (_, addresses) -> immutableSetSnapshot(addresses) }.toMap()

    init {
        require(width > 0 && height > 0)
        require(this.layerAddresses.all { (layerId, addresses) -> addresses.all { it.layerId == layerId } })
    }

    override fun apply(document: CanvasDocument): CanvasDocument =
        document.copy(
            width = width,
            height = height,
            layers = document.layers.map { layer ->
                when (layer.payload) {
                    is LayerPayload.Raster -> layer.copy(
                        payload = LayerPayload.Raster(layerAddresses[layer.id].orEmpty()),
                    )
                }
            },
        )
}

data class AddRasterLayer(
    val layerId: String,
    val name: String,
    val insertionIndex: Int? = null,
) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument {
        require(document.layers.none { it.id == layerId }) { "A layer with id '$layerId' already exists." }
        val index = insertionIndex ?: document.layers.size
        require(index in 0..document.layers.size) { "Layer insertion index is out of bounds." }
        return document.copy(layers = document.layers.toMutableList().apply {
            add(index, Layer(layerId, name, payload = LayerPayload.Raster()))
        })
    }
}

class ImportRasterLayer(val layerId: String, val name: String, addresses: Set<TileAddress>) : DocumentCommand {
    private val addresses = addresses.toSet()
    override fun apply(document: CanvasDocument): CanvasDocument =
        ApplyRasterPatch(layerId, addresses).apply(AddRasterLayer(layerId, name).apply(document))
}

data class RenameLayer(val layerId: String, val name: String) : DocumentCommand {
    init {
        require(name.isNotBlank()) { "Layer name must not be blank." }
    }

    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { it.copy(name = name) }
}

data class SetLayerOpacity(val layerId: String, val opacity: Float) : DocumentCommand {
    init {
        require(opacity in 0f..1f) { "Layer opacity must be between 0 and 1." }
    }

    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { it.copy(opacity = opacity) }
}

data class SetLayerVisibility(val layerId: String, val visible: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { it.copy(visible = visible) }
}

data class SetLayerLocked(val layerId: String, val locked: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { it.copy(locked = locked) }
}

data class SetLayerAlphaLocked(val layerId: String, val alphaLocked: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { it.copy(alphaLocked = alphaLocked) }
}

data class SetLayerClipping(val layerId: String, val clipping: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceLayer(layerId) { it.copy(clipping = clipping) }
}

data class SetLayerBlendMode(val layerId: String, val blendMode: LayerBlendMode) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { it.copy(blendMode = blendMode) }
}

/**
 * Records the tile addresses touched by one committed raster-store mutation.
 *
 * Pixel data deliberately remains in the renderer's tile store.  Keeping this
 * command address-only prevents the core model from depending on renderer
 * types while retaining raster edits in document history.
 */
class ApplyRasterPatch(
    val layerId: String,
    addedTileAddresses: Set<TileAddress> = emptySet(),
    removedTileAddresses: Set<TileAddress> = emptySet(),
) : DocumentCommand {
    val addedTileAddresses: Set<TileAddress> = immutableSetSnapshot(addedTileAddresses)
    val removedTileAddresses: Set<TileAddress> = immutableSetSnapshot(removedTileAddresses)

    init {
        require(layerId.isNotBlank()) { "Raster patch layer id must not be blank." }
        require((this.addedTileAddresses + this.removedTileAddresses).all { it.layerId == layerId }) {
            "Raster patch tile addresses must belong to the patched layer."
        }
        require(this.addedTileAddresses.intersect(this.removedTileAddresses).isEmpty()) {
            "A raster patch cannot add and remove the same tile address."
        }
    }

    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { layer ->
        val raster = layer.payload as? LayerPayload.Raster
            ?: throw IllegalArgumentException("Layer '$layerId' does not accept raster patches.")
        val patchedAddresses = raster.tileAddresses.toMutableSet().apply {
            removeAll(removedTileAddresses)
            addAll(addedTileAddresses)
        }
        layer.copy(payload = LayerPayload.Raster(patchedAddresses))
    }

    override fun equals(other: Any?): Boolean = other is ApplyRasterPatch &&
        layerId == other.layerId &&
        addedTileAddresses == other.addedTileAddresses &&
        removedTileAddresses == other.removedTileAddresses

    override fun hashCode(): Int = 31 * (31 * layerId.hashCode() + addedTileAddresses.hashCode()) + removedTileAddresses.hashCode()

    override fun toString(): String =
        "ApplyRasterPatch(layerId=$layerId, addedTileAddresses=$addedTileAddresses, removedTileAddresses=$removedTileAddresses)"
}

data class MoveLayer(val layerId: String, val targetIndex: Int) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument {
        val sourceIndex = document.layers.indexOfLayer(layerId)
        require(targetIndex in document.layers.indices) { "Layer target index is out of bounds." }
        if (sourceIndex == targetIndex) return document

        return document.copy(layers = document.layers.toMutableList().apply {
            val layer = removeAt(sourceIndex)
            add(targetIndex, layer)
        })
    }
}

class MergeRasterLayerDown(
    val sourceLayerId: String,
    val destinationLayerId: String,
    destinationTileAddresses: Set<TileAddress>,
) : DocumentCommand {
    private val destinationTileAddresses = immutableSetSnapshot(destinationTileAddresses)

    init {
        require(destinationTileAddresses.all { it.layerId == destinationLayerId })
    }

    override fun apply(document: CanvasDocument): CanvasDocument {
        val sourceIndex = document.layers.indexOfLayer(sourceLayerId)
        val destinationIndex = document.layers.indexOfLayer(destinationLayerId)
        require(sourceIndex == destinationIndex + 1) { "Only adjacent layers can be merged down." }
        val destination = document.layers[destinationIndex]
        require(destination.payload is LayerPayload.Raster && document.layers[sourceIndex].payload is LayerPayload.Raster)
        val merged = destination.copy(
            visible = true,
            opacity = 1f,
            payload = LayerPayload.Raster(destinationTileAddresses),
            blendMode = LayerBlendMode.Normal,
        )
        return document.copy(layers = document.layers.toMutableList().apply {
            set(destinationIndex, merged)
            removeAt(sourceIndex)
        })
    }
}

data class DuplicateLayer(
    val sourceLayerId: String,
    val duplicateLayerId: String,
    val duplicateName: String,
) : DocumentCommand {
    init {
        require(duplicateLayerId.isNotBlank()) { "Duplicate layer id must not be blank." }
        require(duplicateName.isNotBlank()) { "Duplicate layer name must not be blank." }
    }

    override fun rasterTileCopies(document: CanvasDocument): Set<RasterTileCopy> = plan(document).tileCopies

    override fun apply(document: CanvasDocument): CanvasDocument {
        val plan = plan(document)
        val source = document.layers[plan.sourceIndex]
        val payload = when (source.payload) {
            is LayerPayload.Raster -> LayerPayload.Raster(
                plan.tileCopies.mapTo(linkedSetOf()) { it.destination },
            )
        }
        val duplicate = source.copy(id = duplicateLayerId, name = duplicateName, payload = payload)
        return document.copy(layers = document.layers.toMutableList().apply {
            add(plan.sourceIndex + 1, duplicate)
        })
    }

    private fun plan(document: CanvasDocument): DuplicateLayerPlan {
        require(document.layers.none { it.id == duplicateLayerId }) { "A layer with id '$duplicateLayerId' already exists." }
        val sourceIndex = document.layers.indexOfLayer(sourceLayerId)
        val tileCopies = when (val sourcePayload = document.layers[sourceIndex].payload) {
            is LayerPayload.Raster -> immutableSetSnapshot(sourcePayload.tileAddresses.mapTo(linkedSetOf()) { address ->
                RasterTileCopy(address, address.copy(layerId = duplicateLayerId))
            })
        }
        return DuplicateLayerPlan(sourceIndex, tileCopies)
    }
}

private data class DuplicateLayerPlan(
    val sourceIndex: Int,
    val tileCopies: Set<RasterTileCopy>,
)

data class DeleteLayer(val layerId: String) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument {
        val index = document.layers.indexOfLayer(layerId)
        return document.copy(layers = document.layers.toMutableList().apply { removeAt(index) })
    }
}

private fun CanvasDocument.replaceLayer(layerId: String, update: (Layer) -> Layer): CanvasDocument {
    val index = layers.indexOfLayer(layerId)
    return copy(layers = layers.toMutableList().apply { set(index, update(this[index])) })
}

private fun List<Layer>.indexOfLayer(layerId: String): Int = indexOfFirst { it.id == layerId }
    .also { require(it >= 0) { "No layer with id '$layerId' exists." } }
