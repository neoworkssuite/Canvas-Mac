package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
    var quickShapePointerDown by remember { mutableStateOf(false) }
    var quickShapeRevision by remember { mutableIntStateOf(0) }
    var quickShapeRawPoints by remember { mutableStateOf<List<DrawPoint>>(emptyList()) }
    var quickShapeSnapped by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    LaunchedEffect(quickShapePointerDown, quickShapeRevision, state.quickShapeEnabled, state.tool) {
        if (!quickShapePointerDown || !state.quickShapeEnabled || state.tool != Tool.Brush) return@LaunchedEffect
        val revision = quickShapeRevision
        delay(450)
        if (!quickShapePointerDown || quickShapeRevision != revision || quickShapeSnapped) return@LaunchedEffect
        val shape = detectQuickShape(quickShapeRawPoints) ?: return@LaunchedEffect
        quickShapeSnapped = true
        inProgress.clear()
        inProgress.addAll(shape.points)
        state.statusMessage = "QuickShape: ${shape.type.label} — lift to place"
    }

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
        val currentRotation by rememberUpdatedState(state.viewRotationDegrees)
        val previewPoints = inProgress.toList()
        val strokePreview = remember(previewPoints, document, state.tool, state.activeLayerId,
            state.brush, state.brushSize, state.brushOpacity, state.smudgeStrength, state.color, state.selection, state.stabilization,
            state.symmetry, quickShapeSnapped) {
            if (state.tool == Tool.Smudge) state.previewSmudge(previewPoints, stabilize = true)
            else state.previewStroke(previewPoints, stabilize = !quickShapeSnapped)
        }
        val movePreview = remember(moveDelta, movingSelection, state.selection, state.activeLayerId, document) {
            if (movingSelection) state.previewSelectionMove(moveDelta.x.toInt(), moveDelta.y.toInt()) else null
        }
        val transformPreview = remember(state.transformSession, state.activeLayerId, document, state.smoothResizing) {
            state.previewTransform()
        }

        Canvas(
            Modifier.fillMaxSize().onSizeChanged { viewport = it }
                .pointerInput(document.id, viewport) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        var maxTouchCount = if (firstDown.type == PointerType.Stylus) 0 else 1
                        var multiTouchStartedAt = 0L
                        var lastEventTime = firstDown.uptimeMillis
                        var transformStarted = false
                        var accumulatedPan = Offset.Zero
                        var accumulatedZoom = 1f
                        var accumulatedRotation = 0f
                        var touchTravel = 0f
                        var stylusSeen = firstDown.type == PointerType.Stylus

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            lastEventTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: lastEventTime
                            stylusSeen = stylusSeen || event.changes.any { it.pressed && it.type == PointerType.Stylus }

                            val touches = event.changes.filter { it.pressed && it.type != PointerType.Stylus }
                            maxTouchCount = maxOf(maxTouchCount, touches.size)
                            if (touches.size >= 2 && multiTouchStartedAt == 0L) multiTouchStartedAt = lastEventTime

                            event.changes.filter { it.type != PointerType.Stylus && (it.pressed || it.previousPressed) }
                                .forEach { touchTravel += (it.position - it.previousPosition).getDistance() }

                            if (touches.size >= 2) {
                                // Multi-touch always belongs to canvas navigation/shortcuts, never to a brush stroke.
                                inProgress.clear()
                                touches.forEach { it.consume() }

                                if (touches.size == 2) {
                                    val first = touches[0]
                                    val second = touches[1]
                                    val previousFirst = first.previousPosition
                                    val previousSecond = second.previousPosition
                                    val previousVector = previousSecond - previousFirst
                                    val currentVector = second.position - first.position
                                    val previousDistance = previousVector.getDistance().coerceAtLeast(.001f)
                                    val zoomChange = (currentVector.getDistance() / previousDistance)
                                        .takeIf { it.isFinite() && it > 0f } ?: 1f
                                    val rotationChange = if (state.canvasRotationEnabled)
                                        angleDeltaDegrees(previousVector, currentVector)
                                    else 0f
                                    val previousCentroid = (previousFirst + previousSecond) / 2f
                                    val currentCentroid = (first.position + second.position) / 2f
                                    val panChange = currentCentroid - previousCentroid

                                    accumulatedPan += panChange
                                    accumulatedZoom *= zoomChange
                                    accumulatedRotation += rotationChange

                                    if (!transformStarted) {
                                        transformStarted =
                                            accumulatedPan.getDistance() > viewConfiguration.touchSlop ||
                                                abs(accumulatedZoom - 1f) > .015f ||
                                                abs(accumulatedRotation) > 1.5f
                                    }
                                    if (transformStarted) {
                                        applyViewportTransform(
                                            state = state,
                                            baseCenter = currentCenter,
                                            previousCentroid = previousCentroid,
                                            currentCentroid = currentCentroid,
                                            zoomChange = zoomChange,
                                            rotationChange = rotationChange,
                                        )
                                    }
                                }
                            } else if (multiTouchStartedAt != 0L) {
                                // Keep the remaining finger from becoming a new stroke while a multi-touch
                                // gesture is winding down.
                                event.changes.filter { it.type != PointerType.Stylus && (it.pressed || it.previousPressed) }
                                    .forEach { it.consume() }
                            }

                            val anyTouchPressed = event.changes.any { it.pressed && it.type != PointerType.Stylus }
                            if (!anyTouchPressed && multiTouchStartedAt != 0L) {
                                val duration = (lastEventTime - multiTouchStartedAt).coerceAtLeast(0L)
                                val tapTravelLimit = viewConfiguration.touchSlop * maxOf(2, maxTouchCount) * 1.5f

                                if (!stylusSeen && !transformStarted && duration <= 350L && touchTravel <= tapTravelLimit) {
                                    when (maxTouchCount) {
                                        2 -> if (state.undo()) state.statusMessage = "Undo"
                                        3 -> if (state.redo()) state.statusMessage = "Redo"
                                    }
                                } else if (
                                    !stylusSeen && transformStarted && maxTouchCount == 2 &&
                                    duration <= 280L && abs(accumulatedZoom - 1f) >= .35f &&
                                    accumulatedPan.getDistance() <= viewConfiguration.touchSlop * 2.5f &&
                                    abs(accumulatedRotation) <= 7f
                                ) {
                                    state.resetView()
                                    state.statusMessage = "Fit canvas"
                                }
                                break
                            }

                            if (event.changes.none { it.pressed }) break
                        }
                    }
                }
                .pointerInput(
                    state.tool,
                    state.activeLayerId,
                    state.brushSize,
                    state.brushOpacity,
                    state.smudgeStrength,
                    state.fingerPaintingEnabled,
                    state.quickShapeEnabled,
                    document.id,
                    viewport,
                ) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (
                        down.type != PointerType.Stylus &&
                        !state.fingerPaintingEnabled &&
                        state.tool in listOf(Tool.Brush, Tool.Eraser, Tool.Smudge)
                    ) {
                        return@awaitEachGesture
                    }
                    if (state.tool != Tool.Pan && state.tool != Tool.Eyedropper && state.tool != Tool.Select &&
                        state.document.layers.any { it.id == state.activeLayerId && it.locked }) {
                        state.statusMessage = "Layer is locked — unlock it in Layers to edit"
                        down.consume()
                        return@awaitEachGesture
                    }
                    val gestureOrigin = currentOrigin
                    val gestureScale = currentScale
                    val gestureRotation = currentRotation
                    val documentCenter = Offset(document.width / 2f, document.height / 2f)
                    fun point(position: Offset, pressure: Float): DrawPoint {
                        val unscaled = Offset(
                            (position.x - gestureOrigin.x) / gestureScale,
                            (position.y - gestureOrigin.y) / gestureScale,
                        )
                        val documentPoint = documentCenter +
                            rotateOffset(unscaled - documentCenter, -gestureRotation)
                        return DrawPoint(
                            documentPoint.x,
                            documentPoint.y,
                            normalizedPressure(pressure),
                        )
                    }
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
                        quickShapeSnapped = false
                        quickShapePointerDown =
                            state.quickShapeEnabled && state.tool == Tool.Brush && startingTransform == null
                        quickShapeRawPoints = if (quickShapePointerDown) listOf(initial) else emptyList()
                        quickShapeRevision++
                        var previous = down.position
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            val pressedTouches = event.changes.count { it.pressed && it.type != PointerType.Stylus }
                            if (change == null || change.isConsumed || pressedTouches > 1) {
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
                                    else (quickShapeRawPoints.lastOrNull()?.pressure ?: inProgress.last().pressure)
                                val drawnPoint = point(change.position, pressure)
                                if (quickShapePointerDown && state.tool == Tool.Brush) {
                                    val movement = (change.position - previous).getDistance()
                                    if (quickShapeSnapped && movement > viewConfiguration.touchSlop * .25f) {
                                        quickShapeSnapped = false
                                        inProgress.clear()
                                        inProgress.addAll(quickShapeRawPoints)
                                    }
                                    quickShapeRawPoints = quickShapeRawPoints + drawnPoint
                                    if (!quickShapeSnapped) inProgress += drawnPoint
                                    if (movement > maxOf(1.5f, viewConfiguration.touchSlop * .10f)) {
                                        quickShapeRevision++
                                    }
                                } else {
                                    inProgress += drawnPoint
                                }
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
                        } else state.recordStroke(inProgress.toList(), stabilize = !quickShapeSnapped)
                    } finally {
                        quickShapePointerDown = false
                        quickShapeRawPoints = emptyList()
                        quickShapeSnapped = false
                        quickShapeRevision++
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
            withTransform({
                translate(origin.x, origin.y)
                scale(scale, scale, pivot = Offset.Zero)
                rotate(state.viewRotationDegrees, pivot = Offset(document.width / 2f, document.height / 2f))
            }) {
                drawRect(NeoCanvasColors.paper, size = Size(document.width.toFloat(), document.height.toFloat()))
                drawStoredTiles(state, transformPreview ?: movePreview ?: state.effectPreviewPatch ?: strokePreview, tileImages)

                if (state.gridGuideVisible) {
                    val spacing = state.guideSpacing.coerceIn(32f, 512f)
                    val gridColor = NeoCanvasColors.accent.copy(alpha = .22f)
                    var gx = spacing
                    while (gx < document.width) {
                        drawLine(gridColor, Offset(gx, 0f), Offset(gx, document.height.toFloat()), 1f / scale)
                        gx += spacing
                    }
                    var gy = spacing
                    while (gy < document.height) {
                        drawLine(gridColor, Offset(0f, gy), Offset(document.width.toFloat(), gy), 1f / scale)
                        gy += spacing
                    }
                }

                if (state.perspectiveGuideVisible) {
                    val perspectiveColor = NeoCanvasColors.accent.copy(alpha = .34f)
                    val vanishing = Offset(document.width / 2f, document.height / 2f)
                    val edgeStep = (state.guideSpacing * 1.5f).coerceIn(64f, 768f)
                    var x = 0f
                    while (x <= document.width) {
                        drawLine(perspectiveColor, Offset(x, 0f), vanishing, 1f / scale)
                        drawLine(perspectiveColor, Offset(x, document.height.toFloat()), vanishing, 1f / scale)
                        x += edgeStep
                    }
                }

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
        if (state.effectPreviewType != null) {
            Box(
                Modifier.fillMaxSize().pointerInput(state.effectPreviewType) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        state.adjustEffectPreviewPrimary(dragAmount / width)
                        change.consume()
                    }
                },
            )
        }

        state.effectPreviewType?.let { type ->
            LiveEffectCanvasReadout(
                type = type,
                amount = state.effectPreviewSettings.amount,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            )
        }

        if (state.selection != null) {
            SelectionControlDock(
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun LiveEffectCanvasReadout(
    type: com.neoworksuite.neocanvas.renderer.RasterEffectType,
    amount: Float,
    modifier: Modifier = Modifier,
) {
    val signed = type == com.neoworksuite.neocanvas.renderer.RasterEffectType.HueSaturation ||
        type == com.neoworksuite.neocanvas.renderer.RasterEffectType.ColourBalance ||
        type == com.neoworksuite.neocanvas.renderer.RasterEffectType.Curves
    val binary = type == com.neoworksuite.neocanvas.renderer.RasterEffectType.GradientMap ||
        type == com.neoworksuite.neocanvas.renderer.RasterEffectType.Grayscale ||
        type == com.neoworksuite.neocanvas.renderer.RasterEffectType.Invert
    val percent = if (binary) 100 else (amount * 100f).toInt()
    val strength = if (binary) 1f else abs(amount).coerceIn(0f, 1f)

    Column(
        modifier.widthIn(min = 210.dp, max = 320.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NeoCanvasColors.chrome.copy(alpha = .94f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                effectName(type),
                color = NeoCanvasColors.paper,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (signed && percent > 0) "+$percent%" else "$percent%",
                color = NeoCanvasColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Slide left/right to adjust",
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
        )
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(NeoCanvasColors.track)) {
            if (strength > 0f) {
                Box(
                    Modifier.fillMaxWidth(strength)
                        .height(3.dp)
                        .background(NeoCanvasColors.accent),
                )
            }
        }
    }
}

@Composable
private fun SelectionControlDock(state: EditorState, modifier: Modifier = Modifier) {
    val transform = state.transformSession
    Column(
        modifier.widthIn(max = 760.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NeoCanvasColors.chrome.copy(alpha = .96f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (transform == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TransformDockButton("Rectangle") { state.selectionMode = SelectionShape.Rectangle; state.tool = Tool.Select }
                TransformDockButton("Ellipse") { state.selectionMode = SelectionShape.Ellipse; state.tool = Tool.Select }
                TransformDockButton("Lasso") { state.selectionMode = SelectionShape.Lasso; state.tool = Tool.Select }
                TransformDockButton("Invert") { state.invertSelection() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TransformDockButton("Replace", emphasized = state.selectionCombineMode == SelectionCombineMode.Replace) {
                    state.selectionCombineMode = SelectionCombineMode.Replace
                }
                TransformDockButton("Add", emphasized = state.selectionCombineMode == SelectionCombineMode.Add) {
                    state.selectionCombineMode = SelectionCombineMode.Add
                }
                TransformDockButton("Subtract", emphasized = state.selectionCombineMode == SelectionCombineMode.Subtract) {
                    state.selectionCombineMode = SelectionCombineMode.Subtract
                }
                TransformDockButton("Intersect", emphasized = state.selectionCombineMode == SelectionCombineMode.Intersect) {
                    state.selectionCombineMode = SelectionCombineMode.Intersect
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TransformDockButton("Transform", emphasized = true) { state.beginTransform() }
                TransformDockButton("Move") { state.tool = Tool.MoveSelection }
                TransformDockButton("Deselect", muted = true) { state.clearSelection() }
                TransformDockButton("Clear pixels", muted = true) { state.clearSelectedPixels() }
            }
        } else {
            val scalePercent = (transform.scale * 100f).toInt()
            val widthPercent = (transform.scaleX * 100f).toInt()
            val heightPercent = (transform.scaleY * 100f).toInt()
            val rotation = transform.rotationDegrees.toInt()
            Text(
                "SCALE " + scalePercent + "%  •  W " + widthPercent + "%  •  H " + heightPercent + "%  •  " + rotation + "°",
                color = NeoCanvasColors.muted,
                fontSize = 10.sp,
                letterSpacing = .7.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 1.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TransformDockButton("Cancel", muted = true) { state.cancelTransform() }
                TransformDockButton("Reset") { state.resetTransform() }
                TransformDockButton("Fit") { state.fitTransformToCanvas() }
                TransformDockButton("Done", emphasized = true) { state.applyTransform() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TransformDockButton("W−") { state.scaleTransformAxis(horizontal = true, factor = .9f) }
                TransformDockButton("W+") { state.scaleTransformAxis(horizontal = true, factor = 1.1f) }
                TransformDockButton("H−") { state.scaleTransformAxis(horizontal = false, factor = .9f) }
                TransformDockButton("H+") { state.scaleTransformAxis(horizontal = false, factor = 1.1f) }
                TransformDockButton(if (state.transformSnapping) "Snap ✓" else "Snap") {
                    state.transformSnapping = !state.transformSnapping
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TransformDockButton("Flip H") {
                    if (state.applyTransform()) {
                        state.flipSelection(horizontal = true)
                        state.beginTransform()
                    }
                }
                TransformDockButton("Flip V") {
                    if (state.applyTransform()) {
                        state.flipSelection(horizontal = false)
                        state.beginTransform()
                    }
                }
                TransformDockButton("−15°") {
                    state.updateTransform(rotationDegrees = (state.transformSession?.rotationDegrees ?: 0f) - 15f)
                }
                TransformDockButton("+15°") {
                    state.updateTransform(rotationDegrees = (state.transformSession?.rotationDegrees ?: 0f) + 15f)
                }
                TransformDockButton(if (state.smoothResizing) "Smooth" else "Pixel") {
                    state.smoothResizing = !state.smoothResizing
                }
            }
        }
    }
}

@Composable
private fun TransformDockButton(
    label: String,
    emphasized: Boolean = false,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            label,
            color = when {
                emphasized -> NeoCanvasColors.accent
                muted -> NeoCanvasColors.muted
                else -> NeoCanvasColors.paper
            },
            fontSize = 12.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun DrawScope.drawSelectionOutline(selection: CanvasSelection, color: Color, width: Float) {
    if (selection.baseRegion != null && selection.combinedRegion != null) {
        drawSelectionOutline(selection.baseRegion, color, width)
        drawSelectionOutline(selection.combinedRegion, color.copy(alpha = .72f), width)
        return
    }
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
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Darken -> androidx.compose.ui.graphics.BlendMode.Darken
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Lighten -> androidx.compose.ui.graphics.BlendMode.Lighten
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.ColorDodge -> androidx.compose.ui.graphics.BlendMode.ColorDodge
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.ColorBurn -> androidx.compose.ui.graphics.BlendMode.ColorBurn
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.SoftLight -> androidx.compose.ui.graphics.BlendMode.Softlight
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.HardLight -> androidx.compose.ui.graphics.BlendMode.Hardlight
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Difference -> androidx.compose.ui.graphics.BlendMode.Difference
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Exclusion -> androidx.compose.ui.graphics.BlendMode.Exclusion
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Add -> androidx.compose.ui.graphics.BlendMode.Plus
                com.neoworksuite.neocanvas.core.model.LayerBlendMode.Subtract -> androidx.compose.ui.graphics.BlendMode.SrcOver
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


private fun applyViewportTransform(
    state: EditorState,
    baseCenter: Offset,
    previousCentroid: Offset,
    currentCentroid: Offset,
    zoomChange: Float,
    rotationChange: Float,
) {
    val previousZoom = state.zoom
    val nextZoom = (previousZoom * zoomChange).coerceIn(.20f, 6f)
    val actualZoomChange = if (previousZoom > 0f) nextZoom / previousZoom else 1f
    val canvasCenter = baseCenter + Offset(state.panX, state.panY)
    val relativeToCanvasCenter = previousCentroid - canvasCenter
    val transformedRelative = rotateOffset(relativeToCanvasCenter * actualZoomChange, rotationChange)
    val nextCanvasCenter = currentCentroid - transformedRelative

    state.zoom = nextZoom
    state.panX = nextCanvasCenter.x - baseCenter.x
    state.panY = nextCanvasCenter.y - baseCenter.y
    state.rotateViewBy(rotationChange)
}

private fun angleDeltaDegrees(previous: Offset, current: Offset): Float {
    if (previous.getDistance() <= .001f || current.getDistance() <= .001f) return 0f
    val previousAngle = atan2(previous.y, previous.x)
    val currentAngle = atan2(current.y, current.x)
    return normalizeViewRotation((currentAngle - previousAngle) * 180f / kotlin.math.PI.toFloat())
}

private fun rotateOffset(offset: Offset, degrees: Float): Offset {
    if (degrees == 0f) return offset
    val radians = degrees * kotlin.math.PI.toFloat() / 180f
    val cosine = cos(radians)
    val sine = sin(radians)
    return Offset(
        x = offset.x * cosine - offset.y * sine,
        y = offset.x * sine + offset.y * cosine,
    )
}
