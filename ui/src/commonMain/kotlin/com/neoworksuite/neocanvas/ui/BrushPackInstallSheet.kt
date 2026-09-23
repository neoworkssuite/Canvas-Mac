package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrushPackInstallSheet(manager: BrushPackManager, onInstalled: () -> Unit) {
    val preview = manager.pending ?: return
    val manifest = preview.pack.manifest
    AlertDialog(
        onDismissRequest = manager::cancel,
        title = { Text(manifest.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(preview.provenanceLabel, color = NeoCanvasColors.accent, fontSize = 11.sp)
                Text(manifest.summary, color = NeoCanvasColors.paper)
                Text("${manifest.brushIds.size} brushes · Version ${manifest.version}", color = NeoCanvasColors.muted)
                Text("By ${manifest.author} · ${manifest.licence}", color = NeoCanvasColors.muted, fontSize = 10.sp)
                preview.pack.brushes.values.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { Text(it.name, Modifier.weight(1f), color = NeoCanvasColors.paper, fontSize = 9.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (manager.install() != null) onInstalled() }) { Text(manager.primaryActionLabel) } },
        dismissButton = { TextButton(onClick = manager::cancel) { Text("Cancel") } },
    )
    manager.errorMessage?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF8A8A), fontSize = 10.sp) }
}
