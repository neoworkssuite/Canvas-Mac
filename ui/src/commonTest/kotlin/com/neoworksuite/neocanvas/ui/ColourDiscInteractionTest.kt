package com.neoworksuite.neocanvas.ui

import kotlin.test.*

class ColourDiscInteractionTest {
    @Test fun snap_targets_cover_artist_neutrals_and_saturation_steps() {
        assertEquals(1f, snapColourDisc(Hsv(20f, .02f, .96f)).value)
        assertEquals(0f, snapColourDisc(Hsv(20f, .03f, .04f)).value)
        assertEquals(.5f, snapColourDisc(Hsv(20f, .02f, .48f)).value)
        assertEquals(.5f, snapColourDisc(Hsv(20f, .47f, .72f)).saturation)
        assertEquals(1f, snapColourDisc(Hsv(20f, .96f, .72f)).saturation)
    }

    @Test fun zoom_is_bounded_and_resettable() {
        val zoom = ColourDiscZoomState()
        zoom.zoomBy(8f); assertEquals(2.5f, zoom.scale)
        zoom.zoomBy(.01f); assertEquals(1f, zoom.scale)
        zoom.zoomBy(2f); zoom.reset(); assertEquals(1f, zoom.scale)
    }

    @Test fun harmony_reticles_and_mode_restoration_are_stable() {
        assertEquals(harmonyHues(25f, ColourHarmony.Tetradic).size, harmonyReticlePositions(25f, ColourHarmony.Tetradic).size)
        assertEquals(ColourStudioMode.Harmony, ColourStudioMode.stored("Harmony"))
        assertEquals(ColourStudioMode.Disc, ColourStudioMode.stored("unknown"))
    }
}
