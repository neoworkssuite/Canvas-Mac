package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class NewCanvasPreset(val name: String, val width: Int, val height: Int)

internal val newCanvasPresets = listOf(
    NewCanvasPreset("Sketch", 2048, 1536),
    NewCanvasPreset("iPad", 2732, 2048),
    NewCanvasPreset("Square", 2048, 2048),
    NewCanvasPreset("Full HD", 1920, 1080),
    NewCanvasPreset("4K", 3840, 2160),
    NewCanvasPreset("A4 Print", 2480, 3508),
    NewCanvasPreset("Social Portrait", 1080, 1350),
    NewCanvasPreset("Story", 1080, 1920),
)

internal fun orientCanvasDimensions(width: Int, height: Int, portrait: Boolean): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return width to height
    if (width == height) {
        val shortSide = width
        val longSide = ((shortSide.toLong() * 4L) / 3L).toInt().coerceAtMost(8192)
        return if (portrait) shortSide to longSide else longSide to shortSide
    }
    val shortSide = minOf(width, height)
    val longSide = maxOf(width, height)
    return if (portrait) shortSide to longSide else longSide to shortSide
}

@Composable
internal fun NewCanvasDialog(state: EditorState) {
    if (!state.newCanvasDialogVisible) return
    var width by remember { mutableStateOf(state.document.width.toString()) }
    var height by remember { mutableStateOf(state.document.height.toString()) }
    val w = width.toIntOrNull() ?: 0
    val h = height.toIntOrNull() ?: 0
    val valid = w in 1..8192 && h in 1..8192 && w.toLong() * h <= 16_000_000
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = NeoCanvasColors.paper,
        unfocusedTextColor = NeoCanvasColors.paper,
        focusedBorderColor = NeoCanvasColors.accent,
        unfocusedBorderColor = NeoCanvasColors.muted,
        focusedLabelColor = NeoCanvasColors.accent,
        unfocusedLabelColor = NeoCanvasColors.muted,
        cursorColor = NeoCanvasColors.accent,
    )

    fun applyOrientation(portrait: Boolean) {
        val oriented = orientCanvasDimensions(w, h, portrait)
        width = oriented.first.toString()
        height = oriented.second.toString()
    }

    AlertDialog(
        onDismissRequest = { state.newCanvasDialogVisible = false },
        containerColor = NeoCanvasColors.panel,
        title = { Text("New canvas", color = NeoCanvasColors.paper) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("ORIENTATION", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CanvasOrientationChoice(
                        label = "Portrait",
                        portrait = true,
                        selected = h > w,
                        modifier = Modifier.weight(1f),
                    ) { applyOrientation(true) }
                    CanvasOrientationChoice(
                        label = "Landscape",
                        portrait = false,
                        selected = w > h,
                        modifier = Modifier.weight(1f),
                    ) { applyOrientation(false) }
                }

                Text("PIXEL DIMENSIONS", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        width,
                        { width = it },
                        label = { Text("Width") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        height,
                        { height = it },
                        label = { Text("Height") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                Text("PRESETS", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
                newCanvasPresets.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    width = preset.width.toString()
                                    height = preset.height.toString()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(preset.name, color = NeoCanvasColors.paper, fontSize = 11.sp)
                                    Text(
                                        preset.width.toString() + " × " + preset.height,
                                        color = NeoCanvasColors.muted,
                                        fontSize = 9.sp,
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                Text(
                    if (valid) {
                        (((w.toLong() * h) / 100_000L) / 10f)
                            .let { pixels -> pixels.toString() + " MP · local canvas" }
                    } else {
                        "Use 1–8192 pixels per side, up to 16 million pixels total."
                    },
                    color = NeoCanvasColors.muted,
                    fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { state.newDocument(w, h) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { state.newCanvasDialogVisible = false }) { Text("Cancel") }
        },
    )
}

@Composable
private fun CanvasOrientationChoice(
    label: String,
    portrait: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border = if (selected) NeoCanvasColors.accent else NeoCanvasColors.line
    val surface = if (selected) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome
    Column(
        modifier
            .height(96.dp)
            .background(surface, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .then(
                    if (portrait) Modifier.width(30.dp).height(44.dp)
                    else Modifier.width(44.dp).height(30.dp)
                )
                .background(Color(0xFFF6F3EC), RoundedCornerShape(3.dp))
                .border(2.dp, if (selected) NeoCanvasColors.accent else NeoCanvasColors.muted, RoundedCornerShape(3.dp)),
        )
        Text(
            if (selected) label + " ✓" else label,
            color = if (selected) NeoCanvasColors.accent else NeoCanvasColors.paper,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
