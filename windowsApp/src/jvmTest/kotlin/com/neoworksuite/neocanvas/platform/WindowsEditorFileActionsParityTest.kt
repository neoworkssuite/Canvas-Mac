package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.store.SaveResult
import java.awt.FileDialog
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsEditorFileActionsParityTest {
    @Test
    fun persistsBrushLibraryAndPreferencesUnderWindowsAppData() {
        val root = Files.createTempDirectory("neocanvas-windows-parity").toFile()
        try {
            val actions = WindowsEditorFileActions(appDataRoot = root)
            val brushSnapshot = "NEOCANVAS_BRUSH_LIBRARY=2\n".encodeToByteArray()

            assertEquals(SaveResult.Success, actions.saveBrushLibrary(brushSnapshot))
            assertContentEquals(brushSnapshot, actions.loadBrushLibrary())

            val preferences = mapOf(
                "quickShape" to "true",
                "interfaceHand" to "right",
                "gridVisible" to "false",
            )
            assertEquals(SaveResult.Success, actions.savePreferences(preferences))
            assertEquals(preferences, actions.loadPreferences())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importsAndExportsNeoCanvasBrushFilesThroughWindowsChooser() {
        val root = Files.createTempDirectory("neocanvas-windows-brush").toFile()
        try {
            val source = File(root, "Nature.neobrush").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val exported = File(root, "Nature-copy.neobrush")
            val actions = WindowsEditorFileActions(
                appDataRoot = root,
                fileChooser = { _, mode, _ ->
                    if (mode == FileDialog.LOAD) source.absolutePath else exported.absolutePath
                },
            )

            var imported = null as Result<com.neoworksuite.neocanvas.ui.PendingBrushImport?>?
            actions.openBrushFile { imported = it }

            val brush = assertNotNull(imported).getOrThrow()
            assertNotNull(brush)
            assertEquals("Nature.neobrush", brush.name)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), brush.bytes)

            assertEquals(SaveResult.Success, actions.shareBrushFile("Nature-copy.neobrush", byteArrayOf(9, 8, 7)))
            assertTrue(exported.isFile)
            assertContentEquals(byteArrayOf(9, 8, 7), exported.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }
}
