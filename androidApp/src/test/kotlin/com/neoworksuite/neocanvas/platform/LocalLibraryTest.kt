package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import java.nio.file.Files
import kotlin.test.*

class LocalLibraryTest {
    @Test fun android_local_versions_workbench_and_deep_layers_round_trip() {
        val directory = Files.createTempDirectory("neocanvas-history-test").toFile()
        try {
            val actions = AndroidEditorFileActions(directory)
            val document = CanvasDocument.blank(16, 16)
            assertEquals(SaveResult.Success, actions.createVersionOnBranch("Initial", "Sketch", null, document, emptyMap()))
            val version = assertNotNull(actions.listVersions(document.id).singleOrNull())
            assertEquals("Initial", version.label)
            assertEquals("Sketch", version.branch)
            assertEquals(16, (actions.loadVersion(document.id, version.id) as LoadResult.Success).document.width)
            assertEquals(SaveResult.Success, actions.saveWorkbench(document.id, byteArrayOf(1, 2)))
            assertContentEquals(byteArrayOf(1, 2), actions.loadWorkbench(document.id))
            assertEquals(SaveResult.Success, actions.saveDormantLayer(document.id, "layer/1", byteArrayOf(3)))
            assertContentEquals(byteArrayOf(3), actions.loadDormantLayer(document.id, "layer/1"))
            assertEquals(SaveResult.Success, actions.deleteDormantLayer(document.id, "layer/1"))
            assertNull(actions.loadDormantLayer(document.id, "layer/1"))
            assertEquals(SaveResult.Success, actions.deleteVersion(document.id, version.id))
            assertEquals(SaveResult.Success, actions.saveBrushLibrary(byteArrayOf(4, 5)))
            assertContentEquals(byteArrayOf(4, 5), actions.loadBrushLibrary())
        } finally { directory.deleteRecursively() }
    }

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
