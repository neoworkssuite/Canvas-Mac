package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.*
import com.neoworksuite.neocanvas.core.store.*
import kotlin.test.*

class FileTargetTest {
    @Test fun failed_open_and_cancelled_save_as_preserve_previous_target() {
        val paths = mutableListOf<String?>("first.neocanvas", "broken.neocanvas", null, "copy.neocanvas")
        val writes = mutableListOf<String>()
        val store = object : DocumentStore {
            override fun save(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
                writes += path
                return SaveResult.Success
            }
            override fun load(path: String): LoadResult = LoadResult.Corrupt("Bad file")
        }
        val actions = WindowsEditorFileActions(store) { _, _, _ -> paths.removeAt(0) }
        val doc = CanvasDocument.blank(16, 16)
        actions.save(doc, emptyMap())
        actions.open()
        actions.save(doc, emptyMap())
        actions.saveAs(doc, emptyMap())
        actions.save(doc, emptyMap())
        actions.saveAs(doc, emptyMap())
        actions.save(doc, emptyMap())
        assertEquals(listOf("first.neocanvas", "first.neocanvas", "first.neocanvas", "copy.neocanvas", "copy.neocanvas"), writes)
    }

    @Test fun new_canvas_and_failed_save_do_not_reuse_previous_target() {
        val paths = mutableListOf<String?>("first.neocanvas", "failed.neocanvas", "new.neocanvas")
        val writes = mutableListOf<String>()
        val store = object : DocumentStore {
            override fun save(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
                writes += path
                return if (path == "failed.neocanvas") SaveResult.Failure("Disk full") else SaveResult.Success
            }
            override fun load(path: String): LoadResult = LoadResult.Failure("Unused")
        }
        val actions = WindowsEditorFileActions(store) { _, _, _ -> paths.removeAt(0) }
        val doc = CanvasDocument.blank(16, 16)
        actions.save(doc, emptyMap())
        actions.resetDocumentTarget()
        actions.save(doc, emptyMap())
        actions.save(doc, emptyMap())
        assertEquals(listOf("first.neocanvas", "failed.neocanvas", "new.neocanvas"), writes)
    }

    @Test fun associated_document_open_sets_future_save_target() {
        val target = kotlin.io.path.createTempFile(suffix = ".neocanvas").toFile()
        val writes = mutableListOf<String>()
        val loaded = CanvasDocument.blank(16, 16)
        val store = object : DocumentStore {
            override fun save(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
                writes += path
                return SaveResult.Success
            }
            override fun load(path: String): LoadResult = LoadResult.Success(loaded, emptyMap())
        }
        try {
            val actions = WindowsEditorFileActions(store)
            assertIs<LoadResult.Success>(actions.openPath(target.absolutePath))
            actions.save(loaded, emptyMap())
            assertEquals(listOf(target.absolutePath), writes)
        } finally {
            target.delete()
        }
    }
}
