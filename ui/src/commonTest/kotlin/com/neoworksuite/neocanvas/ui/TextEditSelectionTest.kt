package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.TextKerningRange
import kotlin.test.Test
import kotlin.test.assertEquals

class TextEditSelectionTest {
    @Test
    fun deleting_selected_text_discards_intersecting_kerning_and_shifts_following_ranges() {
        val result = remapKerningAfterEdit(
            oldText = "ABCD",
            newText = "AD",
            oldSelection = TextEditSelection(1, 3),
            ranges = listOf(
                TextKerningRange(0, 1, -1f),
                TextKerningRange(1, 3, 2f),
                TextKerningRange(3, 4, 3f),
            ),
        )
        assertEquals(
            listOf(TextKerningRange(0, 1, -1f), TextKerningRange(1, 2, 3f)),
            result,
        )
    }

    @Test
    fun deleting_a_surrogate_pair_never_leaves_a_split_utf16_range() {
        val result = remapKerningAfterEdit(
            oldText = "A\uD83D\uDE00V",
            newText = "AV",
            oldSelection = TextEditSelection(1, 3),
            ranges = listOf(TextKerningRange(1, 3, 2f), TextKerningRange(3, 4, -1f)),
        )
        assertEquals(listOf(TextKerningRange(1, 2, -1f)), result)
    }
}
