package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import kotlin.test.*

class CanvasSizeTest {
    @Test fun all_inspector_panels_overlay_the_canvas_without_resizing_it() {
        InspectorPanel.entries.forEach { panel ->
            assertEquals(InspectorPresentation.Overlay, inspectorPresentation(panel))
        }
    }
    @Test fun requested_dimensions_survive_unsaved_confirmation() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.newDocument(1920, 1080)
        assertEquals(16, state.document.width)
        assertEquals(PendingDocumentAction.New, state.pendingDocumentAction)
        state.discardAndContinue()
        assertEquals(1920, state.document.width)
        assertEquals(1080, state.document.height)
        assertFalse(state.hasUnsavedChanges)
        assertFalse(state.canUndo)
    }
    @Test fun invalid_dimensions_do_not_replace_current_document() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        val original = state.document
        for ((w, h) in listOf(0 to 10, -1 to 10, 8193 to 1, 8192 to 8192, Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertFalse(state.newDocument(w, h))
            assertEquals(original, state.document)
            assertNull(state.pendingDocumentAction)
        }
        assertTrue(state.newDocument(4000, 4000))
        assertEquals(4000, state.document.width)
    }
    @Test fun cancelling_creation_does_not_leak_requested_dimensions() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.newDocument(1920, 1080)
        state.cancelDocumentAction()
        state.newDocument()
        state.discardAndContinue()
        assertEquals(16, state.document.width)
        assertEquals(16, state.document.height)
    }


    @Test fun new_canvas_starts_with_locked_background_and_drawing_layer() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        assertTrue(state.newDocument(320, 480))
        assertEquals(listOf("Background", "Layer 1"), state.document.layers.map { it.name })
        val background = state.document.layers.first()
        assertTrue(background.locked)
        val shape = background.payload as com.neoworksuite.neocanvas.core.model.LayerPayload.ShapeObject
        assertEquals(com.neoworksuite.neocanvas.core.model.ShapeKind.Rectangle, shape.kind)
        assertEquals(320f, shape.width)
        assertEquals(480f, shape.height)
        assertEquals(0xffffffff.toInt(), shape.fillArgb)
        assertEquals("layer-1", state.activeLayerId)
    }

    @Test fun orientation_controls_preserve_dimensions_and_make_visual_choice_explicit() {
        assertEquals(1536 to 2048, orientCanvasDimensions(2048, 1536, portrait = true))
        assertEquals(2048 to 1536, orientCanvasDimensions(1536, 2048, portrait = false))
        assertEquals(2048 to 2730, orientCanvasDimensions(2048, 2048, portrait = true))
        assertTrue(newCanvasPresets.size >= 8)
        assertTrue(newCanvasPresets.all { it.width.toLong() * it.height <= 16_000_000 })
    }
}
