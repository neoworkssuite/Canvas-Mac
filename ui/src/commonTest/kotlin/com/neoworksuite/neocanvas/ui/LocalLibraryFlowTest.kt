package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.*
import com.neoworksuite.neocanvas.core.store.*
import kotlin.test.*

class LocalLibraryFlowTest {
    @Test fun library_open_waits_for_discard_and_cancel_retains_edits() {
        var opens = 0
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsLocalLibrary = true
            override fun listLocalDocuments() = listOf("Painting.neocanvas")
            override fun openLocalDocument(name: String): LoadResult {
                opens++
                return LoadResult.Success(CanvasDocument.blank(32, 32), emptyMap())
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.addLayer()
        state.open()
        assertNull(state.localDocuments)
        state.discardAndContinue()
        assertEquals(listOf("Painting.neocanvas"), state.localDocuments)
        state.closeLocalLibrary()
        assertEquals(16, state.document.width)
        assertTrue(state.hasUnsavedChanges)
        state.open()
        state.discardAndContinue()
        state.openLocalDocument("not-listed.neocanvas")
        assertEquals(0, opens)
        state.openLocalDocument("Painting.neocanvas")
        assertEquals(1, opens)
        assertEquals(32, state.document.width)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test fun named_copy_failure_keeps_dialog_and_dirty_state_until_success() {
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsLocalLibrary = true
            override fun saveNamedCopy(name: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
                if (name == "New name") SaveResult.Success else SaveResult.Failure("Duplicate name")
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.addLayer()
        state.saveAs()
        state.saveNamedCopy("Old name")
        assertTrue(state.namingLocalCopy)
        assertTrue(state.hasUnsavedChanges)
        assertEquals("Duplicate name", state.libraryError)
        state.saveNamedCopy("New name")
        assertFalse(state.namingLocalCopy)
        assertFalse(state.hasUnsavedChanges)
    }
}
