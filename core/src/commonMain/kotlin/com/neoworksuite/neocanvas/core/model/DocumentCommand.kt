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
                    is LayerPayload.TextObject,
                    is LayerPayload.ShapeObject -> layer
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

data class AddTextLayer(val layerId: String, val name: String, val text: LayerPayload.TextObject, val insertionIndex: Int? = null) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument {
        require(document.layers.none { it.id == layerId })
        val index = insertionIndex ?: document.layers.size
        require(index in 0..document.layers.size)
        return document.copy(layers = document.layers.toMutableList().apply { add(index, Layer(layerId, name, payload = text)) })
    }
}

data class UpdateTextLayer(val layerId: String, val text: LayerPayload.TextObject) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) {
        require(it.payload is LayerPayload.TextObject)
        it.copy(payload = text)
    }
}

class UpdateEditableObjects(updates: Map<String, LayerPayload>) : DocumentCommand {
    val updates: Map<String, LayerPayload> = updates.toMap()

    init {
        require(this.updates.isNotEmpty()) { "Editable object updates must not be empty." }
        require(this.updates.values.all { it is LayerPayload.TextObject || it is LayerPayload.ShapeObject }) {
            "Only editable text and shape payloads can be updated together."
        }
    }

    override fun apply(document: CanvasDocument): CanvasDocument {
        val layersById = document.layers.associateBy(Layer::id)
        updates.forEach { (layerId, payload) ->
            val layer = layersById[layerId]
                ?: throw IllegalArgumentException("No layer with id '$layerId' exists.")
            require(
                (layer.payload is LayerPayload.TextObject && payload is LayerPayload.TextObject) ||
                    (layer.payload is LayerPayload.ShapeObject && payload is LayerPayload.ShapeObject),
            ) { "Editable object update type must match the existing layer type." }
        }
        return document.copy(
            layers = document.layers.map { layer ->
                updates[layer.id]?.let { layer.copy(payload = it) } ?: layer
            },
        )
    }
}

data class AddShapeLayer(val layerId: String, val name: String, val shape: LayerPayload.ShapeObject, val insertionIndex: Int? = null) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument {
        require(document.layers.none { it.id == layerId })
        val index = insertionIndex ?: document.layers.size
        require(index in 0..document.layers.size)
        return document.copy(layers = document.layers.toMutableList().apply { add(index, Layer(layerId, name, payload = shape)) })
    }
}

data class UpdateShapeLayer(val layerId: String, val shape: LayerPayload.ShapeObject) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) {
        require(it.payload is LayerPayload.ShapeObject)
        it.copy(payload = shape)
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

data class AddLayerGroup(
    val groupId: String,
    val name: String,
) : DocumentCommand {
    init {
        require(groupId.isNotBlank())
        require(name.isNotBlank())
    }

    override fun apply(document: CanvasDocument): CanvasDocument {
        require(document.groups.none { it.id == groupId }) { "A group with id '$groupId' already exists." }
        return document.copy(groups = document.groups + LayerGroup(groupId, name))
    }
}

data class RenameLayerGroup(val groupId: String, val name: String) : DocumentCommand {
    init { require(name.isNotBlank()) }
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceGroup(groupId) { it.copy(name = name.trim()) }
}

data class SetLayerGroupVisibility(val groupId: String, val visible: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceGroup(groupId) { it.copy(visible = visible) }
}

data class SetLayerGroupOpacity(val groupId: String, val opacity: Float) : DocumentCommand {
    init { require(opacity in 0f..1f) }
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceGroup(groupId) { it.copy(opacity = opacity) }
}

data class SetLayerGroupLocked(val groupId: String, val locked: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceGroup(groupId) { it.copy(locked = locked) }
}

data class SetLayerGroupCollapsed(val groupId: String, val collapsed: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceGroup(groupId) { it.copy(collapsed = collapsed) }
}

data class SetLayerGroupMembership(val layerId: String, val groupId: String?) : DocumentCommand {
    init { require(groupId == null || groupId.isNotBlank()) }
    override fun apply(document: CanvasDocument): CanvasDocument {
        if (groupId != null) require(document.groups.any { it.id == groupId }) { "Layer group does not exist." }
        return document.replaceLayer(layerId) { it.copy(groupId = groupId) }
    }
}

data class DeleteLayerGroup(val groupId: String) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument {
        require(document.groups.any { it.id == groupId }) { "Layer group does not exist." }
        return document.copy(
            layers = document.layers.map { if (it.groupId == groupId) it.copy(groupId = null) else it },
            groups = document.groups.filterNot { it.id == groupId },
        )
    }
}

data class AddLayerMask(
    val layerId: String,
    val maskId: String,
) : DocumentCommand {
    init {
        require(layerId.isNotBlank())
        require(maskId.isNotBlank())
    }

    override fun apply(document: CanvasDocument): CanvasDocument = document.replaceLayer(layerId) { layer ->
        require(layer.mask == null) { "Layer already has a mask." }
        require(document.layers.none { it.id == maskId || it.mask?.id == maskId }) { "Mask id must be unique." }
        layer.copy(mask = LayerMask(maskId))
    }
}

data class RemoveLayerMask(val layerId: String) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceLayer(layerId) { it.copy(mask = null) }
}

data class SetLayerMaskEnabled(val layerId: String, val enabled: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceLayer(layerId) { layer ->
            val mask = requireNotNull(layer.mask) { "Layer has no mask." }
            layer.copy(mask = mask.copy(enabled = enabled))
        }
}

data class SetLayerMaskInverted(val layerId: String, val inverted: Boolean) : DocumentCommand {
    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceLayer(layerId) { layer ->
            val mask = requireNotNull(layer.mask) { "Layer has no mask." }
            layer.copy(mask = mask.copy(inverted = inverted))
        }
}

class ApplyLayerMaskPatch(
    val layerId: String,
    val maskId: String,
    addedTileAddresses: Set<TileAddress> = emptySet(),
    removedTileAddresses: Set<TileAddress> = emptySet(),
) : DocumentCommand {
    val addedTileAddresses = immutableSetSnapshot(addedTileAddresses)
    val removedTileAddresses = immutableSetSnapshot(removedTileAddresses)

    init {
        require(layerId.isNotBlank() && maskId.isNotBlank())
        require((this.addedTileAddresses + this.removedTileAddresses).all { it.layerId == maskId }) {
            "Mask patch tile addresses must belong to the mask id."
        }
        require(this.addedTileAddresses.intersect(this.removedTileAddresses).isEmpty())
    }

    override fun apply(document: CanvasDocument): CanvasDocument =
        document.replaceLayer(layerId) { layer ->
            val mask = requireNotNull(layer.mask) { "Layer has no mask." }
            require(mask.id == maskId) { "Mask id does not match layer mask." }
            val addresses = mask.tileAddresses.toMutableSet().apply {
                removeAll(removedTileAddresses)
                addAll(addedTileAddresses)
            }
            layer.copy(mask = mask.copy(tileAddresses = addresses))
        }
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
        val duplicateMaskId = source.mask?.let { duplicateLayerId + "-mask" }
        val payload = when (val sourcePayload = source.payload) {
            is LayerPayload.Raster -> LayerPayload.Raster(
                plan.tileCopies.filter { it.destination.layerId == duplicateLayerId }
                    .mapTo(linkedSetOf()) { it.destination },
            )
            is LayerPayload.TextObject -> sourcePayload.copy()
            is LayerPayload.ShapeObject -> sourcePayload.copy()
        }
        val duplicateMask = source.mask?.let { mask ->
            val maskId = requireNotNull(duplicateMaskId)
            mask.copy(
                id = maskId,
                tileAddresses = plan.tileCopies.filter { it.destination.layerId == maskId }
                    .mapTo(linkedSetOf()) { it.destination },
            )
        }
        val duplicate = source.copy(
            id = duplicateLayerId,
            name = duplicateName,
            payload = payload,
            mask = duplicateMask,
        )
        return document.copy(layers = document.layers.toMutableList().apply {
            add(plan.sourceIndex + 1, duplicate)
        })
    }

    private fun plan(document: CanvasDocument): DuplicateLayerPlan {
        require(document.layers.none { it.id == duplicateLayerId }) { "A layer with id '$duplicateLayerId' already exists." }
        val sourceIndex = document.layers.indexOfLayer(sourceLayerId)
        val source = document.layers[sourceIndex]
        val tileCopies = when (val sourcePayload = source.payload) {
            is LayerPayload.Raster -> {
                val copies = sourcePayload.tileAddresses.mapTo(linkedSetOf()) { address ->
                    RasterTileCopy(address, address.copy(layerId = duplicateLayerId))
                }
                source.mask?.let { mask ->
                    val duplicateMaskId = duplicateLayerId + "-mask"
                    mask.tileAddresses.mapTo(copies) { address ->
                        RasterTileCopy(address, address.copy(layerId = duplicateMaskId))
                    }
                }
                immutableSetSnapshot(copies)
            }
            is LayerPayload.TextObject,
            is LayerPayload.ShapeObject -> emptySet()
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

private fun CanvasDocument.replaceGroup(groupId: String, update: (LayerGroup) -> LayerGroup): CanvasDocument {
    val index = groups.indexOfFirst { it.id == groupId }
    require(index >= 0) { "No group with id '$groupId' exists." }
    return copy(groups = groups.toMutableList().apply { set(index, update(this[index])) })
}

private fun CanvasDocument.replaceLayer(layerId: String, update: (Layer) -> Layer): CanvasDocument {
    val index = layers.indexOfLayer(layerId)
    return copy(layers = layers.toMutableList().apply { set(index, update(this[index])) })
}

private fun List<Layer>.indexOfLayer(layerId: String): Int = indexOfFirst { it.id == layerId }
    .also { require(it >= 0) { "No layer with id '$layerId' exists." } }
