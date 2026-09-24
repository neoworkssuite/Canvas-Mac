package com.neoworksuite.neocanvas.ui

enum class InterfaceSide { Automatic, Left, Right }

data class WorkspacePlacement(
    val railAtStart: Boolean,
    val panelsAtEnd: Boolean,
)

fun workspacePlacement(side: InterfaceSide, compact: Boolean): WorkspacePlacement {
    @Suppress("UNUSED_VARIABLE") val compactLayout = compact
    return when (side) {
        InterfaceSide.Automatic, InterfaceSide.Right -> WorkspacePlacement(railAtStart = true, panelsAtEnd = true)
        InterfaceSide.Left -> WorkspacePlacement(railAtStart = false, panelsAtEnd = false)
    }
}
