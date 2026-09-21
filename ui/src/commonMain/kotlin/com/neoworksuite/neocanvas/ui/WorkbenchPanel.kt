package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
fun WorkbenchPanel(state: EditorState, modifier: Modifier = Modifier, onClose: () -> Unit) {
    var note by remember { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "WORKBENCH",
                    color = NeoCanvasColors.paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .9.sp,
                )
                Text(
                    "Your desk around the artwork",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", color = NeoCanvasColors.accent) }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(NeoCanvasColors.chrome).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("SHOW DESK", color = NeoCanvasColors.paper, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text("Workbench items never appear in PNG or PSD exports.", color = NeoCanvasColors.faint, fontSize = 9.sp)
            }
            Switch(checked = state.workbenchVisible, onCheckedChange = { state.workbenchVisible = it })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            InspectorAction("Reference", Modifier.weight(1f)) { state.importWorkbenchReference() }
            InspectorAction("Colour", Modifier.weight(1f)) { state.addWorkbenchColourCard() }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(NeoCanvasColors.chrome).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = { Text("Add a note") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notes stay on the desk.",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = note.trim().isNotEmpty(),
                    onClick = { if (state.addWorkbenchNote(note)) note = "" },
                ) { Text("Add note", color = NeoCanvasColors.accent) }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.workbenchItems.size.toString() + " DESK ITEM" + if (state.workbenchItems.size == 1) "" else "S",
                color = NeoCanvasColors.faint,
                fontSize = 9.sp,
                letterSpacing = .7.sp,
                modifier = Modifier.weight(1f),
            )
            if (state.workbenchItems.isNotEmpty()) {
                TextButton(onClick = {
                    state.zoom = .72f
                    state.panX = -150f
                    state.panY = 0f
                    state.workbenchVisible = true
                }) {
                    Text("Desk View", color = NeoCanvasColors.accent, fontSize = 9.sp)
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(state.workbenchItems.asReversed(), key = { it.id }) { item ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                        .background(NeoCanvasColors.panelRaised)
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (item) {
                                is WorkbenchItem.Reference -> item.name.ifBlank { "Reference" }
                                is WorkbenchItem.Note -> item.text.take(45)
                                is WorkbenchItem.ColourCard -> item.hex
                            },
                            color = NeoCanvasColors.paper,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            when (item) {
                                is WorkbenchItem.Reference -> "REFERENCE"
                                is WorkbenchItem.Note -> "NOTE"
                                is WorkbenchItem.ColourCard -> "COLOUR CARD"
                            },
                            color = NeoCanvasColors.faint,
                            fontSize = 8.sp,
                            letterSpacing = .6.sp,
                        )
                    }
                    TextButton(onClick = { state.deleteWorkbenchItem(item.id) }) {
                        Text("Delete", color = NeoCanvasColors.muted, fontSize = 9.sp)
                    }
                }
            }
        }

        Text(
            "Drag cards directly on the desk. Two-finger pan or zoom out to arrange items around the artboard.",
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
        )
    }
}
