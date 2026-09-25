package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.TextKerningRange
import com.neoworksuite.neocanvas.core.model.normalizeKerningRanges

data class TextEditSelection(val startUtf16: Int, val endUtf16: Int) {
    val min: Int get() = minOf(startUtf16, endUtf16)
    val max: Int get() = maxOf(startUtf16, endUtf16)
}

fun remapKerningAfterEdit(
    oldText: String,
    newText: String,
    oldSelection: TextEditSelection,
    ranges: List<TextKerningRange>,
): List<TextKerningRange> {
    val start = oldSelection.min.coerceIn(0, oldText.length)
    val end = oldSelection.max.coerceIn(start, oldText.length)
    val delta = newText.length - oldText.length
    val remapped = ranges.mapNotNull { range ->
        when {
            range.endUtf16 <= start -> range
            range.startUtf16 >= end -> range.copy(
                startUtf16 = range.startUtf16 + delta,
                endUtf16 = range.endUtf16 + delta,
            )
            else -> null
        }
    }
    return normalizeKerningRanges(newText, remapped)
}
