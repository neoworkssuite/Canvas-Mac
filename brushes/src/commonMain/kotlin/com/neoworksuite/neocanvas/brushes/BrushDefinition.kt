package com.neoworksuite.neocanvas.brushes

/** Whether a brush adds pigment or clears alpha in the rasterizer. */
enum class BrushMode {
    PAINT,
    ERASE,
}

enum class BrushTip {
    Round, Pencil, SoftRound, Flat, DryPaint, Bristle, Chalk, Water, Spray, Pixel,
    Leaf, Grass, Bark,
}

data class BrushCategory(val id: String, val name: String) {
    init {
        require(id.isNotBlank()) { "Brush category id must not be blank." }
        require(name.isNotBlank()) { "Brush category name must not be blank." }
    }
}

/** Bounded, deterministic brush dynamics. Values are normalized unless noted. */
data class BrushDynamics(
    val grain: Float = 0f,
    val scatter: Float = 0f,
    val rotation: Float = 0f,
    val shapeRatio: Float = 1f,
    val hardness: Float = 1f,
    val wetMix: Float = 0f,
    val jitter: Float = 0f,
) {
    init {
        require(grain in 0f..1f) { "Grain must be between zero and one." }
        require(scatter in 0f..1f) { "Scatter must be between zero and one." }
        require(rotation in 0f..1f) { "Rotation must be between zero and one." }
        require(shapeRatio in .1f..1f) { "Shape ratio must be between 0.1 and one." }
        require(hardness in 0f..1f) { "Hardness must be between zero and one." }
        require(wetMix in 0f..1f) { "Wet mix must be between zero and one." }
        require(jitter in 0f..1f) { "Jitter must be between zero and one." }
    }
}

/** Versioned, data-only brush settings shared by all v1 hosts. */
data class BrushDefinition(
    val id: String,
    val name: String,
    val spacing: Float,
    val baseSize: Float,
    val opacity: Float,
    val mode: BrushMode = BrushMode.PAINT,
    val version: Int = 1,
    val tip: BrushTip = BrushTip.Round,
    val pressureSize: Float = 1f,
    val pressureOpacity: Float = 1f,
    val categoryId: String = "basics",
    val dynamics: BrushDynamics = BrushDynamics(),
    val stamp: BrushStamp? = null,
) {
    init {
        require(id.isNotBlank()) { "Brush id must not be blank." }
        require(name.isNotBlank()) { "Brush name must not be blank." }
        require(spacing.isFinite() && spacing > 0f) { "Brush spacing must be positive and finite." }
        require(baseSize.isFinite() && baseSize > 0f) { "Brush base size must be positive and finite." }
        require(opacity.isFinite() && opacity in 0f..1f) { "Brush opacity must be between 0 and 1." }
        require(version > 0) { "Brush version must be positive." }
        require(stamp == null || version >= 2) { "Image stamps require brush version 2 or newer." }
        require(pressureSize in 0f..1f && pressureOpacity in 0f..1f) { "Pressure response must be between zero and one." }
        require(categoryId.isNotBlank()) { "Brush category id must not be blank." }
    }
}

/** Extensibility boundary for built-in, local custom, and imported brush catalogs. */
interface BrushCatalog {
    val brushes: List<BrushDefinition>
    val categories: List<BrushCategory>
        get() = emptyList()
    val paintBrushes: List<BrushDefinition>
        get() = brushes.filter { it.mode == BrushMode.PAINT }

    fun find(id: String): BrushDefinition? = brushes.firstOrNull { it.id == id }
    fun inCategory(categoryId: String): List<BrushDefinition> = paintBrushes.filter { it.categoryId == categoryId }
    fun search(query: String): List<BrushDefinition> {
        val term = query.trim()
        if (term.isEmpty()) return paintBrushes
        val categoryNames = categories.associate { it.id to it.name }
        return paintBrushes.filter { brush ->
            brush.name.contains(term, ignoreCase = true) ||
                categoryNames[brush.categoryId]?.contains(term, ignoreCase = true) == true
        }
    }
}

/** NeoCanvas's original local brush library. */
object BuiltInBrushes : BrushCatalog {
    override val categories: List<BrushCategory> = NeoBrushLibrary.categories
    override val paintBrushes: List<BrushDefinition> = NeoBrushLibrary.brushes
    val pencil = paintBrushes.first { it.id == "neo.pencil" }
    val ink = paintBrushes.first { it.id == "neo.ink" }
    val softRound = paintBrushes.first { it.id == "neo.soft-round" }
    val dryPaint = paintBrushes.first { it.id == "neo.dry-paint" }
    val flatMarker = paintBrushes.first { it.id == "neo.flat-marker" }
    val eraser = BrushDefinition("neo.eraser", "Eraser", spacing = 2f, baseSize = 20f, opacity = 1f, mode = BrushMode.ERASE)

    override val brushes: List<BrushDefinition> = paintBrushes + eraser
    val all: List<BrushDefinition>
        get() = brushes
}
