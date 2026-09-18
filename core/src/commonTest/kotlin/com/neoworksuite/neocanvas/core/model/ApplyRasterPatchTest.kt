package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApplyRasterPatchTest {
    @Test
    fun applying_a_raster_patch_adds_the_touched_tiles_and_is_undoable() {
        val history = DocumentHistory(CanvasDocument.blank(100, 100)).also {
            it.execute(AddRasterLayer("layer-1", "Ink"))
        }

        history.execute(ApplyRasterPatch("layer-1", setOf(TileAddress("layer-1", 0, 0))))

        val raster = history.current.layers.single().payload as LayerPayload.Raster
        assertEquals(setOf(TileAddress("layer-1", 0, 0)), raster.tileAddresses)
        assertTrue(history.undo())
        assertEquals(emptySet(), (history.current.layers.single().payload as LayerPayload.Raster).tileAddresses)
    }

    @Test
    fun raster_patch_rejects_tile_addresses_for_a_different_layer() {
        assertFailsWith<IllegalArgumentException> {
            ApplyRasterPatch("layer-1", setOf(TileAddress("layer-2", 0, 0)))
        }
    }

    @Test
    fun removing_a_raster_tile_is_undoable() {
        val first = TileAddress("layer-1", 0, 0)
        val second = TileAddress("layer-1", 1, 0)
        val history = DocumentHistory(
            CanvasDocument(
                "document-1",
                100,
                100,
                listOf(Layer("layer-1", "Ink", payload = LayerPayload.Raster(setOf(first, second)))),
            ),
        )

        history.execute(ApplyRasterPatch("layer-1", removedTileAddresses = setOf(first)))

        assertEquals(setOf(second), (history.current.layers.single().payload as LayerPayload.Raster).tileAddresses)
        assertTrue(history.undo())
        assertEquals(setOf(first, second), (history.current.layers.single().payload as LayerPayload.Raster).tileAddresses)
    }
}
