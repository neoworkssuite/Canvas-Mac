package com.neoworksuite.neocanvas.core.model

/** Applies immutable commands and retains reversible document snapshots. */
class DocumentHistory(initial: CanvasDocument) {
    private val undoStates = mutableListOf<CanvasDocument>()
    private val redoStates = mutableListOf<CanvasDocument>()

    var current: CanvasDocument = initial.copy()
        private set

    val canUndo: Boolean get() = undoStates.isNotEmpty()
    val canRedo: Boolean get() = redoStates.isNotEmpty()

    fun execute(command: DocumentCommand) {
        val updated = command.apply(current)
        undoStates += current
        current = updated
        redoStates.clear()
    }

    fun undo(): Boolean {
        val previous = undoStates.removeLastOrNull() ?: return false
        redoStates += current
        current = previous
        return true
    }

    fun redo(): Boolean {
        val next = redoStates.removeLastOrNull() ?: return false
        undoStates += current
        current = next
        return true
    }

    /** Loading a local document establishes a new, clean undo boundary. */
    fun reset(document: CanvasDocument) {
        current = document.copy()
        undoStates.clear()
        redoStates.clear()
    }
}
