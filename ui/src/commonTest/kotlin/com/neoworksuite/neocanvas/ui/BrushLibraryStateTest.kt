package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.brushes.BuiltInBrushes
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.renderer.RasterColor
import com.neoworksuite.neocanvas.renderer.RasterPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrushLibraryStateTest {
    @Test
    fun usage_persistence_encoding_can_be_deferred_off_the_selection_path() {
        var deferred: (() -> ByteArray)? = null
        var persisted: ByteArray? = null
        val state = BrushLibraryState(
            onPersist = { persisted = it },
            onPersistDeferred = { deferred = it },
        )

        state.choose(BuiltInBrushes.ink)

        assertNull(persisted)
        val encode = assertNotNull(deferred)
        val restored = BrushLibraryState(initialSnapshot = encode())
        restored.showRecent()
        assertEquals(listOf(BuiltInBrushes.ink.id), restored.visibleBrushes.map { it.id })
    }

    @Test
    fun favourites_recents_and_custom_brushes_survive_snapshot_reload() {
        var persisted: ByteArray? = null
        val state = BrushLibraryState(onPersist = { persisted = it })
        state.toggleFavourite(BuiltInBrushes.pencil.id)
        state.choose(BuiltInBrushes.ink)
        val custom = state.saveCustom("My wet pencil", BuiltInBrushes.pencil.copy(
            dynamics = BuiltInBrushes.pencil.dynamics.copy(wetMix = .7f),
        ))

        val restored = BrushLibraryState(initialSnapshot = persisted)
        restored.showFavourites()
        assertEquals(listOf(BuiltInBrushes.pencil.id), restored.visibleBrushes.map { it.id })
        restored.showRecent()
        assertEquals(listOf(BuiltInBrushes.ink.id), restored.visibleBrushes.map { it.id })
        restored.selectCategory("custom")
        assertEquals(listOf(custom), restored.visibleBrushes)
    }

    @Test
    fun importing_a_colliding_or_invalid_brush_never_overwrites_the_catalogue() {
        val state = BrushLibraryState()
        val imported = state.importBrush(com.neoworksuite.neocanvas.brushes.NeoBrushCodec.encode(BuiltInBrushes.ink))
        assertTrue(imported.id.startsWith("user."))
        assertEquals("custom", imported.categoryId)
        assertEquals(BuiltInBrushes.ink, BuiltInBrushes.find("neo.ink"))
        val before = state.customBrushes.toList()
        kotlin.test.assertFailsWith<IllegalArgumentException> { state.importBrush("bad".encodeToByteArray()) }
        assertEquals(before, state.customBrushes)
    }

    @Test
    fun category_search_favourites_and_recents_filter_the_library() {
        val state = BrushLibraryState()
        state.selectCategory("inks")
        assertEquals(10, state.visibleBrushes.size)

        state.query = "marker"
        assertTrue(state.visibleBrushes.size >= 10)
        assertTrue(state.visibleBrushes.all { "marker" in it.name.lowercase() || it.categoryId == "markers" })

        val favourite = BuiltInBrushes.pencil
        state.toggleFavourite(favourite.id)
        state.showFavourites()
        assertEquals(listOf(favourite.id), state.visibleBrushes.map { it.id })

        state.choose(BuiltInBrushes.ink)
        state.choose(BuiltInBrushes.softRound)
        state.choose(BuiltInBrushes.ink)
        state.showRecent()
        assertEquals(listOf(BuiltInBrushes.ink.id, BuiltInBrushes.softRound.id), state.visibleBrushes.map { it.id })
    }

    @Test
    fun scratch_pad_draw_and_clear_are_isolated_from_the_artwork_history() {
        val editor = EditorState(DocumentHistory(CanvasDocument.blank(64, 64)))
        val original = editor.document
        val pad = BrushTestPadState(128, 72)

        pad.draw(
            listOf(RasterPoint(12f, 36f), RasterPoint(112f, 36f)),
            BuiltInBrushes.ink,
            RasterColor(40, 90, 180),
            12f,
            .8f,
        )

        assertFalse(pad.isEmpty)
        assertTrue(editor.tileStore.keys.isEmpty())
        assertEquals(original, editor.document)
        assertFalse(editor.canUndo)

        pad.clear()
        assertTrue(pad.isEmpty)
        assertFalse(editor.canUndo)
    }
}
