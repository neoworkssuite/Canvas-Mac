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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

private enum class BrushPanelPage { Library, Studio }

private enum class BrushStudioSection(val label: String) {
    Stroke("Stroke"),
    Shape("Shape"),
    Dynamics("Dynamics"),
    WetMix("Wet Mix"),
    ApplePencil("Apple Pencil"),
    Properties("Properties"),
}

@Composable
fun BrushPanel(state: EditorState, modifier: Modifier = Modifier) {
    val library = remember(state) {
        BrushLibraryState(
            initialSnapshot = state.loadBrushLibrarySnapshot(),
            onPersist = state::persistBrushLibrarySnapshot,
        )
    }
    val pad = remember { BrushTestPadState() }
    val packManager = remember(library) { BrushPackManager(library) }
    var page by remember { mutableStateOf(BrushPanelPage.Library) }
    var addMenu by remember { mutableStateOf(false) }

    BrushPackInstallSheet(packManager) { state.statusMessage = "Brush pack installed" }

    if (page == BrushPanelPage.Studio) {
        BrushStudio(
            state = state,
            library = library,
            pad = pad,
            modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            onBack = { page = BrushPanelPage.Library },
        )
        return
    }

    Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f)) { InspectorHeading("BRUSHES", library.allBrushes.size.toString() + " brushes") }
            BoxWithConstraints {
                TextButton(onClick = { addMenu = true }, modifier = Modifier.semantics { contentDescription = "Add or import brush" }) { Text("＋") }
                DropdownMenu(addMenu, { addMenu = false }, containerColor = NeoCanvasColors.panelRaised) {
                    DropdownMenuItem(text = { Text("Import Brush or Pack") }, onClick = {
                        addMenu = false
                        state.openBrushFile { result -> result.onSuccess { item ->
                            if (item != null) {
                                if (item.name.endsWith(".neobrushpack", true)) packManager.preview(item.bytes)
                                else runCatching { library.importBrush(item.bytes) }
                                    .onSuccess { state.statusMessage = "Imported brush: ${it.name}" }
                                    .onFailure { state.statusMessage = "This brush could not be opened" }
                            }
                        }.onFailure { state.statusMessage = it.message ?: "Brush import failed" } }
                    })
                    DropdownMenuItem(text = { Text("Create Brush") }, onClick = { addMenu = false; page = BrushPanelPage.Studio })
                    val selectedPack = library.installedPacks.firstOrNull { it.pack.manifest.id == library.selectedCategoryId }
                    if (selectedPack != null) {
                        DropdownMenuItem(text = { Text("Share ${selectedPack.pack.manifest.name}") }, onClick = {
                            addMenu = false
                            val bytes = library.exportPack(selectedPack.pack.manifest.id)
                            if (bytes != null) state.shareBrushFile(
                                selectedPack.pack.manifest.name.replace(' ', '-') + ".neobrushpack",
                                bytes,
                            )
                        })
                        DropdownMenuItem(text = { Text("Remove ${selectedPack.pack.manifest.name}") }, onClick = {
                            addMenu = false
                            val fallback = packManager.remove(selectedPack.pack.manifest.id, state.brush.id)
                            if (fallback != null) state.selectBrush(BuiltInBrushes.pencil)
                            state.statusMessage = "Removed ${selectedPack.pack.manifest.name}"
                        })
                    }
                }
            }
        }
        OutlinedTextField(
            value = library.query,
            onValueChange = { library.query = it },
            singleLine = true,
            label = { Text("Search brushes") },
            modifier = Modifier.fillMaxWidth().height(54.dp).semantics { contentDescription = "Search brush library" },
        )
        BrushShelfRow(library)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 420.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryRail(library, Modifier.width(118.dp).fillMaxHeight())
                    BrushList(
                        state = state,
                        library = library,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onOpenStudio = { page = BrushPanelPage.Studio },
                    )
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryStrip(library)
                    BrushList(
                        state = state,
                        library = library,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onOpenStudio = { page = BrushPanelPage.Studio },
                    )
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
private fun BrushList(
    state: EditorState,
    library: BrushLibraryState,
    modifier: Modifier,
    onOpenStudio: () -> Unit,
) {
    val brushes = library.visibleBrushes
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (brushes.isEmpty()) {
            item { Text("No matching brushes", color = NeoCanvasColors.muted, fontSize = 11.sp) }
        }
        items(brushes, key = { it.id }) { brush ->
            val selected = brush.id == state.brush.id
            BrushPreset(
                brush = brush,
                selected = selected,
                favourite = library.isFavourite(brush.id),
                onFavourite = { library.toggleFavourite(brush.id) },
                onClick = {
                    if (selected) {
                        onOpenStudio()
                    } else {
                        library.choose(brush)
                        state.selectBrush(brush)
                    }
                },
            )
        }
    }
}

@Composable
private fun BrushPreset(
    brush: BrushDefinition,
    selected: Boolean,
    favourite: Boolean,
    onFavourite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokePreview(brush, Modifier.width(72.dp).height(29.dp))
        Column(Modifier.weight(1f).padding(start = 9.dp)) {
            Text(brush.name, color = NeoCanvasColors.paper, fontSize = 12.sp, maxLines = 1)
            Text(
                if (selected) "Selected · tap again for Brush Studio" else brush.tip.name,
                color = if (selected) NeoCanvasColors.accent else NeoCanvasColors.faint,
                fontSize = 8.sp,
                maxLines = 1,
            )
        }
        if (selected) {
            Text("EDIT ›", color = NeoCanvasColors.accent, fontSize = 8.sp, modifier = Modifier.padding(end = 5.dp))
        }
        Text(
            if (favourite) "★" else "☆",
            color = if (favourite) NeoCanvasColors.accent else NeoCanvasColors.faint,
            fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = onFavourite).padding(3.dp)
                .semantics { contentDescription = if (favourite) "Remove favourite" else "Add favourite" },
        )
    }
}

@Composable
private fun BrushStudio(
    state: EditorState,
    library: BrushLibraryState,
    pad: BrushTestPadState,
    modifier: Modifier,
    onBack: () -> Unit,
) {
    var section by remember { mutableStateOf(BrushStudioSection.Stroke) }
    var customName by remember(state.brush.id) { mutableStateOf(state.brush.name + " Custom") }

    fun updateBrush(update: (BrushDefinition) -> BrushDefinition) {
        state.brush = update(state.brush)
    }

    fun updateDynamics(update: (BrushDynamics) -> BrushDynamics) {
        state.brush = state.brush.copy(dynamics = update(state.brush.dynamics))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("‹ Brushes", color = NeoCanvasColors.accent, fontSize = 11.sp)
            }
            Column(Modifier.weight(1f).padding(start = 5.dp)) {
                Text("BRUSH STUDIO", color = NeoCanvasColors.faint, fontSize = 8.sp, letterSpacing = .9.sp)
                Text(state.brush.name, color = NeoCanvasColors.paper, fontSize = 14.sp, maxLines = 1)
            }
            TextButton(onClick = pad::clear) {
                Text("Clear Pad", color = NeoCanvasColors.muted, fontSize = 10.sp)
            }
        }

        BrushTestPadCanvas(state, pad, Modifier.fillMaxWidth().height(150.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BrushStudioSection.entries.forEach { option ->
                ShelfChip(option.label, section == option) { section = option }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(10.dp)).background(NeoCanvasColors.chrome)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(section.label.uppercase(), color = NeoCanvasColors.faint, fontSize = 8.sp, letterSpacing = .8.sp)

            when (section) {
                BrushStudioSection.Stroke -> {
                    CompactSetting("SIZE", state.brushSize.toInt().toString() + " px", state.brushSize, 1f..192f) {
                        state.brushSize = it
                    }
                    CompactSetting("FLOW", (state.brushOpacity * 100).toInt().toString() + "%", state.brushOpacity, .01f..1f) {
                        state.brushOpacity = it
                    }
                    CompactSetting("SPACE", state.brush.spacing.toInt().toString(), state.brush.spacing, .5f..96f) {
                        updateBrush { brush -> brush.copy(spacing = it.coerceAtLeast(.5f)) }
                    }
                }
                BrushStudioSection.Shape -> {
                    CompactSetting("HARD", (state.brush.dynamics.hardness * 100).toInt().toString() + "%", state.brush.dynamics.hardness, 0f..1f) { value ->
                        updateDynamics { dynamics -> dynamics.copy(hardness = value) }
                    }
                    CompactSetting("SHAPE", (state.brush.dynamics.shapeRatio * 100).toInt().toString() + "%", state.brush.dynamics.shapeRatio, .1f..1f) { value ->
                        updateDynamics { dynamics -> dynamics.copy(shapeRatio = value) }
                    }
                    CompactSetting("ROTATE", (state.brush.dynamics.rotation * 100).toInt().toString() + "%", state.brush.dynamics.rotation, 0f..1f) { value ->
                        updateDynamics { dynamics -> dynamics.copy(rotation = value) }
                    }
                }
                BrushStudioSection.Dynamics -> {
                    CompactSetting("GRAIN", (state.brush.dynamics.grain * 100).toInt().toString() + "%", state.brush.dynamics.grain, 0f..1f) { value ->
                        updateDynamics { dynamics -> dynamics.copy(grain = value) }
                    }
                    CompactSetting("SCAT", (state.brush.dynamics.scatter * 100).toInt().toString() + "%", state.brush.dynamics.scatter, 0f..1f) { value ->
                        updateDynamics { dynamics -> dynamics.copy(scatter = value) }
                    }
                    CompactSetting("JITTER", (state.brush.dynamics.jitter * 100).toInt().toString() + "%", state.brush.dynamics.jitter, 0f..1f) { value ->
                        updateDynamics { dynamics -> dynamics.copy(jitter = value) }
                    }
                }
                BrushStudioSection.WetMix -> {
                    Text(
                        "Pigment mixing and edge bloom for wet brushes.",
                        color = NeoCanvasColors.muted,
                        fontSize = 10.sp,
                    )
                    CompactSetting("WET", (state.brush.dynamics.wetMix * 100).toInt().toString() + "%", state.brush.dynamics.wetMix, 0f..1f) { value ->
                        updateDynamics { dynamics -> dynamics.copy(wetMix = value) }
                    }
                }
                BrushStudioSection.ApplePencil -> {
                    Text(
                        "Pressure response used by Apple Pencil and other pressure-aware styluses.",
                        color = NeoCanvasColors.muted,
                        fontSize = 10.sp,
                    )
                    CompactSetting("P SIZE", (state.brush.pressureSize * 100).toInt().toString() + "%", state.brush.pressureSize, 0f..1f) { value ->
                        updateBrush { brush -> brush.copy(pressureSize = value) }
                    }
                    CompactSetting("P FLOW", (state.brush.pressureOpacity * 100).toInt().toString() + "%", state.brush.pressureOpacity, 0f..1f) { value ->
                        updateBrush { brush -> brush.copy(pressureOpacity = value) }
                    }
                }
                BrushStudioSection.Properties -> {
                    Text(
                        "Save these settings as a local custom brush without changing the built-in preset.",
                        color = NeoCanvasColors.muted,
                        fontSize = 10.sp,
                    )
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
                                val source = state.brush.copy(
                                    baseSize = state.brushSize,
                                    opacity = state.brushOpacity,
                                )
                                val saved = runCatching { library.saveCustom(customName, source) }.getOrNull()
                                if (saved != null) {
                                    library.choose(saved)
                                    state.selectBrush(saved)
                                    customName = saved.name + " Copy"
                                    state.statusMessage = "Saved custom brush: " + saved.name
                                }
                            },
                        ) {
                            Text("Save Custom", color = NeoCanvasColors.accent, fontSize = 10.sp)
                        }
                    }
                }
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
