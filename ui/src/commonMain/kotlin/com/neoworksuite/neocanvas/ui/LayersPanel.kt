package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.LayerGroup
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class LayerDragRegion { Handle, Body, Controls }
internal fun allowsLayerReorder(region: LayerDragRegion): Boolean = region == LayerDragRegion.Handle

@Composable
fun StudioInspector(state: EditorState, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.background(NeoCanvasColors.panel).padding(top = 6.dp)) {
        when (state.inspectorPanel) {
            InspectorPanel.Layers -> LayersPanel(state, Modifier.fillMaxSize())
            InspectorPanel.Brushes -> BrushPanel(state, Modifier.fillMaxSize())
            InspectorPanel.Colors -> ColorPanel(state, Modifier.fillMaxSize())
            InspectorPanel.Effects -> EffectsPanel(state, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun LayersPanel(state: EditorState, modifier: Modifier = Modifier) {
    val images = remember { TileImageCache(32) }
    val collapsedGroups = state.document.groups.filter { it.collapsed }.mapTo(linkedSetOf(), LayerGroup::id)
    val visibleLayers = state.document.layers.asReversed().filterNot { it.groupId in collapsedGroups }

    Column(modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("LAYERS", color = NeoCanvasColors.paper, fontSize = 11.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.weight(1f))
            Text(
                state.document.layers.size.toString() + " LAYERS" +
                    if (state.document.groups.isNotEmpty()) " · " + state.document.groups.size + " GROUPS" else "" +
                    if (state.sleepingLayerCount > 0) " · " + state.sleepingLayerCount + " SLEEPING" else "",
                color = NeoCanvasColors.faint,
                fontSize = 9.sp,
                letterSpacing = .5.sp,
            )
            LayerMemoryMenu(state)
            LayerTrayAction("Group +", Modifier.padding(start = 5.dp)) { state.addGroupFromActive() }
            Text("＋", color = NeoCanvasColors.ink, fontSize = 20.sp,
                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(9.dp)).background(NeoCanvasColors.accent)
                    .clickable { state.addLayer() }.padding(horizontal = 10.dp, vertical = 3.dp)
                    .semantics { contentDescription = "New layer" })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.document.groups.asReversed(), key = { "group-" + it.id }) { group ->
                LayerGroupCard(group, state)
            }
            items(visibleLayers, key = { "layer-" + it.id }) { layer ->
                LayerCard(layer, state, images)
            }
        }
    }
}

@Composable
private fun LayerCard(layer: Layer, state: EditorState, images: TileImageCache) {
    val selected = layer.id == state.activeLayerId
    val density = LocalDensity.current
    val actionWidthPx = with(density) { 132.dp.toPx() }
    val reorderThresholdPx = with(density) { 44.dp.toPx() }
    var swipeOffset by remember(layer.id) { mutableFloatStateOf(0f) }
    var optionsOpen by remember(layer.id) { mutableStateOf(false) }
    var renaming by remember(layer.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(NeoCanvasColors.chrome)) {
        Row(
            Modifier.align(Alignment.CenterStart).width(132.dp).padding(horizontal = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            LayerTrayAction("Duplicate", Modifier.weight(1f)) {
                state.selectLayer(layer.id)
                state.duplicateActiveLayer()
                swipeOffset = 0f
            }
            LayerTrayAction("Delete", Modifier.weight(1f), destructive = true) {
                state.selectLayer(layer.id)
                state.deleteActiveLayer()
                swipeOffset = 0f
            }
        }

        Column(
            Modifier.fillMaxWidth().offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .background(if (selected) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
                .clickable {
                    if (swipeOffset != 0f) {
                        swipeOffset = 0f
                    } else if (!selected) {
                        state.clearSelection()
                        state.selectLayer(layer.id)
                        optionsOpen = false
                    } else {
                        optionsOpen = !optionsOpen
                    }
                }
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                Modifier.pointerInput(layer.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            if (state.activeLayerId != layer.id) state.clearSelection()
                            state.selectLayer(layer.id)
                        },
                        onDragEnd = {
                            swipeOffset = if (swipeOffset > actionWidthPx / 3f) actionWidthPx else 0f
                        },
                        onDragCancel = {
                            swipeOffset = if (swipeOffset > actionWidthPx / 3f) actionWidthPx else 0f
                        },
                    ) { change, amount ->
                        swipeOffset = (swipeOffset + amount).coerceIn(0f, actionWidthPx)
                        change.consume()
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "≡",
                    color = NeoCanvasColors.faint,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 6.dp)
                        .pointerInput(layer.id) {
                            var totalY = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    if (state.activeLayerId != layer.id) state.clearSelection()
                                    state.selectLayer(layer.id)
                                    totalY = 0f
                                },
                            ) { change, amount ->
                                totalY += amount
                                if (abs(totalY) >= reorderThresholdPx) {
                                    if (state.reorderActiveLayerInDisplay(if (totalY > 0f) 1 else -1)) {
                                        totalY = 0f
                                    }
                                }
                                change.consume()
                            }
                        }
                        .semantics { contentDescription = "Drag " + layer.name + " to reorder" },
                )
                LayerThumbnail(layer, state, images, Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (renaming) {
                        BasicTextField(
                            value = layer.name,
                            onValueChange = { state.renameLayer(layer.id, it) },
                            singleLine = true,
                            textStyle = TextStyle(color = NeoCanvasColors.paper, fontSize = 12.sp),
                            modifier = Modifier.semantics { contentDescription = "Rename " + layer.name },
                        )
                    } else {
                        Text(layer.name, color = NeoCanvasColors.paper, fontSize = 12.sp, maxLines = 1)
                    }
                    val groupName = layer.groupId?.let { groupId ->
                        state.document.groups.firstOrNull { it.id == groupId }?.name
                    }
                    Text(
                        buildString {
                            append(layer.blendMode.displayName())
                            append(" · ")
                            append((layer.opacity * 100).toInt())
                            append("%")
                            groupName?.let { append(" · ").append(it) }
                            if (layer.locked) append(" · Locked")
                            if (layer.alphaLocked) append(" · α")
                            if (layer.clipping) append(" · Clip")
                            if (layer.mask != null) {
                                append(if (state.maskEditingLayerId == layer.id) " · MASK EDIT" else " · Mask")
                            }
                            if (state.isLayerDormant(layer.id)) append(" · Sleeping")
                        },
                        color = NeoCanvasColors.faint,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
                layer.mask?.let {
                    Text(
                        if (state.maskEditingLayerId == layer.id) "M*" else "M",
                        color = if (state.maskEditingLayerId == layer.id) NeoCanvasColors.accent else NeoCanvasColors.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            if (state.maskEditingLayerId == layer.id) state.editLayerArtwork()
                            else state.editLayerMask(layer.id)
                        }.padding(horizontal = 5.dp, vertical = 6.dp)
                            .semantics { contentDescription = "Toggle " + layer.name + " mask editing" },
                    )
                }
                Text(
                    if (layer.visible) "◉" else "○",
                    color = if (layer.visible) NeoCanvasColors.accent else NeoCanvasColors.faint,
                    fontSize = 18.sp,
                    modifier = Modifier.clickable { state.toggleLayerVisibility(layer.id) }
                        .padding(6.dp)
                        .semantics { contentDescription = "Toggle " + layer.name + " visibility" },
                )
                if (selected) {
                    Text(
                        "•••",
                        color = if (optionsOpen) NeoCanvasColors.accent else NeoCanvasColors.muted,
                        fontSize = 17.sp,
                        modifier = Modifier.clickable { optionsOpen = !optionsOpen }
                            .padding(horizontal = 6.dp, vertical = 5.dp)
                            .semantics { contentDescription = "Layer options" },
                    )
                }
            }

            if (selected && optionsOpen) {
                LayerOptionsPanel(
                    layer = layer,
                    state = state,
                    renaming = renaming,
                    onToggleRename = { renaming = !renaming },
                    onClose = { optionsOpen = false },
                )
            }
        }
    }
}

@Composable
private fun LayerOptionsPanel(
    layer: Layer,
    state: EditorState,
    renaming: Boolean,
    onToggleRename: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(NeoCanvasColors.chrome).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlendModePicker(
                layer = layer,
                modifier = Modifier.width(118.dp),
                onSelect = { state.setLayerBlendMode(layer.id, it) },
            )
            Text("Opacity", color = NeoCanvasColors.faint, fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp))
            Slider(
                layer.opacity,
                { state.setLayerOpacity(layer.id, it) },
                modifier = Modifier.weight(1f).height(30.dp),
                colors = studioSliderColors(),
            )
            Text(
                (layer.opacity * 100).toInt().toString(),
                color = NeoCanvasColors.muted,
                fontSize = 9.sp,
                modifier = Modifier.width(25.dp),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LayerTrayAction(if (layer.locked) "Unlock" else "Lock", Modifier.weight(1f)) {
                state.toggleLayerLock(layer.id)
            }
            LayerTrayAction(if (layer.alphaLocked) "Alpha ✓" else "Alpha", Modifier.weight(1f)) {
                state.toggleLayerAlphaLock(layer.id)
            }
            LayerTrayAction(if (layer.clipping) "Clip ✓" else "Clip", Modifier.weight(1f)) {
                state.toggleLayerClipping(layer.id)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LayerTrayAction("Select", Modifier.weight(1f)) {
                state.selectLayerArtwork()
                onClose()
            }
            LayerTrayAction(if (renaming) "Done Name" else "Rename", Modifier.weight(1f)) {
                onToggleRename()
            }
            LayerTrayAction("Duplicate", Modifier.weight(1f)) {
                state.duplicateActiveLayer()
                onClose()
            }
        }

        LayerGroupPicker(layer, state)

        if (layer.mask == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LayerTrayAction("Add Mask", Modifier.weight(1f)) {
                    state.addMaskToActiveLayer()
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LayerTrayAction(
                    if (state.maskEditingLayerId == layer.id) "Artwork" else "Edit Mask",
                    Modifier.weight(1f),
                ) {
                    if (state.maskEditingLayerId == layer.id) state.editLayerArtwork()
                    else state.editLayerMask(layer.id)
                }
                LayerTrayAction(if (layer.mask.enabled) "Mask ✓" else "Mask Off", Modifier.weight(1f)) {
                    state.toggleActiveMaskEnabled()
                }
                LayerTrayAction(if (layer.mask.inverted) "Invert ✓" else "Invert", Modifier.weight(1f)) {
                    state.toggleActiveMaskInverted()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LayerTrayAction("Remove Mask", Modifier.weight(1f), destructive = true) {
                    state.removeActiveMask()
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LayerTrayAction("Merge Down", Modifier.weight(1f)) {
                state.mergeActiveLayerDown()
                onClose()
            }
            LayerTrayAction("Delete", Modifier.weight(1f), destructive = true) {
                state.deleteActiveLayer()
                onClose()
            }
        }
    }
}

@Composable
private fun LayerGroupCard(group: LayerGroup, state: EditorState) {
    val childCount = state.document.layers.count { it.groupId == group.id }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(NeoCanvasColors.panelRaised)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (group.collapsed) "▸" else "▾",
                color = NeoCanvasColors.accent,
                fontSize = 14.sp,
                modifier = Modifier.clickable { state.toggleGroupCollapsed(group.id) }
                    .padding(end = 7.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(group.name, color = NeoCanvasColors.paper, fontSize = 11.sp, maxLines = 1)
                Text(
                    childCount.toString() + " layer" + if (childCount == 1) "" else "s" +
                        if (group.locked) " · Locked" else "",
                    color = NeoCanvasColors.faint,
                    fontSize = 8.sp,
                )
            }
            Text(
                if (group.visible) "◉" else "○",
                color = if (group.visible) NeoCanvasColors.accent else NeoCanvasColors.faint,
                fontSize = 17.sp,
                modifier = Modifier.clickable { state.toggleGroupVisibility(group.id) }.padding(5.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opacity", color = NeoCanvasColors.faint, fontSize = 8.sp)
            Slider(
                value = group.opacity,
                onValueChange = { state.setGroupOpacity(group.id, it) },
                modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 5.dp),
                colors = studioSliderColors(),
            )
            Text(
                (group.opacity * 100).toInt().toString(),
                color = NeoCanvasColors.muted,
                fontSize = 8.sp,
                modifier = Modifier.width(24.dp),
            )
            LayerTrayAction(if (group.locked) "Unlock" else "Lock", Modifier.padding(start = 4.dp)) {
                state.toggleGroupLocked(group.id)
            }
            LayerTrayAction("Delete", Modifier.padding(start = 4.dp), destructive = true) {
                state.deleteGroup(group.id)
            }
        }
    }
}

@Composable
private fun LayerGroupPicker(layer: Layer, state: EditorState) {
    var expanded by remember(layer.id, state.document.groups) { mutableStateOf(false) }
    val current = layer.groupId?.let { id -> state.document.groups.firstOrNull { it.id == id } }
    Box {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(NeoCanvasColors.panelRaised)
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Text(
                "Group · " + (current?.name ?: "None"),
                color = NeoCanvasColors.muted,
                fontSize = 9.sp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = NeoCanvasColors.panelRaised,
        ) {
            DropdownMenuItem(
                text = { Text("No group", color = if (current == null) NeoCanvasColors.accent else NeoCanvasColors.paper) },
                onClick = {
                    expanded = false
                    state.setActiveLayerGroup(null)
                },
            )
            state.document.groups.forEach { group ->
                DropdownMenuItem(
                    text = {
                        Text(
                            group.name,
                            color = if (current?.id == group.id) NeoCanvasColors.accent else NeoCanvasColors.paper,
                        )
                    },
                    onClick = {
                        expanded = false
                        state.setActiveLayerGroup(group.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun LayerMemoryMenu(state: EditorState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            "•••",
            color = NeoCanvasColors.muted,
            fontSize = 17.sp,
            modifier = Modifier.clickable { expanded = true }
                .padding(horizontal = 7.dp, vertical = 4.dp)
                .semantics { contentDescription = "Layer memory options" },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = NeoCanvasColors.panelRaised,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "Resident · " + formatMemoryBytes(state.residentRasterBytes),
                        color = NeoCanvasColors.faint,
                        fontSize = 10.sp,
                    )
                },
                enabled = false,
                onClick = {},
            )
            DropdownMenuItem(
                text = { Text("Sleep hidden layers", color = NeoCanvasColors.paper, fontSize = 11.sp) },
                enabled = state.supportsDeepLayers,
                onClick = {
                    expanded = false
                    state.sleepHiddenLayers()
                },
            )
            DropdownMenuItem(
                text = { Text("Wake all layers", color = NeoCanvasColors.paper, fontSize = 11.sp) },
                enabled = state.sleepingLayerCount > 0,
                onClick = {
                    expanded = false
                    state.wakeAllLayers()
                },
            )
        }
    }
}

@Composable
private fun BlendModePicker(
    layer: Layer,
    modifier: Modifier = Modifier,
    onSelect: (LayerBlendMode) -> Unit,
) {
    var expanded by remember(layer.id) { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .background(NeoCanvasColors.panelRaised)
                .clickable { expanded = true }
                .padding(horizontal = 5.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                layer.blendMode.displayName(),
                color = NeoCanvasColors.muted,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = NeoCanvasColors.panelRaised,
        ) {
            LayerBlendMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            mode.displayName(),
                            color = if (mode == layer.blendMode) NeoCanvasColors.accent else NeoCanvasColors.paper,
                            fontSize = 11.sp,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

private fun LayerBlendMode.displayName(): String = when (this) {
    LayerBlendMode.Normal -> "Normal"
    LayerBlendMode.Multiply -> "Multiply"
    LayerBlendMode.Screen -> "Screen"
    LayerBlendMode.Overlay -> "Overlay"
    LayerBlendMode.Darken -> "Darken"
    LayerBlendMode.Lighten -> "Lighten"
    LayerBlendMode.ColorDodge -> "Colour Dodge"
    LayerBlendMode.ColorBurn -> "Colour Burn"
    LayerBlendMode.SoftLight -> "Soft Light"
    LayerBlendMode.HardLight -> "Hard Light"
    LayerBlendMode.Difference -> "Difference"
    LayerBlendMode.Exclusion -> "Exclusion"
    LayerBlendMode.Add -> "Add"
    LayerBlendMode.Subtract -> "Subtract"
}

@Composable
private fun LayerTrayAction(label: String, modifier: Modifier = Modifier, destructive: Boolean = false, onClick: () -> Unit) = Box(
    modifier.clip(RoundedCornerShape(6.dp)).background(if (destructive) Color(0xFF71313A) else NeoCanvasColors.panelRaised)
        .clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 7.dp), contentAlignment = Alignment.Center,
) { Text(label, color = if (destructive) Color.White else NeoCanvasColors.muted, fontSize = 9.sp) }

@Composable
private fun LayerThumbnail(layer: Layer, state: EditorState, images: TileImageCache, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(5.dp)).background(NeoCanvasColors.chrome)) {
        val document = state.document // Observe document revision so paint and undo refresh previews.
        drawLayerPreview(layer, document.width, document.height, state.tileStore, images)
    }
}

@Composable
fun InspectorHeading(title: String, detail: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(title, color = NeoCanvasColors.paper, fontSize = 11.sp, letterSpacing = 1.2.sp)
    Spacer(Modifier.weight(1f))
    Text(detail.uppercase(), color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .5.sp)
}

@Composable
fun InspectorAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) = Box(
    modifier.clip(RoundedCornerShape(9.dp)).background(NeoCanvasColors.panelRaised).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
) { Text(label, color = NeoCanvasColors.muted, fontSize = 11.sp) }
