package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.core.model.ShapeKind
import com.neoworksuite.neocanvas.core.model.TextAlignment

@Composable
fun ObjectPanel(state: EditorState, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val layer = state.activeObjectLayer
    val text = state.activeTextObject
    val shape = state.activeShapeObject

    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("OBJECT", color = NeoCanvasColors.paper, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = .9.sp)
                Text(
                    when {
                        text != null -> "EDITABLE TEXT · " + (layer?.name ?: "Text")
                        shape != null -> "EDITABLE " + shape.kind.name.uppercase() + " · " + (layer?.name ?: "Shape")
                        else -> "No editable object selected"
                    },
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done", color = NeoCanvasColors.accent) }
        }

        if (text != null) {
            OutlinedTextField(
                value = text.text,
                onValueChange = state::setActiveTextContent,
                label = { Text("Text") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            ObjectSlider("Size", text.fontSize, 6f..256f, text.fontSize.toInt().toString() + " px", state::setActiveTextSize)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Left", Modifier.weight(1f), text.alignment == TextAlignment.Left) {
                    state.setActiveTextAlignment(TextAlignment.Left)
                }
                ObjectAction("Centre", Modifier.weight(1f), text.alignment == TextAlignment.Center) {
                    state.setActiveTextAlignment(TextAlignment.Center)
                }
                ObjectAction("Right", Modifier.weight(1f), text.alignment == TextAlignment.Right) {
                    state.setActiveTextAlignment(TextAlignment.Right)
                }
            }
            ObjectAction("Use Current Colour", Modifier.fillMaxWidth()) { state.useCurrentColourForActiveObject() }
        }

        if (shape != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Rectangle", Modifier.weight(1f), shape.kind == ShapeKind.Rectangle) {
                    state.setActiveShapeKind(ShapeKind.Rectangle)
                }
                ObjectAction("Ellipse", Modifier.weight(1f), shape.kind == ShapeKind.Ellipse) {
                    state.setActiveShapeKind(ShapeKind.Ellipse)
                }
                ObjectAction("Line", Modifier.weight(1f), shape.kind == ShapeKind.Line) {
                    state.setActiveShapeKind(ShapeKind.Line)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (shape.kind != ShapeKind.Line) {
                    ObjectAction("Fill Current", Modifier.weight(1f)) { state.useCurrentColourForActiveObject() }
                }
                ObjectAction("Outline Current", Modifier.weight(1f)) {
                    state.useCurrentColourForActiveObject(asStroke = true)
                }
                if (shape.kind != ShapeKind.Line && shape.strokeArgb != null) {
                    ObjectAction("No Outline", Modifier.weight(1f)) { state.removeActiveShapeStroke() }
                }
            }
            if (shape.strokeArgb != null || shape.kind == ShapeKind.Line) {
                ObjectSlider(
                    "Stroke",
                    shape.strokeWidth.coerceAtLeast(1f),
                    1f..64f,
                    shape.strokeWidth.toInt().toString() + " px",
                    state::setActiveShapeStrokeWidth,
                )
            }
        }

        if (text != null || shape != null) {
            Text("POSITION & GEOMETRY", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .7.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("←", Modifier.weight(1f)) { state.moveActiveObject(-10f, 0f) }
                ObjectAction("↑", Modifier.weight(1f)) { state.moveActiveObject(0f, -10f) }
                ObjectAction("↓", Modifier.weight(1f)) { state.moveActiveObject(0f, 10f) }
                ObjectAction("→", Modifier.weight(1f)) { state.moveActiveObject(10f, 0f) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Smaller", Modifier.weight(1f)) { state.scaleActiveObject(.9f) }
                ObjectAction("Larger", Modifier.weight(1f)) { state.scaleActiveObject(1.1f) }
                ObjectAction("−15°", Modifier.weight(1f)) { state.rotateActiveObject(-15f) }
                ObjectAction("+15°", Modifier.weight(1f)) { state.rotateActiveObject(15f) }
            }
            Text(
                "Objects stay editable in NeoCanvas format v2. Rasterize only when a target format requires it.",
                color = NeoCanvasColors.faint,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun ObjectSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NeoCanvasColors.faint, fontSize = 9.sp, modifier = Modifier.width(52.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(32.dp),
            colors = studioSliderColors(),
        )
        Text(display, color = NeoCanvasColors.muted, fontSize = 9.sp, modifier = Modifier.width(56.dp))
    }
}

@Composable
private fun ObjectAction(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    InspectorAction(if (selected) label + " ✓" else label, modifier, onClick)
}
