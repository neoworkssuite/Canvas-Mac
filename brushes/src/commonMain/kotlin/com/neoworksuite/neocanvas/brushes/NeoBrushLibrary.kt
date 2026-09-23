package com.neoworksuite.neocanvas.brushes

private data class CategoryTemplate(
    val category: BrushCategory,
    val names: List<String>,
    val tips: List<BrushTip>,
    val baseSize: Float,
    val opacity: Float,
    val spacingRatio: Float,
    val dynamics: BrushDynamics,
)

/** Original NeoCanvas presets. No external brush assets or proprietary settings are used. */
internal object NeoBrushLibrary {
    private val templates = listOf(
        template("pencils", "Pencils", listOf("Graphite Pencil", "Precision Pencil", "Soft Sketch", "Carpenter Lead", "Blue Draft", "Velvet Graphite", "Shading Stick", "Mechanical Point", "Loose Scribble", "Powder Pencil"), listOf(BrushTip.Pencil, BrushTip.Chalk), 5f, .72f, .14f, BrushDynamics(grain = .48f, shapeRatio = .72f, hardness = .68f)),
        template("pens", "Pens", listOf("Technical Pen", "Fine Liner", "Ballpoint", "Gel Writer", "Fountain Fine", "Fountain Broad", "Rapid Detail", "Mono Pen", "Journal Pen", "Contour Pen"), listOf(BrushTip.Round, BrushTip.Flat), 6f, .92f, .12f, BrushDynamics(hardness = .92f, shapeRatio = .82f)),
        template("inks", "Inks", listOf("Studio Ink", "Sumi Line", "India Ink", "Bleed Ink", "Dry Nib", "Tapered Inker", "Comic Ink", "Fluid Nib", "Rough Inker", "Dense Black"), listOf(BrushTip.Round, BrushTip.DryPaint, BrushTip.Bristle), 9f, .96f, .10f, BrushDynamics(grain = .12f, rotation = .35f, hardness = .88f)),
        template("markers", "Markers", listOf("Flat Marker", "Chisel Marker", "Alcohol Marker", "Brush Marker", "Highlighter", "Bleed Marker", "Poster Marker", "Twin Tip", "Broad Felt", "Dry Marker"), listOf(BrushTip.Flat, BrushTip.Round, BrushTip.DryPaint), 22f, .67f, .16f, BrushDynamics(grain = .08f, shapeRatio = .42f, rotation = .25f, wetMix = .15f)),
        template("pastels", "Pastels", listOf("Soft Pastel", "Hard Pastel", "Oil Pastel", "Pastel Block", "Pastel Edge", "Dusty Pastel", "Chalk Pastel", "Smudged Pastel", "Crayon Pastel", "Pastel Grain"), listOf(BrushTip.Chalk, BrushTip.DryPaint), 28f, .58f, .18f, BrushDynamics(grain = .72f, scatter = .12f, rotation = .32f, hardness = .48f)),
        template("oils", "Oils", listOf("Filbert Oil", "Round Oil", "Flat Oil", "Palette Knife", "Heavy Impasto", "Dry Oil", "Glaze Oil", "Bristle Oil", "Loaded Oil", "Oil Smear"), listOf(BrushTip.Bristle, BrushTip.Flat, BrushTip.DryPaint), 34f, .78f, .13f, BrushDynamics(grain = .38f, rotation = .62f, shapeRatio = .58f, wetMix = .72f)),
        template("paints", "Paints", listOf("Dry Paint", "Round Paint", "Flat Paint", "Bristle Paint", "Canvas Drag", "Loaded Brush", "Soft Paint", "Rough Paint", "Fan Paint", "Expressive Paint"), listOf(BrushTip.DryPaint, BrushTip.Bristle, BrushTip.Flat), 30f, .68f, .15f, BrushDynamics(grain = .48f, rotation = .52f, wetMix = .38f)),
        template("gouache", "Gouache", listOf("Opaque Gouache", "Cream Gouache", "Dry Gouache", "Flat Gouache", "Detail Gouache", "Rough Gouache", "Loaded Gouache", "Soft Gouache", "Poster Gouache", "Granular Gouache"), listOf(BrushTip.Bristle, BrushTip.DryPaint, BrushTip.Flat), 26f, .82f, .14f, BrushDynamics(grain = .32f, rotation = .38f, hardness = .72f, wetMix = .28f)),
        template("watercolors", "Watercolors", listOf("Clear Wash", "Wet Round", "Water Bloom", "Granulating Wash", "Dry Watercolour", "Soft Glaze", "Edge Pool", "Water Brush", "Pigment Wash", "Loose Wash"), listOf(BrushTip.Water, BrushTip.SoftRound, BrushTip.DryPaint), 40f, .24f, .12f, BrushDynamics(grain = .22f, scatter = .08f, hardness = .18f, wetMix = .92f)),
        template("charcoals", "Charcoals", listOf("Vine Charcoal", "Compressed Charcoal", "Charcoal Block", "Charcoal Edge", "Powder Charcoal", "Soft Charcoal", "Hard Charcoal", "Charcoal Stick", "Rough Charcoal", "Smoked Charcoal"), listOf(BrushTip.Chalk, BrushTip.DryPaint), 24f, .62f, .16f, BrushDynamics(grain = .82f, scatter = .16f, rotation = .28f, hardness = .45f)),
        template("basics", "Basics", listOf("Hard Round", "Soft Round", "Hard Flat", "Soft Flat", "Monoline", "Pressure Round", "Taper Round", "Opaque Fill", "Smooth Shade", "Clean Edge"), listOf(BrushTip.Round, BrushTip.SoftRound, BrushTip.Flat), 18f, .86f, .12f, BrushDynamics(hardness = .82f)),
        template("lettering", "Lettering", listOf("Modern Script", "Classic Script", "Sign Writer", "Ribbon Letter", "Chalk Letter", "Brush Letter", "Italic Nib", "Bold Letter", "Bounce Script", "Outline Letter"), listOf(BrushTip.Flat, BrushTip.Round, BrushTip.Chalk), 16f, .92f, .10f, BrushDynamics(rotation = .45f, shapeRatio = .38f, hardness = .86f)),
        template("comics", "Comics", listOf("Comic Inker", "Panel Pen", "Manga Line", "Action Brush", "Tone Speckle", "Cape Shadow", "Speed Line", "Bubble Letter", "Hero Block", "Halftone Dot"), listOf(BrushTip.Round, BrushTip.Flat, BrushTip.Spray, BrushTip.Pixel), 12f, .9f, .12f, BrushDynamics(grain = .12f, scatter = .12f, hardness = .9f)),
        template("design", "Design", listOf("Shape Liner", "Vector Round", "Draft Grid", "Clean Chisel", "Pixel Edge", "Diagram Pen", "Solid Block", "Texture Fill", "Precision Dot", "Layout Marker"), listOf(BrushTip.Round, BrushTip.Flat, BrushTip.Pixel), 14f, .94f, .14f, BrushDynamics(hardness = .96f, shapeRatio = .72f)),
        template("grunge", "Grunge", listOf("Concrete Dust", "Rust Scatter", "Worn Edge", "Scraped Paint", "Ink Noise", "Dirty Roller", "Broken Chalk", "Paper Tear", "Speckled Wash", "Distressed Mark"), listOf(BrushTip.Spray, BrushTip.DryPaint, BrushTip.Chalk), 42f, .55f, .24f, BrushDynamics(grain = .82f, scatter = .58f, rotation = .72f, jitter = .48f, hardness = .52f)),
        template("street-art", "Street Art", listOf("Spray Can", "Fat Cap", "Skinny Cap", "Paint Drip", "Mop Marker", "Wall Chalk", "Stencil Edge", "Aerosol Dust", "Tag Marker", "Roller Fill"), listOf(BrushTip.Spray, BrushTip.Flat, BrushTip.DryPaint), 38f, .62f, .20f, BrushDynamics(grain = .35f, scatter = .55f, rotation = .25f, jitter = .38f, hardness = .62f)),
        template("digital", "Digital", listOf("Airbrush", "Pixel Pen", "Glow Shade", "Soft Render", "Hard Render", "Dither Pixel", "Concept Block", "Smooth Blend", "Neon Line", "Texture Pixel"), listOf(BrushTip.SoftRound, BrushTip.Pixel, BrushTip.Round, BrushTip.Flat), 28f, .48f, .13f, BrushDynamics(grain = .06f, hardness = .6f)),
        template("creative", "Creative", listOf("Confetti", "Ribbon Trail", "Orbit Dots", "Crystal Shard", "Cloud Maker", "Leaf Scatter", "Spark Trail", "Thread Line", "Prism Chalk", "Dream Texture"), listOf(BrushTip.Spray, BrushTip.Flat, BrushTip.SoftRound, BrushTip.Chalk), 32f, .63f, .22f, BrushDynamics(grain = .3f, scatter = .52f, rotation = .78f, jitter = .44f, hardness = .55f)),
        template("landscape", "Landscape", listOf("Terrain Block", "Distant Mountain", "Rock Texture", "Cloud Soft", "Cloud Edge", "Water Reflection", "Mist Glaze", "Ground Grain", "Snow Highlight", "Horizon Blend"), listOf(BrushTip.Flat, BrushTip.Bark, BrushTip.Chalk, BrushTip.SoftRound, BrushTip.Water), 46f, .58f, .28f, BrushDynamics(grain = .32f, scatter = .12f, rotation = .28f, shapeRatio = .5f, hardness = .5f, wetMix = .18f, jitter = .12f)),
        template("foliage", "Foliage", listOf("Leaf Cluster", "Fine Leaves", "Broad Leaves", "Bush Scatter", "Fern", "Pine Needles", "Grass Tuft", "Wild Grass", "Moss", "Blossom Scatter"), listOf(BrushTip.Leaf, BrushTip.Leaf, BrushTip.Leaf, BrushTip.Leaf, BrushTip.Grass, BrushTip.Grass, BrushTip.Grass, BrushTip.Grass, BrushTip.Chalk, BrushTip.Spray), 34f, .74f, .34f, BrushDynamics(grain = .28f, scatter = .58f, rotation = .9f, shapeRatio = .48f, hardness = .72f, jitter = .42f)),
        template("trees", "Trees", listOf("Bark Grain", "Trunk Block", "Branch Taper", "Twig Liner", "Pine Bough", "Canopy Fill", "Canopy Edge", "Roots", "Sapling", "Tree Shadow"), listOf(BrushTip.Bark, BrushTip.Bark, BrushTip.Bark, BrushTip.Grass, BrushTip.Grass, BrushTip.Leaf, BrushTip.Leaf, BrushTip.Bark, BrushTip.Grass, BrushTip.SoftRound), 38f, .78f, .3f, BrushDynamics(grain = .42f, scatter = .18f, rotation = .62f, shapeRatio = .4f, hardness = .76f, jitter = .22f)),
    )

    val categories: List<BrushCategory> = templates.map { it.category }
    val brushes: List<BrushDefinition> = templates.flatMap(::makeBrushes)

    private fun makeBrushes(template: CategoryTemplate): List<BrushDefinition> = template.names.mapIndexed { index, name ->
        val variant = index / 9f
        val id = legacyId(name) ?: "neo.${template.category.id}.${slug(name)}"
        val generated = BrushDefinition(
            id = id,
            name = name,
            spacing = template.baseSize * (template.spacingRatio + variant * .08f),
            baseSize = template.baseSize * (.72f + variant * .56f),
            opacity = (template.opacity + (variant - .5f) * .16f).coerceIn(.12f, 1f),
            tip = template.tips[index % template.tips.size],
            pressureSize = (.45f + (index % 4) * .16f).coerceAtMost(1f),
            pressureOpacity = (.22f + (index % 5) * .17f).coerceAtMost(1f),
            categoryId = template.category.id,
            dynamics = template.dynamics.copy(
                grain = (template.dynamics.grain + (index % 3) * .07f).coerceAtMost(1f),
                scatter = (template.dynamics.scatter + (index % 4) * .035f).coerceAtMost(1f),
                rotation = (template.dynamics.rotation + (index % 5) * .04f).coerceAtMost(1f),
                shapeRatio = (template.dynamics.shapeRatio - (index % 3) * .08f).coerceAtLeast(.1f),
                hardness = (template.dynamics.hardness - (index % 4) * .06f).coerceAtLeast(0f),
                wetMix = (template.dynamics.wetMix + (index % 3) * .04f).coerceAtMost(1f),
                jitter = (template.dynamics.jitter + (index % 4) * .03f).coerceAtMost(1f),
            ),
        )
        when (id) {
            "neo.pencil" -> generated.copy(spacing = .65f, baseSize = 4f, opacity = .75f,
                tip = BrushTip.Pencil, pressureSize = 1f, pressureOpacity = 1f, dynamics = BrushDynamics())
            "neo.ink" -> generated.copy(spacing = .9f, baseSize = 6f, opacity = 1f,
                tip = BrushTip.Round, pressureSize = 1f, pressureOpacity = .15f, dynamics = BrushDynamics())
            "neo.soft-round" -> generated.copy(spacing = 2f, baseSize = 32f, opacity = .18f,
                tip = BrushTip.SoftRound, pressureSize = .25f, pressureOpacity = 1f, dynamics = BrushDynamics())
            "neo.dry-paint" -> generated.copy(spacing = 2f, baseSize = 24f, opacity = .55f,
                tip = BrushTip.DryPaint, pressureSize = .6f, pressureOpacity = 1f, dynamics = BrushDynamics())
            "neo.flat-marker" -> generated.copy(spacing = 3f, baseSize = 20f, opacity = .65f,
                tip = BrushTip.Flat, pressureSize = 1f, pressureOpacity = 1f, dynamics = BrushDynamics())
            else -> generated
        }
    }

    private fun template(id: String, name: String, names: List<String>, tips: List<BrushTip>, baseSize: Float,
        opacity: Float, spacingRatio: Float, dynamics: BrushDynamics) = CategoryTemplate(
        BrushCategory(id, name), names.also { require(it.size == 10) }, tips, baseSize, opacity, spacingRatio, dynamics,
    )

    private fun legacyId(name: String): String? = when (name) {
        "Graphite Pencil" -> "neo.pencil"
        "Studio Ink" -> "neo.ink"
        "Airbrush" -> "neo.soft-round"
        "Dry Paint" -> "neo.dry-paint"
        "Flat Marker" -> "neo.flat-marker"
        else -> null
    }

    private fun slug(value: String): String = value.lowercase().replace(' ', '-').replace(Regex("[^a-z0-9-]"), "")
}
