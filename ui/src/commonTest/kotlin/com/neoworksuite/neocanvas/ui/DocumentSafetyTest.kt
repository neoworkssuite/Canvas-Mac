package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.*
import com.neoworksuite.neocanvas.core.store.*
import kotlin.test.*

class DocumentSafetyTest {
    @Test fun close_cancel_and_failed_save_keep_editor_open() {
        var closed = 0
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.requestClose { closed++ }
        assertEquals(0, closed)
        assertEquals(PendingDocumentAction.Close, state.pendingDocumentAction)
        state.saveAndContinue() // No storage host: failure must not close.
        assertEquals(0, closed)
        state.cancelDocumentAction()
        state.discardAndContinue() // Stale callback must not survive cancellation.
        assertEquals(0, closed)
        state.requestClose { closed++ }
        state.discardAndContinue()
        state.discardAndContinue()
        assertEquals(1, closed)
    }

    @Test fun clean_close_is_immediate_and_save_then_close_saves_artwork() {
        var savedLayers = 0
        var closed = 0
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
                savedLayers = document.layers.size
                return SaveResult.Success
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.requestClose { closed++ }
        assertEquals(1, closed)
        state.addLayer()
        state.requestClose { closed++ }
        assertEquals(1, closed)
        state.saveAndContinue()
        assertEquals(1, savedLayers)
        assertEquals(2, closed)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test fun new_waits_for_confirmation_and_cancel_keeps_artwork() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        val original = state.document
        state.newDocument()
        assertEquals(original, state.document)
        assertEquals(PendingDocumentAction.New, state.pendingDocumentAction)
        state.cancelDocumentAction()
        assertNull(state.pendingDocumentAction)
        assertTrue(state.hasUnsavedChanges)
        state.newDocument()
        state.discardAndContinue()
        assertNotEquals(original.id, state.document.id)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test fun failed_save_blocks_open_and_successful_save_allows_it() {
        var fail = true
        var opens = 0
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
                if (fail) SaveResult.Failure("Disk full") else SaveResult.Success
            override fun open(): LoadResult {
                opens++
                return LoadResult.Success(CanvasDocument.blank(32, 32), emptyMap())
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.addLayer()
        state.open()
        assertEquals(0, opens)
        state.saveAndContinue()
        assertEquals(0, opens)
        assertEquals("Disk full", state.documentActionError)
        assertTrue(state.hasUnsavedChanges)
        assertEquals(PendingDocumentAction.Open, state.pendingDocumentAction)
        fail = false
        state.saveAndContinue()
        assertEquals(1, opens)
        assertEquals(32, state.document.width)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test fun undo_returns_to_saved_revision_but_new_branch_is_dirty() {
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>) = SaveResult.Success
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.addLayer()
        state.save()
        assertFalse(state.hasUnsavedChanges)
        state.addLayer()
        assertTrue(state.hasUnsavedChanges)
        state.undo()
        assertFalse(state.hasUnsavedChanges)
        state.undo()
        assertTrue(state.hasUnsavedChanges)
        state.addLayer()
        assertTrue(state.hasUnsavedChanges)
    }
}
