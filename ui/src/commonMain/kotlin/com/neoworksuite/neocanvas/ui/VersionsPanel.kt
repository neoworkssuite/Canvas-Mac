package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VersionsPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    var label by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "VERSIONS",
                    color = NeoCanvasColors.paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .9.sp,
                )
                Text(
                    "Local milestones · no cloud required",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("Done", color = NeoCanvasColors.accent)
            }
        }

        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeoCanvasColors.chrome)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(60) },
                singleLine = true,
                label = { Text("Milestone name") },
                placeholder = { Text("e.g. Colour approved") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Creates a complete local snapshot of layers and pixels.",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = label.trim().isNotEmpty(),
                    onClick = {
                        if (state.createVersion(label)) label = ""
                    },
                ) {
                    Text("Save milestone", color = NeoCanvasColors.accent)
                }
            }
        }

        state.versionError?.let { error ->
            Text(
                error,
                color = NeoCanvasColors.accent,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeoCanvasColors.panelRaised)
                    .padding(8.dp),
            )
        }

        if (state.versions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No milestones yet.\nSave one before a major change.",
                    color = NeoCanvasColors.muted,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(state.versions, key = { _, item -> item.id }) { index, version ->
                    VersionTimelineRow(
                        version = version,
                        number = state.versions.size - index,
                        newest = index == 0,
                        onRestore = { state.restoreVersion(version.id) },
                        onDelete = { state.deleteVersion(version.id) },
                    )
                }
            }
        }

        Text(
            "Restore is non-destructive: NeoCanvas first saves the current canvas as “Before restore”.",
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun VersionTimelineRow(
    version: LocalVersionEntry,
    number: Int,
    newest: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (newest) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.clip(CircleShape)
                    .background(if (newest) NeoCanvasColors.accent else NeoCanvasColors.line)
                    .padding(5.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                version.label,
                color = NeoCanvasColors.paper,
                fontSize = 12.sp,
                fontWeight = if (newest) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                "VERSION " + number + if (newest) " · LATEST" else " · LOCAL",
                color = NeoCanvasColors.faint,
                fontSize = 8.sp,
                letterSpacing = .6.sp,
            )
        }
        TextButton(onClick = onRestore) {
            Text("Restore", color = NeoCanvasColors.accent, fontSize = 10.sp)
        }
        TextButton(onClick = onDelete) {
            Text("Delete", color = NeoCanvasColors.muted, fontSize = 10.sp)
        }
    }
}
