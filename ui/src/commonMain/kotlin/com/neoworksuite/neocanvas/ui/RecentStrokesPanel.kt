package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RecentStrokesPanel(
    state: EditorState,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val selected = state.selectedEditableStroke

    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "RECENT STROKES",
                    color = NeoCanvasColors.paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .8.sp,
                )
                Text(
                    "Re-edit raster strokes and replay later marks",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", color = NeoCanvasColors.accent) }
        }

        if (selected != null) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(NeoCanvasColors.chrome)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    if (selected.isEraser) "ERASER" else selected.brushName.uppercase(),
                    color = NeoCanvasColors.paper,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "SIZE " + selected.size.toInt() + " px  ·  OPACITY " +
                        (selected.opacity * 100f).toInt() + "%",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    StrokeAction("Size −", Modifier.weight(1f)) { state.scaleEditableStroke(selected.id, .9f) }
                    StrokeAction("Size +", Modifier.weight(1f)) { state.scaleEditableStroke(selected.id, 1.1f) }
                    StrokeAction("Opacity −", Modifier.weight(1f)) { state.adjustEditableStrokeOpacity(selected.id, -.1f) }
                    StrokeAction("Opacity +", Modifier.weight(1f)) { state.adjustEditableStrokeOpacity(selected.id, .1f) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    StrokeAction("Current Colour", Modifier.weight(1f), enabled = !selected.isEraser) {
                        state.useCurrentColourForEditableStroke(selected.id)
                    }
                    StrokeAction("Current Brush", Modifier.weight(1f), enabled = !selected.isEraser) {
                        state.useCurrentBrushForEditableStroke(selected.id)
                    }
                    StrokeAction("Delete", Modifier.weight(1f)) { state.deleteEditableStroke(selected.id) }
                }
            }
        }

        Text(
            state.recentEditableStrokes.size.toString() + " EDITABLE STROKE" +
                if (state.recentEditableStrokes.size == 1) "" else "S",
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
            letterSpacing = .7.sp,
        )

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(state.recentEditableStrokes.asReversed(), key = { it.id }) { stroke ->
                val active = selected?.id == stroke.id
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
                        .clickable { state.selectEditableStroke(stroke.id) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (stroke.isEraser) "ERASER" else stroke.brushName,
                            color = if (active) NeoCanvasColors.accent else NeoCanvasColors.paper,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            stroke.size.toInt().toString() + " px · " +
                                (stroke.opacity * 100f).toInt() + "% · " +
                                stroke.pointCount + " points",
                            color = NeoCanvasColors.faint,
                            fontSize = 8.sp,
                        )
                    }
                    Text(
                        if (active) "EDITING" else "EDIT",
                        color = if (active) NeoCanvasColors.accent else NeoCanvasColors.muted,
                        fontSize = 8.sp,
                    )
                }
            }
        }

        Text(
            "The chain closes after Undo/Redo or another non-stroke edit. Smudge is not replayed in V1.",
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun StrokeAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(
            label,
            color = if (enabled) NeoCanvasColors.accent else NeoCanvasColors.faint,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}
