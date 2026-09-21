package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.geometry.Rect
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerGroup
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ObjectArrangeMarqueeTest {
    @Test
    fun arrange_marquee_selects_visible_editable_objects_that_intersect_it() {
        val document = CanvasDocument(
            id = "marquee",
            width = 400,
            height = 300,
            layers = listOf(
                Layer("a", "A", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Rectangle, x = 20f, y = 20f, width = 60f, height = 60f,
                )),
                Layer("b", "B", payload = LayerPayload.TextObject(
                    text = "B", x = 120f, y = 30f, width = 100f, height = 50f,
                )),
                Layer("outside", "Outside", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Ellipse, x = 300f, y = 200f, width = 60f, height = 60f,
                )),
            ),
        )

        assertEquals(
            setOf("a", "b"),
            editableObjectLayerIdsInRect(document, Rect(0f, 0f, 180f, 100f)),
        )
    }

    @Test
    fun arrange_marquee_uses_rotated_bounds_and_ignores_hidden_group_content() {
        val document = CanvasDocument(
            id = "marquee-rotated",
            width = 400,
            height = 300,
            groups = listOf(LayerGroup("hidden-group", "Hidden", visible = false)),
            layers = listOf(
                Layer("rotated", "Rotated", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Rectangle,
                    x = 160f,
                    y = 100f,
                    width = 100f,
                    height = 20f,
                    rotationDegrees = 90f,
                )),
                Layer("hidden", "Hidden", groupId = "hidden-group", payload = LayerPayload.TextObject(
                    text = "Hidden", x = 140f, y = 80f, width = 100f, height = 100f,
                )),
            ),
        )

        assertEquals(
            setOf("rotated"),
            editableObjectLayerIdsInRect(document, Rect(195f, 50f, 225f, 170f)),
        )
    }
}
