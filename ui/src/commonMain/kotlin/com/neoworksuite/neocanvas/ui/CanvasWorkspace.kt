package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.atan2

private enum class TransformDrag { None, Move, Scale, Rotate }

/** Bounded document viewport. Strokes map to document pixels before the shared rasterizer stores them. */
@Composable
fun CanvasWorkspace(
    state: EditorState,
    modifier: Modifier = Modifier,
    leftInset: Dp = 0.dp,
    rightInset: Dp = 0.dp,
) {
    val inProgress = remember { mutableStateListOf<DrawPoint>() }
    val tileImages = remember { TileImageCache() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var moveDelta by remember { mutableStateOf(Offset.Zero) }
    var movingSelection by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(
        modifier = modifier.background(NeoCanvasColors.canvasBed).clipToBounds().semantics { contentDescription = "Drawing canvas" },
        contentAlignment = Alignment.Center,
    ) {
        val viewportWidth = viewport.width.toFloat().coerceAtLeast(1f)
        val viewportHeight = viewport.height.toFloat().coerceAtLeast(1f)
        val leftInsetPx = with(density) { leftInset.toPx() }
        val rightInsetPx = with(density) { rightInset.toPx() }
        val usableWidth = (viewportWidth - leftInsetPx - rightInsetPx).coerceAtLeast(1f)
        val margin = with(density) { 32.dp.toPx() }
        val fitWidth = (usableWidth - margin * 2f).coerceAtLeast(1f)
        val fitHeight = (viewportHeight - margin * 2f).coerceAtLeast(1f)
        val document = state.document
        val fit = minOf(fitWidth / document.width, fitHeight / document.height).coerceAtLeast(.01f)
        val scale = fit * state.zoom
        val documentWidth = document.width * scale
        val documentHeight = document.height * scale
        val origin = Offset(
            leftInsetPx + (usableWidth - documentWidth) / 2f + state.panX,
            (viewportHeight - documentHeight) / 2f + state.panY,
        )
        val currentOrigin by rememberUpdatedState(origin)
        val currentScale by rememberUpdatedState(scale)
        val currentCenter by rememberUpdatedState(Offset(leftInsetPx + usableWidth / 2f, viewportHeight / 2f))
        val previewPoints = inProgress.toList()
        val strokePreview = remember(previewPoints, document, state.tool, state.activeLayerId,
            state.brush, state.brushSize, state.brushOpacity, state.color, state.selection, state.stabilization, state.symmetry) {
            state.previewStroke(previewPoints)
        }
        val movePreview = remember(moveDelta, movingSelection, state.selection, state.activeLayerId, document) {
            if (movingSelection) state.previewSelectionMove(moveDelta.x.toInt(), moveDelta.y.toInt()) else null
        }
        val transformPreview = remember(state.transformSession, state.activeLayerId, document, state.smoothResizing) {
            state.previewTransform()
        }

        Canvas(
            Modifier.fillMaxSize().onSizeChanged { viewport = it }
                .pointerInput(state.tool, state.activeLayerId, state.brushSize, state.brushOpacity, document.id, viewport) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (state.tool != Tool.Pan && state.tool != Tool.Eyedropper && state.tool != Tool.Select &&
                        state.document.layers.any { it.id == state.activeLayerId && it.locked }) {
                        state.statusMessage = "Layer is locked — unlock it in Layers to edit"
                        down.consume()
                        return@awaitEachGesture
                    }
                    val gestureOrigin = currentOrigin
                    val gestureScale = currentScale
                    fun point(position: Offset, pressure: Float) = DrawPoint(
                        (position.x - gestureOrigin.x) / gestureScale,
                        (position.y - gestureOrigin.y) / gestureScale,
                        normalizedPressure(pressure))
                    val initial = point(down.position, if (down.type == PointerType.Stylus) down.pressure else 1f)
                    if (state.tool != Tool.Pan && (initial.x < 0f || initial.y < 0f ||
                        initial.x >= document.width || initial.y >= document.height)) return@awaitEachGesture
                    down.consume()
                    try {
                        inProgress.clear()
                        moveDelta = Offset.Zero
                        val startingTransform = state.transformSession
                        val startingBounds = startingTransform?.targetBounds(document.width, document.height)
                        val transformCenter = startingBounds?.let { Offset((it.left + it.right) / 2f, (it.top + it.bottom) / 2f) }
                        val handleRadius = 18f / gestureScale
                        val corners = startingBounds?.let { listOf(Offset(it.left.toFloat(), it.top.toFloat()),
                            Offset(it.right.toFloat(), it.top.toFloat()), Offset(it.left.toFloat(), it.bottom.toFloat()),
                            Offset(it.right.toFloat(), it.bottom.toFloat())) }.orEmpty()
                        val rotationHandle = startingBounds?.let { Offset((it.left + it.right) / 2f, it.top - 32f / gestureScale) }
                        val transformDrag = when {
                            startingTransform == null || startingBounds == null -> TransformDrag.None
                            rotationHandle != null && (Offset(initial.x, initial.y) - rotationHandle).getDistance() <= handleRadius -> TransformDrag.Rotate
                            corners.any { (Offset(initial.x, initial.y) - it).getDistance() <= handleRadius } -> TransformDrag.Scale
                            startingBounds.contains(initial.x.toInt(), initial.y.toInt()) -> TransformDrag.Move
                            else -> TransformDrag.None
                        }
                        if (startingTransform != null && transformDrag == TransformDrag.None) return@awaitEachGesture
                        val startDistance = transformCenter?.let { (Offset(initial.x, initial.y) - it).getDistance().coerceAtLeast(.001f) } ?: 1f
                        val startAngle = transformCenter?.let { atan2(initial.y - it.y, initial.x - it.x) } ?: 0f
                        movingSelection = startingTransform == null && state.tool == Tool.MoveSelection &&
                            (state.selection?.contains(initial.x.toInt(), initial.y.toInt()) == true)
                        inProgress += initial
                        var previous = down.position
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) {
                                cancelled = true
                                break
                            }
                            val amount = change.position - previous
                            val currentPoint = point(change.position, 1f)
                            if (startingTransform != null && transformCenter != null) {
                                when (transformDrag) {
                                    TransformDrag.Move -> state.updateTransform(
                                        translationX = startingTransform.translationX + currentPoint.x - initial.x,
                                        translationY = startingTransform.translationY + currentPoint.y - initial.y)
                                    TransformDrag.Scale -> state.updateTransform(
                                        scale = startingTransform.scale * ((Offset(currentPoint.x, currentPoint.y) - transformCenter).getDistance() / startDistance))
                                    TransformDrag.Rotate -> {
                                        val angle = atan2(currentPoint.y - transformCenter.y, currentPoint.x - transformCenter.x)
                                        state.updateTransform(rotationDegrees = startingTransform.rotationDegrees +
                                            (angle - startAngle) * 180f / kotlin.math.PI.toFloat())
                                    }
                                    TransformDrag.None -> Unit
                                }
                            } else if (state.tool == Tool.Pan) {
                                state.panX += amount.x
                                state.panY += amount.y
                            } else if (state.tool == Tool.MoveSelection) {
                                if (movingSelection) moveDelta += amount / gestureScale
                            } else if (change.position != previous) {
                                val pressure = if (change.type == PointerType.Stylus && change.pressed) change.pressure
                                    else inProgress.last().pressure
                                inProgress += point(change.position, pressure)
                            }
                            previous = change.position
                            change.consume()
                            if (!change.pressed) break
                        }
                        if (cancelled) return@awaitEachGesture
                        if (startingTransform != null) {
                            // The preview remains pending until the explicit Apply or Cancel action.
                        } else if (state.tool == Tool.Fill || state.tool == Tool.Eyedropper) {
                            if ((previous - down.position).getDistance() <= viewConfiguration.touchSlop)
                                state.applyPointTool(initial)
                        } else if (state.tool == Tool.MoveSelection && movingSelection) {
                            state.moveSelection(moveDelta.x.toInt(), moveDelta.y.toInt())
                        } else if (state.tool == Tool.Select && inProgress.isNotEmpty()) {
                            state.selectArea(inProgress.toList())
                        } else state.recordStroke(inProgress.toList())
                    } finally {
                        inProgress.clear()
                        moveDelta = Offset.Zero
                        movingSelection = false
                    }
                }
            }.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val scroll = change.scrollDelta.y
                        if (scroll != 0f && event.changes.none { it.pressed }) {
                            state.zoomAt(if (scroll < 0f) 1.12f else 1f / 1.12f,
                                change.position.x, change.position.y, currentCenter.x, currentCenter.y)
                            change.consume()
                        }
                    }
                }
            },
        ) {
            withTransform({ translate(origin.x, origin.y); scale(scale, scale, pivot = Offset.Zero) }) {
                drawRect(NeoCanvasColors.paper, size = Size(document.width.toFloat(), document.height.toFloat()))
                drawStoredTiles(state, transformPreview ?: movePreview ?: strokePreview, tileImages)
                val symmetry = state.symmetry
                val guideColor = NeoCanvasColors.accent.copy(alpha = .65f)
                if (symmetry == com.neoworksuite.neocanvas.renderer.DrawingSymmetry.Vertical ||
                    symmetry == com.neoworksuite.neocanvas.renderer.DrawingSymmetry.Both) {
                    drawLine(guideColor, Offset(document.width / 2f, 0f),
                        Offset(document.width / 2f, document.height.toFloat()), 1f / scale)
                }
                if (symmetry == com.neoworksuite.neocanvas.renderer.DrawingSymmetry.Horizontal ||
                    symmetry == com.neoworksuite.neocanvas.renderer.DrawingSymmetry.Both) {
                    drawLine(guideColor, Offset(0f, document.height / 2f),
                        Offset(document.width.toFloat(), document.height / 2f), 1f / scale)
                }
                drawRect(NeoCanvasColors.canvasEdge, size = Size(document.width.toFloat(), document.height.toFloat()), style = Stroke(1f / scale))
                val liveSelection = if (state.tool == Tool.Select && inProgress.isNotEmpty()) {
                    val first = inProgress.first()
                    val last = inProgress.last()
                    if (state.selectionMode == SelectionShape.Lasso) CanvasSelection.lasso(inProgress.toList())
                    else CanvasSelection(minOf(first.x, last.x).toInt(), minOf(first.y, last.y).toInt(),
                        maxOf(first.x, last.x).toInt() + 1, maxOf(first.y, last.y).toInt() + 1,
                        shape = state.selectionMode)
                } else null
                val bounds = state.transformSession?.targetBounds(document.width, document.height) ?: liveSelection ?: state.selection?.let {
                    val dx = moveDelta.x.toInt().coerceIn(-it.left, document.width - it.right)
                    val dy = moveDelta.y.toInt().coerceIn(-it.top, document.height - it.bottom)
                    it.copy(left = it.left + dx, top = it.top + dy, right = it.right + dx, bottom = it.bottom + dy,
                        points = it.points.map { point -> point.copy(x = point.x + dx, y = point.y + dy) })
                }
                bounds?.let {
                    drawSelectionOutline(it, Color.Black, 3f / scale)
                    drawSelectionOutline(it, NeoCanvasColors.accent, 1f / scale)
                    if (state.transformSession != null) {
                        val radius = 7f / scale
                        val handleColor = NeoCanvasColors.accent
                        listOf(Offset(it.left.toFloat(), it.top.toFloat()), Offset(it.right.toFloat(), it.top.toFloat()),
                            Offset(it.left.toFloat(), it.bottom.toFloat()), Offset(it.right.toFloat(), it.bottom.toFloat())).forEach { handle ->
                            drawCircle(Color.Black, radius * 1.5f, handle)
                            drawCircle(handleColor, radius, handle)
                        }
                        val topCenter = Offset((it.left + it.right) / 2f, it.top.toFloat())
                        val rotate = Offset(topCenter.x, topCenter.y - 32f / scale)
                        drawLine(handleColor, topCenter, rotate, 1f / scale)
                        drawCircle(Color.Black, radius * 1.5f, rotate)
                        drawCircle(handleColor, radius, rotate)
                    }
                }
            }
        }
        if (state.selection != null) {
            Row(Modifier.align(Alignment.BottomCenter).padding(12.dp)
                .background(NeoCanvasColors.chrome).horizontalScroll(rememberScrollState())) {
                if (state.transformSession == null) {
                    TextButton(onClick = { state.selectionMode = SelectionShape.Rectangle; state.tool = Tool.Select }) { Text("Rectangle", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.selectionMode = SelectionShape.Ellipse; state.tool = Tool.Select }) { Text("Ellipse", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.selectionMode = SelectionShape.Lasso; state.tool = Tool.Select }) { Text("Lasso", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.invertSelection() }) { Text("Invert", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.beginTransform() }) { Text("Transform", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.tool = Tool.MoveSelection }) { Text("Move", color = NeoCanvasColors.accent) }
                } else {
                    TextButton(onClick = { state.applyTransform() }) { Text("Apply", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.cancelTransform() }) { Text("Cancel", color = NeoCanvasColors.muted) }
                    TextButton(onClick = { state.updateTransform(scale = (state.transformSession?.scale ?: 1f) / 1.1f) }) { Text("Scale −", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.updateTransform(scale = (state.transformSession?.scale ?: 1f) * 1.1f) }) { Text("Scale +", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.updateTransform(rotationDegrees = (state.transformSession?.rotationDegrees ?: 0f) - 15f) }) { Text("Rotate −15°", color = NeoCanvasColors.accent) }
                    TextButton(onClick = { state.updateTransform(rotationDegrees = (state.transformSession?.rotationDegrees ?: 0f) + 15f) }) { Text("Rotate +15°", color = NeoCanvasColors.accent) }
                }
                TextButton(onClick = { state.flipSelection(horizontal = true) }) { Text("Flip horizontal", color = NeoCanvasColors.accent) }
                TextButton(onClick = { state.rotateSelection() }) { Text("Rotate 90°", color = NeoCanvasColors.accent) }
                TextButton(onClick = { state.rotateSelection(-15f) }) { Text("−15°", color = NeoCanvasColors.accent) }
                TextButton(onClick = { state.rotateSelection(15f) }) { Text("+15°", color = NeoCanvasColors.accent) }
                TextButton(onClick = { state.resizeSelection(.5f) }) { Text("Half size", color = NeoCanvasColors.accent) }
                TextButton(onClick = { state.smoothResizing = !state.smoothResizing }) {
                    Text(if (state.smoothResizing) "Resize: smooth" else "Resize: pixel", color = NeoCanvasColors.accent)
                }
                TextButton(onClick = { state.resizeSelection(2f) }) { Text("Double size", color = NeoCanvasColors.accent) }
                TextButton(onClick = { state.flipSelection(horizontal = false) }) { Text("Flip vertical", color = NeoCanvasColors.accent) }
                TextButton(onClick = { state.clearSelection() }) { Text("Deselect", color = NeoCanvasColors.muted) }
                TextButton(onClick = { state.clearSelectedPixels() }) { Text("Clear pixels", color = NeoCanvasColors.muted) }
            }
        }
    }
}

private fun DrawScope.drawSelectionOutline(selection: CanvasSelection, color: Color, width: Float) {
    selection.invertedRegion?.let { inverted ->
        drawRect(color, Offset(selection.left.toFloat(), selection.top.toFloat()),
            Size((selection.right - selection.left).toFloat(), (selection.bottom - selection.top).toFloat()), style = Stroke(width))
        drawSelectionOutline(inverted, color, width)
        return
    }
    when (selection.shape) {
        SelectionShape.Rectangle -> drawRect(color, Offset(selection.left.toFloat(), selection.top.toFloat()),
            Size((selection.right - selection.left).toFloat(), (selection.bottom - selection.top).toFloat()), style = Stroke(width))
        SelectionShape.Ellipse -> drawOval(color, Offset(selection.left.toFloat(), selection.top.toFloat()),
            Size((selection.right - selection.left).toFloat(), (selection.bottom - selection.top).toFloat()), style = Stroke(width))
        SelectionShape.Lasso -> if (selection.points.size >= 2) {
            val path = Path().apply {
                moveTo(selection.points.first().x, selection.points.first().y)
                selection.points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, color, style = Stroke(width))
        }
    }
}

/** Draws persisted tile pixels, so reopening a saved document is visibly identical to the original. */
private fun DrawScope.drawStoredTiles(state: EditorState, preview: com.neoworksuite.neocanvas.renderer.RasterPatch?, images: TileImageCache) {
    state.document.layers.filter { it.visible && it.opacity > 0f }.forEach { layer ->
        val raster = layer.payload as? com.neoworksuite.neocanvas.core.model.LayerPayload.Raster ?: return@forEach
        val addresses = raster.tileAddresses + preview?.keys.orEmpty().filter { it.layerId == layer.id }
        addresses.forEach { address ->
            val pixels = (if (preview != null) preview.previewTile(address, state.tileStore)
                else state.tileStore.read(address)) ?: return@forEach
            val blendMode = when (layer.blendMode) {
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Normal -> androidx.compose.ui.graphics.BlendMode.SrcOver
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Multiply -> androidx.compose.ui.graphics.BlendMode.Multiply
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Screen -> androidx.compose.ui.graphics.BlendMode.Screen
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Overlay -> androidx.compose.ui.graphics.BlendMode.Overlay
            }
            drawImage(images.image(address, pixels), Offset(address.x * 256f, address.y * 256f), alpha = layer.opacity, blendMode = blendMode)
        }
    }
}

internal object NeoCanvasColors {
    val workspace = Color(0xFF0E1014)
    val chrome = Color(0xFF171B22)
    val rail = Color(0xFF15191F)
    val panel = Color(0xFF1B2028)
    val panelRaised = Color(0xFF242B35)
    val canvasBed = Color(0xFF292E36)
    val canvasEdge = Color(0xFFCFC9BF)
    val paper = Color(0xFFFAF8F2)
    val accent = Color(0xFF69D5BF)
    val ink = Color(0xFF0E1818)
    val muted = Color(0xFFC3CBD5)
    val faint = Color(0xFF7C8797)
    val line = Color(0xFF303744)
    val track = Color(0xFF3B4452)
    val disabled = Color(0xFF53606E)
}
