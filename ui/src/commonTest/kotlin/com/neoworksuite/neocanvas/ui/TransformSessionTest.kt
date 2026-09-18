package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.*
import kotlin.test.*

class TransformSessionTest {
    private fun state(): EditorState = EditorState(DocumentHistory(CanvasDocument.blank(32, 32))).also {
        it.insertImage(ImportedImage("Shape", 4, 2, IntArray(8) { 0xFFFF0000.toInt() }))
    }

    @Test fun transform_preview_does_not_mutate_and_cancel_restores_exact_state() {
        val state = state()
        val before = state.tileStore.snapshot()
        assertTrue(state.beginTransform())
        state.updateTransform(translationX = 3f, translationY = 2f, scale = 1.5f, rotationDegrees = 20f)
        assertNotNull(state.previewTransform())
        before.forEach { (key, pixels) -> assertContentEquals(pixels, state.tileStore.read(key)) }
        state.cancelTransform()
        assertNull(state.transformSession)
        assertEquals(CanvasSelection(14, 15, 18, 17), state.selection)
        before.forEach { (key, pixels) -> assertContentEquals(pixels, state.tileStore.read(key)) }
    }

    @Test fun apply_is_one_undo_entry_and_cancelled_transform_is_not_history() {
        val state = state()
        val before = state.tileStore.snapshot()
        state.beginTransform()
        state.updateTransform(translationX = 3f, translationY = 2f, scale = 1.5f, rotationDegrees = 20f)
        assertTrue(state.applyTransform())
        assertNull(state.transformSession)
        assertFalse(before.values.single().contentEquals(state.tileStore.snapshot().values.single()))
        assertTrue(state.undo())
        before.forEach { (key, pixels) -> assertContentEquals(pixels, state.tileStore.read(key)) }
        assertTrue(state.redo())
        assertTrue(state.undo())
        state.selectLayerArtwork()
        assertTrue(state.beginTransform())
        state.cancelTransform()
        assertTrue(state.undo()) // Cancelling added no entry; this undoes the image import.
        assertTrue(state.tileStore.keys.isEmpty())
    }

    @Test fun locked_and_oversized_transforms_are_rejected() {
        val state = state()
        val id = state.activeLayerId!!
        state.toggleLayerLock(id)
        assertFalse(state.beginTransform())
        state.toggleLayerLock(id)
        assertTrue(state.beginTransform())
        state.updateTransform(scale = 20f)
        assertNull(state.previewTransform())
        assertFalse(state.applyTransform())
        assertNotNull(state.transformSession)
    }
}
