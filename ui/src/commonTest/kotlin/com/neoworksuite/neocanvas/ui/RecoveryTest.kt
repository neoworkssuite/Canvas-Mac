package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.*
import com.neoworksuite.neocanvas.core.store.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RecoveryTest {
    @Test fun unreadable_recovery_is_not_overwritten_until_user_starts_fresh() = runBlocking {
        var writes = 0
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsRecovery = true
            override fun loadRecovery(): LoadResult = LoadResult.Corrupt("Invalid package")
            override fun saveRecovery(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
                writes++
                return SaveResult.Success
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.checkRecovery()
        state.addLayer()
        state.autosaveRecovery()
        assertEquals(0, writes)
        state.restoreRecovery()
        assertEquals(16, state.document.width)
        state.dismissRecovery()
        state.autosaveRecovery()
        assertEquals(1, writes)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test fun autosave_retries_failure_and_never_marks_manual_document_saved() = runBlocking {
        var writes = 0
        var fail = true
        var stored: LoadResult.Success? = null
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsRecovery = true
            override fun saveRecovery(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
                writes++
                if (fail) return SaveResult.Failure("Disk full")
                stored = LoadResult.Success(document, tiles)
                return SaveResult.Success
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.checkRecovery()
        state.autosaveRecovery()
        assertEquals(0, writes)
        state.insertImage(ImportedImage("Art", 1, 1, intArrayOf(0xFFFF0000.toInt())))
        state.autosaveRecovery()
        assertEquals(1, writes)
        assertTrue(state.hasUnsavedChanges)
        fail = false
        state.autosaveRecovery()
        state.autosaveRecovery()
        assertEquals(2, writes)
        assertTrue(state.hasUnsavedChanges)
        assertEquals("Art", stored!!.document.layers.single().name)
        assertTrue(stored!!.tiles.isNotEmpty())
    }

    @Test fun startup_offers_recovery_without_replacing_canvas_and_restores_as_unsaved() = runBlocking {
        val recovered = CanvasDocument.blank(64, 32)
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsRecovery = true
            override fun loadRecovery(): LoadResult = LoadResult.Success(recovered, emptyMap())
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.checkRecovery()
        assertEquals(16, state.document.width)
        assertNotNull(state.recoveryCandidate)
        state.restoreRecovery()
        assertEquals(64, state.document.width)
        assertTrue(state.hasUnsavedChanges)
        assertNull(state.recoveryCandidate)
    }
}
