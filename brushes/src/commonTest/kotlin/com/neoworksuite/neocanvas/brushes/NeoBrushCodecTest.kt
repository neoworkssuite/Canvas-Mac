package com.neoworksuite.neocanvas.brushes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
