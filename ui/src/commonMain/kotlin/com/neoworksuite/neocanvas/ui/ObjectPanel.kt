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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val locked = state.activeObjectLocked

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

        if ((text != null || shape != null) && locked) {
            Box(
                Modifier.fillMaxWidth()
                    .background(NeoCanvasColors.panelRaised, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    "LOCKED · Unlock the layer or its group in Layers to edit this object.",
                    color = NeoCanvasColors.faint,
                    fontSize = 9.sp,
                )
            }
        }

        if (text != null) {
            OutlinedTextField(
                value = text.text,
                onValueChange = state::setActiveTextContent,
                label = { Text("Text") },
                minLines = 2,
                maxLines = 6,
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
            )
            ObjectSlider("Size", text.fontSize, 6f..256f, text.fontSize.toInt().toString() + " px", !locked, state::setActiveTextSize)
            Text("FONT", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .7.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("System", Modifier.weight(1f), text.fontFamily.equals("System", ignoreCase = true), !locked) {
                    state.setActiveTextFontFamily("System")
                }
                ObjectAction("Sans", Modifier.weight(1f), text.fontFamily.equals("Sans", ignoreCase = true), !locked) {
                    state.setActiveTextFontFamily("Sans")
                }
                ObjectAction("Serif", Modifier.weight(1f), text.fontFamily.equals("Serif", ignoreCase = true), !locked) {
                    state.setActiveTextFontFamily("Serif")
                }
                ObjectAction("Mono", Modifier.weight(1f), text.fontFamily.equals("Mono", ignoreCase = true), !locked) {
                    state.setActiveTextFontFamily("Mono")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Bold", Modifier.weight(1f), text.bold, !locked) {
                    state.setActiveTextBold(!text.bold)
                }
                ObjectAction("Italic", Modifier.weight(1f), text.italic, !locked) {
                    state.setActiveTextItalic(!text.italic)
                }
            }
            ObjectSlider(
                "Leading",
                text.lineSpacing,
                .7f..3f,
                ((text.lineSpacing * 100f).toInt() / 100f).toString() + "×",
                !locked,
                state::setActiveTextLineSpacing,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Left", Modifier.weight(1f), text.alignment == TextAlignment.Left, !locked) {
                    state.setActiveTextAlignment(TextAlignment.Left)
                }
                ObjectAction("Centre", Modifier.weight(1f), text.alignment == TextAlignment.Center, !locked) {
                    state.setActiveTextAlignment(TextAlignment.Center)
                }
                ObjectAction("Right", Modifier.weight(1f), text.alignment == TextAlignment.Right, !locked) {
                    state.setActiveTextAlignment(TextAlignment.Right)
                }
            }
            ObjectAction("Use Current Colour", Modifier.fillMaxWidth(), enabled = !locked) { state.useCurrentColourForActiveObject() }
        }

        if (shape != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Rectangle", Modifier.weight(1f), shape.kind == ShapeKind.Rectangle, !locked) {
                    state.setActiveShapeKind(ShapeKind.Rectangle)
                }
                ObjectAction("Ellipse", Modifier.weight(1f), shape.kind == ShapeKind.Ellipse, !locked) {
                    state.setActiveShapeKind(ShapeKind.Ellipse)
                }
                ObjectAction("Line", Modifier.weight(1f), shape.kind == ShapeKind.Line, !locked) {
                    state.setActiveShapeKind(ShapeKind.Line)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (shape.kind != ShapeKind.Line) {
                    ObjectAction("Fill Current", Modifier.weight(1f), enabled = !locked) { state.useCurrentColourForActiveObject() }
                }
                ObjectAction("Outline Current", Modifier.weight(1f), enabled = !locked) {
                    state.useCurrentColourForActiveObject(asStroke = true)
                }
            }
            if (shape.kind != ShapeKind.Line && shape.strokeArgb != null && shape.fillArgb != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ObjectAction("No Fill", Modifier.weight(1f), enabled = !locked) { state.removeActiveShapeFill() }
                    ObjectAction("No Outline", Modifier.weight(1f), enabled = !locked) { state.removeActiveShapeStroke() }
                }
            }
            if (shape.kind == ShapeKind.Rectangle) {
                ObjectSlider(
                    "Corners",
                    shape.cornerRadius,
                    0f..(minOf(kotlin.math.abs(shape.width), kotlin.math.abs(shape.height)) / 2f).coerceAtLeast(1f),
                    shape.cornerRadius.toInt().toString() + " px",
                    !locked,
                    state::setActiveShapeCornerRadius,
                )
            }
            if (shape.strokeArgb != null || shape.kind == ShapeKind.Line) {
                ObjectSlider(
                    "Stroke",
                    shape.strokeWidth.coerceAtLeast(1f),
                    1f..64f,
                    shape.strokeWidth.toInt().toString() + " px",
                    !locked,
                    state::setActiveShapeStrokeWidth,
                )
            }
        }

        if (text != null || shape != null) {
            Text("POSITION & GEOMETRY", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .7.sp)
            Text(
                "On canvas: drag to move · corner handles resize · top handle rotates",
                color = NeoCanvasColors.muted,
                fontSize = 9.sp,
            )
            Text("ALIGN TO CANVAS", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .7.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Left", Modifier.weight(1f), enabled = !locked) {
                    state.alignActiveObjectToCanvas(ObjectCanvasAlignment.Left)
                }
                ObjectAction("Centre", Modifier.weight(1f), enabled = !locked) {
                    state.alignActiveObjectToCanvas(ObjectCanvasAlignment.CenterHorizontal)
                }
                ObjectAction("Right", Modifier.weight(1f), enabled = !locked) {
                    state.alignActiveObjectToCanvas(ObjectCanvasAlignment.Right)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Top", Modifier.weight(1f), enabled = !locked) {
                    state.alignActiveObjectToCanvas(ObjectCanvasAlignment.Top)
                }
                ObjectAction("Middle", Modifier.weight(1f), enabled = !locked) {
                    state.alignActiveObjectToCanvas(ObjectCanvasAlignment.CenterVertical)
                }
                ObjectAction("Bottom", Modifier.weight(1f), enabled = !locked) {
                    state.alignActiveObjectToCanvas(ObjectCanvasAlignment.Bottom)
                }
            }
            ObjectAction("Centre on Canvas", Modifier.fillMaxWidth(), enabled = !locked) {
                state.alignActiveObjectToCanvas(ObjectCanvasAlignment.CenterBoth)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("←", Modifier.weight(1f), enabled = !locked) { state.moveActiveObject(-10f, 0f) }
                ObjectAction("↑", Modifier.weight(1f), enabled = !locked) { state.moveActiveObject(0f, -10f) }
                ObjectAction("↓", Modifier.weight(1f), enabled = !locked) { state.moveActiveObject(0f, 10f) }
                ObjectAction("→", Modifier.weight(1f), enabled = !locked) { state.moveActiveObject(10f, 0f) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ObjectAction("Smaller", Modifier.weight(1f), enabled = !locked) { state.scaleActiveObject(.9f) }
                ObjectAction("Larger", Modifier.weight(1f), enabled = !locked) { state.scaleActiveObject(1.1f) }
                ObjectAction("−15°", Modifier.weight(1f), enabled = !locked) { state.rotateActiveObject(-15f) }
                ObjectAction("+15°", Modifier.weight(1f), enabled = !locked) { state.rotateActiveObject(15f) }
            }
            Text(
                "Objects stay editable in NeoCanvas format v2. PNG and PSD export a flattened copy while the NeoCanvas source stays editable.",
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
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = NeoCanvasColors.faint, fontSize = 9.sp, modifier = Modifier.width(52.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            enabled = enabled,
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(NeoCanvasColors.panelRaised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) label + " ✓" else label,
            color = if (enabled) NeoCanvasColors.muted else NeoCanvasColors.faint,
            fontSize = 11.sp,
        )
    }
}
