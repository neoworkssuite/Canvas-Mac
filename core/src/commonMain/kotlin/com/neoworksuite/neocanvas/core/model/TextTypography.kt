package com.neoworksuite.neocanvas.core.model

enum class TextOrientation { Horizontal, Vertical }

data class TextKerningRange(
    val startUtf16: Int,
    val endUtf16: Int,
    val adjustment: Float,
)

fun normalizeKerningRanges(
    text: String,
    ranges: List<TextKerningRange>,
    maximumEntries: Int = 256,
): List<TextKerningRange> {
    if (ranges.isEmpty() || maximumEntries <= 0) return emptyList()
    fun isBoundary(offset: Int): Boolean = offset in 0..text.length && !(
        offset > 0 && offset < text.length &&
            text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate()
    )
    val accepted = ArrayList<TextKerningRange>(minOf(ranges.size, maximumEntries))
    ranges.asReversed().forEach { candidate ->
        if (accepted.size >= maximumEntries) return@forEach
        if (!candidate.adjustment.isFinite()) return@forEach
        val start = candidate.startUtf16.coerceIn(0, text.length)
        val end = candidate.endUtf16.coerceIn(0, text.length)
        if (start >= end || !isBoundary(start) || !isBoundary(end)) return@forEach
        if (accepted.any { start < it.endUtf16 && end > it.startUtf16 }) return@forEach
        accepted += TextKerningRange(start, end, candidate.adjustment.coerceIn(-64f, 64f))
    }
    return accepted.sortedWith(compareBy(TextKerningRange::startUtf16, TextKerningRange::endUtf16))
}
