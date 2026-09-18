package com.neoworksuite.neocanvas.brushes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BrushCatalogTest {
    @Test
    fun library_has_eighteen_ordered_categories_with_ten_paint_brushes_each() {
        assertEquals(18, BuiltInBrushes.categories.size)
        assertEquals(180, BuiltInBrushes.paintBrushes.size)
        BuiltInBrushes.categories.forEach { category ->
            assertEquals(10, BuiltInBrushes.inCategory(category.id).size, category.name)
        }
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
