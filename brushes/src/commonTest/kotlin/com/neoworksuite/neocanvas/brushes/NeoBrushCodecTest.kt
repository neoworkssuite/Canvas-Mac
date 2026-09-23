package com.neoworksuite.neocanvas.brushes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NeoBrushCodecTest {
    @Test
    fun round_trip_preserves_every_brush_field_and_unicode_name() {
        val brush = BrushDefinition(
            id = "user.123",
            name = "Étoile 水彩",
            spacing = 3.25f,
            baseSize = 27f,
            opacity = .62f,
            mode = BrushMode.PAINT,
            version = 3,
            tip = BrushTip.Water,
            pressureSize = .72f,
            pressureOpacity = .41f,
            categoryId = "custom",
            dynamics = BrushDynamics(.2f, .3f, .4f, .5f, .6f, .7f, .8f),
        )

        assertEquals(brush, NeoBrushCodec.decode(NeoBrushCodec.encode(brush)))
    }

    @Test
    fun malformed_unknown_or_unsafe_files_are_rejected() {
        assertFailsWith<IllegalArgumentException> { NeoBrushCodec.decode("not a brush".encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> {
            NeoBrushCodec.decode("NEOCANVAS_BRUSH=99\nid=x".encodeToByteArray())
        }
        val encoded = NeoBrushCodec.encode(BuiltInBrushes.ink).decodeToString()
        assertFailsWith<IllegalArgumentException> {
            NeoBrushCodec.decode((encoded + "unknown=value\n").encodeToByteArray())
        }
    }

    @Test
    fun v2_round_trip_preserves_stamp_while_v1_stays_stamp_free() {
        val stamp = BrushStamp(
            shape = BrushAssetRef("oak-leaf", "0".repeat(64)),
            grain = BrushAssetRef("paper-grain", "1".repeat(64)),
            angleMode = StampAngleMode.DirectionJitter,
            angleDegrees = 12f,
            angleJitter = .35f,
            scaleX = 1.25f,
            scaleY = .7f,
            spacingRatio = .24f,
            scatterAlong = .2f,
            scatterAcross = .55f,
            stampCount = 4,
            stampCountJitter = .25f,
            grainScale = 1.4f,
            grainMovement = GrainMovement.Canvas,
            hueJitter = .03f,
            saturationJitter = .08f,
            brightnessJitter = .06f,
            pressureScatter = .5f,
            pressureStampCount = .7f,
            startTaper = .2f,
            endTaper = .15f,
        )
        val brush = BuiltInBrushes.ink.copy(version = 2, stamp = stamp)

        assertEquals(brush, NeoBrushCodec.decode(NeoBrushCodec.encode(brush)))
        assertNull(NeoBrushCodec.decode(NeoBrushCodec.encode(BuiltInBrushes.ink)).stamp)
    }
}
