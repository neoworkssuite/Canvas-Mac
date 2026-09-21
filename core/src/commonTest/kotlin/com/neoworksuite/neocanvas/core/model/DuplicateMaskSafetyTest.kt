package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DuplicateMaskSafetyTest {
    private val maskAddress = TileAddress("object-mask", 1, 2)

    private val document = CanvasDocument(
        id = "duplicate-mask-safety",
        width = 200,
        height = 120,
        layers = listOf(
            Layer(
                id = "object",
                name = "Object",
                payload = LayerPayload.TextObject("Masked"),
                mask = LayerMask("object-mask", setOf(maskAddress)),
            ),
            Layer(
                id = "copy-mask",
                name = "Reserved id",
                payload = LayerPayload.Raster(),
            ),
        ),
    )

    @Test
    fun ordinary_editable_layer_duplicate_copies_mask_addresses() {
        val command = DuplicateLayer("object", "object-copy", "Object copy")
        assertEquals(
            setOf(RasterTileCopy(maskAddress, TileAddress("object-copy-mask", 1, 2))),
            command.rasterTileCopies(document),
        )
        val copy = command.apply(document).layers.first { it.id == "object-copy" }
        assertEquals("object-copy-mask", copy.mask?.id)
        assertEquals(setOf(TileAddress("object-copy-mask", 1, 2)), copy.mask?.tileAddresses)
    }

    @Test
    fun batch_duplicate_rejects_layer_or_mask_id_collisions_before_copying_tiles() {
        assertFailsWith<IllegalArgumentException> {
            DuplicateEditableLayers(mapOf("object" to "object-mask")).rasterTileCopies(document)
        }
        assertFailsWith<IllegalArgumentException> {
            DuplicateEditableLayers(mapOf("object" to "copy")).rasterTileCopies(document)
        }
    }
}
