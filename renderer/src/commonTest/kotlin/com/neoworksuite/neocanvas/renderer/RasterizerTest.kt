package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.brushes.BrushMode
import com.neoworksuite.neocanvas.core.model.TileAddress
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.neoworksuite.neocanvas.brushes.BuiltInBrushes

class RasterizerTest {
    @Test fun alpha_lock_recolours_existing_pixels_without_creating_or_erasing_alpha() {
        val store = TileStore()
        val point = listOf(RasterPoint(8.5f, 8.5f))
        store.applyPatch(Rasterizer.stroke(store, "a", point, RasterColor(20, 30, 40),
            1f, .5f, BrushMode.PAINT, 32, 32))
        val before = store.read(TileAddress("a", 0, 0))!!
        val offset = (8 * 256 + 8) * 4
        val alpha = before[offset + 3]
        store.applyPatch(Rasterizer.stroke(store, "a", point, RasterColor(200, 100, 50),
            1f, 1f, BrushMode.PAINT, 32, 32, alphaLocked = true))
        val painted = store.read(TileAddress("a", 0, 0))!!
        assertEquals(alpha, painted[offset + 3])
        assertEquals(200, painted[offset].toInt() and 255)
        store.applyPatch(Rasterizer.stroke(store, "a", point, RasterColor(0, 0, 0),
            1f, 1f, BrushMode.ERASE, 32, 32, alphaLocked = true))
        assertTrue(painted.contentEquals(store.read(TileAddress("a", 0, 0))!!))
        val blank = TileStore()
        assertTrue(Rasterizer.stroke(blank, "a", point, RasterColor(1, 2, 3),
            1f, 1f, BrushMode.PAINT, 32, 32, alphaLocked = true).keys.isEmpty())
    }

    @Test fun four_way_symmetry_draws_all_quadrants_and_respects_selection() {
        val store = TileStore()
        store.applyPatch(Rasterizer.stroke(store, "a", listOf(RasterPoint(4.5f, 6.5f)),
            RasterColor(10, 20, 30), 1f, 1f, BrushMode.PAINT, 32, 32, symmetry = DrawingSymmetry.Both))
        val bytes = store.read(TileAddress("a", 0, 0))!!
        for ((x, y) in listOf(4 to 6, 27 to 6, 4 to 25, 27 to 25))
            assertEquals(255, bytes[(y * 256 + x) * 4 + 3].toInt() and 255)
        val restricted = TileStore()
        restricted.applyPatch(Rasterizer.stroke(restricted, "a", listOf(RasterPoint(4.5f, 6.5f)),
            RasterColor(10, 20, 30), 1f, 1f, BrushMode.PAINT, 32, 32,
            acceptsPixel = { x, _ -> x < 16 }, symmetry = DrawingSymmetry.Both))
        val selected = restricted.read(TileAddress("a", 0, 0))!!
        assertEquals(0, selected[(6 * 256 + 27) * 4 + 3].toInt())
    }

    @Test fun symmetry_on_axis_does_not_apply_opacity_twice() {
        val store = TileStore()
        store.applyPatch(Rasterizer.stroke(store, "a", listOf(RasterPoint(16f, 16f)),
            RasterColor(10, 20, 30), 4f, .5f, BrushMode.PAINT, 32, 32, symmetry = DrawingSymmetry.Both))
        val bytes = store.read(TileAddress("a", 0, 0))!!
        assertEquals(127, bytes[(16 * 256 + 16) * 4 + 3].toInt() and 255)
    }
    @Test fun soft_brush_has_fading_edges_and_marker_has_flat_footprint() {
        fun paint(brush: com.neoworksuite.neocanvas.brushes.BrushDefinition): ByteArray {
            val store = TileStore()
            store.applyPatch(Rasterizer.stroke(store, "a", listOf(RasterPoint(16.5f, 16.5f)),
                RasterColor(0, 0, 0), 20f, 1f, BrushMode.PAINT, 64, 64, brush = brush))
            return store.read(TileAddress("a", 0, 0))!!
        }
        fun alpha(bytes: ByteArray, x: Int, y: Int) = bytes[(y * 256 + x) * 4 + 3].toInt() and 255
        val soft = paint(BuiltInBrushes.softRound)
        assertTrue(alpha(soft, 16, 16) > alpha(soft, 24, 16))
        assertTrue(alpha(soft, 24, 16) > 0)
        val flat = paint(BuiltInBrushes.flatMarker)
        assertEquals(255, alpha(flat, 24, 16))
        assertEquals(0, alpha(flat, 16, 24))
        val pencil = paint(BuiltInBrushes.pencil)
        val ink = paint(BuiltInBrushes.ink)
        assertTrue(!pencil.contentEquals(ink))
        assertTrue(pencil.contentEquals(paint(BuiltInBrushes.pencil)))
    }

    @Test fun partial_eraser_reduces_alpha_without_changing_colour() {
        val store = TileStore()
        val point = listOf(RasterPoint(8f, 8f))
        store.applyPatch(Rasterizer.stroke(store, "a", point, RasterColor(50, 100, 150),
            8f, 1f, BrushMode.PAINT, 64, 64))
        store.applyPatch(Rasterizer.stroke(store, "a", point, RasterColor(0, 0, 0),
            8f, .5f, BrushMode.ERASE, 64, 64))
        val bytes = store.read(TileAddress("a", 0, 0))!!
        val offset = (8 * 256 + 8) * 4
        assertEquals(127, bytes[offset + 3].toInt() and 255)
        assertEquals(50, bytes[offset].toInt() and 255)
    }
    @Test
    fun stroke_allocates_the_correct_tile_and_eraser_releases_an_empty_tile() {
        val store = TileStore()
        val key = TileAddress("ink", 1, 0)
        store.applyPatch(
            Rasterizer.stroke(
                store, "ink", listOf(RasterPoint(256f, 8f)), RasterColor(20, 30, 40),
                size = 4f, opacity = 1f, mode = BrushMode.PAINT, canvasWidth = 512, canvasHeight = 512,
            ),
        )
        assertTrue(key in store.keys)
        store.applyPatch(
            Rasterizer.stroke(
                store, "ink", listOf(RasterPoint(256f, 8f)), RasterColor(0, 0, 0),
                size = 4f, opacity = 1f, mode = BrushMode.ERASE, canvasWidth = 512, canvasHeight = 512,
            ),
        )
        assertTrue(key !in store.keys)
    }
}
