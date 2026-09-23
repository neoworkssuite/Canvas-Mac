package com.neoworksuite.neocanvas.brushes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BrushCatalogTest {
    @Test
    fun library_has_twenty_one_ordered_categories_with_ten_paint_brushes_each() {
        assertEquals(21, BuiltInBrushes.categories.size)
        assertEquals(210, BuiltInBrushes.paintBrushes.size)
        BuiltInBrushes.categories.forEach { category ->
            assertEquals(10, BuiltInBrushes.inCategory(category.id).size, category.name)
        }
    }

    @Test
    fun nature_categories_offer_landscape_foliage_and_tree_tools() {
        assertEquals(
            listOf("Landscape", "Foliage", "Trees"),
            BuiltInBrushes.categories.takeLast(3).map { it.name },
        )
        assertTrue(BuiltInBrushes.inCategory("landscape").any { it.name == "Water Reflection" })
        assertTrue(BuiltInBrushes.inCategory("foliage").any { it.name == "Leaf Cluster" && it.tip == BrushTip.Leaf })
        assertTrue(BuiltInBrushes.inCategory("foliage").any { it.name == "Grass Tuft" && it.tip == BrushTip.Grass })
        assertTrue(BuiltInBrushes.inCategory("trees").any { it.name == "Bark Grain" && it.tip == BrushTip.Bark })
    }

    @Test
    fun brush_and_category_ids_are_unique_and_legacy_ids_remain_resolvable() {
        assertEquals(BuiltInBrushes.categories.size, BuiltInBrushes.categories.map { it.id }.toSet().size)
        assertEquals(BuiltInBrushes.brushes.size, BuiltInBrushes.brushes.map { it.id }.toSet().size)
        listOf("neo.pencil", "neo.ink", "neo.soft-round", "neo.dry-paint", "neo.flat-marker", "neo.eraser")
            .forEach { assertTrue(BuiltInBrushes.find(it) != null, it) }
    }

    @Test
    fun search_matches_names_and_categories_without_case_sensitivity() {
        assertTrue(BuiltInBrushes.search("GRAPHITE").any { it.id == "neo.pencil" })
        assertEquals(10, BuiltInBrushes.search("watercolors").size)
        assertEquals(BuiltInBrushes.paintBrushes, BuiltInBrushes.search(""))
    }

    @Test
    fun dynamics_reject_values_outside_supported_ranges() {
        assertFailsWith<IllegalArgumentException> { BrushDynamics(grain = 1.1f) }
        assertFailsWith<IllegalArgumentException> { BrushDynamics(shapeRatio = 0.05f) }
        assertFailsWith<IllegalArgumentException> { BrushDynamics(scatter = -0.1f) }
    }
}
