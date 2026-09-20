package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        SettingsSection("CANVAS & EDITING")
        SettingsToggle(
            "Smooth selection resizing",
            "Use smooth interpolation when resizing selected artwork.",
            state.smoothResizing,
        ) { state.smoothResizing = it; state.persistPreferences() }

        SettingsSection("SAVING & RECOVERY")
        SettingsToggle(
            "Automatic recovery",
            "Keep a local recovery snapshot while artwork has unsaved changes.",
            state.autoRecoveryEnabled,
        ) { state.autoRecoveryEnabled = it; state.persistPreferences() }

        SettingsSection("INTERFACE")
        SettingsToggle(
            "Status messages",
            "Show save, tool and editing messages along the bottom of the workspace.",
            state.showStatusMessages,
        ) { state.showStatusMessages = it; state.persistPreferences() }

        TextButton(onClick = { state.resetPreferences() }) {
            Text("Reset preferences", color = NeoCanvasColors.accent)
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
        Modifier.fillMaxWidth().background(NeoCanvasColors.panelRaised).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NeoCanvasColors.paper, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = NeoCanvasColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
