package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import java.nio.file.Files
import kotlin.test.*

class LocalLibraryTest {
    @Test fun android_host_advertises_all_supported_tablet_export_and_psd_import_actions() {
        val directory = Files.createTempDirectory("neocanvas-capabilities-test").toFile()
        try {
            val actions = AndroidEditorFileActions(directory)
            assertTrue(actions.supportsPsdImport)
            assertTrue(actions.supportsPsdExport)
            assertTrue(actions.supportsJpegExport)
            assertTrue(actions.supportsPdfExport)
            assertTrue(actions.supportsTiffExport)
        } finally { directory.deleteRecursively() }
    }

    @Test fun named_copies_round_trip_without_overwriting_or_traversing_paths() {
        val directory = Files.createTempDirectory("neocanvas-library-test").toFile()
        try {
            val actions = AndroidEditorFileActions(directory)
            assertEquals(SaveResult.Success, actions.saveNamedCopy("First", CanvasDocument.blank(16, 16), emptyMap()))
            assertTrue(actions.saveNamedCopy("first", CanvasDocument.blank(32, 32), emptyMap()) is SaveResult.Failure)
            assertTrue(actions.saveNamedCopy("../escape", CanvasDocument.blank(32, 32), emptyMap()) is SaveResult.Failure)
            assertEquals(listOf("First.neocanvas"), actions.listLocalDocuments())
            actions.resetDocumentTarget()
            assertEquals(SaveResult.Success, actions.save(CanvasDocument.blank(32, 32), emptyMap()))
            assertEquals(2, actions.listLocalDocuments().size)
            val result = actions.openLocalDocument("First.neocanvas") as LoadResult.Success
            assertEquals(16, result.document.width)
            assertTrue(actions.openLocalDocument("../escape.neocanvas") is LoadResult.Failure)
            actions.save(CanvasDocument.blank(24, 24), emptyMap())
            assertEquals(24, (actions.openLocalDocument("First.neocanvas") as LoadResult.Success).document.width)
        } finally { directory.deleteRecursively() }
    }
}
