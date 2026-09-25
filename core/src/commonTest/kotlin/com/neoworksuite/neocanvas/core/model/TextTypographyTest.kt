package com.neoworksuite.neocanvas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextTypographyTest {
    @Test
    fun malformed_kerning_is_normalized_capped_and_never_splits_a_surrogate_pair() {
        val crowded = buildList {
            add(TextKerningRange(-2, 1, 2f))
            add(TextKerningRange(1, 2, 4f))
            repeat(300) { add(TextKerningRange(0, 1, it.toFloat())) }
        }

        val normalized = normalizeKerningRanges("A\uD83D\uDE00V", crowded)

        assertTrue(normalized.size <= 256)
        assertTrue(normalized.all { it.startUtf16 >= 0 && it.endUtf16 <= 4 && it.startUtf16 < it.endUtf16 })
        assertTrue(normalized.none { it.startUtf16 == 2 || it.endUtf16 == 2 })
        assertEquals(normalized.sortedWith(compareBy(TextKerningRange::startUtf16, TextKerningRange::endUtf16)), normalized)
    }

    @Test
    fun last_valid_overlapping_kerning_range_wins_and_adjustment_is_bounded() {
        val normalized = normalizeKerningRanges(
            "AV",
            listOf(
                TextKerningRange(0, 2, -2f),
                TextKerningRange(0, 1, 900f),
            ),
        )

        assertEquals(listOf(TextKerningRange(0, 1, 64f)), normalized)
    }
}
