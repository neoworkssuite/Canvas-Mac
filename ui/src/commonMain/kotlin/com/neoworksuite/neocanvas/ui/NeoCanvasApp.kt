package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.neoworksuite.neocanvas.core.store.LoadResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerPayload

internal enum class InspectorPresentation { Overlay }
internal fun inspectorPresentation(panel: InspectorPanel): InspectorPresentation = InspectorPresentation.Overlay

internal data class ColourStudioBounds(
    val widthFraction: Float,
    val heightFraction: Float,
    val maxWidth: Dp,
    val maxHeight: Dp,
)

internal fun colourStudioBounds(compact: Boolean): ColourStudioBounds = if (compact) {
    ColourStudioBounds(.94f, .60f, 380.dp, 520.dp)
} else {
    ColourStudioBounds(.36f, .66f, 380.dp, 520.dp)
}

@Composable
fun rememberEditorState(fileActions: EditorFileActions = UnavailableEditorFileActions): EditorState = remember {
        EditorState(
            DocumentHistory(
                CanvasDocument(
                    id = "new-local-document",
                    width = 2048,
                    height = 1536,
                    layers = listOf(Layer("layer-1", "Sketch", payload = LayerPayload.Raster())),
                ),
            ), fileActions,
        )
}

/** Shared, local-first creative studio host for Android tablets and Windows. */
@Composable
fun NeoCanvasApp(
    fileActions: EditorFileActions = UnavailableEditorFileActions,
    state: EditorState = rememberEditorState(fileActions),
    startInEditor: Boolean = false,
) = MaterialTheme {
    var destination by remember {
        mutableStateOf(if (startInEditor) AppDestination.Editor else AppDestination.Gallery)
    }
    LocalLibraryDialogs(state)
    NewCanvasDialog(state)
    LaunchedEffect(state) {
        state.checkRecovery()
        while (true) {
            delay(30_000)
            if (state.autoRecoveryEnabled) state.autosaveRecovery()
        }
    }
    LaunchedEffect(
        state.recoveryChecking,
        state.recoveryCandidate,
        state.automaticUpdateChecksEnabled,
    ) {
        if (!state.recoveryChecking &&
            state.recoveryCandidate == null &&
            state.automaticUpdateChecksEnabled
        ) {
            delay(1_800)
            state.checkForUpdates()
        }
    }
        LaunchedEffect(state.statusMessage, state.showStatusMessages) {
        val message = state.statusMessage ?: return@LaunchedEffect
        if (!state.showStatusMessages) return@LaunchedEffect
        val important = message.contains("fail", ignoreCase = true) ||
            message.contains("error", ignoreCase = true) ||
            message.contains("could not", ignoreCase = true)
        delay(if (important) 6_000 else 2_800)
        if (state.statusMessage == message) state.statusMessage = null
    }
    if (state.recoveryChecking || state.recoveryCandidate != null) {
        val available = state.recoveryCandidate is LoadResult.Success
        AlertDialog(
            onDismissRequest = {},
            containerColor = NeoCanvasColors.panel,
            title = { Text(if (state.recoveryChecking) "Checking local recovery…" else "Local recovery snapshot", color = NeoCanvasColors.paper) },
            text = { Text(
                if (state.recoveryChecking) "Checking this device for your last recovery copy."
                else if (available) "NeoCanvas found an unsaved recovery snapshot from a previous session. Recover it to inspect and save it, or start fresh to remove it. Your manually saved Gallery artwork is unchanged."
                else "The previous recovery snapshot could not be opened. Start fresh to remove it and continue. Your manually saved Gallery artwork is unchanged.",
                color = NeoCanvasColors.muted,
            ) },
            confirmButton = {
                if (available) TextButton(onClick = { state.restoreRecovery(); destination = AppDestination.Editor }) { Text("Recover snapshot") }
            },
            dismissButton = {
                if (!state.recoveryChecking) TextButton(onClick = { state.dismissRecovery() }) { Text("Start fresh") }
            },
        )
    }
    state.updateAvailable?.let { update ->
        if (!state.recoveryChecking && state.recoveryCandidate == null && state.pendingDocumentAction == null) {
            AlertDialog(
                onDismissRequest = state::dismissUpdateNotice,
                containerColor = NeoCanvasColors.panel,
                title = { Text("NeoCanvas " + update.version + " is available", color = NeoCanvasColors.paper) },
                text = {
                    Text(
                        update.releaseNotes?.take(700)
                            ?: "A newer NeoCanvas release is available from " + fileActions.updateServiceDescription + ".",
                        color = NeoCanvasColors.muted,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { state.openAvailableUpdate() }) { Text(fileActions.updateActionLabel) }
                },
                dismissButton = {
                    TextButton(onClick = state::dismissUpdateNotice) { Text("Later") }
                },
            )
        }
    }

        if (state.pendingDocumentAction != null) {
        AlertDialog(
            onDismissRequest = { state.cancelDocumentAction() },
            containerColor = NeoCanvasColors.panel,
            title = { Text("Save your changes?", color = NeoCanvasColors.paper) },
            text = { Text(state.documentActionError?.let { "Save failed: $it\n\nYour canvas is still open. Retry saving or cancel to return to it." }
                ?: "This canvas has unsaved changes. Save before continuing, or discard them.", color = NeoCanvasColors.muted) },
            confirmButton = { TextButton(onClick = { state.saveAndContinue() }) { Text("Save and continue") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { state.cancelDocumentAction() }) { Text("Cancel") }
                    TextButton(onClick = { state.discardAndContinue() }) { Text("Discard") }
                }
            },
        )
    }
    if (destination == AppDestination.Gallery && !state.recoveryChecking && state.recoveryCandidate == null) {
        GalleryScreen(
            actions = fileActions,
            onNew = { destination = AppDestination.Editor; state.newCanvasDialogVisible = true },
            onImportDocument = {
                state.importDocumentFromPicker { opened ->
                    if (opened) destination = AppDestination.Editor
                }
            },
            onOpen = { name -> if (state.openFromGallery(name)) destination = AppDestination.Editor },
        )
    } else BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(NeoCanvasColors.workspace)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Z -> { state.undo(); true }
                    Key.Y -> { state.redo(); true }
                    else -> false
                }
            },
    ) {
        val compact = maxWidth < 860.dp
        val placement = workspacePlacement(state.interfaceSide, compact)
        val sidePanelAlignment = if (placement.panelsAtEnd) Alignment.CenterEnd else Alignment.CenterStart
        val responsivePanelAlignment = if (compact && state.interfaceSide == InterfaceSide.Automatic) {
            Alignment.BottomCenter
        } else sidePanelAlignment
        if (state.canvasOnlyMode) {
            CanvasWorkspace(state, Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize()) {
                StudioTopBar(state, compact, onGallery = { state.requestClose { destination = AppDestination.Gallery } })
                if (compact) {
                    CanvasWorkspace(state, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (placement.railAtStart) StudioRail(state, Modifier.fillMaxHeight().width(76.dp))
                        CanvasWorkspace(state, Modifier.fillMaxHeight().weight(1f))
                        if (!placement.railAtStart) StudioRail(state, Modifier.fillMaxHeight().width(76.dp))
                    }
                }
            }
        }
        if (state.showStatusMessages) state.statusMessage?.let { message ->
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .fillMaxWidth(.72f)
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeoCanvasColors.chrome.copy(alpha = .96f))
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    message,
                    color = NeoCanvasColors.muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                )
            }
        }
        if (state.inspectorVisible) {
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = null,
                    indication = null,
                ) { state.dismissInspectorToCanvas() },
            )
            val panel = state.inspectorPanel
            val overlayAlignment = when {
                panel == InspectorPanel.Brushes && state.interfaceSide == InterfaceSide.Automatic -> Alignment.Center
                else -> responsivePanelAlignment
            }
            val panelModifier = when (panel) {
                InspectorPanel.Brushes ->
                    Modifier.align(overlayAlignment)
                        .fillMaxWidth(if (compact) .94f else .76f)
                        .fillMaxHeight(if (compact) .82f else .74f)
                        .widthIn(max = 720.dp)
                        .heightIn(max = 620.dp)

                InspectorPanel.Colors ->
                    colourStudioBounds(compact).let { bounds ->
                        Modifier.align(overlayAlignment)
                            .padding(
                                end = if (compact) 10.dp else 16.dp,
                                start = if (compact) 10.dp else 0.dp,
                                bottom = 12.dp,
                            )
                            .fillMaxWidth(bounds.widthFraction)
                            .fillMaxHeight(bounds.heightFraction)
                            .widthIn(max = bounds.maxWidth)
                            .heightIn(max = bounds.maxHeight)
                    }

                InspectorPanel.Layers ->
                    Modifier.align(overlayAlignment)
                        .padding(
                            end = if (compact) 10.dp else 16.dp,
                            start = if (compact) 10.dp else 0.dp,
                            bottom = 12.dp,
                        )
                        .fillMaxWidth(if (compact) .94f else .38f)
                        .fillMaxHeight(if (compact) .68f else .82f)
                        .widthIn(max = 390.dp)
                        .heightIn(max = 680.dp)

                InspectorPanel.Effects ->
                    Modifier.align(overlayAlignment)
                        .padding(
                            end = if (compact) 10.dp else 16.dp,
                            start = if (compact) 10.dp else 0.dp,
                            bottom = 12.dp,
                        )
                        .fillMaxWidth(if (compact) .94f else .40f)
                        .fillMaxHeight(if (compact) .66f else .78f)
                        .widthIn(max = 420.dp)
                        .heightIn(max = 640.dp)

                InspectorPanel.Liquify ->
                    Modifier.align(overlayAlignment)
                        .padding(
                            end = if (compact) 10.dp else 16.dp,
                            start = if (compact) 10.dp else 0.dp,
                            bottom = 12.dp,
                        )
                        .fillMaxWidth(if (compact) .94f else .38f)
                        .fillMaxHeight(if (compact) .58f else .62f)
                        .widthIn(max = 400.dp)
                        .heightIn(max = 520.dp)
            }

            Box(
                panelModifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeoCanvasColors.panel.copy(alpha = .98f))
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(16.dp)),
            ) {
                StudioInspector(state, compact = compact, modifier = Modifier.fillMaxSize())
                Text(
                    "×",
                    color = NeoCanvasColors.paper,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .clickable { state.dismissInspectorToCanvas() }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                )
            }
        }
        if (state.objectEditorVisible) {
            Box(
                Modifier.align(responsivePanelAlignment)
                    .padding(
                        end = if (compact) 10.dp else 16.dp,
                        start = if (compact) 10.dp else 0.dp,
                        bottom = 12.dp,
                    )
                    .fillMaxWidth(if (compact) .94f else .42f)
                    .fillMaxHeight(if (compact) .72f else .82f)
                    .widthIn(max = 460.dp)
                    .heightIn(max = 700.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeoCanvasColors.panel.copy(alpha = .98f))
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(16.dp)),
            ) {
                ObjectPanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onClose = state::closeObjectEditor,
                )
            }
        }
        if (state.psdCompatibilityVisible) {
            Box(
                Modifier.align(responsivePanelAlignment)
                    .padding(
                        end = if (compact) 10.dp else 16.dp,
                        start = if (compact) 10.dp else 0.dp,
                        bottom = 12.dp,
                    )
                    .fillMaxWidth(if (compact) .94f else .42f)
                    .fillMaxHeight(if (compact) .74f else .82f)
                    .widthIn(max = 460.dp)
                    .heightIn(max = 720.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeoCanvasColors.panel.copy(alpha = .98f))
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(16.dp)),
            ) {
                PsdCompatibilityPanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onClose = state::closePsdCompatibility,
                )
            }
        }
        if (state.recentStrokesVisible) {
            Box(
                Modifier.align(responsivePanelAlignment)
                    .padding(
                        end = if (compact) 10.dp else 16.dp,
                        start = if (compact) 10.dp else 0.dp,
                        bottom = 12.dp,
                    )
                    .fillMaxWidth(if (compact) .94f else .44f)
                    .fillMaxHeight(if (compact) .72f else .82f)
                    .widthIn(max = 470.dp)
                    .heightIn(max = 700.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeoCanvasColors.panel.copy(alpha = .98f))
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(16.dp)),
            ) {
                RecentStrokesPanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onClose = state::closeRecentStrokes,
                )
            }
        }
        if (state.workbenchPanelVisible) {
            Box(
                Modifier.align(responsivePanelAlignment)
                    .padding(
                        end = if (compact) 10.dp else 16.dp,
                        start = if (compact) 10.dp else 0.dp,
                        bottom = 12.dp,
                    )
                    .fillMaxWidth(if (compact) .94f else .42f)
                    .fillMaxHeight(if (compact) .72f else .82f)
                    .widthIn(max = 440.dp)
                    .heightIn(max = 700.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeoCanvasColors.panel.copy(alpha = .98f))
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(16.dp)),
            ) {
                WorkbenchPanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onClose = state::closeWorkbench,
                )
            }
        }
        if (state.versionsVisible) {
            Box(
                Modifier.align(responsivePanelAlignment)
                    .padding(
                        end = if (compact) 10.dp else 16.dp,
                        start = if (compact) 10.dp else 0.dp,
                        bottom = 12.dp,
                    )
                    .fillMaxWidth(if (compact) .94f else .42f)
                    .fillMaxHeight(if (compact) .72f else .82f)
                    .widthIn(max = 440.dp)
                    .heightIn(max = 700.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeoCanvasColors.panel.copy(alpha = .98f))
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(16.dp)),
            ) {
                VersionsPanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onClose = state::closeVersions,
                )
            }
        }
        if (state.settingsVisible) {
            Box(
                Modifier.align(if (state.interfaceSide == InterfaceSide.Automatic) Alignment.Center else sidePanelAlignment)
                    .fillMaxWidth(.82f).fillMaxHeight(.82f)
                    .widthIn(max = 720.dp).heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(18.dp)).background(NeoCanvasColors.panel)
                    .border(1.dp, NeoCanvasColors.line, RoundedCornerShape(18.dp)),
            ) {
                SettingsPanel(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onClose = { state.settingsVisible = false },
                )
            }
        }
    }
}

private enum class AppDestination { Gallery, Editor }
