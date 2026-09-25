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

enum class TextAlignment { Left, Center, Right, Justified }

enum class ShapeKind { Rectangle, Ellipse, Line }

enum class LineStyle { Solid, Dashed, Dotted }

enum class LineCap { Round, Square, Butt }

enum class LineMarker { None, Arrow }

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

    data class TextObject(
        val text: String,
        val fontFamily: String = "System",
        val fontSize: Float = 48f,
        val colorArgb: Int = 0xff000000.toInt(),
        val x: Float = 0f,
        val y: Float = 0f,
        val width: Float = 640f,
        val height: Float = 160f,
        val rotationDegrees: Float = 0f,
        val alignment: TextAlignment = TextAlignment.Left,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val lineSpacing: Float = 1.2f,
        val tracking: Float = 0f,
        val baselineOffset: Float = 0f,
        val underline: Boolean = false,
        val uppercase: Boolean = false,
        val fontStyle: String = "Regular",
        val kerning: List<TextKerningRange> = emptyList(),
        val outline: Boolean = false,
        val outlineWidth: Float = 1f,
        val orientation: TextOrientation = TextOrientation.Horizontal,
    ) : LayerPayload {
        init {
            require(text.length <= 10_000)
            require(fontFamily.isNotBlank())
            require(fontSize.isFinite() && fontSize > 0f)
            require(x.isFinite() && y.isFinite())
            require(width.isFinite() && height.isFinite() && width > 0f && height > 0f)
            require(rotationDegrees.isFinite())
            require(lineSpacing.isFinite() && lineSpacing in .7f..3f)
            require(tracking.isFinite() && tracking in -8f..40f)
            require(baselineOffset.isFinite() && baselineOffset in -256f..256f)
            require(fontStyle.isNotBlank())
            require(outlineWidth.isFinite() && outlineWidth in .25f..32f)
            require(kerning == normalizeKerningRanges(text, kerning))
        }
    }

    data class ShapeObject(
        val kind: ShapeKind,
        val x: Float = 0f,
        val y: Float = 0f,
        val width: Float = 240f,
        val height: Float = 240f,
        val fillArgb: Int? = 0xff000000.toInt(),
        val strokeArgb: Int? = null,
        val strokeWidth: Float = 0f,
        val rotationDegrees: Float = 0f,
        val cornerRadius: Float = 0f,
        val lineStyle: LineStyle = LineStyle.Solid,
        val lineCap: LineCap = LineCap.Round,
        val startMarker: LineMarker = LineMarker.None,
        val endMarker: LineMarker = LineMarker.None,
        val angleSnapping: Boolean = true,
    ) : LayerPayload {
        init {
            require(x.isFinite() && y.isFinite() && width.isFinite() && height.isFinite())
            when (kind) {
                ShapeKind.Rectangle, ShapeKind.Ellipse -> require(width > 0f && height > 0f)
                ShapeKind.Line -> require(width != 0f || height != 0f)
            }
            require(fillArgb != null || strokeArgb != null)
            require(strokeWidth.isFinite() && strokeWidth >= 0f)
            if (strokeArgb != null) require(strokeWidth > 0f)
            require(rotationDegrees.isFinite())
            require(cornerRadius.isFinite() && cornerRadius >= 0f)
        }
    }
}

/** A renderer-neutral instruction to copy raster content between tile addresses. */
data class RasterTileCopy(
    val source: TileAddress,
    val destination: TileAddress,
)

data class LayerMask(
    val id: String,
    val tileAddresses: Set<TileAddress> = emptySet(),
    val enabled: Boolean = true,
    val inverted: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Layer mask id must not be blank." }
        require(tileAddresses.all { it.layerId == id }) { "Mask tile addresses must belong to the mask id." }
    }
}

data class LayerGroup(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val locked: Boolean = false,
    val collapsed: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Layer group id must not be blank." }
        require(name.isNotBlank()) { "Layer group name must not be blank." }
        require(opacity in 0f..1f) { "Layer group opacity must be between zero and one." }
    }
}

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
    val groupId: String? = null,
    val mask: LayerMask? = null,
) {
    init {
        require(id.isNotBlank()) { "Layer id must not be blank." }
        require(name.isNotBlank()) { "Layer name must not be blank." }
        require(opacity in 0f..1f) { "Layer opacity must be between 0 and 1." }
        require(groupId == null || groupId.isNotBlank()) { "Layer group id must not be blank." }
        if (payload is LayerPayload.Raster) {
            require(payload.tileAddresses.all { it.layerId == id }) {
                "Raster tile addresses must belong to their layer."
            }
        } else {
            require(!alphaLocked) { "Alpha lock currently applies only to raster layers." }
        }
    }
}
