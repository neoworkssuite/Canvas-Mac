package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun NewCanvasDialog(state: EditorState) {
    if (!state.newCanvasDialogVisible) return
    var width by remember { mutableStateOf(state.document.width.toString()) }
    var height by remember { mutableStateOf(state.document.height.toString()) }
    val w = width.toIntOrNull() ?: 0
    val h = height.toIntOrNull() ?: 0
    val valid = w in 1..8192 && h in 1..8192 && w.toLong() * h <= 16_000_000
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = NeoCanvasColors.paper, unfocusedTextColor = NeoCanvasColors.paper,
        focusedBorderColor = NeoCanvasColors.accent, unfocusedBorderColor = NeoCanvasColors.muted,
        focusedLabelColor = NeoCanvasColors.accent, unfocusedLabelColor = NeoCanvasColors.muted,
        cursorColor = NeoCanvasColors.accent,
    )
    AlertDialog(
        onDismissRequest = { state.newCanvasDialogVisible = false },
        containerColor = NeoCanvasColors.panel,
        title = { Text("New canvas", color = NeoCanvasColors.paper) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pixel dimensions", color = NeoCanvasColors.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(width, { width = it }, label = { Text("Width") }, singleLine = true,
                        modifier = Modifier.weight(1f), colors = colors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(height, { height = it }, label = { Text("Height") }, singleLine = true,
                        modifier = Modifier.weight(1f), colors = colors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                TextButton(onClick = { val previous = width; width = height; height = previous }) { Text("Swap orientation", color = NeoCanvasColors.accent) }
                listOf("Sketch · 2048 × 1536" to (2048 to 1536), "Square · 2048 × 2048" to (2048 to 2048),
                    "HD · 1920 × 1080" to (1920 to 1080), "Portrait · 1536 × 2048" to (1536 to 2048)).forEach { (label, dimensions) ->
                    TextButton(onClick = { width = dimensions.first.toString(); height = dimensions.second.toString() }) {
                        Text(label, color = NeoCanvasColors.paper)
                    }
                }
                Text(if (valid) "${w.toLong() * h} pixels · local canvas" else "Use 1–8192 pixels per side, up to 16 million pixels total.", color = NeoCanvasColors.muted)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { state.newDocument(w, h) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { state.newCanvasDialogVisible = false }) { Text("Cancel") } },
    )
}
