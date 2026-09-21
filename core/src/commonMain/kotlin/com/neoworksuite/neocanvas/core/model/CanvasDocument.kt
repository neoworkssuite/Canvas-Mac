package com.neoworksuite.neocanvas.core.model

import kotlin.random.Random

/**
 * Immutable metadata and layer state for one bounded raster canvas.
 *
 * Raster pixels remain outside this model in the tile store; the raster layer
 * payload records only their stable addresses.
 */
class CanvasDocument(
    val id: String,
    val width: Int,
    val height: Int,
    layers: List<Layer> = emptyList(),
    groups: List<LayerGroup> = emptyList(),
) {
    /** Collection snapshots isolated from caller-owned mutable collections. */
    val layers: List<Layer> = immutableListSnapshot(layers)
    val groups: List<LayerGroup> = immutableListSnapshot(groups)

    init {
        require(id.isNotBlank()) { "Document id must not be blank." }
        require(width > 0) { "Canvas width must be positive." }
        require(height > 0) { "Canvas height must be positive." }
        require(this.layers.map(Layer::id).distinct().size == this.layers.size) { "Layer ids must be unique." }
        require(this.groups.map(LayerGroup::id).distinct().size == this.groups.size) { "Layer group ids must be unique." }
        val groupIds = this.groups.mapTo(linkedSetOf(), LayerGroup::id)
        require(this.layers.all { it.groupId == null || it.groupId in groupIds }) {
            "Every grouped layer must reference a group in the document."
        }
        val layerIds = this.layers.mapTo(linkedSetOf(), Layer::id)
        val maskIds = this.layers.mapNotNull { it.mask?.id }
        require(maskIds.distinct().size == maskIds.size) { "Layer mask ids must be unique." }
        require(maskIds.none { it in layerIds }) { "Layer mask ids must not collide with layer ids." }
    }

    companion object {
        fun blank(width: Int, height: Int, id: String = randomDocumentId()) = CanvasDocument(
            id = id,
            width = width,
            height = height,
        )

        private fun randomDocumentId(): String = buildString {
            appendRandomHex(8)
            append('-')
            appendRandomHex(4)
            append("-4")
            appendRandomHex(3)
            append('-')
            append("89ab"[Random.nextInt(4)])
            appendRandomHex(3)
            append('-')
            appendRandomHex(12)
        }

        private fun StringBuilder.appendRandomHex(length: Int) {
            repeat(length) { append("0123456789abcdef"[Random.nextInt(16)]) }
        }
    }

    fun copy(
        id: String = this.id,
        width: Int = this.width,
        height: Int = this.height,
        layers: List<Layer> = this.layers,
        groups: List<LayerGroup> = this.groups,
    ): CanvasDocument = CanvasDocument(id, width, height, layers, groups)

    override fun equals(other: Any?): Boolean = other is CanvasDocument &&
        id == other.id &&
        width == other.width &&
        height == other.height &&
        layers == other.layers &&
        groups == other.groups

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + layers.hashCode()
        result = 31 * result + groups.hashCode()
        return result
    }

    override fun toString(): String =
        "CanvasDocument(id=$id, width=$width, height=$height, layers=$layers, groups=$groups)"
}
