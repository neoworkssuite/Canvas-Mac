package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.brushes.*
import kotlin.test.*

class NeoNatureStudioQualityTest {
    @Test fun every_nature_brush_is_deterministic_non_empty_and_visually_distinct() {
        val signatures = linkedSetOf<Int>()
        NeoNatureStudio.pack.brushes.values.forEach { brush ->
            fun render(): ByteArray {
                val store = TileStore()
                store.applyPatch(Rasterizer.stroke(store, "n", listOf(RasterPoint(20f, 64f, .25f), RasterPoint(220f, 64f, 1f)),
                    RasterColor(30, 90, 45), brush.baseSize, brush.opacity, BrushMode.PAINT, 256, 128, brush = brush))
                return store.read(TileKey("n", 0, 0)) ?: ByteArray(0)
            }
            val first = render(); val second = render()
            assertContentEquals(first, second, brush.name)
            assertTrue(first.any { it.toInt() != 0 }, brush.name)
            signatures += first.contentHashCode()
        }
        assertEquals(18, signatures.size)
    }
}
