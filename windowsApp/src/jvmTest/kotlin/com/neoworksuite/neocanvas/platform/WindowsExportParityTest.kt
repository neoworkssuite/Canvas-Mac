package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.store.SaveResult
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsExportParityTest {
    @Test
    fun exportsEditableTextAcrossWindowsProfessionalFormats() {
        val root = Files.createTempDirectory("neocanvas-windows-exports").toFile()
        try {
            val targets = mutableMapOf<String, File>()
            val actions = WindowsEditorFileActions(
                appDataRoot = root,
                fileChooser = { title, _, suggested ->
                    val extension = when {
                        title.contains("PNG") -> ".png"
                        title.contains("JPEG") -> ".jpg"
                        title.contains("PDF") -> ".pdf"
                        title.contains("TIFF") -> ".tiff"
                        title.contains("PSD") -> ".psd"
                        else -> suggested?.substringAfterLast('.', "")?.let { if (it.isBlank()) ".out" else ".$it" } ?: ".out"
                    }
                    File(root, "export$extension").also { targets[extension] = it }.absolutePath
                },
            )
            val document = CanvasDocument(
                id = "windows-export-test",
                width = 320,
                height = 180,
                layers = listOf(
                    Layer(
                        id = "text",
                        name = "Editable text",
                        payload = LayerPayload.TextObject(
                            text = "NeoCanvas Windows",
                            fontFamily = "System",
                            fontSize = 30f,
                            x = 20f,
                            y = 40f,
                            width = 280f,
                            height = 80f,
                        ),
                    ),
                ),
            )

            assertEquals(SaveResult.Success, actions.exportPng(document, emptyMap()))
            assertEquals(SaveResult.Success, actions.exportJpeg(document, emptyMap(), 90))
            assertEquals(SaveResult.Success, actions.exportPdf(document, emptyMap()))
            assertEquals(SaveResult.Success, actions.exportTiff(document, emptyMap()))
            assertEquals(SaveResult.Success, actions.exportPsd(document, emptyMap()))

            assertTrue(targets.getValue(".png").length() > 32)
            assertContentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte()), targets.getValue(".jpg").readBytes().take(2).toByteArray())
            assertEquals("%PDF", targets.getValue(".pdf").readBytes().take(4).toByteArray().decodeToString())
            assertContentEquals(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0), targets.getValue(".tiff").readBytes().take(4).toByteArray())
            assertEquals("8BPS", targets.getValue(".psd").readBytes().take(4).toByteArray().decodeToString())
        } finally {
            root.deleteRecursively()
        }
    }
}
