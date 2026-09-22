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

    @Test fun successful_manual_save_retires_stale_recovery_snapshot() = runBlocking {
        var clears = 0
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsRecovery = true
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
                SaveResult.Success
            override fun clearRecovery(): SaveResult {
                clears++
                return SaveResult.Success
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.checkRecovery()
        state.addLayer()
        assertTrue(state.hasUnsavedChanges)

        assertTrue(state.save())
        assertFalse(state.hasUnsavedChanges)
        assertEquals(1, clears)
        assertEquals("Saved locally", state.statusMessage)
    }

    @Test fun failed_manual_save_keeps_recovery_snapshot() = runBlocking {
        var clears = 0
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsRecovery = true
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
                SaveResult.Failure("Disk full")
            override fun clearRecovery(): SaveResult {
                clears++
                return SaveResult.Success
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.checkRecovery()
        state.addLayer()

        assertFalse(state.save())
        assertTrue(state.hasUnsavedChanges)
        assertEquals(0, clears)
        assertEquals("Disk full", state.statusMessage)
    }

    @Test fun recovery_cleanup_failure_does_not_turn_a_successful_save_into_failure() = runBlocking {
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsRecovery = true
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
                SaveResult.Success
            override fun clearRecovery(): SaveResult = SaveResult.Failure("Read-only recovery folder")
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.checkRecovery()
        state.addLayer()

        assertTrue(state.save())
        assertFalse(state.hasUnsavedChanges)
        assertTrue(state.statusMessage?.contains("Recovery cleanup failed") == true)
    }

    @Test fun starting_fresh_retires_the_offered_recovery_snapshot() = runBlocking {
        var clears = 0
        val actions = object : EditorFileActions by UnavailableEditorFileActions {
            override val supportsRecovery = true
            override fun loadRecovery(): LoadResult = LoadResult.Success(CanvasDocument.blank(32, 32), emptyMap())
            override fun clearRecovery(): SaveResult {
                clears++
                return SaveResult.Success
            }
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)
        state.checkRecovery()
        assertNotNull(state.recoveryCandidate)

        state.dismissRecovery()
        assertNull(state.recoveryCandidate)
        assertEquals(1, clears)
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
