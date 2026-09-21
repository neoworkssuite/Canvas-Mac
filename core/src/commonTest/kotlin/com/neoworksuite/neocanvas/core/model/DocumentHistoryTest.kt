package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentHistoryTest {
    @Test
    fun blank_documents_receive_distinct_stable_ids() {
        val first = CanvasDocument.blank(100, 100)
        val second = CanvasDocument.blank(100, 100)

        assertFalse(first.id == second.id)
        assertEquals(first.id, first.copy().id)
    }

    @Test
    fun document_and_history_snapshots_do_not_share_caller_mutable_collections() {
        val suppliedTiles = mutableSetOf(TileAddress("layer-1", 0, 0))
        val suppliedLayers = mutableListOf(
            Layer("layer-1", "Ink", payload = LayerPayload.Raster(suppliedTiles)),
        )
        val document = CanvasDocument("document-1", 100, 100, suppliedLayers)
        val history = DocumentHistory(document)

        suppliedTiles += TileAddress("layer-1", 1, 0)
        suppliedLayers.clear()

        assertEquals(listOf("layer-1"), document.layers.map { it.id })
        assertEquals(setOf(TileAddress("layer-1", 0, 0)), (document.layers.single().payload as LayerPayload.Raster).tileAddresses)
        assertEquals(listOf("layer-1"), history.current.layers.map { it.id })
    }

    @Test
    fun document_and_history_collections_cannot_be_cast_to_mutable_collections() {
        val document = CanvasDocument(
            "document-1",
            100,
            100,
            listOf(Layer("layer-1", "Ink", payload = LayerPayload.Raster(setOf(TileAddress("layer-1", 0, 0))))),
        )
        val history = DocumentHistory(document)
        val raster = document.layers.single().payload as LayerPayload.Raster

        assertEquals(null, document.layers as? MutableList<Layer>)
        assertEquals(null, history.current.layers as? MutableList<Layer>)
        assertEquals(null, raster.tileAddresses as? MutableSet<TileAddress>)
        assertTrue(runCatching { (document.layers as MutableList<Layer>).clear() }.isFailure)
        assertTrue(runCatching { (raster.tileAddresses as MutableSet<TileAddress>).clear() }.isFailure)
        assertEquals(listOf("layer-1"), history.current.layers.map { it.id })
        assertEquals(setOf(TileAddress("layer-1", 0, 0)), raster.tileAddresses)
    }

    @Test
    fun undo_restores_the_previous_layer_list() {
        val history = DocumentHistory(CanvasDocument.blank(100, 100))

        history.execute(AddRasterLayer("layer-1", "Ink"))
        assertTrue(history.undo())

        assertTrue(history.current.layers.isEmpty())
    }

    @Test
    fun opacity_outside_zero_to_one_is_rejected() {
        assertFailsWith<IllegalArgumentException> { SetLayerOpacity("layer-1", 1.1f) }
    }

    @Test
    fun setting_opacity_is_undoable() {
        val history = historyWithTwoLayers()

        history.execute(SetLayerOpacity("layer-1", 0.4f))
        assertEquals(0.4f, history.current.layers.first().opacity)

        assertTrue(history.undo())
        assertEquals(1f, history.current.layers.first().opacity)
    }

    @Test
    fun rename_changes_only_the_target_layer() {
        val history = historyWithTwoLayers()

        history.execute(RenameLayer("layer-2", "Colour"))

        assertEquals(listOf("Ink", "Colour"), history.current.layers.map { it.name })
    }

    @Test
    fun moving_layer_changes_its_order_and_undo_restores_it() {
        val history = historyWithTwoLayers()

        history.execute(MoveLayer("layer-2", 0))
        assertEquals(listOf("layer-2", "layer-1"), history.current.layers.map { it.id })

        assertTrue(history.undo())
        assertEquals(listOf("layer-1", "layer-2"), history.current.layers.map { it.id })
    }

    @Test
    fun setting_visibility_is_undoable() {
        val history = historyWithTwoLayers()

        history.execute(SetLayerVisibility("layer-1", false))
        assertFalse(history.current.layers.first().visible)

        assertTrue(history.undo())
        assertTrue(history.current.layers.first().visible)
    }

    @Test
    fun groups_and_masks_are_undoable_document_metadata() {
        val history = historyWithTwoLayers()

        history.execute(AddLayerGroup("group-1", "Characters"))
        history.execute(SetLayerGroupMembership("layer-2", "group-1"))
        history.execute(SetLayerGroupOpacity("group-1", .6f))
        history.execute(AddLayerMask("layer-2", "mask-layer-2"))
        history.execute(
            ApplyLayerMaskPatch(
                "layer-2",
                "mask-layer-2",
                addedTileAddresses = setOf(TileAddress("mask-layer-2", 0, 0)),
            ),
        )

        assertEquals("group-1", history.current.layers[1].groupId)
        assertEquals(.6f, history.current.groups.single().opacity)
        assertEquals(
            setOf(TileAddress("mask-layer-2", 0, 0)),
            history.current.layers[1].mask!!.tileAddresses,
        )

        assertTrue(history.undo())
        assertTrue(history.current.layers[1].mask!!.tileAddresses.isEmpty())

        history.execute(DeleteLayerGroup("group-1"))
        assertTrue(history.current.groups.isEmpty())
        assertEquals(null, history.current.layers[1].groupId)
    }

    @Test
    fun editable_text_and_shape_layers_are_undoable_and_duplicate_without_raster_copies() {
        val history = DocumentHistory(CanvasDocument.blank(800, 600))
        val text = LayerPayload.TextObject(
            text = "NeoCanvas",
            fontSize = 64f,
            colorArgb = 0xff336699.toInt(),
            x = 120f, y = 80f, width = 420f, height = 120f,
            alignment = TextAlignment.Center,
        )
        history.execute(AddTextLayer("text-1", "Title", text))
        history.execute(UpdateTextLayer("text-1", text.copy(text = "NeoCanvas Pro", rotationDegrees = 12f)))
        val shape = LayerPayload.ShapeObject(
            kind = ShapeKind.Ellipse,
            x = 40f, y = 220f, width = 260f, height = 180f,
            fillArgb = 0xffff6600.toInt(),
            strokeArgb = 0xff202020.toInt(),
            strokeWidth = 6f,
        )
        history.execute(AddShapeLayer("shape-1", "Badge", shape))

        assertEquals("NeoCanvas Pro", (history.current.layers[0].payload as LayerPayload.TextObject).text)
        assertEquals(ShapeKind.Ellipse, (history.current.layers[1].payload as LayerPayload.ShapeObject).kind)

        val duplicate = DuplicateLayer("text-1", "text-2", "Title copy")
        assertTrue(duplicate.rasterTileCopies(history.current).isEmpty())
        history.execute(duplicate)
        assertEquals(text.copy(text = "NeoCanvas Pro", rotationDegrees = 12f), history.current.layers[1].payload)
        assertTrue(history.undo())
        assertEquals(2, history.current.layers.size)
    }

    @Test
    fun duplicate_creates_an_independent_raster_layer_after_source() {
        val history = DocumentHistory(
            CanvasDocument.blank(100, 100).copy(
                layers = listOf(
                    Layer("layer-1", "Ink", payload = LayerPayload.Raster(setOf(TileAddress("layer-1", 0, 0)))),
                ),
            ),
        )

        history.execute(DuplicateLayer("layer-1", "layer-2", "Ink copy"))

        assertEquals(listOf("layer-1", "layer-2"), history.current.layers.map { it.id })
        assertEquals("Ink copy", history.current.layers[1].name)
        assertEquals(setOf(TileAddress("layer-2", 0, 0)), (history.current.layers[1].payload as LayerPayload.Raster).tileAddresses)
    }

    @Test
    fun deleting_a_layer_is_undoable() {
        val history = historyWithTwoLayers()

        history.execute(DeleteLayer("layer-1"))
        assertEquals(listOf("layer-2"), history.current.layers.map { it.id })

        assertTrue(history.undo())
        assertEquals(listOf("layer-1", "layer-2"), history.current.layers.map { it.id })
    }

    @Test
    fun executing_after_undo_discards_the_redo_branch() {
        val history = historyWithTwoLayers()
        history.execute(DeleteLayer("layer-2"))
        assertTrue(history.undo())

        history.execute(RenameLayer("layer-1", "Line"))

        assertFalse(history.redo())
        assertEquals("Line", history.current.layers.first().name)
    }

    @Test
    fun redo_reapplies_the_undone_command() {
        val history = DocumentHistory(CanvasDocument.blank(100, 100))
        history.execute(AddRasterLayer("layer-1", "Ink"))

        assertTrue(history.undo())
        assertTrue(history.redo())

        assertEquals(listOf("layer-1"), history.current.layers.map { it.id })
    }

    @Test
    fun empty_undo_and_redo_report_no_state_change() {
        val history = DocumentHistory(CanvasDocument.blank(100, 100))

        assertFalse(history.undo())
        assertFalse(history.redo())
    }

    @Test
    fun duplicate_exposes_source_to_destination_raster_tile_copies() {
        val document = CanvasDocument(
            "document-1",
            100,
            100,
            listOf(
                Layer(
                    "layer-1",
                    "Ink",
                    payload = LayerPayload.Raster(
                        setOf(TileAddress("layer-1", 0, 0), TileAddress("layer-1", 1, 0)),
                    ),
                ),
            ),
        )
        val command = DuplicateLayer("layer-1", "layer-2", "Ink copy")

        assertEquals(
            setOf(
                RasterTileCopy(TileAddress("layer-1", 0, 0), TileAddress("layer-2", 0, 0)),
                RasterTileCopy(TileAddress("layer-1", 1, 0), TileAddress("layer-2", 1, 0)),
            ),
            command.rasterTileCopies(document),
        )
        assertEquals(
            setOf(TileAddress("layer-2", 0, 0), TileAddress("layer-2", 1, 0)),
            ((command.apply(document).layers[1].payload as LayerPayload.Raster).tileAddresses),
        )
    }

    @Test
    fun duplicate_does_not_plan_tile_copies_when_destination_id_already_exists() {
        val document = CanvasDocument(
            "document-1",
            100,
            100,
            listOf(
                Layer("layer-1", "Ink", payload = LayerPayload.Raster(setOf(TileAddress("layer-1", 0, 0)))),
                Layer("layer-2", "Existing", payload = LayerPayload.Raster()),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            DuplicateLayer("layer-1", "layer-2", "Ink copy").rasterTileCopies(document)
        }
    }

    private fun historyWithTwoLayers(): DocumentHistory = DocumentHistory(CanvasDocument.blank(100, 100)).also {
        it.execute(AddRasterLayer("layer-1", "Ink"))
        it.execute(AddRasterLayer("layer-2", "Paint"))
    }
}
