package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.brushes.BrushDefinition
import com.neoworksuite.neocanvas.brushes.BrushDynamics
import com.neoworksuite.neocanvas.brushes.BrushMode
import com.neoworksuite.neocanvas.brushes.BuiltInBrushes
import com.neoworksuite.neocanvas.renderer.RasterColor
import com.neoworksuite.neocanvas.renderer.RasterPoint
import com.neoworksuite.neocanvas.renderer.Rasterizer
import com.neoworksuite.neocanvas.renderer.TileStore
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun BrushPanel(state: EditorState, modifier: Modifier = Modifier) {
    val library = remember(state) {
        BrushLibraryState(
            initialSnapshot = state.loadBrushLibrarySnapshot(),
            onPersist = state::persistBrushLibrarySnapshot,
        )
    }
    val pad = remember { BrushTestPadState() }
    Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        InspectorHeading("BRUSH LIBRARY", "${BuiltInBrushes.paintBrushes.size} brushes")
        OutlinedTextField(
            value = library.query,
            onValueChange = { library.query = it },
            singleLine = true,
            label = { Text("Search brushes") },
            modifier = Modifier.fillMaxWidth().height(54.dp).semantics { contentDescription = "Search brush library" },
        )
        BrushShelfRow(library)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 470.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryRail(library, Modifier.width(112.dp).fillMaxHeight())
                    BrushList(state, library, Modifier.width(190.dp).fillMaxHeight())
                    BrushWorkbench(state, library, pad, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryStrip(library)
                    BrushList(state, library, Modifier.weight(1f).fillMaxWidth())
                    BrushWorkbench(state, library, pad, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun BrushShelfRow(library: BrushLibraryState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ShelfChip("All", library.shelf == BrushShelf.All, library::showAll)
        ShelfChip("★ Favourites", library.shelf == BrushShelf.Favourites, library::showFavourites)
        ShelfChip("↶ Recent", library.shelf == BrushShelf.Recent, library::showRecent)
    }
}

@Composable
private fun ShelfChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) NeoCanvasColors.ink else NeoCanvasColors.muted, fontSize = 10.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) NeoCanvasColors.accent else NeoCanvasColors.panelRaised)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp))
}

@Composable
private fun CategoryRail(library: BrushLibraryState, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(library.categories, key = { it.id }) { category ->
            CategoryChip(category.name, library.selectedCategoryId == category.id) { library.selectCategory(category.id) }
        }
    }
}

@Composable
private fun CategoryStrip(library: BrushLibraryState) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(library.categories, key = { it.id }) { category ->
            CategoryChip(category.name, library.selectedCategoryId == category.id) { library.selectCategory(category.id) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) NeoCanvasColors.accent else NeoCanvasColors.muted, fontSize = 10.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
            .background(if (selected) NeoCanvasColors.chrome else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp))
}

@Composable
private fun BrushList(state: EditorState, library: BrushLibraryState, modifier: Modifier) {
    val brushes = library.visibleBrushes
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (brushes.isEmpty()) item { Text("No matching brushes", color = NeoCanvasColors.muted, fontSize = 11.sp) }
        items(brushes, key = { it.id }) { brush ->
            BrushPreset(
                brush = brush,
                selected = brush.id == state.brush.id,
                favourite = library.isFavourite(brush.id),
                onFavourite = { library.toggleFavourite(brush.id) },
                onClick = { library.choose(brush); state.selectBrush(brush) },
            )
        }
    }
}

@Composable
private fun BrushPreset(brush: BrushDefinition, selected: Boolean, favourite: Boolean,
    onFavourite: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(if (selected) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
            .clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokePreview(brush, Modifier.width(58.dp).height(25.dp))
        Column(Modifier.weight(1f).padding(start = 7.dp)) {
            Text(brush.name, color = NeoCanvasColors.paper, fontSize = 11.sp, maxLines = 1)
            Text(brush.tip.name, color = NeoCanvasColors.faint, fontSize = 8.sp)
        }
        Text(if (favourite) "★" else "☆", color = if (favourite) NeoCanvasColors.accent else NeoCanvasColors.faint,
            fontSize = 15.sp, modifier = Modifier.clickable(onClick = onFavourite).padding(3.dp)
                .semantics { contentDescription = if (favourite) "Remove favourite" else "Add favourite" })
    }
}

@Composable
private fun BrushWorkbench(state: EditorState, library: BrushLibraryState, pad: BrushTestPadState, modifier: Modifier) {
    var customName by remember(state.brush.id) { mutableStateOf("${state.brush.name} Custom") }

    fun updateBrush(update: (BrushDefinition) -> BrushDefinition) {
        state.brush = update(state.brush)
    }

    fun updateDynamics(update: (BrushDynamics) -> BrushDynamics) {
        state.brush = state.brush.copy(dynamics = update(state.brush.dynamics))
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(state.brush.name, color = NeoCanvasColors.paper, fontSize = 12.sp, maxLines = 1)
                Text("TEST PAD · draw here", color = NeoCanvasColors.faint, fontSize = 8.sp, letterSpacing = .7.sp)
            }
            TextButton(onClick = pad::clear) { Text("Clear", color = NeoCanvasColors.accent, fontSize = 10.sp) }
        }
        BrushTestPadCanvas(state, pad, Modifier.fillMaxWidth().height(118.dp))
        Text("BRUSH STUDIO", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .9.sp)

        CompactSetting("SIZE", "${state.brushSize.toInt()} px", state.brushSize, 1f..192f) { state.brushSize = it }
        CompactSetting("FLOW", "${(state.brushOpacity * 100).toInt()}%", state.brushOpacity, .01f..1f) { state.brushOpacity = it }
        CompactSetting("SPACE", "${state.brush.spacing.toInt()}", state.brush.spacing, .5f..96f) {
            updateBrush { brush -> brush.copy(spacing = it.coerceAtLeast(.5f)) }
        }
        CompactSetting("P SIZE", "${(state.brush.pressureSize * 100).toInt()}%", state.brush.pressureSize, 0f..1f) {
            updateBrush { brush -> brush.copy(pressureSize = it) }
        }
        CompactSetting("P FLOW", "${(state.brush.pressureOpacity * 100).toInt()}%", state.brush.pressureOpacity, 0f..1f) {
            updateBrush { brush -> brush.copy(pressureOpacity = it) }
        }
        CompactSetting("GRAIN", "${(state.brush.dynamics.grain * 100).toInt()}%", state.brush.dynamics.grain, 0f..1f) {
            updateDynamics { dynamics -> dynamics.copy(grain = it) }
        }
        CompactSetting("SCAT", "${(state.brush.dynamics.scatter * 100).toInt()}%", state.brush.dynamics.scatter, 0f..1f) {
            updateDynamics { dynamics -> dynamics.copy(scatter = it) }
        }
        CompactSetting("HARD", "${(state.brush.dynamics.hardness * 100).toInt()}%", state.brush.dynamics.hardness, 0f..1f) {
            updateDynamics { dynamics -> dynamics.copy(hardness = it) }
        }
        CompactSetting("WET", "${(state.brush.dynamics.wetMix * 100).toInt()}%", state.brush.dynamics.wetMix, 0f..1f) {
            updateDynamics { dynamics -> dynamics.copy(wetMix = it) }
        }
        CompactSetting("JITTER", "${(state.brush.dynamics.jitter * 100).toInt()}%", state.brush.dynamics.jitter, 0f..1f) {
            updateDynamics { dynamics -> dynamics.copy(jitter = it) }
        }
        CompactSetting("SHAPE", "${(state.brush.dynamics.shapeRatio * 100).toInt()}%", state.brush.dynamics.shapeRatio, .1f..1f) {
            updateDynamics { dynamics -> dynamics.copy(shapeRatio = it) }
        }

        OutlinedTextField(
            value = customName,
            onValueChange = { customName = it.take(80) },
            singleLine = true,
            label = { Text("Custom brush name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                BuiltInBrushes.find(state.brush.id)?.let(state::selectBrush)
            }) {
                Text("Reset preset", color = NeoCanvasColors.muted, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = customName.trim().isNotEmpty(),
                onClick = {
                    val saved = runCatching { library.saveCustom(customName, state.brush) }.getOrNull()
                    if (saved != null) {
                        library.choose(saved)
                        state.selectBrush(saved)
                        customName = "${saved.name} Copy"
                        state.statusMessage = "Saved custom brush: ${saved.name}"
                    }
                },
            ) {
                Text("Save Custom", color = NeoCanvasColors.accent, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CompactSetting(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NeoCanvasColors.faint, fontSize = 8.sp, modifier = Modifier.width(34.dp))
        Slider(value, onChange, valueRange = range, modifier = Modifier.weight(1f).height(28.dp), colors = studioSliderColors())
        Text(valueLabel, color = NeoCanvasColors.muted, fontSize = 9.sp, modifier = Modifier.width(42.dp))
    }
}

@Composable
private fun BrushTestPadCanvas(state: EditorState, pad: BrushTestPadState, modifier: Modifier) {
    val images = remember { TileImageCache(6) }
    val revision = pad.revision
    val tiles = remember(revision) { pad.snapshot().map { (key, bytes) -> key to images.image(key, bytes) } }
    var lastPoint by remember { mutableStateOf<RasterPoint?>(null) }
    val rasterColor = RasterColor((state.color.red * 255).roundToInt(), (state.color.green * 255).roundToInt(), (state.color.blue * 255).roundToInt())
    Canvas(modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFF4F1EA))
        .pointerInput(state.brush, state.brushSize, state.brushOpacity, rasterColor) {
            fun point(offset: Offset, pressure: Float = 1f) = RasterPoint(
                offset.x / size.width * pad.width,
                offset.y / size.height * pad.height,
                pressure.coerceIn(.05f, 1f),
            )
            detectDragGestures(
                onDragStart = { offset ->
                    lastPoint = point(offset)
                    pad.draw(listOf(lastPoint!!), state.brush, rasterColor, state.brushSize, state.brushOpacity)
                },
                onDragEnd = { lastPoint = null },
                onDragCancel = { lastPoint = null },
                onDrag = { change, _ ->
                    val next = point(change.position, change.pressure)
                    pad.draw(listOfNotNull(lastPoint, next), state.brush, rasterColor, state.brushSize, state.brushOpacity)
                    lastPoint = next
                    change.consume()
                },
            )
        }.semantics { contentDescription = "Brush test pad" }) {
        withTransform({ scale(size.width / pad.width, size.height / pad.height, Offset.Zero) }) {
            tiles.forEach { (key, bitmap) -> drawImage(bitmap, Offset(key.x * 256f, key.y * 256f)) }
        }
    }
}

@Composable
private fun StrokePreview(brush: BrushDefinition, modifier: Modifier = Modifier) {
    val images = remember { TileImageCache(4) }
    val tiles = remember(brush) {
        val store = TileStore()
        val points = (0..48).map { index ->
            val t = index / 48f
            RasterPoint(12f + 296f * t, 36f + sin(t * 6.283f) * 9f, .18f + .82f * sin(t * 3.14159f))
        }
        store.applyPatch(Rasterizer.stroke(store, "preview", points, RasterColor(105, 213, 191),
            brush.baseSize.coerceIn(3f, 34f), brush.opacity, BrushMode.PAINT, 320, 72, brush = brush))
        store.snapshot().map { (key, bytes) -> key to images.image(key, bytes) }
    }
    Canvas(modifier.clip(RoundedCornerShape(6.dp)).background(NeoCanvasColors.workspace)) {
        withTransform({ scale(size.width / 320f, size.height / 72f, Offset.Zero) }) {
            tiles.forEach { (key, bitmap) -> drawImage(bitmap, Offset(key.x * 256f, key.y * 256f)) }
        }
    }
}
