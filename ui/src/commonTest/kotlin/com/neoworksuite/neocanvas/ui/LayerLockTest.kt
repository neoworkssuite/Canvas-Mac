package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.*
import com.neoworksuite.neocanvas.core.store.*
import kotlin.test.*

class LayerLockTest {
    @Test fun lock_blocks_pixel_edits_and_deletion_and_round_trips() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)))
        state.addLayer()
        state.insertImage(ImportedImage("Art", 2, 2, IntArray(4) { 0xFFFF0000.toInt() }))
        val id = state.activeLayerId!!
        val pixels = state.tilesForDocument()
        state.toggleLayerLock(id)
        state.tool = Tool.Brush
        state.recordStroke(listOf(DrawPoint(8f, 8f)))
        state.tool = Tool.Eraser
        state.recordStroke(listOf(DrawPoint(8f, 8f)))
        state.tool = Tool.Fill
        state.applyPointTool(DrawPoint(8f, 8f))
        state.moveSelection(2, 2)
        state.rotateSelection()
        state.resizeSelection(.5f)
        state.flipSelection(true)
        state.clearSelectedPixels()
        state.deleteActiveLayer()
        assertEquals(2, state.document.layers.size)
        assertTrue(state.document.layers.last().locked)
        pixels.forEach { (key, bytes) -> assertContentEquals(bytes, state.tileStore.read(key)) }
        val reopened = NeoCanvasPackage.read(NeoCanvasPackage.write(state.document, state.tilesForDocument())) as LoadResult.Success
        assertTrue(reopened.document.layers.last().locked)
        assertTrue(state.undo())
        assertFalse(state.document.layers.last().locked) // Blocked operations must not add history.
        assertTrue(state.redo())
        assertTrue(state.document.layers.last().locked)
    }
}
