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
}
