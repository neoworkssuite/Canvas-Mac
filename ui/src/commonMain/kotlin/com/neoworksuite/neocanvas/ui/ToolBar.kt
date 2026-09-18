package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.neoworksuite.neocanvas.renderer.DrawingSymmetry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StudioTopBar(state: EditorState, compact: Boolean, modifier: Modifier = Modifier, onGallery: () -> Unit = {}) {
    val toolScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStep = with(LocalDensity.current) { 240.dp.toPx() }
    Row(
        modifier = modifier.fillMaxWidth().height(58.dp).background(NeoCanvasColors.chrome).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!compact) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            BrandMark()
            Text("NEOCANVAS", color = NeoCanvasColors.paper, fontSize = 13.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 8.dp))
        }
        StudioMenu(if (state.hasUnsavedChanges) "File •" else "File", buildList {
            addAll(listOf(
            "Gallery" to onGallery,
            "New canvas…" to { state.newCanvasDialogVisible = true },
            "Open local document…" to { state.open() },
            "Import image…" to { state.importImage() },
            "Save local document" to { state.save() },
            ))
            if (state.supportsSaveAs) add("Save As…" to { state.saveAs() })
            addAll(listOf(
            "Export visible PNG…" to { state.exportPng() },
            ))
        })
        if (toolScroll.maxValue > 0) {
            StudioButton(Glyph.Previous, "Show previous tools", enabled = toolScroll.canScrollBackward) {
                scope.launch { toolScroll.animateScrollBy(-scrollStep) }
            }
        }
        Row(Modifier.weight(1f).horizontalScroll(toolScroll),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudioButton(Glyph.Brush, "Paint brush", state.tool == Tool.Brush) { state.tool = Tool.Brush }
        StudioButton(Glyph.Eraser, "Eraser", state.tool == Tool.Eraser) { state.tool = Tool.Eraser }
        StudioButton(Glyph.Transform, "Move canvas", state.tool == Tool.Pan) { state.tool = Tool.Pan }
        StudioButton(Glyph.Fill, "Fill connected area on active layer", state.tool == Tool.Fill) { state.tool = Tool.Fill }
        StudioButton(Glyph.Eyedropper, "Sample visible colour", state.tool == Tool.Eyedropper) { state.tool = Tool.Eyedropper }
        StudioButton(Glyph.Select, "Rectangle selection", state.tool == Tool.Select) { state.tool = Tool.Select }
        DividerTick()
        SymmetryMenu(state)
        if (state.selection != null) {
            StudioButton(Glyph.Transform, "Move selected artwork", state.tool == Tool.MoveSelection) { state.tool = Tool.MoveSelection }
            StudioButton(Glyph.ClearSelection, "Deselect") { state.clearSelection() }
        }
        }
        if (toolScroll.maxValue > 0) {
            StudioButton(Glyph.Next, "Show more tools", enabled = toolScroll.canScrollForward) {
                scope.launch { toolScroll.animateScrollBy(scrollStep) }
            }
        }
        if (!compact) Text("${(state.zoom * 100).toInt()}%", color = NeoCanvasColors.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        StudioButton(Glyph.Fit, "Fit canvas") { state.resetView() }
        if (compact) {
            StudioMenu("Studio", listOf(
                "Layers" to { state.showInspector(InspectorPanel.Layers) },
                "Brushes" to { state.showInspector(InspectorPanel.Brushes) },
                "Colour and palette" to { state.showInspector(InspectorPanel.Colors) },
                "Hide panel" to { state.inspectorVisible = false },
            ), active = state.inspectorVisible)
        } else {
        StudioButton(Glyph.Palette, "Colour studio", state.inspectorVisible && state.inspectorPanel == InspectorPanel.Colors) { state.toggleInspector(InspectorPanel.Colors) }
        StudioButton(Glyph.Library, "Brush library", state.inspectorVisible && state.inspectorPanel == InspectorPanel.Brushes) { state.toggleInspector(InspectorPanel.Brushes) }
        StudioButton(Glyph.Layers, "Layers", state.inspectorVisible && state.inspectorPanel == InspectorPanel.Layers) { state.toggleInspector(InspectorPanel.Layers) }
        }
    }
}

@Composable
private fun StudioMenu(label: String, actions: List<Pair<String, () -> Unit>>, active: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.height(48.dp).clip(RoundedCornerShape(10.dp))
            .background(if (active || expanded) NeoCanvasColors.accent else NeoCanvasColors.panelRaised)
            .clickable { expanded = !expanded }
            .semantics { contentDescription = "$label menu" }
            .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text("$label ▾", color = if (active || expanded) NeoCanvasColors.ink else NeoCanvasColors.paper, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            containerColor = NeoCanvasColors.panelRaised) {
            actions.forEach { (title, action) ->
                DropdownMenuItem(text = { Text(title, color = NeoCanvasColors.paper) }, onClick = {
                    expanded = false
                    action()
                })
            }
        }
    }
}

@Composable
private fun SymmetryMenu(state: EditorState) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(DrawingSymmetry.None to "Symmetry off", DrawingSymmetry.Vertical to "Vertical mirror",
        DrawingSymmetry.Horizontal to "Horizontal mirror", DrawingSymmetry.Both to "Four-way mirror")
    val active = state.symmetry != DrawingSymmetry.None
    Box {
        StudioTooltip("Symmetry: ${options.first { it.first == state.symmetry }.second}") {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(11.dp))
                .background(if (active) NeoCanvasColors.accent else NeoCanvasColors.panelRaised)
                .clickable { expanded = !expanded }
                .semantics { contentDescription = "Choose symmetry mode"; selected = active }, contentAlignment = Alignment.Center) {
                SymmetryIcon(state.symmetry, if (active) NeoCanvasColors.ink else NeoCanvasColors.muted)
                Text("▾", color = if (active) NeoCanvasColors.ink else NeoCanvasColors.muted,
                    fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
            }
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = NeoCanvasColors.panelRaised) {
            options.forEach { (mode, label) ->
                val chosen = state.symmetry == mode
                DropdownMenuItem(
                    text = { Text(label, color = if (chosen) NeoCanvasColors.accent else NeoCanvasColors.paper) },
                    leadingIcon = { SymmetryIcon(mode, if (chosen) NeoCanvasColors.accent else NeoCanvasColors.muted) },
                    trailingIcon = { if (chosen) Text("✓", color = NeoCanvasColors.accent) },
                    modifier = Modifier.semantics { selected = chosen },
                    onClick = { state.symmetry = mode; expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SymmetryIcon(mode: DrawingSymmetry, tint: Color) = Canvas(Modifier.size(24.dp)) {
    val w = size.width
    val h = size.height
    val stroke = 1.5.dp.toPx()
    drawRect(tint.copy(alpha = .6f), Offset(w * .12f, h * .12f), Size(w * .76f, h * .76f), style = Stroke(stroke))
    if (mode == DrawingSymmetry.None) {
        drawLine(tint, Offset(w * .12f, h * .88f), Offset(w * .88f, h * .12f), stroke, StrokeCap.Round)
    } else {
        if (mode == DrawingSymmetry.Vertical || mode == DrawingSymmetry.Both)
            drawLine(tint, Offset(w / 2, 0f), Offset(w / 2, h), stroke)
        if (mode == DrawingSymmetry.Horizontal || mode == DrawingSymmetry.Both)
            drawLine(tint, Offset(0f, h / 2), Offset(w, h / 2), stroke)
        val centres = when (mode) {
            DrawingSymmetry.Vertical -> listOf(.3f to .5f, .7f to .5f)
            DrawingSymmetry.Horizontal -> listOf(.5f to .3f, .5f to .7f)
            else -> listOf(.3f to .3f, .7f to .3f, .3f to .7f, .7f to .7f)
        }
        centres.forEach { (x, y) -> drawCircle(tint, w * .07f, Offset(w * x, h * y)) }
    }
}

@Composable
fun StudioRail(state: EditorState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(NeoCanvasColors.rail).padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.tool == Tool.Fill)
            VerticalRailControl("TOL", state.fillTolerance.toFloat(), 0f..255f, { "${it.toInt()}" }) { state.fillTolerance = it.toInt() }
        else VerticalRailControl("SIZE", state.brushSize, 1f..96f, { "${it.toInt()}" }) { state.brushSize = it }
        StudioButton(Glyph.Undo, "Undo", enabled = state.canUndo) { state.undo() }
        StudioButton(Glyph.Redo, "Redo", enabled = state.canRedo) { state.redo() }
        VerticalRailControl("FLOW", state.brushOpacity, 0.05f..1f, { "${(it * 100).toInt()}" }) { state.brushOpacity = it }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(38.dp).border(2.dp, Color.White, CircleShape).padding(3.dp).clip(CircleShape).background(state.color)
            .clickable { state.showInspector(InspectorPanel.Colors) }
            .semantics { contentDescription = "Current colour ${colorHex(state.color)}; open palette" })
        Text(colorHex(state.color), color = NeoCanvasColors.paper, fontSize = 8.sp,
            modifier = Modifier.clickable { state.showInspector(InspectorPanel.Colors) })
        StudioButton(Glyph.Palette, "Colour studio") { state.showInspector(InspectorPanel.Colors) }
    }
}

@Composable
private fun VerticalRailControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, onChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = NeoCanvasColors.faint, fontSize = 8.sp, letterSpacing = 1.sp)
        Text(format(value), color = NeoCanvasColors.paper, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        val update: (Float, Float) -> Unit = { y, height -> onChange(verticalValueFromY(y, height, range)) }
        Canvas(
            Modifier.width(34.dp).height(112.dp).padding(top = 4.dp)
                .semantics { contentDescription = "$label ${format(value)} vertical slider" }
                .pointerInput(range) { detectTapGestures { update(it.y, size.height.toFloat()) } }
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = { update(it.y, size.height.toFloat()) },
                        onDrag = { change, _ -> update(change.position.y, size.height.toFloat()); change.consume() },
                    )
                },
        ) {
            val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val x = size.width / 2f
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val thumbY = bottom - (bottom - top) * fraction
            drawLine(NeoCanvasColors.track, Offset(x, top), Offset(x, bottom), 6.dp.toPx(), StrokeCap.Round)
            drawLine(NeoCanvasColors.accent, Offset(x, thumbY), Offset(x, bottom), 6.dp.toPx(), StrokeCap.Round)
            drawCircle(NeoCanvasColors.paper, 7.dp.toPx(), Offset(x, thumbY))
            drawCircle(NeoCanvasColors.ink, 3.dp.toPx(), Offset(x, thumbY))
        }
    }
}

@Composable
fun studioSliderColors() = SliderDefaults.colors(
    thumbColor = NeoCanvasColors.paper,
    activeTrackColor = NeoCanvasColors.accent,
    inactiveTrackColor = NeoCanvasColors.track,
)

private enum class Glyph { Previous, Next, New, Open, Save, Export, Brush, Eraser, Transform, Fill, Eyedropper, Select, ClearSelection, Undo, Redo, Fit, Palette, Library, Layers }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip(containerColor = NeoCanvasColors.panelRaised, contentColor = NeoCanvasColors.paper) { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun StudioButton(glyph: Glyph, label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = when {
        !enabled -> NeoCanvasColors.disabled
        selected -> NeoCanvasColors.ink
        else -> NeoCanvasColors.muted
    }
    val surface = if (selected) NeoCanvasColors.accent else Color.Transparent
    StudioTooltip(label) {
    Box(
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(surface)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { StudioGlyph(glyph, tint) }
    }
}

@Composable
private fun DividerTick() = Box(Modifier.width(1.dp).height(24.dp).background(NeoCanvasColors.line).padding(horizontal = 3.dp))

@Composable
private fun BrandMark() = androidx.compose.foundation.Image(
    painter = neoCanvasIcon(), contentDescription = "NeoCanvas logo", modifier = Modifier.size(40.dp),
)

@Composable
private fun StudioGlyph(glyph: Glyph, color: Color) = Canvas(Modifier.size(20.dp)) {
    val w = size.width
    val h = size.height
    fun line(a: Offset, b: Offset, width: Float = 1.8f) = drawLine(color, a, b, width, StrokeCap.Round)
    when (glyph) {
        Glyph.Previous -> {
            line(Offset(w * .65f, h * .2f), Offset(w * .35f, h * .5f))
            line(Offset(w * .35f, h * .5f), Offset(w * .65f, h * .8f))
        }
        Glyph.Next -> {
            line(Offset(w * .35f, h * .2f), Offset(w * .65f, h * .5f))
            line(Offset(w * .65f, h * .5f), Offset(w * .35f, h * .8f))
        }
        Glyph.Select -> drawRect(color, Offset(w * .15f, h * .15f), Size(w * .7f, h * .7f), style = Stroke(1.5f))
        Glyph.ClearSelection -> {
            line(Offset(w * .2f, h * .2f), Offset(w * .8f, h * .8f))
            line(Offset(w * .8f, h * .2f), Offset(w * .2f, h * .8f))
        }
        Glyph.Fill -> {
            line(Offset(w * .2f, h * .45f), Offset(w * .5f, h * .15f))
            line(Offset(w * .5f, h * .15f), Offset(w * .8f, h * .45f))
            line(Offset(w * .8f, h * .45f), Offset(w * .5f, h * .75f))
            line(Offset(w * .5f, h * .75f), Offset(w * .2f, h * .45f))
            line(Offset(w * .25f, h * .45f), Offset(w * .75f, h * .45f))
            drawCircle(color, w * .09f, Offset(w * .85f, h * .8f))
        }
        Glyph.Eyedropper -> {
            line(Offset(w * .2f, h * .8f), Offset(w * .75f, h * .25f), 3f)
            line(Offset(w * .5f, h * .2f), Offset(w * .8f, h * .5f), 3f)
            drawCircle(color, w * .07f, Offset(w * .15f, h * .85f))
        }
        Glyph.New -> { line(Offset(w * .50f, h * .16f), Offset(w * .50f, h * .84f)); line(Offset(w * .16f, h * .50f), Offset(w * .84f, h * .50f)) }
        Glyph.Open -> { drawRect(color, Offset(w * .18f, h * .34f), Size(w * .64f, h * .42f), style = Stroke(1.8f)); line(Offset(w * .20f, h * .34f), Offset(w * .42f, h * .18f)); line(Offset(w * .42f, h * .18f), Offset(w * .62f, h * .34f)) }
        Glyph.Save -> { drawRoundRect(color, Offset(w * .20f, h * .16f), Size(w * .60f, h * .68f), androidx.compose.ui.geometry.CornerRadius(3f, 3f), style = Stroke(1.8f)); line(Offset(w * .35f, h * .20f), Offset(w * .35f, h * .44f)); line(Offset(w * .35f, h * .44f), Offset(w * .65f, h * .44f)); drawRect(color, Offset(w * .34f, h * .58f), Size(w * .32f, h * .18f), style = Stroke(1.5f)) }
        Glyph.Export -> { drawRect(color, Offset(w * .20f, h * .57f), Size(w * .60f, h * .22f), style = Stroke(1.8f)); line(Offset(w * .50f, h * .16f), Offset(w * .50f, h * .61f)); line(Offset(w * .50f, h * .16f), Offset(w * .34f, h * .32f)); line(Offset(w * .50f, h * .16f), Offset(w * .66f, h * .32f)) }
        Glyph.Brush -> { line(Offset(w * .25f, h * .78f), Offset(w * .75f, h * .28f), 2.7f); drawCircle(color, w * .16f, Offset(w * .27f, h * .75f)) }
        Glyph.Eraser -> { drawRoundRect(color, Offset(w * .27f, h * .36f), Size(w * .48f, h * .34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)); line(Offset(w * .22f, h * .76f), Offset(w * .78f, h * .76f)) }
        Glyph.Transform -> { line(Offset(w * .15f, h / 2), Offset(w * .85f, h / 2)); line(Offset(w / 2, h * .15f), Offset(w / 2, h * .85f)); drawCircle(color, 2.4f, Offset(w / 2, h / 2)) }
        Glyph.Undo -> { line(Offset(w * .78f, h * .35f), Offset(w * .35f, h * .35f)); line(Offset(w * .35f, h * .35f), Offset(w * .50f, h * .20f)); line(Offset(w * .35f, h * .35f), Offset(w * .50f, h * .50f)); line(Offset(w * .78f, h * .35f), Offset(w * .78f, h * .73f)) }
        Glyph.Redo -> { line(Offset(w * .22f, h * .35f), Offset(w * .65f, h * .35f)); line(Offset(w * .65f, h * .35f), Offset(w * .50f, h * .20f)); line(Offset(w * .65f, h * .35f), Offset(w * .50f, h * .50f)); line(Offset(w * .22f, h * .35f), Offset(w * .22f, h * .73f)) }
        Glyph.Fit -> { drawRect(color, Offset(w * .22f, h * .22f), Size(w * .56f, h * .56f), style = Stroke(1.8f)); line(Offset(w * .10f, h * .10f), Offset(w * .32f, h * .10f)); line(Offset(w * .10f, h * .10f), Offset(w * .10f, h * .32f)) }
        Glyph.Palette -> { drawCircle(color, w * .35f, Offset(w * .50f, h * .50f), style = Stroke(1.9f)); drawCircle(color, 2f, Offset(w * .39f, h * .44f)); drawCircle(color, 2f, Offset(w * .57f, h * .40f)); drawCircle(color, 2f, Offset(w * .55f, h * .60f)) }
        Glyph.Library -> { drawRoundRect(color, Offset(w * .20f, h * .22f), Size(w * .60f, h * .56f), androidx.compose.ui.geometry.CornerRadius(4f, 4f), style = Stroke(1.8f)); line(Offset(w * .32f, h * .42f), Offset(w * .68f, h * .42f)); line(Offset(w * .32f, h * .58f), Offset(w * .57f, h * .58f)) }
        Glyph.Layers -> { drawRoundRect(color, Offset(w * .24f, h * .22f), Size(w * .52f, h * .14f), androidx.compose.ui.geometry.CornerRadius(2f, 2f)); drawRoundRect(color, Offset(w * .24f, h * .44f), Size(w * .52f, h * .14f), androidx.compose.ui.geometry.CornerRadius(2f, 2f)); drawRoundRect(color, Offset(w * .24f, h * .66f), Size(w * .52f, h * .14f), androidx.compose.ui.geometry.CornerRadius(2f, 2f)) }
    }
}
