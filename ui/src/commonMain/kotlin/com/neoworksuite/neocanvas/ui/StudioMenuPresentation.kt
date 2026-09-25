package com.neoworksuite.neocanvas.ui

internal enum class Glyph {
    Previous, Next, Gallery, ImportImage, New, Open, Save, Export, Brush, Eraser, Smudge,
    Transform, Fill, Eyedropper, Select, ClearSelection, Undo, Redo, Fit, Palette, Library,
    Layers, Fx, Settings, Ellipsis, Add, Canvas, Assist, Tools, Import, Share, Delete, Grid,
}

internal enum class StudioMenuCommand {
    AddImport,
    Canvas,
    DrawingAssist,
    UtilityTools,
    FileExport,
    ImportBrush,
    CreateBrush,
    ShareBrush,
    RemoveBrush,
    GridGuide,
}

internal data class StudioMenuPresentation(
    val glyph: Glyph,
    val destructive: Boolean = false,
    val selected: Boolean = false,
)

internal fun menuPresentation(
    command: StudioMenuCommand,
    selected: Boolean = false,
): StudioMenuPresentation = when (command) {
    StudioMenuCommand.AddImport -> StudioMenuPresentation(Glyph.Add)
    StudioMenuCommand.Canvas -> StudioMenuPresentation(Glyph.Canvas)
    StudioMenuCommand.DrawingAssist -> StudioMenuPresentation(Glyph.Assist)
    StudioMenuCommand.UtilityTools -> StudioMenuPresentation(Glyph.Tools)
    StudioMenuCommand.FileExport -> StudioMenuPresentation(Glyph.Export)
    StudioMenuCommand.ImportBrush -> StudioMenuPresentation(Glyph.Import)
    StudioMenuCommand.CreateBrush -> StudioMenuPresentation(Glyph.Brush)
    StudioMenuCommand.ShareBrush -> StudioMenuPresentation(Glyph.Share)
    StudioMenuCommand.RemoveBrush -> StudioMenuPresentation(Glyph.Delete, destructive = true)
    StudioMenuCommand.GridGuide -> StudioMenuPresentation(Glyph.Grid, selected = selected)
}
