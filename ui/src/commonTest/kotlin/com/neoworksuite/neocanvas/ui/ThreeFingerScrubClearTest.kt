package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.renderer.TileFormat
import com.neoworksuite.neocanvas.renderer.TileKey
import com.neoworksuite.neocanvas.renderer.TileStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreeFingerScrubClearTest {
    @Test
    fun scrub_clear_requires_multiple_direction_changes_and_real_travel() {
        assertTrue(isThreeFingerScrubClear(
            maxTouchCount = 3,
            durationMillis = 720,
            reversals = 3,
            horizontalTravel = 130f,
            touchSlop = 10f,
            stylusSeen = false,
        ))
        assertFalse(isThreeFingerScrubClear(3, 720, 1, 130f, 10f, false))
        assertFalse(isThreeFingerScrubClear(3, 720, 3, 30f, 10f, false))
        assertFalse(isThreeFingerScrubClear(2, 720, 3, 130f, 10f, false))
        assertFalse(isThreeFingerScrubClear(3, 720, 3, 130f, 10f, true))
    }

    @Test
    fun clear_active_layer_is_one_undoable_raster_edit() {
        val key = TileKey("paint", 0, 0)
        val bytes = ByteArray(TileFormat.BYTES_PER_TILE)
        val offset = (10 * 256 + 10) * 4
        bytes[offset] = 220.toByte()
        bytes[offset + 3] = 255.toByte()
        val state = EditorState(
            DocumentHistory(
                CanvasDocument(
                    id = "scrub-clear",
                    width = 64,
                    height = 64,
                    layers = listOf(Layer("paint", "Paint", payload = LayerPayload.Raster(setOf(key)))),
                ),
            ),
            tileStore = TileStore(mapOf(key to bytes)),
        )
        state.activeLayerId = "paint"

        assertTrue(state.clearActiveRasterLayer())
        assertTrue(state.tileStore.keys.none { it.layerId == "paint" })
        assertTrue(state.undo())
        assertTrue(state.tileStore.read(key)?.contentEquals(bytes) == true)
    }
}
