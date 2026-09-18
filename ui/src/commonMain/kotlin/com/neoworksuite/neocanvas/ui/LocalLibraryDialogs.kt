package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LocalLibraryDialogs(state: EditorState) {
    if (state.namingLocalCopy) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { state.closeLocalLibrary() },
            containerColor = NeoCanvasColors.panel,
            title = { Text("Save a local copy", color = NeoCanvasColors.paper) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose a new name. Existing artwork will not be overwritten.", color = NeoCanvasColors.muted)
                    OutlinedTextField(name, { name = it }, singleLine = true,
                        label = { Text("Document name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NeoCanvasColors.paper, unfocusedTextColor = NeoCanvasColors.paper,
                            focusedBorderColor = NeoCanvasColors.accent, unfocusedBorderColor = NeoCanvasColors.muted,
                            focusedLabelColor = NeoCanvasColors.accent, unfocusedLabelColor = NeoCanvasColors.muted,
                            cursorColor = NeoCanvasColors.accent,
                        ))
                    state.libraryError?.let { Text(it, color = NeoCanvasColors.paper) }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { state.saveNamedCopy(name) }) { Text("Save copy") } },
            dismissButton = { TextButton(onClick = { state.closeLocalLibrary() }) { Text("Cancel") } },
        )
    }
    state.localDocuments?.let { documents ->
        AlertDialog(
            onDismissRequest = { state.closeLocalLibrary() },
            containerColor = NeoCanvasColors.panel,
            title = { Text("Open local artwork", color = NeoCanvasColors.paper) },
            text = {
                Column {
                    state.libraryError?.let { Text(it, color = NeoCanvasColors.paper) }
                    if (documents.isEmpty()) Text("No saved documents yet. Use Save or Save As to keep your artwork on this device.", color = NeoCanvasColors.muted)
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(documents, key = { it }) { name ->
                            TextButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = { state.openLocalDocument(name) }) {
                                Text(name.removeSuffix(".neocanvas"), color = NeoCanvasColors.paper)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { state.closeLocalLibrary() }) { Text("Cancel") } },
        )
    }
}
