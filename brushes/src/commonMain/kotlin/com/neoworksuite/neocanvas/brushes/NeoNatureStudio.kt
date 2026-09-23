package com.neoworksuite.neocanvas.brushes

/** NeoWorks' original, free landscape brush pack. All variation is bounded and deterministic. */
object NeoNatureStudio {
    private data class Recipe(
        val name: String, val tip: BrushTip, val size: Float, val spacing: Float,
        val grain: Float, val scatter: Float, val ratio: Float, val count: Int,
    )

    private val recipes = listOf(
        Recipe("Oak Canopy", BrushTip.Leaf, 72f, 17f, .24f, .42f, .72f, 5),
        Recipe("Distant Tree Line", BrushTip.Bark, 58f, 28f, .35f, .12f, .48f, 3),
        Recipe("Pine Tree Builder", BrushTip.Grass, 82f, 22f, .18f, .16f, .38f, 4),
        Recipe("Pine Bough", BrushTip.Grass, 44f, 9f, .30f, .28f, .24f, 5),
        Recipe("Branch and Twig", BrushTip.Bark, 18f, 3f, .42f, .08f, .18f, 1),
        Recipe("Rough Bark", BrushTip.Bark, 54f, 11f, .72f, .06f, .32f, 2),
        Recipe("Dense Leaf Cluster", BrushTip.Leaf, 52f, 8f, .20f, .58f, .68f, 7),
        Recipe("Fine Leaves", BrushTip.Leaf, 18f, 6f, .28f, .74f, .42f, 3),
        Recipe("Broad Tropical Leaves", BrushTip.Leaf, 84f, 31f, .12f, .30f, .54f, 2),
        Recipe("Hedge Builder", BrushTip.DryPaint, 66f, 12f, .46f, .40f, .78f, 6),
        Recipe("Fern", BrushTip.Grass, 46f, 7f, .22f, .18f, .30f, 6),
        Recipe("Moss and Ground Cover", BrushTip.Spray, 38f, 10f, .64f, .82f, .92f, 8),
        Recipe("Wild Grass", BrushTip.Grass, 56f, 8f, .26f, .44f, .18f, 5),
        Recipe("Meadow Grass", BrushTip.Grass, 34f, 5f, .18f, .34f, .28f, 7),
        Recipe("Rock and Gravel", BrushTip.Chalk, 42f, 15f, .58f, .76f, .70f, 4),
        Recipe("Cloud Builder", BrushTip.SoftRound, 94f, 20f, .12f, .22f, .84f, 5),
        Recipe("Mountain Texture", BrushTip.DryPaint, 68f, 13f, .68f, .18f, .36f, 3),
        Recipe("Water Reflection", BrushTip.Flat, 48f, 7f, .38f, .32f, .16f, 4),
    )

    val pack: NeoBrushPack by lazy {
        val brushes = linkedMapOf<String, BrushDefinition>()
        recipes.forEachIndexed { index, recipe ->
            val id = "nature.${recipe.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}"
            brushes[id] = BrushDefinition(
                id = id, name = recipe.name, spacing = recipe.spacing, baseSize = recipe.size, opacity = .82f,
                version = 2, tip = recipe.tip, categoryId = "com.neoworks.nature-studio",
                pressureSize = .72f, pressureOpacity = .58f,
                dynamics = BrushDynamics(recipe.grain, recipe.scatter, ((index + 1) % 9) / 10f, recipe.ratio,
                    .34f + (index % 5) * .1f, if (recipe.tip == BrushTip.SoftRound) .42f else 0f, (index % 7) / 10f),
                stamp = BrushStamp(
                    angleMode = if (recipe.tip in setOf(BrushTip.Grass, BrushTip.Bark, BrushTip.Flat)) StampAngleMode.DirectionJitter else StampAngleMode.Randomized,
                    angleJitter = .08f + (index % 4) * .12f, scaleX = 1f, scaleY = recipe.ratio,
                    spacingRatio = (recipe.spacing / recipe.size).coerceIn(.05f, 1f), scatterAlong = recipe.scatter * .35f,
                    scatterAcross = recipe.scatter, stampCount = recipe.count, stampCountJitter = .18f,
                    hueJitter = (index % 3) * .008f, saturationJitter = .04f, brightnessJitter = .05f,
                    pressureScatter = .45f, pressureStampCount = .62f, startTaper = .12f, endTaper = .16f,
                ),
            )
        }
        NeoBrushPack(
            NeoBrushPackManifest(
                id = "com.neoworks.nature-studio", version = "1.0.0", name = "Neo Nature Studio",
                summary = "18 original tree, foliage and landscape brushes for NeoCanvas.", author = "NeoWorks",
                website = "https://neoworkssuite.com/neocanvas/brushes", licence = "NeoWorks Free Brush Pack Licence",
                minimumAppVersion = "1.0.0", brushIds = brushes.keys.toList(),
            ),
            brushes,
        )
    }
}
