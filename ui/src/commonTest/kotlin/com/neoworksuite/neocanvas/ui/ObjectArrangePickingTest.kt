package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.geometry.Offset
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerGroup
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectArrangePickingTest {
    @Test
    fun canvas_pick_prefers_topmost_visible_editable_object() {
        val document = CanvasDocument(
            id = "pick-top",
            width = 300,
            height = 200,
            layers = listOf(
                Layer(
                    "bottom",
                    "Bottom",
                    payload = LayerPayload.ShapeObject(
                        kind = ShapeKind.Rectangle,
                        x = 20f,
                        y = 20f,
                        width = 100f,
                        height = 100f,
                    ),
                ),
                Layer(
                    "top",
                    "Top",
                    payload = LayerPayload.TextObject(
                        text = "Top",
                        x = 40f,
                        y = 40f,
                        width = 100f,
                        height = 60f,
                    ),
                ),
            ),
        )

        assertEquals("top", editableObjectLayerAtPoint(document, Offset(60f, 60f)))
        assertEquals("bottom", editableObjectLayerAtPoint(document, Offset(25f, 25f)))
        assertNull(editableObjectLayerAtPoint(document, Offset(250f, 150f)))
    }

    @Test
    fun canvas_pick_ignores_hidden_layers_and_hidden_groups_and_respects_rotation() {
        val hiddenGroup = LayerGroup("g1", "Hidden", visible = false)
        val document = CanvasDocument(
            id = "pick-visible",
            width = 300,
            height = 200,
            groups = listOf(hiddenGroup),
            layers = listOf(
                Layer(
                    "visible",
                    "Visible",
                    payload = LayerPayload.ShapeObject(
                        kind = ShapeKind.Rectangle,
                        x = 100f,
                        y = 50f,
                        width = 80f,
                        height = 20f,
                        rotationDegrees = 90f,
                    ),
                ),
                Layer(
                    "hidden",
                    "Hidden",
                    visible = false,
                    payload = LayerPayload.ShapeObject(
                        kind = ShapeKind.Rectangle,
                        x = 90f,
                        y = 20f,
                        width = 120f,
                        height = 120f,
                    ),
                ),
                Layer(
                    "group-hidden",
                    "Group hidden",
                    groupId = "g1",
                    payload = LayerPayload.ShapeObject(
                        kind = ShapeKind.Rectangle,
                        x = 90f,
                        y = 20f,
                        width = 120f,
                        height = 120f,
                    ),
                ),
            ),
        )

        assertEquals("visible", editableObjectLayerAtPoint(document, Offset(140f, 45f), padding = 1f))
        assertNull(editableObjectLayerAtPoint(document, Offset(100f, 60f), padding = 1f))
    }
}
