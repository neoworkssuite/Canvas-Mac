package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.brushes.BrushDefinition
import com.neoworksuite.neocanvas.brushes.BrushMode
import com.neoworksuite.neocanvas.renderer.DrawingSymmetry
import com.neoworksuite.neocanvas.renderer.RasterColor

internal data class EditableStroke(
    val id: Int,
    val layerId: String,
    val points: List<DrawPoint>,
    val stabilize: Boolean,
    val stabilization: Float,
    val brush: BrushDefinition,
    val size: Float,
    val opacity: Float,
    val mode: BrushMode,
    val color: RasterColor,
    val symmetry: DrawingSymmetry,
    val selection: CanvasSelection?,
    val alphaLocked: Boolean,
)

data class EditableStrokeSummary(
    val id: Int,
    val brushName: String,
    val size: Float,
    val opacity: Float,
    val isEraser: Boolean,
    val pointCount: Int,
)
