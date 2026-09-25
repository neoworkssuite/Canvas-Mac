package com.neoworksuite.neocanvas.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudioMenuPresentationTest {
    @Test
    fun common_menu_commands_have_familiar_distinct_icons() {
        assertEquals(Glyph.Add, menuPresentation(StudioMenuCommand.AddImport).glyph)
        assertEquals(Glyph.Canvas, menuPresentation(StudioMenuCommand.Canvas).glyph)
        assertEquals(Glyph.Assist, menuPresentation(StudioMenuCommand.DrawingAssist).glyph)
        assertEquals(Glyph.Export, menuPresentation(StudioMenuCommand.FileExport).glyph)
        assertEquals(Glyph.Import, menuPresentation(StudioMenuCommand.ImportBrush).glyph)
        assertEquals(Glyph.Brush, menuPresentation(StudioMenuCommand.CreateBrush).glyph)
    }

    @Test
    fun remove_brush_is_the_only_destructive_brush_command() {
        assertTrue(menuPresentation(StudioMenuCommand.RemoveBrush).destructive)
        assertFalse(menuPresentation(StudioMenuCommand.ImportBrush).destructive)
        assertFalse(menuPresentation(StudioMenuCommand.CreateBrush).destructive)
        assertFalse(menuPresentation(StudioMenuCommand.ShareBrush).destructive)
    }

    @Test
    fun toggled_menu_items_expose_a_checkmark_without_changing_their_icon() {
        val off = menuPresentation(StudioMenuCommand.GridGuide, selected = false)
        val on = menuPresentation(StudioMenuCommand.GridGuide, selected = true)

        assertEquals(Glyph.Grid, off.glyph)
        assertEquals(Glyph.Grid, on.glyph)
        assertFalse(off.selected)
        assertTrue(on.selected)
    }
}
