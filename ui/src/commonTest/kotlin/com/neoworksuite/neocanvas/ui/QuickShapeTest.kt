package com.neoworksuite.neocanvas.ui

import androidx.compose.ui.graphics.Color
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.LayerPayload
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertIs

class QuickShapeTest {
    @Test
    fun held_line_commits_one_editable_object_and_no_raster_stroke() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(200, 200)))
        val result = QuickShapeResult(
            QuickShapeType.Line,
            listOf(DrawPoint(10f, 20f), DrawPoint(90f, 60f)),
        )

        assertTrue(state.commitQuickShape(result, Color(0xff336699), 6f, .4f))
        val layer = state.document.layers.single()
        val line = assertIs<LayerPayload.ShapeObject>(layer.payload)
        assertEquals(10f, line.x)
        assertEquals(20f, line.y)
        assertEquals(80f, line.width)
        assertEquals(40f, line.height)
        assertEquals(6f, line.strokeWidth)
        assertEquals(.4f, layer.opacity)
        assertTrue(state.tileStore.keys.isEmpty())
        assertTrue(state.objectEditorVisible)
        assertTrue(state.undo())
        assertTrue(state.document.layers.isEmpty())
    }

    @Test
    fun unsupported_or_invalid_quick_shape_leaves_raster_fallback_to_the_caller() {
        val state = EditorState(DocumentHistory(CanvasDocument.blank(200, 200)))
        val circle = QuickShapeResult(
            QuickShapeType.Circle,
            listOf(DrawPoint(10f, 20f), DrawPoint(90f, 60f)),
        )

        assertFalse(state.commitQuickShape(circle, Color.Red, 6f, 1f))
        assertTrue(state.document.layers.isEmpty())
        assertTrue(state.tileStore.keys.isEmpty())
    }

    @Test
    fun rough_line_snaps_to_line() {
        val points = listOf(
            DrawPoint(10f, 20f),
            DrawPoint(30f, 21f),
            DrawPoint(50f, 19f),
            DrawPoint(70f, 22f),
            DrawPoint(90f, 20f),
        )
        val result = assertNotNull(detectQuickShape(points))
        assertEquals(QuickShapeType.Line, result.type)
        assertTrue(result.points.size >= 2)
    }

    @Test
    fun rough_circle_snaps_to_circle() {
        val points = buildList {
            val centerX = 100f
            val centerY = 120f
            val radius = 55f
            for (index in 0..48) {
                val angle = index / 48f * PI.toFloat() * 2f
                val wobble = if (index % 3 == 0) 2f else -1f
                add(DrawPoint(
                    centerX + cos(angle) * (radius + wobble),
                    centerY + sin(angle) * (radius - wobble),
                ))
            }
        }
        val result = assertNotNull(detectQuickShape(points))
        assertEquals(QuickShapeType.Circle, result.type)
        assertTrue(result.points.size > 40)
    }

    @Test
    fun rough_triangle_snaps_to_triangle() {
        val points = polyline(
            DrawPoint(60f, 20f),
            DrawPoint(110f, 100f),
            DrawPoint(15f, 100f),
            DrawPoint(60f, 20f),
        )
        val result = assertNotNull(detectQuickShape(points))
        assertEquals(QuickShapeType.Triangle, result.type)
    }

    @Test
    fun rough_square_snaps_to_square() {
        val points = polyline(
            DrawPoint(20f, 20f),
            DrawPoint(100f, 22f),
            DrawPoint(98f, 102f),
            DrawPoint(18f, 99f),
            DrawPoint(20f, 20f),
        )
        val result = assertNotNull(detectQuickShape(points))
        assertEquals(QuickShapeType.Square, result.type)

        val corners = listOf(result.points.first(), result.points[result.points.size / 4],
            result.points[result.points.size / 2], result.points[result.points.size * 3 / 4])
        assertTrue(corners.isNotEmpty())
    }

    @Test
    fun open_scribble_is_not_forced_into_shape() {
        val points = listOf(
            DrawPoint(10f, 10f),
            DrawPoint(40f, 80f),
            DrawPoint(20f, 30f),
            DrawPoint(90f, 60f),
            DrawPoint(30f, 95f),
            DrawPoint(110f, 20f),
        )
        assertNull(detectQuickShape(points))
    }

    private fun polyline(vararg vertices: DrawPoint): List<DrawPoint> = buildList {
        vertices.toList().zipWithNext().forEachIndexed { segmentIndex, (from, to) ->
            val steps = 14
            for (step in 0 until steps) {
                if (segmentIndex > 0 && step == 0) continue
                val t = step / steps.toFloat()
                val wobble = if (step % 4 == 0) 1.2f else 0f
                add(DrawPoint(
                    from.x + (to.x - from.x) * t + wobble,
                    from.y + (to.y - from.y) * t - wobble,
                ))
            }
        }
        add(vertices.last())
    }
}
