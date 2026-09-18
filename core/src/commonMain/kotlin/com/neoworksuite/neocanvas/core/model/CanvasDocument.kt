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
) {
    /** A collection snapshot, isolated from caller-owned mutable layer lists. */
    val layers: List<Layer> = immutableListSnapshot(layers)

    init {
        require(id.isNotBlank()) { "Document id must not be blank." }
        require(width > 0) { "Canvas width must be positive." }
        require(height > 0) { "Canvas height must be positive." }
        require(this.layers.map(Layer::id).distinct().size == this.layers.size) { "Layer ids must be unique." }
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
    ): CanvasDocument = CanvasDocument(id, width, height, layers)

    override fun equals(other: Any?): Boolean = other is CanvasDocument &&
        id == other.id &&
        width == other.width &&
        height == other.height &&
        layers == other.layers

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + layers.hashCode()
        return result
    }

    override fun toString(): String = "CanvasDocument(id=$id, width=$width, height=$height, layers=$layers)"
}
