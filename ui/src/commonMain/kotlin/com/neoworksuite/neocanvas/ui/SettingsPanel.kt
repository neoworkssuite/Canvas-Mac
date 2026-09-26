package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(NeoCanvasColors.panel)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Settings", color = NeoCanvasColors.paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("NeoCanvas preferences", color = NeoCanvasColors.muted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", color = NeoCanvasColors.accent) }
        }

        SettingsSection("INPUT")
        SettingsToggle(
            "Draw with finger",
            "Allow a finger to paint and erase. Apple Pencil always remains available.",
            state.fingerPaintingEnabled,
        ) { state.fingerPaintingEnabled = it; state.persistPreferences() }
        SettingsToggle(
            "Canvas rotation",
            "Allow two-finger twist to rotate the canvas.",
            state.canvasRotationEnabled,
        ) { state.canvasRotationEnabled = it; state.persistPreferences() }

        SettingsToggle(
            "QuickShape hold-to-snap",
            "Hold at the end of a rough line, circle, triangle or square to snap it cleanly.",
            state.quickShapeEnabled,
        ) { state.quickShapeEnabled = it; state.persistPreferences() }

        SettingsToggle(
            "Eyedropper samples merged canvas",
            "On samples the visible raster composite; off samples only the active raster layer.",
            state.eyedropperSampleMerged,
        ) { state.eyedropperSampleMerged = it; state.persistPreferences() }

        SettingsToggle(
            "Eyedropper returns to previous tool",
            "Return to the tool you were using after one sample instead of staying in Eyedropper.",
            state.eyedropperReturnAfterSample,
        ) { state.eyedropperReturnAfterSample = it; state.persistPreferences() }

        SettingsSection("CANVAS & EDITING")
        SettingsToggle(
            "Smooth selection resizing",
            "Use smooth interpolation when resizing selected artwork.",
            state.smoothResizing,
        ) { state.smoothResizing = it; state.persistPreferences() }

        SettingsToggle(
            "2D drawing grid",
            "Overlay an adjustable local drawing grid on the canvas.",
            state.gridGuideVisible,
        ) { state.gridGuideVisible = it; state.persistPreferences() }

        SettingsToggle(
            "Perspective guide",
            "Overlay a one-point perspective guide from the canvas centre.",
            state.perspectiveGuideVisible,
        ) { state.perspectiveGuideVisible = it; state.persistPreferences() }

        if (state.gridGuideVisible) {
            Column(Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Grid spacing",
                        color = NeoCanvasColors.paper,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${state.guideSpacing.toInt()} px", color = NeoCanvasColors.muted, fontSize = 11.sp)
                }
                Slider(
                    value = state.guideSpacing,
                    onValueChange = { state.guideSpacing = it },
                    onValueChangeFinished = state::persistPreferences,
                    valueRange = 32f..512f,
                    colors = studioSliderColors(),
                )
            }
        }

        SettingsSection("SAVING & RECOVERY")
        SettingsToggle(
            "Automatic recovery",
            "Keep a local recovery snapshot while artwork has unsaved changes.",
            state.autoRecoveryEnabled,
        ) { state.autoRecoveryEnabled = it; state.persistPreferences() }

        SettingsSection("UPDATES")
        SettingsToggle(
            "Automatic update checks",
            "Ask Apple’s App Store service for the latest NeoCanvas version once per app session. No artwork or account data is sent.",
            state.automaticUpdateChecksEnabled,
        ) {
            state.automaticUpdateChecksEnabled = it
            state.persistPreferences()
        }
        TextButton(
            enabled = state.supportsUpdateChecks && !state.updateCheckInProgress,
            onClick = { state.checkForUpdates(manual = true) },
        ) {
            Text(
                if (state.updateCheckInProgress) "Checking…" else "Check for updates now",
                color = if (state.supportsUpdateChecks) NeoCanvasColors.accent else NeoCanvasColors.disabled,
            )
        }

        SettingsSection("INTERFACE")
        Text("Drawing hand", color = NeoCanvasColors.paper, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            "Moves the drawing rail and inspectors away from your preferred drawing hand.",
            color = NeoCanvasColors.muted,
            fontSize = 11.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InterfaceSide.entries.forEach { side ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (state.interfaceSide == side) NeoCanvasColors.accent else NeoCanvasColors.panelRaised)
                        .clickable {
                            state.interfaceSide = side
                            state.persistPreferences()
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(side.name, color = NeoCanvasColors.paper, fontSize = 11.sp)
                }
            }
        }
        SettingsToggle(
            "Status messages",
            "Show temporary save, tool and editing notifications over the workspace.",
            state.showStatusMessages,
        ) { state.showStatusMessages = it; state.persistPreferences() }

        TextButton(onClick = { state.resetPreferences() }) {
            Text("Reset preferences", color = NeoCanvasColors.accent)
        }

        SettingsSection("ABOUT & SUPPORT")
        Column(
            Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "NeoCanvas " + NeoCanvasReleaseInfo.marketingVersion +
                    " (build " + NeoCanvasReleaseInfo.buildNumber + ")",
                color = NeoCanvasColors.paper,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                NeoCanvasReleaseInfo.stage,
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
            )
        }

        Column(
            Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Fonts & licensing", color = NeoCanvasColors.paper, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "Inter, Noto Sans, Lora, Playfair Display, Caveat and JetBrains Mono are bundled under the SIL Open Font License 1.1. " +
                    "Complete copyright and licence notices are included with NeoCanvas. Apple system fonts are used through iPadOS and are not redistributed.",
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
            )
        }

        Column(
            Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Privacy by design", color = NeoCanvasColors.paper, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                "No account, advertising SDK, telemetry service or cloud storage is required. " +
                    "Artwork stays on this device unless you explicitly import or export it. " +
                    "When update checks are enabled, NeoCanvas asks Apple’s App Store service only for the latest app version; no artwork is sent.",
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                onClick = { state.openProjectWebsite() },
                modifier = Modifier.weight(1f),
            ) { Text("NeoWorks website", color = NeoCanvasColors.accent) }
            TextButton(
                onClick = { state.openCommunitySupport() },
                modifier = Modifier.weight(1f),
            ) { Text("Community support", color = NeoCanvasColors.accent) }
        }
    }
}

@Composable
private fun SettingsSection(label: String) {
    Text(label, color = NeoCanvasColors.faint, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsToggle(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NeoCanvasColors.panelRaised)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NeoCanvasColors.paper, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = NeoCanvasColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.width(60.dp).height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (checked) NeoCanvasColors.accent else NeoCanvasColors.track),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier.padding(horizontal = 4.dp)
                        .width(26.dp).height(26.dp)
                        .clip(CircleShape)
                        .background(NeoCanvasColors.paper),
                )
            }
            Text(
                if (checked) "ON" else "OFF",
                color = if (checked) NeoCanvasColors.accent else NeoCanvasColors.faint,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
