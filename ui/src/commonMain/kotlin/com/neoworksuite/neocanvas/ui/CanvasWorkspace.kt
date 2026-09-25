package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.LineCap
import com.neoworksuite.neocanvas.core.model.LineMarker
import com.neoworksuite.neocanvas.core.model.LineStyle
import com.neoworksuite.neocanvas.core.model.ShapeKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class TransformDrag { None, Move, Scale, Rotate }
private enum class ObjectDrag { None, Move, Scale, Rotate, LineStart, LineEnd }

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
    var quickShapeResult by remember { mutableStateOf<QuickShapeResult?>(null) }
    var objectGesturePreview by remember { mutableStateOf<LayerPayload?>(null) }
    var objectGroupGesturePreview by remember { mutableStateOf<Map<String, LayerPayload>>(emptyMap()) }
    var arrangePickMarquee by remember { mutableStateOf<Rect?>(null) }
    var quickMenuPointerDown by remember { mutableStateOf(false) }
    var quickMenuRevision by remember { mutableIntStateOf(0) }
    var quickMenuCandidate by remember { mutableStateOf<Offset?>(null) }
    var quickMenuAnchor by remember { mutableStateOf<Offset?>(null) }
    var clipboardMenuVisible by remember { mutableStateOf(false) }
    var rapidHistoryFingerCount by remember { mutableIntStateOf(0) }
    var rapidHistoryRevision by remember { mutableIntStateOf(0) }
    var rapidHistoryTriggered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textFontFamilies = rememberNeoCanvasFontFamilies()

    LaunchedEffect(quickShapePointerDown, quickShapeRevision, state.quickShapeEnabled, state.tool) {
        if (!quickShapePointerDown || !state.quickShapeEnabled || state.tool != Tool.Brush) return@LaunchedEffect
        val revision = quickShapeRevision
        delay(450)
        if (!quickShapePointerDown || quickShapeRevision != revision || quickShapeSnapped) return@LaunchedEffect
        val shape = detectQuickShape(quickShapeRawPoints) ?: return@LaunchedEffect
        quickShapeSnapped = true
        quickShapeResult = shape
        inProgress.clear()
        inProgress.addAll(shape.points)
        state.statusMessage = "QuickShape: ${shape.type.label} — lift to place"
    }

    LaunchedEffect(quickMenuPointerDown, quickMenuRevision) {
        if (!quickMenuPointerDown) return@LaunchedEffect
        val revision = quickMenuRevision
        delay(450)
        if (!quickMenuPointerDown || quickMenuRevision != revision) return@LaunchedEffect
        quickMenuAnchor = quickMenuCandidate
        if (quickMenuAnchor != null) state.statusMessage = "QuickMenu — choose an action"
    }

    LaunchedEffect(rapidHistoryFingerCount, rapidHistoryRevision) {
        val fingers = rapidHistoryFingerCount
        if (fingers != 2 && fingers != 3) return@LaunchedEffect
        val revision = rapidHistoryRevision
        delay(550)
        while (rapidHistoryFingerCount == fingers && rapidHistoryRevision == revision) {
            val changed = if (fingers == 2) state.undo() else state.redo()
            if (!changed) break
            rapidHistoryTriggered = true
            state.statusMessage = if (fingers == 2) "Rapid Undo" else "Rapid Redo"
            delay(140)
        }
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
            state.brush, state.brushSize, state.brushOpacity, state.smudgeStrength,
            state.liquifySize, state.liquifyStrength, state.liquifyMode, state.color, state.selection, state.stabilization,
            state.symmetry, quickShapeSnapped) {
            when (state.tool) {
                Tool.Smudge -> state.previewSmudge(previewPoints, stabilize = true)
                Tool.Liquify -> state.previewLiquify(previewPoints, stabilize = true)
                else -> state.previewStroke(previewPoints, stabilize = !quickShapeSnapped)
            }
        }
        val movePreview = remember(moveDelta, movingSelection, state.selection, state.activeLayerId, document) {
            if (movingSelection) state.previewSelectionMove(moveDelta.x.toInt(), moveDelta.y.toInt()) else null
        }
        val transformState = remember(state.transformSession, state.activeLayerId, document, state.smoothResizing) {
            state.previewTransformState()
        }
        val transformPreview = transformState?.patch
        val transformSelection = transformState?.selection

        Canvas(
            Modifier.fillMaxSize().onSizeChanged { viewport = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                quickMenuPointerDown = false
                                quickMenuCandidate = null
                                quickMenuRevision++
                                quickMenuAnchor = event.changes.firstOrNull()?.position
                                if (quickMenuAnchor != null) state.statusMessage = "QuickMenu — choose an action"
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
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
                        var threeFingerStart: Offset? = null
                        var threeFingerEnd: Offset? = null
                        var threeFingerLastX: Float? = null
                        var threeFingerScrubSegment = 0f
                        var threeFingerScrubDirection = 0
                        var threeFingerScrubReversals = 0
                        var threeFingerHorizontalTravel = 0f
                        rapidHistoryTriggered = false
                        rapidHistoryFingerCount = 0
                        rapidHistoryRevision++

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            lastEventTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: lastEventTime
                            stylusSeen = stylusSeen || event.changes.any { it.pressed && it.type == PointerType.Stylus }

                            val touches = event.changes.filter { it.pressed && it.type != PointerType.Stylus }
                            maxTouchCount = maxOf(maxTouchCount, touches.size)
                            if (touches.size >= 2 && multiTouchStartedAt == 0L) multiTouchStartedAt = lastEventTime

                            event.changes.filter { it.type != PointerType.Stylus && (it.pressed || it.previousPressed) }
                                .forEach { touchTravel += (it.position - it.previousPosition).getDistance() }

                            val rapidFingers = if (shouldArmRapidHistoryGesture(
                                    fingerCount = touches.size,
                                    touchTravel = touchTravel,
                                    touchSlop = viewConfiguration.touchSlop,
                                    stylusSeen = stylusSeen,
                                    transformStarted = transformStarted,
                                )
                            ) touches.size else 0
                            if (rapidFingers != rapidHistoryFingerCount) {
                                rapidHistoryFingerCount = rapidFingers
                                rapidHistoryRevision++
                            }

                            if (touches.size >= 2) {
                                // Multi-touch always belongs to canvas navigation/shortcuts, never to a brush stroke.
                                inProgress.clear()
                                touches.forEach { it.consume() }

                                if (touches.size >= 3) {
                                    val centroid = touches.map { it.position }.reduce { a, b -> a + b } / touches.size.toFloat()
                                    if (threeFingerStart == null) threeFingerStart = centroid
                                    threeFingerEnd = centroid
                                    threeFingerLastX?.let { previousX ->
                                        val dx = centroid.x - previousX
                                        threeFingerHorizontalTravel += kotlin.math.abs(dx)
                                        threeFingerScrubSegment += dx
                                        if (kotlin.math.abs(threeFingerScrubSegment) >= viewConfiguration.touchSlop * 1.25f) {
                                            val direction = if (threeFingerScrubSegment > 0f) 1 else -1
                                            if (threeFingerScrubDirection != 0 && direction != threeFingerScrubDirection) {
                                                threeFingerScrubReversals++
                                            }
                                            threeFingerScrubDirection = direction
                                            threeFingerScrubSegment = 0f
                                        }
                                    }
                                    threeFingerLastX = centroid.x
                                }

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
                                rapidHistoryFingerCount = 0
                                rapidHistoryRevision++

                                if (rapidHistoryTriggered) {
                                    // Hold-to-repeat already performed the history action; do not add the tap action.
                                } else if (isThreeFingerScrubClear(
                                        maxTouchCount = maxTouchCount,
                                        durationMillis = duration,
                                        reversals = threeFingerScrubReversals,
                                        horizontalTravel = threeFingerHorizontalTravel,
                                        touchSlop = viewConfiguration.touchSlop,
                                        stylusSeen = stylusSeen,
                                    )
                                ) {
                                    state.clearActiveRasterLayer()
                                } else if (isThreeFingerClipboardSwipe(
                                        maxTouchCount = maxTouchCount,
                                        durationMillis = duration,
                                        start = threeFingerStart,
                                        end = threeFingerEnd,
                                        touchSlop = viewConfiguration.touchSlop,
                                        stylusSeen = stylusSeen,
                                    )
                                ) {
                                    clipboardMenuVisible = true
                                    state.statusMessage = "Artwork clipboard — Copy or Paste"
                                } else if (isFourFingerCanvasToggle(
                                        maxTouchCount = maxTouchCount,
                                        durationMillis = duration,
                                        touchTravel = touchTravel,
                                        touchSlop = viewConfiguration.touchSlop,
                                        stylusSeen = stylusSeen,
                                    )
                                ) {
                                    state.toggleCanvasOnlyMode()
                                } else if (!stylusSeen && !transformStarted && duration <= 350L && touchTravel <= tapTravelLimit) {
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
                    state.liquifySize,
                    state.liquifyStrength,
                    state.liquifyMode,
                    state.fingerPaintingEnabled,
                    state.quickShapeEnabled,
                    state.objectEditorVisible,
                    state.objectArrangePicking,
                    document.id,
                    viewport,
                ) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (quickMenuAnchor != null) {
                        quickMenuAnchor = null
                        down.consume()
                        return@awaitEachGesture
                    }
                    if (shouldArmQuickMenu(
                            isStylus = down.type == PointerType.Stylus,
                            fingerPaintingEnabled = state.fingerPaintingEnabled,
                            tool = state.tool,
                            objectArrangePicking = state.objectArrangePicking,
                        )
                    ) {
                        quickMenuPointerDown = true
                        quickMenuCandidate = down.position
                        quickMenuRevision++
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val pressedTouches = event.changes.count { it.pressed && it.type != PointerType.Stylus }
                                if (pressedTouches > 1 ||
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                ) {
                                    return@awaitEachGesture
                                }
                                if (quickMenuAnchor != null) change.consume()
                                if (!change.pressed) break
                            }
                        } finally {
                            quickMenuPointerDown = false
                            quickMenuCandidate = null
                            quickMenuRevision++
                        }
                        return@awaitEachGesture
                    }
                    if (
                        down.type != PointerType.Stylus &&
                        !state.fingerPaintingEnabled &&
                        state.tool in listOf(Tool.Brush, Tool.Eraser, Tool.Smudge, Tool.Liquify)
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

                    val initialOffset = Offset(initial.x, initial.y)

                    if (state.objectArrangePicking) {
                        var moved = false
                        var finalPoint = initialOffset
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val current = point(change.position, 1f)
                            finalPoint = Offset(current.x, current.y)
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            arrangePickMarquee = if (moved) {
                                Rect(
                                    left = minOf(initialOffset.x, finalPoint.x),
                                    top = minOf(initialOffset.y, finalPoint.y),
                                    right = maxOf(initialOffset.x, finalPoint.x),
                                    bottom = maxOf(initialOffset.y, finalPoint.y),
                                )
                            } else null
                            change.consume()
                            if (!change.pressed) break
                        }
                        if (moved) {
                            arrangePickMarquee?.let { marquee ->
                                state.setObjectArrangeSelection(
                                    editableObjectLayerIdsInRect(document, marquee),
                                )
                            }
                        } else {
                            val hit = editableObjectLayerAtPoint(
                                document = document,
                                point = initialOffset,
                                padding = 8f / gestureScale,
                            )
                            if (hit != null) {
                                state.toggleObjectArrangeSelection(hit)
                            } else {
                                state.statusMessage = "No editable Text or Shape object under that point"
                            }
                        }
                        arrangePickMarquee = null
                        return@awaitEachGesture
                    }

                    val arrangeGroupsById = document.groups.associateBy { it.id }
                    val startingGroupLayers = document.layers.filter { layer ->
                        layer.id in state.selectedObjectLayerIds &&
                            (layer.payload is LayerPayload.TextObject || layer.payload is LayerPayload.ShapeObject)
                    }
                    val startingGroupPayloads: Map<String, LayerPayload> =
                        if (startingGroupLayers.size >= 2) startingGroupLayers.associate { it.id to it.payload } else emptyMap()
                    val startingGroupBounds = editableObjectArrangeBounds(startingGroupPayloads.values.toList())
                    val groupCenter = startingGroupBounds?.let {
                        Offset((it.left + it.right) / 2f, (it.top + it.bottom) / 2f)
                    }
                    val groupHandleRadius = 18f / gestureScale
                    val groupCorners = startingGroupBounds?.let {
                        listOf(
                            Offset(it.left, it.top),
                            Offset(it.right, it.top),
                            Offset(it.left, it.bottom),
                            Offset(it.right, it.bottom),
                        )
                    }.orEmpty()
                    val groupRotationHandle = startingGroupBounds?.let {
                        Offset((it.left + it.right) / 2f, it.top - 32f / gestureScale)
                    }
                    val groupDrag = when {
                        startingGroupBounds == null -> ObjectDrag.None
                        groupRotationHandle != null &&
                            (initialOffset - groupRotationHandle).getDistance() <= groupHandleRadius -> ObjectDrag.Rotate
                        groupCorners.any { (initialOffset - it).getDistance() <= groupHandleRadius } -> ObjectDrag.Scale
                        initialOffset.x in startingGroupBounds.left..startingGroupBounds.right &&
                            initialOffset.y in startingGroupBounds.top..startingGroupBounds.bottom -> ObjectDrag.Move
                        else -> ObjectDrag.None
                    }
                    if (groupDrag != ObjectDrag.None && startingGroupLayers.any { layer ->
                            layer.locked || layer.groupId?.let { arrangeGroupsById[it]?.locked } == true
                        }) {
                        state.statusMessage = "Unlock all marked objects before transforming them"
                        down.consume()
                        return@awaitEachGesture
                    }

                    val startingObjectLayer = state.activeObjectLayer?.takeIf {
                        state.objectEditorVisible && groupDrag == ObjectDrag.None
                    }
                    val startingObjectPayload = startingObjectLayer?.payload?.takeIf {
                        it is LayerPayload.TextObject || it is LayerPayload.ShapeObject
                    }
                    val startingObjectGeometry = startingObjectPayload?.editableObjectGeometry()
                    val objectHandleRadius = 18f / gestureScale
                    val objectDrag = when {
                        startingObjectGeometry == null -> ObjectDrag.None
                        startingObjectGeometry.isLine &&
                            (initialOffset - startingObjectGeometry.scaleHandles()[0]).getDistance() <= objectHandleRadius ->
                            ObjectDrag.LineStart
                        startingObjectGeometry.isLine &&
                            (initialOffset - startingObjectGeometry.scaleHandles()[1]).getDistance() <= objectHandleRadius ->
                            ObjectDrag.LineEnd
                        (initialOffset - startingObjectGeometry.rotationHandle(32f / gestureScale)).getDistance() <= objectHandleRadius ->
                            ObjectDrag.Rotate
                        startingObjectGeometry.scaleHandles().any { (initialOffset - it).getDistance() <= objectHandleRadius } ->
                            ObjectDrag.Scale
                        startingObjectGeometry.contains(initialOffset, objectHandleRadius) -> ObjectDrag.Move
                        else -> ObjectDrag.None
                    }
                    val objectGroupLocked = startingObjectLayer?.groupId?.let { groupId ->
                        document.groups.firstOrNull { it.id == groupId }?.locked
                    } == true
                    if (startingObjectLayer != null && (startingObjectLayer.locked || objectGroupLocked)) {
                        state.statusMessage = "Unlock this object before transforming it"
                        down.consume()
                        return@awaitEachGesture
                    }
                    if (startingObjectPayload != null && objectDrag == ObjectDrag.None) {
                        down.consume()
                        return@awaitEachGesture
                    }

                    down.consume()
                    try {
                        inProgress.clear()
                        moveDelta = Offset.Zero
                        if (groupDrag != ObjectDrag.None) {
                            objectGroupGesturePreview = startingGroupPayloads
                        }
                        if (startingObjectPayload != null && objectDrag != ObjectDrag.None) {
                            objectGesturePreview = startingObjectPayload
                        }
                        val startingTransform =
                            if (objectDrag == ObjectDrag.None && groupDrag == ObjectDrag.None) state.transformSession else null
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
                        val objectCenter = startingObjectGeometry?.center
                        val objectStartDistance = objectCenter?.let { (initialOffset - it).getDistance().coerceAtLeast(.001f) } ?: 1f
                        val groupStartDistance = groupCenter?.let {
                            (initialOffset - it).getDistance().coerceAtLeast(.001f)
                        } ?: 1f
                        var groupTranslation = Offset.Zero
                        var groupScale = 1f
                        var groupRotationDelta = 0f
                        movingSelection = startingTransform == null && startingObjectPayload == null &&
                            groupDrag == ObjectDrag.None && state.tool == Tool.MoveSelection &&
                            (state.selection?.contains(initial.x.toInt(), initial.y.toInt()) == true)
                        inProgress += initial
                        quickShapeSnapped = false
                        quickShapePointerDown =
                            state.quickShapeEnabled && state.tool == Tool.Brush && startingTransform == null &&
                                startingObjectPayload == null && groupDrag == ObjectDrag.None
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
                            val currentObjectPoint = Offset(currentPoint.x, currentPoint.y)
                            if (groupDrag != ObjectDrag.None && groupCenter != null) {
                                when (groupDrag) {
                                    ObjectDrag.Move -> {
                                        val rawTranslation = currentObjectPoint - initialOffset
                                        groupTranslation = if (state.objectSnapping) {
                                            snapEditableObjectGroupTranslation(
                                                payloads = startingGroupPayloads.values,
                                                groupCenter = groupCenter,
                                                translation = rawTranslation,
                                                documentWidth = document.width,
                                                documentHeight = document.height,
                                                threshold = 10f / gestureScale,
                                            )
                                        } else rawTranslation
                                        groupScale = 1f
                                        groupRotationDelta = 0f
                                    }
                                    ObjectDrag.Scale -> {
                                        groupTranslation = Offset.Zero
                                        groupScale = ((currentObjectPoint - groupCenter).getDistance() / groupStartDistance)
                                            .takeIf { it.isFinite() }?.coerceIn(.05f, 20f) ?: 1f
                                        groupRotationDelta = 0f
                                    }
                                    ObjectDrag.Rotate -> {
                                        groupTranslation = Offset.Zero
                                        groupScale = 1f
                                        val rawRotationDelta = angleDeltaDegrees(
                                            initialOffset - groupCenter,
                                            currentObjectPoint - groupCenter,
                                        )
                                        groupRotationDelta = if (state.objectSnapping) {
                                            snapEditableObjectGroupRotation(rawRotationDelta)
                                        } else rawRotationDelta
                                    }
                                    ObjectDrag.LineStart, ObjectDrag.LineEnd -> Unit
                                    ObjectDrag.None -> Unit
                                }
                                objectGroupGesturePreview = startingGroupPayloads.mapValues { (_, payload) ->
                                    transformEditableObjectAsGroup(
                                        payload = payload,
                                        groupCenter = groupCenter,
                                        translation = groupTranslation,
                                        scale = groupScale,
                                        rotationDelta = groupRotationDelta,
                                        documentWidth = document.width,
                                        documentHeight = document.height,
                                    )
                                }
                            } else if (startingObjectPayload != null && objectCenter != null && objectDrag != ObjectDrag.None) {
                                objectGesturePreview = when (objectDrag) {
                                    ObjectDrag.Move -> state.snapEditableObjectPreview(
                                        transformEditableObject(
                                            startingObjectPayload,
                                            translation = currentObjectPoint - initialOffset,
                                            scale = 1f,
                                            rotationDelta = 0f,
                                            documentWidth = document.width,
                                            documentHeight = document.height,
                                        ),
                                        positionThreshold = 10f / gestureScale,
                                        snapPosition = true,
                                        snapRotation = false,
                                    )
                                    ObjectDrag.Scale -> transformEditableObject(
                                        startingObjectPayload,
                                        translation = Offset.Zero,
                                        scale = ((currentObjectPoint - objectCenter).getDistance() / objectStartDistance)
                                            .takeIf { it.isFinite() }?.coerceIn(.05f, 20f) ?: 1f,
                                        rotationDelta = 0f,
                                        documentWidth = document.width,
                                        documentHeight = document.height,
                                    )
                                    ObjectDrag.Rotate -> state.snapEditableObjectPreview(
                                        transformEditableObject(
                                            startingObjectPayload,
                                            translation = Offset.Zero,
                                            scale = 1f,
                                            rotationDelta = angleDeltaDegrees(initialOffset - objectCenter, currentObjectPoint - objectCenter),
                                            documentWidth = document.width,
                                            documentHeight = document.height,
                                        ),
                                        positionThreshold = 0f,
                                        snapPosition = false,
                                        snapRotation = true,
                                    )
                                    ObjectDrag.LineStart -> (startingObjectPayload as? LayerPayload.ShapeObject)?.let {
                                        lineWithEndpoint(it, moveStart = true, x = currentObjectPoint.x, y = currentObjectPoint.y)
                                    } ?: startingObjectPayload
                                    ObjectDrag.LineEnd -> (startingObjectPayload as? LayerPayload.ShapeObject)?.let {
                                        lineWithEndpoint(it, moveStart = false, x = currentObjectPoint.x, y = currentObjectPoint.y)
                                    } ?: startingObjectPayload
                                    ObjectDrag.None -> startingObjectPayload
                                }
                            } else if (startingTransform != null && transformCenter != null) {
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
                        if (groupDrag != ObjectDrag.None) {
                            state.transformSelectedObjects(
                                translationX = groupTranslation.x,
                                translationY = groupTranslation.y,
                                scale = groupScale,
                                rotationDelta = groupRotationDelta,
                            )
                        } else if (startingObjectPayload != null && objectDrag != ObjectDrag.None) {
                            objectGesturePreview?.let { state.commitActiveObjectTransform(it) }
                        } else if (startingTransform != null) {
                            // The preview remains pending until the explicit Apply or Cancel action.
                        } else if (state.tool == Tool.Fill || state.tool == Tool.Eyedropper) {
                            if ((previous - down.position).getDistance() <= viewConfiguration.touchSlop)
                                state.applyPointTool(initial)
                        } else if (state.tool == Tool.MoveSelection && movingSelection) {
                            state.moveSelection(moveDelta.x.toInt(), moveDelta.y.toInt())
                        } else if (state.tool == Tool.Select && inProgress.isNotEmpty()) {
                            state.selectArea(inProgress.toList())
                        } else {
                            val promoted = quickShapeSnapped && quickShapeResult?.let { result ->
                                state.commitQuickShape(result, state.color, state.brushSize, state.brushOpacity)
                            } == true
                            if (!promoted) state.recordStroke(inProgress.toList(), stabilize = !quickShapeSnapped)
                        }
                    } finally {
                        quickShapePointerDown = false
                        quickShapeRawPoints = emptyList()
                        quickShapeSnapped = false
                        quickShapeResult = null
                        quickShapeRevision++
                        inProgress.clear()
                        moveDelta = Offset.Zero
                        movingSelection = false
                        objectGesturePreview = null
                        objectGroupGesturePreview = emptyMap()
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
            val backdropSpacing = 42.dp.toPx()
            val backdropColor = Color.White.copy(alpha = .055f)
            var backdropX = backdropSpacing
            while (backdropX < size.width) {
                drawLine(backdropColor, Offset(backdropX, 0f), Offset(backdropX, size.height), 1.dp.toPx())
                backdropX += backdropSpacing
            }
            var backdropY = backdropSpacing
            while (backdropY < size.height) {
                drawLine(backdropColor, Offset(0f, backdropY), Offset(size.width, backdropY), 1.dp.toPx())
                backdropY += backdropSpacing
            }

            withTransform({
                translate(origin.x, origin.y)
                scale(scale, scale, pivot = Offset.Zero)
                rotate(state.viewRotationDegrees, pivot = Offset(document.width / 2f, document.height / 2f))
            }) {
                drawRect(NeoCanvasColors.paper, size = Size(document.width.toFloat(), document.height.toFloat()))
                drawStoredTiles(
                    state,
                    transformPreview ?: movePreview ?: state.effectPreviewPatch ?: strokePreview,
                    tileImages,
                    textMeasurer,
                    textFontFamilies,
                    editableObjectPreviewLayerId = if (objectGesturePreview != null) state.activeLayerId else null,
                    editableObjectPreview = objectGesturePreview,
                    editableObjectPreviews = objectGroupGesturePreview,
                )

                if (state.tool == Tool.Liquify) {
                    previewPoints.lastOrNull()?.let { point ->
                        val radius = liquifyFootprintRadius(state.liquifySize, point.pressure)
                        val centre = Offset(point.x, point.y)
                        drawCircle(
                            NeoCanvasColors.accent.copy(alpha = .92f),
                            radius = radius,
                            center = centre,
                            style = Stroke(1.5f / scale),
                        )
                        drawCircle(
                            Color.Black.copy(alpha = .65f),
                            radius = 2.8f / scale,
                            center = centre,
                        )
                        drawCircle(
                            NeoCanvasColors.accent,
                            radius = 1.6f / scale,
                            center = centre,
                        )
                    }
                }

                arrangePickMarquee?.let { marquee ->
                    drawRect(
                        NeoCanvasColors.accent.copy(alpha = .15f),
                        topLeft = Offset(marquee.left, marquee.top),
                        size = Size(marquee.width, marquee.height),
                    )
                    drawRect(
                        NeoCanvasColors.accent.copy(alpha = .9f),
                        topLeft = Offset(marquee.left, marquee.top),
                        size = Size(marquee.width, marquee.height),
                        style = Stroke(1.5f / scale),
                    )
                }

                if (state.objectSnapping) {
                    val guides = when {
                        objectGroupGesturePreview.isNotEmpty() -> editableObjectGroupSmartGuides(
                            objectGroupGesturePreview.values.toList(),
                            document.width,
                            document.height,
                            tolerance = 1.5f / scale,
                        )
                        objectGesturePreview != null -> editableObjectSmartGuides(
                            objectGesturePreview!!,
                            document.width,
                            document.height,
                            tolerance = 1.5f / scale,
                        )
                        else -> null
                    }
                    guides?.let {
                        val guideColor = NeoCanvasColors.accent.copy(alpha = .9f)
                        it.verticalX?.let { x ->
                            drawLine(
                                guideColor,
                                Offset(x, 0f),
                                Offset(x, document.height.toFloat()),
                                1.5f / scale,
                            )
                        }
                        it.horizontalY?.let { y ->
                            drawLine(
                                guideColor,
                                Offset(0f, y),
                                Offset(document.width.toFloat(), y),
                                1.5f / scale,
                            )
                        }
                    }
                }

                if (state.selectedObjectLayerIds.isNotEmpty()) {
                    val groupsById = document.groups.associateBy { it.id }
                    val markedLayers = document.layers.mapNotNull { layer ->
                        if (layer.id !in state.selectedObjectLayerIds) return@mapNotNull null
                        val payload = objectGroupGesturePreview[layer.id] ?: layer.payload
                        if (payload is LayerPayload.TextObject || payload is LayerPayload.ShapeObject) {
                            layer to payload
                        } else null
                    }
                    val memberColor = NeoCanvasColors.accent.copy(alpha = .45f)
                    markedLayers.forEach { (layer, payload) ->
                        val group = layer.groupId?.let(groupsById::get)
                        if (layer.visible && group?.visible != false) {
                            val corners = payload.editableObjectGeometry()?.outlineCorners().orEmpty()
                            if (corners.size >= 2) {
                                corners.forEachIndexed { index, point ->
                                    val next = corners[(index + 1) % corners.size]
                                    drawLine(memberColor, point, next, 1f / scale)
                                }
                            }
                        }
                    }
                    editableObjectArrangeBounds(markedLayers.map { it.second })?.let { bounds ->
                        val accent = NeoCanvasColors.accent
                        drawRect(
                            accent.copy(alpha = .85f),
                            topLeft = Offset(bounds.left, bounds.top),
                            size = Size(bounds.right - bounds.left, bounds.bottom - bounds.top),
                            style = Stroke(1.5f / scale),
                        )
                        if (markedLayers.size >= 2) {
                            val radius = 6f / scale
                            val handles = listOf(
                                Offset(bounds.left, bounds.top),
                                Offset(bounds.right, bounds.top),
                                Offset(bounds.left, bounds.bottom),
                                Offset(bounds.right, bounds.bottom),
                            )
                            handles.forEach { handle ->
                                drawCircle(Color.Black, radius * 1.5f, handle)
                                drawCircle(accent, radius, handle)
                            }
                            val topCenter = Offset((bounds.left + bounds.right) / 2f, bounds.top)
                            val rotateHandle = Offset(topCenter.x, topCenter.y - 32f / scale)
                            drawLine(accent, topCenter, rotateHandle, 1.25f / scale)
                            drawCircle(Color.Black, radius * 1.5f, rotateHandle)
                            drawCircle(accent, radius, rotateHandle)
                        }
                    }
                }

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
                if (state.objectEditorVisible && state.selectedObjectCount < 2) {
                    val activeObjectLayer = state.activeObjectLayer
                    val groupVisible = activeObjectLayer?.groupId?.let { groupId ->
                        document.groups.firstOrNull { it.id == groupId }?.visible
                    } != false
                    if (activeObjectLayer != null && activeObjectLayer.visible && groupVisible) {
                        drawEditableObjectControls(objectGesturePreview ?: activeObjectLayer.payload, scale)
                    }
                }
                drawRect(NeoCanvasColors.canvasEdge, size = Size(document.width.toFloat(), document.height.toFloat()), style = Stroke(1f / scale))
                val liveSelection = if (
                    state.tool == Tool.Select &&
                    state.selectionMode != SelectionShape.Automatic &&
                    inProgress.isNotEmpty()
                ) {
                    val first = inProgress.first()
                    val last = inProgress.last()
                    if (state.selectionMode == SelectionShape.Lasso) CanvasSelection.lasso(inProgress.toList())
                    else CanvasSelection(minOf(first.x, last.x).toInt(), minOf(first.y, last.y).toInt(),
                        maxOf(first.x, last.x).toInt() + 1, maxOf(first.y, last.y).toInt() + 1,
                        shape = state.selectionMode)
                } else null
                val bounds = transformSelection ?: liveSelection ?: state.selection?.let {
                    val dx = moveDelta.x.toInt().coerceIn(-it.left, document.width - it.right)
                    val dy = moveDelta.y.toInt().coerceIn(-it.top, document.height - it.bottom)
                    it.translated(dx, dy)
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
        WorkbenchOverlay(
            state = state,
            origin = origin,
            scale = scale,
            modifier = Modifier.fillMaxSize(),
        )

        quickMenuAnchor?.let { anchor ->
            QuickMenuOverlay(
                state = state,
                anchor = anchor,
                viewport = viewport,
                onDismiss = { quickMenuAnchor = null },
            )
        }

        if (clipboardMenuVisible) {
            ClipboardGestureMenu(
                state = state,
                onDismiss = { clipboardMenuVisible = false },
            )
        }

        state.maskEditingLayerId?.let { layerId ->
            val layerName = state.document.layers.firstOrNull { it.id == layerId }?.name ?: "Layer"
            Box(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeoCanvasColors.chrome.copy(alpha = .94f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    "MASK · " + layerName + " · dark hides · eraser reveals",
                    color = NeoCanvasColors.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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

        if (state.selection != null || state.tool == Tool.Select) {
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
                TransformDockButton("Automatic", emphasized = state.selectionMode == SelectionShape.Automatic) {
                    state.selectionMode = SelectionShape.Automatic
                    state.tool = Tool.Select
                    state.statusMessage = "Automatic selection — tap a colour area"
                }
                TransformDockButton("Freehand", emphasized = state.selectionMode == SelectionShape.Lasso) {
                    state.selectionMode = SelectionShape.Lasso
                    state.tool = Tool.Select
                }
                TransformDockButton("Rectangle", emphasized = state.selectionMode == SelectionShape.Rectangle) {
                    state.selectionMode = SelectionShape.Rectangle
                    state.tool = Tool.Select
                }
                TransformDockButton("Ellipse", emphasized = state.selectionMode == SelectionShape.Ellipse) {
                    state.selectionMode = SelectionShape.Ellipse
                    state.tool = Tool.Select
                }
            }

            if (state.selectionMode == SelectionShape.Automatic) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tolerance", color = NeoCanvasColors.muted, fontSize = 10.sp)
                    Slider(
                        value = state.automaticSelectionTolerancePercent.toFloat(),
                        onValueChange = { state.automaticSelectionTolerancePercent = it.toInt().coerceIn(0, 100) },
                        onValueChangeFinished = { state.persistPreferences() },
                        valueRange = 0f..100f,
                        modifier = Modifier.width(180.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = NeoCanvasColors.accent,
                            activeTrackColor = NeoCanvasColors.accent,
                            inactiveTrackColor = NeoCanvasColors.track,
                        ),
                    )
                    Text(
                        state.automaticSelectionTolerancePercent.toString() + "%",
                        color = NeoCanvasColors.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                TransformDockButton("New", emphasized = state.selectionCombineMode == SelectionCombineMode.Replace) {
                    state.selectionCombineMode = SelectionCombineMode.Replace
                }
                TransformDockButton("Add", emphasized = state.selectionCombineMode == SelectionCombineMode.Add) {
                    state.selectionCombineMode = SelectionCombineMode.Add
                }
                TransformDockButton("Remove", emphasized = state.selectionCombineMode == SelectionCombineMode.Subtract) {
                    state.selectionCombineMode = SelectionCombineMode.Subtract
                }
                TransformDockButton("Intersect", emphasized = state.selectionCombineMode == SelectionCombineMode.Intersect) {
                    state.selectionCombineMode = SelectionCombineMode.Intersect
                }
                if (state.selection != null) {
                    TransformDockButton("Transform", emphasized = true) { state.beginTransform() }
                    SelectionMoreMenu(state)
                }
            }

            if (state.selection == null) {
                Text(
                    if (state.selectionMode == SelectionShape.Automatic)
                        "Tap a colour area on the active layer"
                    else "Drag on the canvas to make a selection",
                    color = NeoCanvasColors.faint,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
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
                TransformDockButton(if (state.transformSnapping) "Snap ✓" else "Snap") {
                    state.transformSnapping = !state.transformSnapping
                }
                TransformMoreMenu(state)
                TransformDockButton("Done", emphasized = true) { state.applyTransform() }
            }
        }
    }
}

@Composable
private fun SelectionMoreMenu(state: EditorState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TransformDockButton("More ···") { expanded = true }
        DropdownMenu(expanded, { expanded = false }, containerColor = NeoCanvasColors.panelRaised) {
            SelectionMenuItem("Invert") { expanded = false; state.invertSelection() }
            SelectionMenuItem("Move") { expanded = false; state.tool = Tool.MoveSelection }
            SelectionMenuItem("Crop Canvas") { expanded = false; state.cropCanvasToSelection() }
            SelectionMenuItem("Clear Pixels") { expanded = false; state.clearSelectedPixels() }
            SelectionMenuItem("Deselect") { expanded = false; state.clearSelection() }
        }
    }
}

@Composable
private fun TransformMoreMenu(state: EditorState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TransformDockButton("More ···") { expanded = true }
        DropdownMenu(expanded, { expanded = false }, containerColor = NeoCanvasColors.panelRaised) {
            SelectionMenuItem("Width −10%") { state.scaleTransformAxis(horizontal = true, factor = .9f) }
            SelectionMenuItem("Width +10%") { state.scaleTransformAxis(horizontal = true, factor = 1.1f) }
            SelectionMenuItem("Height −10%") { state.scaleTransformAxis(horizontal = false, factor = .9f) }
            SelectionMenuItem("Height +10%") { state.scaleTransformAxis(horizontal = false, factor = 1.1f) }
            SelectionMenuItem("Flip Horizontal") {
                expanded = false
                if (state.applyTransform()) {
                    state.flipSelection(horizontal = true)
                    state.beginTransform()
                }
            }
            SelectionMenuItem("Flip Vertical") {
                expanded = false
                if (state.applyTransform()) {
                    state.flipSelection(horizontal = false)
                    state.beginTransform()
                }
            }
            SelectionMenuItem("Rotate −15°") {
                state.updateTransform(rotationDegrees = (state.transformSession?.rotationDegrees ?: 0f) - 15f)
            }
            SelectionMenuItem("Rotate +15°") {
                state.updateTransform(rotationDegrees = (state.transformSession?.rotationDegrees ?: 0f) + 15f)
            }
            SelectionMenuItem(if (state.smoothResizing) "Interpolation: Smooth" else "Interpolation: Pixel") {
                state.smoothResizing = !state.smoothResizing
            }
        }
    }
}

@Composable
private fun SelectionMenuItem(label: String, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = NeoCanvasColors.paper) },
        onClick = action,
    )
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
        SelectionShape.Automatic -> selection.rasterRegion?.outline?.forEach { edge ->
            drawLine(
                color,
                Offset(edge.x1.toFloat(), edge.y1.toFloat()),
                Offset(edge.x2.toFloat(), edge.y2.toFloat()),
                width,
            )
        }
    }
}

/** Draws persisted tile pixels, so reopening a saved document is visibly identical to the original. */
private fun DrawScope.drawStoredTiles(
    state: EditorState,
    preview: com.neoworksuite.neocanvas.renderer.RasterPatch?,
    images: TileImageCache,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    textFontFamilies: Map<String, FontFamily>,
    editableObjectPreviewLayerId: String? = null,
    editableObjectPreview: LayerPayload? = null,
    editableObjectPreviews: Map<String, LayerPayload> = emptyMap(),
) {
    val groupsById = state.document.groups.associateBy { it.id }
    state.document.layers.forEachIndexed { index, layer ->
        val group = layer.groupId?.let(groupsById::get)
        val effectiveOpacity = layer.opacity * (group?.opacity ?: 1f)
        if (!layer.visible || group?.visible == false || effectiveOpacity <= 0f) return@forEachIndexed
        val blendMode = layerBlendMode(layer.blendMode)
        val renderedPayload = editableObjectPreviews[layer.id] ?: if (
            layer.id == editableObjectPreviewLayerId && editableObjectPreview != null
        ) {
            editableObjectPreview
        } else layer.payload
        when (val payload = renderedPayload) {
            is com.neoworksuite.neocanvas.core.model.LayerPayload.Raster -> {
                val clippingBase = if (layer.clipping && index > 0) state.document.layers[index - 1] else null
                val layerPreview = preview?.takeIf { patch -> patch.keys.any { it.layerId == layer.id } }
                val addresses = payload.tileAddresses + layerPreview?.keys.orEmpty().filter { it.layerId == layer.id }
                addresses.forEach { address ->
                    val sourcePixels = (if (layerPreview != null) layerPreview.previewTile(address, state.tileStore)
                        else state.tileStore.read(address)) ?: return@forEach
                    val maskedPixels = applyLayerMaskPreview(sourcePixels, layer, address, state, preview)
                    val pixels = if (layer.clipping) {
                        val mask = clippingBase?.let { base ->
                            val raw = state.tileStore.read(com.neoworksuite.neocanvas.renderer.TileKey(base.id, address.x, address.y))
                            raw?.let { applyLayerMaskPreview(it, base, address, state, preview) }
                        }
                        clipTileAlpha(maskedPixels, mask)
                    } else maskedPixels
                    drawImage(
                        images.image(address, pixels),
                        Offset(address.x * 256f, address.y * 256f),
                        alpha = effectiveOpacity,
                        blendMode = blendMode,
                    )
                }
            }

            is com.neoworksuite.neocanvas.core.model.LayerPayload.TextObject -> {
                val center = Offset(payload.x + payload.width / 2f, payload.y + payload.height / 2f)
                withTransform({ rotate(payload.rotationDegrees, pivot = center) }) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = if (payload.uppercase) payload.text.uppercase() else payload.text,
                        topLeft = Offset(payload.x, payload.y + payload.baselineOffset),
                        style = TextStyle(
                            color = Color(payload.colorArgb).copy(
                                alpha = Color(payload.colorArgb).alpha * effectiveOpacity,
                            ),
                            fontSize = payload.fontSize.toSp(),
                            fontFamily = editableTextFontFamily(payload.fontFamily, textFontFamilies),
                            fontWeight = if (payload.bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (payload.italic) FontStyle.Italic else FontStyle.Normal,
                            lineHeight = (payload.fontSize * payload.lineSpacing).toSp(),
                            letterSpacing = payload.tracking.toSp(),
                            textDecoration = if (payload.underline) TextDecoration.Underline else TextDecoration.None,
                            textAlign = when (payload.alignment) {
                                com.neoworksuite.neocanvas.core.model.TextAlignment.Left -> TextAlign.Left
                                com.neoworksuite.neocanvas.core.model.TextAlignment.Center -> TextAlign.Center
                                com.neoworksuite.neocanvas.core.model.TextAlignment.Right -> TextAlign.Right
                                com.neoworksuite.neocanvas.core.model.TextAlignment.Justified -> TextAlign.Justify
                            },
                        ),
                        size = Size(payload.width, payload.height),
                        blendMode = blendMode,
                    )
                }
            }

            is com.neoworksuite.neocanvas.core.model.LayerPayload.ShapeObject ->
                drawEditableShape(payload, effectiveOpacity, blendMode)
        }
    }
}

private fun DrawScope.drawEditableShape(
    shape: com.neoworksuite.neocanvas.core.model.LayerPayload.ShapeObject,
    opacity: Float,
    blendMode: androidx.compose.ui.graphics.BlendMode,
) {
    val fill = shape.fillArgb?.let { Color(it).copy(alpha = Color(it).alpha * opacity) }
    val stroke = shape.strokeArgb?.let { Color(it).copy(alpha = Color(it).alpha * opacity) }
    val center = Offset(shape.x + shape.width / 2f, shape.y + shape.height / 2f)
    withTransform({ rotate(shape.rotationDegrees, pivot = center) }) {
        when (shape.kind) {
            com.neoworksuite.neocanvas.core.model.ShapeKind.Rectangle -> {
                val radius = shape.cornerRadius.coerceIn(
                    0f,
                    minOf(kotlin.math.abs(shape.width), kotlin.math.abs(shape.height)) / 2f,
                )
                fill?.let {
                    drawRoundRect(
                        it,
                        Offset(shape.x, shape.y),
                        Size(shape.width, shape.height),
                        CornerRadius(radius, radius),
                        blendMode = blendMode,
                    )
                }
                stroke?.let {
                    drawRoundRect(
                        it,
                        Offset(shape.x, shape.y),
                        Size(shape.width, shape.height),
                        CornerRadius(radius, radius),
                        style = Stroke(shape.strokeWidth),
                        blendMode = blendMode,
                    )
                }
            }
            com.neoworksuite.neocanvas.core.model.ShapeKind.Ellipse -> {
                fill?.let { drawOval(it, Offset(shape.x, shape.y), Size(shape.width, shape.height), blendMode = blendMode) }
                stroke?.let {
                    drawOval(
                        it,
                        Offset(shape.x, shape.y),
                        Size(shape.width, shape.height),
                        style = Stroke(shape.strokeWidth),
                        blendMode = blendMode,
                    )
                }
            }
            com.neoworksuite.neocanvas.core.model.ShapeKind.Line -> {
                val color = stroke ?: fill ?: Color.Black
                drawStyledLine(shape, color, blendMode)
            }
        }
    }
}

internal fun DrawScope.drawStyledLine(
    shape: LayerPayload.ShapeObject,
    color: Color,
    blendMode: androidx.compose.ui.graphics.BlendMode = androidx.compose.ui.graphics.BlendMode.SrcOver,
) {
    val start = Offset(shape.x, shape.y)
    val end = Offset(shape.x + shape.width, shape.y + shape.height)
    val width = shape.strokeWidth.coerceAtLeast(1f)
    val effect = when (shape.lineStyle) {
        LineStyle.Solid -> null
        LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(width * 4f, width * 2f))
        LineStyle.Dotted -> PathEffect.dashPathEffect(floatArrayOf(width, width * 2f))
    }
    val cap = when (shape.lineCap) {
        LineCap.Round -> StrokeCap.Round
        LineCap.Square -> StrokeCap.Square
        LineCap.Butt -> StrokeCap.Butt
    }
    drawLine(color, start, end, width, cap = cap, pathEffect = effect, blendMode = blendMode)

    val delta = end - start
    val length = delta.getDistance()
    if (length <= 0f) return
    val unit = delta / length
    val normal = Offset(-unit.y, unit.x)
    val markerLength = maxOf(8f, width * 4f)
    fun arrow(base: Offset, direction: Float) {
        val tip = base + unit * markerLength * direction
        val back = base - unit * markerLength * .35f * direction
        val half = markerLength * .45f
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(back.x + normal.x * half, back.y + normal.y * half)
            lineTo(back.x - normal.x * half, back.y - normal.y * half)
            close()
        }
        drawPath(path, color, blendMode = blendMode)
    }
    if (shape.startMarker == LineMarker.Arrow) arrow(start, -1f)
    if (shape.endMarker == LineMarker.Arrow) arrow(end, 1f)
}

private fun layerBlendMode(
    mode: com.neoworksuite.neocanvas.core.model.LayerBlendMode,
): androidx.compose.ui.graphics.BlendMode = when (mode) {
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

private fun applyLayerMaskPreview(
    source: ByteArray,
    layer: com.neoworksuite.neocanvas.core.model.Layer,
    address: com.neoworksuite.neocanvas.core.model.TileAddress,
    state: EditorState,
    preview: com.neoworksuite.neocanvas.renderer.RasterPatch?,
): ByteArray {
    val mask = layer.mask ?: return source
    if (!mask.enabled) return source
    val maskAddress = com.neoworksuite.neocanvas.renderer.TileKey(mask.id, address.x, address.y)
    val maskPreview = preview?.takeIf { patch -> patch.keys.any { it.layerId == mask.id } }
    val maskPixels = if (maskPreview != null) maskPreview.previewTile(maskAddress, state.tileStore)
        else state.tileStore.read(maskAddress)
    if (maskPixels == null && !mask.inverted) return source

    val output = source.copyOf()
    var offset = 0
    while (offset < output.size) {
        val raw = maskPixels?.get(offset)?.toInt()?.and(255) ?: 255
        val value = if (mask.inverted) 255 - raw else raw
        val sourceAlpha = output[offset + 3].toInt() and 255
        val masked = (sourceAlpha * value + 127) / 255
        output[offset + 3] = masked.toByte()
        if (masked == 0) {
            output[offset] = 0
            output[offset + 1] = 0
            output[offset + 2] = 0
        }
        offset += 4
    }
    return output
}

private fun clipTileAlpha(source: ByteArray, mask: ByteArray?): ByteArray {
    if (mask == null) return ByteArray(source.size)
    val output = source.copyOf()
    var offset = 0
    while (offset < output.size) {
        val sourceAlpha = output[offset + 3].toInt() and 255
        val maskAlpha = mask[offset + 3].toInt() and 255
        val clipped = (sourceAlpha * maskAlpha + 127) / 255
        output[offset + 3] = clipped.toByte()
        if (clipped == 0) {
            output[offset] = 0
            output[offset + 1] = 0
            output[offset + 2] = 0
        }
        offset += 4
    }
    return output
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


internal data class EditableObjectArrangeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal fun editableObjectArrangeBounds(payloads: List<LayerPayload>): EditableObjectArrangeBounds? {
    val points = payloads.flatMap { payload ->
        payload.editableObjectGeometry()?.outlineCorners().orEmpty()
    }
    if (points.isEmpty()) return null
    return EditableObjectArrangeBounds(
        left = points.minOf { it.x },
        top = points.minOf { it.y },
        right = points.maxOf { it.x },
        bottom = points.maxOf { it.y },
    )
}

internal data class EditableObjectSmartGuides(
    val verticalX: Float? = null,
    val horizontalY: Float? = null,
)

internal fun editableObjectSmartGuides(
    payload: LayerPayload,
    documentWidth: Int,
    documentHeight: Int,
    tolerance: Float,
): EditableObjectSmartGuides {
    val bounds = editableObjectArrangeBounds(listOf(payload)) ?: return EditableObjectSmartGuides()
    return editableObjectSmartGuidesForBounds(bounds, documentWidth, documentHeight, tolerance)
}

internal fun editableObjectGroupSmartGuides(
    payloads: List<LayerPayload>,
    documentWidth: Int,
    documentHeight: Int,
    tolerance: Float,
): EditableObjectSmartGuides {
    val bounds = editableObjectArrangeBounds(payloads) ?: return EditableObjectSmartGuides()
    return editableObjectSmartGuidesForBounds(bounds, documentWidth, documentHeight, tolerance)
}

private fun editableObjectSmartGuidesForBounds(
    bounds: EditableObjectArrangeBounds,
    documentWidth: Int,
    documentHeight: Int,
    tolerance: Float,
): EditableObjectSmartGuides {
    if (tolerance < 0f || !tolerance.isFinite()) return EditableObjectSmartGuides()
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f

    val vertical = listOf(
        kotlin.math.abs(bounds.left) to 0f,
        kotlin.math.abs(centerX - documentWidth / 2f) to documentWidth / 2f,
        kotlin.math.abs(bounds.right - documentWidth) to documentWidth.toFloat(),
    ).filter { it.first <= tolerance }.minByOrNull { it.first }?.second

    val horizontal = listOf(
        kotlin.math.abs(bounds.top) to 0f,
        kotlin.math.abs(centerY - documentHeight / 2f) to documentHeight / 2f,
        kotlin.math.abs(bounds.bottom - documentHeight) to documentHeight.toFloat(),
    ).filter { it.first <= tolerance }.minByOrNull { it.first }?.second

    return EditableObjectSmartGuides(vertical, horizontal)
}

private data class EditableObjectGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotationDegrees: Float,
    val isLine: Boolean = false,
    val strokeWidth: Float = 0f,
) {
    val center: Offset get() = Offset(x + width / 2f, y + height / 2f)

    private fun rotated(point: Offset): Offset = center + rotateOffset(point - center, rotationDegrees)

    fun outlineCorners(): List<Offset> = listOf(
        Offset(x, y),
        Offset(x + width, y),
        Offset(x + width, y + height),
        Offset(x, y + height),
    ).map(::rotated)

    fun scaleHandles(): List<Offset> = if (isLine) {
        listOf(rotated(Offset(x, y)), rotated(Offset(x + width, y + height)))
    } else outlineCorners()

    fun rotationHandle(distance: Float): Offset {
        val top = minOf(y, y + height)
        return rotated(Offset(x + width / 2f, top - distance))
    }

    fun contains(point: Offset, padding: Float): Boolean {
        val local = center + rotateOffset(point - center, -rotationDegrees)
        if (isLine) {
            return distanceToSegment(local, Offset(x, y), Offset(x + width, y + height)) <=
                padding + strokeWidth.coerceAtLeast(1f) / 2f
        }
        val left = minOf(x, x + width) - padding
        val right = maxOf(x, x + width) + padding
        val top = minOf(y, y + height) - padding
        val bottom = maxOf(y, y + height) + padding
        return local.x in left..right && local.y in top..bottom
    }
}

internal fun editableObjectLayerAtPoint(
    document: com.neoworksuite.neocanvas.core.model.CanvasDocument,
    point: Offset,
    padding: Float = 0f,
): String? {
    val groupsById = document.groups.associateBy { it.id }
    return document.layers.asReversed().firstOrNull { layer ->
        val group = layer.groupId?.let(groupsById::get)
        if (!layer.visible || group?.visible == false) return@firstOrNull false
        val geometry = layer.payload.editableObjectGeometry() ?: return@firstOrNull false
        geometry.contains(point, padding)
    }?.id
}

internal fun editableObjectLayerIdsInRect(
    document: com.neoworksuite.neocanvas.core.model.CanvasDocument,
    rect: Rect,
): Set<String> {
    val groupsById = document.groups.associateBy { it.id }
    return document.layers.filter { layer ->
        val group = layer.groupId?.let(groupsById::get)
        if (!layer.visible || group?.visible == false) return@filter false
        val corners = layer.payload.editableObjectGeometry()?.outlineCorners().orEmpty()
        if (corners.isEmpty()) return@filter false
        val left = corners.minOf { it.x }
        val top = corners.minOf { it.y }
        val right = corners.maxOf { it.x }
        val bottom = corners.maxOf { it.y }
        right >= rect.left && left <= rect.right && bottom >= rect.top && top <= rect.bottom
    }.mapTo(linkedSetOf()) { it.id }
}

private fun LayerPayload.editableObjectGeometry(): EditableObjectGeometry? = when (this) {
    is LayerPayload.TextObject -> EditableObjectGeometry(x, y, width, height, rotationDegrees)
    is LayerPayload.ShapeObject -> EditableObjectGeometry(
        x = x,
        y = y,
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
        isLine = kind == ShapeKind.Line,
        strokeWidth = strokeWidth,
    )
    is LayerPayload.Raster -> null
}

private fun transformEditableObject(
    payload: LayerPayload,
    translation: Offset,
    scale: Float,
    rotationDelta: Float,
    documentWidth: Int,
    documentHeight: Int,
): LayerPayload {
    val safeScale = scale.takeIf { it.isFinite() && it > 0f }?.coerceIn(.05f, 20f) ?: 1f
    return when (payload) {
        is LayerPayload.TextObject -> {
            val newWidth = (payload.width * safeScale).coerceIn(20f, maxOf(20f, documentWidth * 2f))
            val newHeight = (payload.height * safeScale).coerceIn(20f, maxOf(20f, documentHeight * 2f))
            val centerX = payload.x + payload.width / 2f + translation.x
            val centerY = payload.y + payload.height / 2f + translation.y
            payload.copy(
                x = (centerX - newWidth / 2f).coerceIn(-newWidth, documentWidth.toFloat()),
                y = (centerY - newHeight / 2f).coerceIn(-newHeight, documentHeight.toFloat()),
                width = newWidth,
                height = newHeight,
                fontSize = (payload.fontSize * safeScale).coerceIn(6f, 512f),
                rotationDegrees = normalizeViewRotation(payload.rotationDegrees + rotationDelta),
            )
        }

        is LayerPayload.ShapeObject -> {
            val newWidth = signedObjectScale(payload.width, safeScale, 8f, maxOf(8f, documentWidth * 2f))
            val newHeight = if (payload.kind == ShapeKind.Line) {
                signedObjectScale(payload.height, safeScale, 0f, maxOf(8f, documentHeight * 2f))
            } else {
                signedObjectScale(payload.height, safeScale, 8f, maxOf(8f, documentHeight * 2f))
            }
            val centerX = payload.x + payload.width / 2f + translation.x
            val centerY = payload.y + payload.height / 2f + translation.y
            payload.copy(
                x = (centerX - newWidth / 2f).coerceIn(-abs(newWidth), documentWidth.toFloat()),
                y = (centerY - newHeight / 2f).coerceIn(-abs(newHeight), documentHeight.toFloat()),
                width = newWidth,
                height = newHeight,
                strokeWidth = (payload.strokeWidth * safeScale).coerceIn(0f, 128f),
                cornerRadius = (payload.cornerRadius * safeScale).coerceAtLeast(0f),
                rotationDegrees = normalizeViewRotation(payload.rotationDegrees + rotationDelta),
            )
        }

        is LayerPayload.Raster -> payload
    }
}

private fun transformEditableObjectAsGroup(
    payload: LayerPayload,
    groupCenter: Offset,
    translation: Offset,
    scale: Float,
    rotationDelta: Float,
    documentWidth: Int,
    documentHeight: Int,
): LayerPayload {
    val safeScale = scale.takeIf { it.isFinite() && it > 0f }?.coerceIn(.05f, 20f) ?: 1f
    val objectCenter = payload.editableObjectGeometry()?.center ?: return payload
    val transformedCenter =
        groupCenter + rotateOffset((objectCenter - groupCenter) * safeScale, rotationDelta) + translation

    return when (payload) {
        is LayerPayload.TextObject -> {
            val width = (payload.width * safeScale).coerceIn(20f, maxOf(20f, documentWidth * 2f))
            val height = (payload.height * safeScale).coerceIn(20f, maxOf(20f, documentHeight * 2f))
            payload.copy(
                x = transformedCenter.x - width / 2f,
                y = transformedCenter.y - height / 2f,
                width = width,
                height = height,
                fontSize = (payload.fontSize * safeScale).coerceIn(6f, 512f),
                rotationDegrees = normalizeViewRotation(payload.rotationDegrees + rotationDelta),
            )
        }

        is LayerPayload.ShapeObject -> {
            val width = signedObjectScale(payload.width, safeScale, 8f, maxOf(8f, documentWidth * 2f))
            val height = if (payload.kind == ShapeKind.Line) {
                signedObjectScale(payload.height, safeScale, 0f, maxOf(8f, documentHeight * 2f))
            } else {
                signedObjectScale(payload.height, safeScale, 8f, maxOf(8f, documentHeight * 2f))
            }
            payload.copy(
                x = transformedCenter.x - width / 2f,
                y = transformedCenter.y - height / 2f,
                width = width,
                height = height,
                strokeWidth = (payload.strokeWidth * safeScale).coerceIn(0f, 128f),
                cornerRadius = (payload.cornerRadius * safeScale).coerceAtLeast(0f),
                rotationDegrees = normalizeViewRotation(payload.rotationDegrees + rotationDelta),
            )
        }

        is LayerPayload.Raster -> payload
    }
}

private fun snapEditableObjectGroupTranslation(
    payloads: Collection<LayerPayload>,
    groupCenter: Offset,
    translation: Offset,
    documentWidth: Int,
    documentHeight: Int,
    threshold: Float,
): Offset {
    if (!threshold.isFinite() || threshold < 0f) return translation
    val preview = payloads.map { payload ->
        transformEditableObjectAsGroup(
            payload = payload,
            groupCenter = groupCenter,
            translation = translation,
            scale = 1f,
            rotationDelta = 0f,
            documentWidth = documentWidth,
            documentHeight = documentHeight,
        )
    }
    val bounds = editableObjectArrangeBounds(preview) ?: return translation
    val horizontalCandidates = listOf(
        -bounds.left,
        documentWidth / 2f - (bounds.left + bounds.right) / 2f,
        documentWidth - bounds.right,
    )
    val verticalCandidates = listOf(
        -bounds.top,
        documentHeight / 2f - (bounds.top + bounds.bottom) / 2f,
        documentHeight - bounds.bottom,
    )
    val dx = horizontalCandidates.filter { kotlin.math.abs(it) <= threshold }
        .minByOrNull { kotlin.math.abs(it) } ?: 0f
    val dy = verticalCandidates.filter { kotlin.math.abs(it) <= threshold }
        .minByOrNull { kotlin.math.abs(it) } ?: 0f
    return translation + Offset(dx, dy)
}

private fun snapEditableObjectGroupRotation(rotationDelta: Float): Float {
    if (!rotationDelta.isFinite()) return 0f
    val guide = kotlin.math.round(rotationDelta / 15f) * 15f
    val delta = normalizeViewRotation(rotationDelta - guide)
    return if (kotlin.math.abs(delta) <= 3f) guide else rotationDelta
}

internal fun liquifyFootprintRadius(size: Float, pressure: Float): Float =
    maxOf(.75f, size.coerceAtLeast(.01f) * normalizedPressure(pressure) * .5f)

private fun signedObjectScale(value: Float, factor: Float, minMagnitude: Float, maxMagnitude: Float): Float {
    if (value == 0f && minMagnitude == 0f) return 0f
    val sign = if (value < 0f) -1f else 1f
    return sign * (abs(value) * factor).coerceIn(minMagnitude, maxMagnitude)
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared <= .0001f) return (point - start).getDistance()
    val relative = point - start
    val t = ((relative.x * segment.x + relative.y * segment.y) / lengthSquared).coerceIn(0f, 1f)
    return (point - (start + segment * t)).getDistance()
}

private fun DrawScope.drawEditableObjectControls(payload: LayerPayload, scale: Float) {
    val geometry = payload.editableObjectGeometry() ?: return
    val accent = NeoCanvasColors.accent
    val outline = geometry.outlineCorners()
    val width = 1.25f / scale
    val radius = 6f / scale

    if (geometry.isLine) {
        val handles = geometry.scaleHandles()
        if (handles.size == 2) drawLine(accent.copy(alpha = .72f), handles[0], handles[1], width)
    } else {
        outline.indices.forEach { index ->
            drawLine(accent.copy(alpha = .78f), outline[index], outline[(index + 1) % outline.size], width)
        }
    }

    geometry.scaleHandles().forEach { handle ->
        drawCircle(Color.Black, radius * 1.5f, handle)
        drawCircle(accent, radius, handle)
    }

    val topCenter = geometry.center + rotateOffset(
        Offset(0f, minOf(geometry.y, geometry.y + geometry.height) - geometry.center.y),
        geometry.rotationDegrees,
    )
    val rotateHandle = geometry.rotationHandle(32f / scale)
    drawLine(accent, topCenter, rotateHandle, width)
    drawCircle(Color.Black, radius * 1.5f, rotateHandle)
    drawCircle(accent, radius, rotateHandle)
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

internal fun isThreeFingerScrubClear(
    maxTouchCount: Int,
    durationMillis: Long,
    reversals: Int,
    horizontalTravel: Float,
    touchSlop: Float,
    stylusSeen: Boolean,
): Boolean {
    if (stylusSeen || maxTouchCount != 3 || durationMillis !in 0L..1400L) return false
    if (!horizontalTravel.isFinite() || !touchSlop.isFinite() || touchSlop <= 0f) return false
    return reversals >= 2 && horizontalTravel >= touchSlop * 10f
}

internal fun shouldArmRapidHistoryGesture(
    fingerCount: Int,
    touchTravel: Float,
    touchSlop: Float,
    stylusSeen: Boolean,
    transformStarted: Boolean,
): Boolean {
    if (stylusSeen || transformStarted || fingerCount !in 2..3) return false
    if (!touchTravel.isFinite() || !touchSlop.isFinite() || touchSlop <= 0f) return false
    return touchTravel <= touchSlop * fingerCount * 1.5f
}

internal fun isThreeFingerClipboardSwipe(
    maxTouchCount: Int,
    durationMillis: Long,
    start: Offset?,
    end: Offset?,
    touchSlop: Float,
    stylusSeen: Boolean,
): Boolean {
    if (stylusSeen || maxTouchCount != 3 || durationMillis !in 0L..900L) return false
    if (!touchSlop.isFinite() || touchSlop <= 0f) return false
    val from = start ?: return false
    val to = end ?: return false
    val delta = to - from
    return kotlin.math.abs(delta.x) >= touchSlop * 4f &&
        kotlin.math.abs(delta.x) >= kotlin.math.abs(delta.y) * 1.5f
}

@Composable
private fun ClipboardGestureMenu(
    state: EditorState,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .10f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp))
                .background(NeoCanvasColors.chrome.copy(alpha = .98f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuickMenuAction("Copy", enabled = state.selection != null) {
                if (state.copySelectionToArtworkClipboard()) onDismiss()
            }
            QuickMenuAction("Paste", enabled = state.hasArtworkClipboard) {
                if (state.pasteArtworkClipboard()) onDismiss()
            }
            QuickMenuAction("Cancel") { onDismiss() }
        }
    }
}

internal fun shouldArmQuickMenu(
    isStylus: Boolean,
    fingerPaintingEnabled: Boolean,
    tool: Tool,
    objectArrangePicking: Boolean,
): Boolean =
    !isStylus &&
        !fingerPaintingEnabled &&
        !objectArrangePicking &&
        tool in setOf(Tool.Brush, Tool.Eraser, Tool.Smudge, Tool.Liquify)

@Composable
private fun QuickMenuOverlay(
    state: EditorState,
    anchor: Offset,
    viewport: IntSize,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val panelWidth = 236.dp
    val panelHeight = 116.dp
    val panelWidthPx = with(density) { panelWidth.toPx() }
    val panelHeightPx = with(density) { panelHeight.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val x = (anchor.x - panelWidthPx / 2f).coerceIn(
        margin,
        (viewport.width - panelWidthPx - margin).coerceAtLeast(margin),
    )
    val y = (anchor.y - panelHeightPx / 2f).coerceIn(
        margin,
        (viewport.height - panelHeightPx - margin).coerceAtLeast(margin),
    )
    fun perform(action: () -> Unit) {
        onDismiss()
        action()
    }

    Column(
        Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(panelWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(NeoCanvasColors.chrome.copy(alpha = .98f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "QUICKMENU",
                color = NeoCanvasColors.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .7.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "×",
                color = NeoCanvasColors.muted,
                fontSize = 16.sp,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 5.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            QuickMenuAction("Undo", Modifier.weight(1f), state.canUndo) { perform { state.undo() } }
            QuickMenuAction("Redo", Modifier.weight(1f), state.canRedo) { perform { state.redo() } }
            QuickMenuAction("Pick", Modifier.weight(1f)) { perform { state.activateTool(Tool.Eyedropper) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            QuickMenuAction("Brush", Modifier.weight(1f)) { perform { state.activateTool(Tool.Brush) } }
            QuickMenuAction("Eraser", Modifier.weight(1f)) { perform { state.activateTool(Tool.Eraser) } }
            QuickMenuAction("Layers", Modifier.weight(1f)) {
                perform { state.showInspector(InspectorPanel.Layers) }
            }
        }
    }
}

@Composable
private fun QuickMenuAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (enabled) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) NeoCanvasColors.paper else NeoCanvasColors.disabled,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

internal fun isFourFingerCanvasToggle(
    maxTouchCount: Int,
    durationMillis: Long,
    touchTravel: Float,
    touchSlop: Float,
    stylusSeen: Boolean,
): Boolean {
    if (stylusSeen || maxTouchCount < 4 || durationMillis !in 0L..450L) return false
    if (!touchTravel.isFinite() || !touchSlop.isFinite() || touchSlop <= 0f) return false
    return touchTravel <= touchSlop * maxTouchCount * 1.5f
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
