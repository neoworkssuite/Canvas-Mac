package com.neoworksuite.neocanvas.brushes

import kotlin.test.Test
import kotlin.test.assertFailsWith

class BrushStampTest {
    @Test
    fun asset_references_require_portable_ids_and_lowercase_sha256() {
        assertFailsWith<IllegalArgumentException> { BrushAssetRef("Oak Leaf", "0".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { BrushAssetRef("oak-leaf", "A".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { BrushAssetRef("oak-leaf", "0".repeat(63)) }
    }

    @Test
    fun stamp_count_and_every_normalized_dynamic_are_bounded() {
        assertFailsWith<IllegalArgumentException> { BrushStamp(stampCount = 0) }
        assertFailsWith<IllegalArgumentException> { BrushStamp(stampCount = 9) }
        assertFailsWith<IllegalArgumentException> { BrushStamp(angleJitter = 1.01f) }
        assertFailsWith<IllegalArgumentException> { BrushStamp(scatterAlong = -0.01f) }
        assertFailsWith<IllegalArgumentException> { BrushStamp(hueJitter = .51f) }
        assertFailsWith<IllegalArgumentException> { BrushStamp(startTaper = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { BrushStamp(scaleX = 0f) }
        assertFailsWith<IllegalArgumentException> { BrushStamp(grainScale = 8.01f) }
    }
}
