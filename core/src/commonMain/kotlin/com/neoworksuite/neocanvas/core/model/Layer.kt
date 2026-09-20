package com.neoworksuite.neocanvas.core.model

/** A core-owned, renderer-independent address for a raster tile. */
data class TileAddress(
    val layerId: String,
    val x: Int,
    val y: Int,
) {
    init {
        require(layerId.isNotBlank()) { "Tile layer id must not be blank." }
    }
}

sealed interface LayerPayload {
    class Raster(tileAddresses: Set<TileAddress> = emptySet()) : LayerPayload {
        /** A collection snapshot, isolated from caller-owned mutable tile sets. */
        val tileAddresses: Set<TileAddress> = immutableSetSnapshot(tileAddresses)

        init {
            require(this.tileAddresses.all { it.layerId.isNotBlank() }) { "Raster tile layer ids must not be blank." }
        }

        override fun equals(other: Any?): Boolean = other is Raster && tileAddresses == other.tileAddresses

        override fun hashCode(): Int = tileAddresses.hashCode()

        override fun toString(): String = "Raster(tileAddresses=$tileAddresses)"
    }
}

/** A renderer-neutral instruction to copy raster content between tile addresses. */
data class RasterTileCopy(
    val source: TileAddress,
    val destination: TileAddress,
)

enum class LayerBlendMode {
    Normal,
    Multiply,
    Screen,
    Overlay,
    Darken,
    Lighten,
    ColorDodge,
    ColorBurn,
    SoftLight,
    HardLight,
    Difference,
    Exclusion,
    Add,
    Subtract,
}

data class Layer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val payload: LayerPayload,
    val locked: Boolean = false,
    val alphaLocked: Boolean = false,
    val clipping: Boolean = false,
    val blendMode: LayerBlendMode = LayerBlendMode.Normal,
) {
    init {
        require(id.isNotBlank()) { "Layer id must not be blank." }
        require(name.isNotBlank()) { "Layer name must not be blank." }
        require(opacity in 0f..1f) { "Layer opacity must be between 0 and 1." }
        if (payload is LayerPayload.Raster) {
            require(payload.tileAddresses.all { it.layerId == id }) {
                "Raster tile addresses must belong to their layer."
            }
        }
    }
}
