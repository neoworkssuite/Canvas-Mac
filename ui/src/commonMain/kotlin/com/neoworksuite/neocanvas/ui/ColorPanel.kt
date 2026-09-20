package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorPanel(state: EditorState, modifier: Modifier = Modifier) {
    var hsv by remember { mutableStateOf(colorHsv(state.color)) }
    var hex by remember(state.color) { mutableStateOf(colorHex(state.color)) }
    LaunchedEffect(state.color) {
        val next = colorHsv(state.color)
        hsv = next.copy(hue = if (next.saturation == 0f) hsv.hue else next.hue,
            saturation = if (next.value == 0f) hsv.saturation else next.saturation)
    }
    fun choose(next: Hsv) {
        hsv = next
        state.color = Color.hsv(next.hue, next.saturation, next.value)
    }
    val swatches = listOf(
        Color(0xFF11151C), Color(0xFF334E68), Color(0xFF336B87), Color(0xFF63C5B5),
        Color(0xFFF2A65A), Color(0xFFE76F51), Color(0xFFC85570), Color(0xFFEEE9DF),
    )
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InspectorHeading("COLOUR", "Local palette")
        Text("ACTIVE ${colorHex(state.color)}", color = NeoCanvasColors.paper, fontSize = 16.sp)
        TextButton(onClick = { state.addPaletteColor() }) { Text("Save active colour to palette", color = NeoCanvasColors.accent) }
        if (state.palette.isEmpty()) Text("Your saved colours will appear here.", color = NeoCanvasColors.muted, fontSize = 11.sp)
        state.palette.forEach { saved ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ColourSwatch(parseColorHex(saved)!!, saved == colorHex(state.color)) { state.color = parseColorHex(saved)!! }
                TextButton(onClick = { state.color = parseColorHex(saved)!! }) { Text(saved, color = NeoCanvasColors.paper) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.removePaletteColor(saved) }) { Text("Remove", color = NeoCanvasColors.muted) }
            }
        }
        ColourWheel(hsv, Modifier.size(190.dp).align(Alignment.CenterHorizontally)) { choose(it) }
        Text(
            "H ${hsv.hue.toInt()}°   S ${(hsv.saturation * 100).toInt()}%   B ${(hsv.value * 100).toInt()}%",
            color = NeoCanvasColors.muted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        OutlinedTextField(value = hex, onValueChange = { hex = it }, singleLine = true,
            label = { Text("Hex colour (#RRGGBB)") }, isError = parseColorHex(hex) == null,
            modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { parseColorHex(hex)?.let { state.color = it } }, enabled = parseColorHex(hex) != null) {
            Text("Apply hex colour", color = NeoCanvasColors.accent)
        }
        Text("CURATED SWATCHES", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
        swatches.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { swatch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColourSwatch(swatch, state.color == swatch) { state.color = swatch }
                        TextButton(onClick = { state.color = swatch }) { Text(colorHex(swatch), color = NeoCanvasColors.muted, fontSize = 10.sp) }
                    }
                }
            }
        }
        Text("OPACITY", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp, modifier = Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(state.brushOpacity, { state.brushOpacity = it }, valueRange = .05f..1f, modifier = Modifier.weight(1f), colors = studioSliderColors())
            Text("${(state.brushOpacity * 100).toInt()}%", color = NeoCanvasColors.paper, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ColourSwatch(color: Color, selected: Boolean, onClick: () -> Unit) = Box(
    Modifier.size(31.dp).clip(CircleShape).background(color).clickable(onClick = onClick)
        .semantics { contentDescription = "Set brush colour ${colorHex(color)}" },
    contentAlignment = Alignment.Center,
) {
    if (selected) Canvas(Modifier.size(17.dp)) { drawCircle(NeoCanvasColors.paper, size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)) }
}

@Composable
private fun ColourWheel(hsv: Hsv, modifier: Modifier = Modifier, onHsv: (Hsv) -> Unit) {
    val currentOnHsv by rememberUpdatedState(onHsv)
    Canvas(
        modifier.semantics {
            contentDescription = "Colour wheel. Outer ring selects hue; inner disc selects saturation and brightness"
        }.pointerInput(hsv) {
            fun select(position: Offset) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val dx = position.x - center.x
                val dy = position.y - center.y
                val radius = minOf(size.width, size.height).toFloat() / 2f
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                if (distance >= radius * .67f) {
                    val hue = ((kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI.toFloat()) + 360f) % 360f
                    // When starting from black/grey, choosing a hue should immediately produce a visible colour.
                    val saturation = if (hsv.saturation < .02f) 1f else hsv.saturation
                    val value = if (hsv.value < .02f) 1f else hsv.value
                    currentOnHsv(Hsv(hue, saturation, value))
                } else {
                    val extent = radius * .46f
                    val saturation = ((dx / (extent * 2f)) + .5f).coerceIn(0f, 1f)
                    val value = (1f - ((dy / (extent * 2f)) + .5f)).coerceIn(0f, 1f)
                    currentOnHsv(hsv.copy(saturation = saturation, value = value))
                }
            }
            detectDragGestures(
                onDragStart = { select(it) },
                onDrag = { change, _ -> change.consume(); select(change.position) },
            )
        }.pointerInput(hsv) {
            detectTapGestures { selectPosition ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val dx = selectPosition.x - center.x
                val dy = selectPosition.y - center.y
                val radius = minOf(size.width, size.height).toFloat() / 2f
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance >= radius * .67f) {
                    val hue = ((kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI.toFloat()) + 360f) % 360f
                    currentOnHsv(Hsv(
                        hue,
                        if (hsv.saturation < .02f) 1f else hsv.saturation,
                        if (hsv.value < .02f) 1f else hsv.value,
                    ))
                } else {
                    val extent = radius * .46f
                    currentOnHsv(hsv.copy(
                        saturation = ((dx / (extent * 2f)) + .5f).coerceIn(0f, 1f),
                        value = (1f - ((dy / (extent * 2f)) + .5f)).coerceIn(0f, 1f),
                    ))
                }
            }
        },
    ) {
        val radius = minOf(size.width, size.height).toFloat() / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Hue ring.
        val hueColors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        drawCircle(Brush.sweepGradient(hueColors), radius * .96f)
        drawCircle(NeoCanvasColors.panel, radius * .67f)

        // Saturation/value disc for the currently selected hue.
        val discRadius = radius * .62f
        drawCircle(
            Brush.horizontalGradient(
                listOf(Color.White, Color.hsv(hsv.hue, 1f, 1f)),
                startX = center.x - discRadius,
                endX = center.x + discRadius,
            ),
            discRadius,
            center,
        )
        drawCircle(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black),
                startY = center.y - discRadius,
                endY = center.y + discRadius,
            ),
            discRadius,
            center,
        )

        // Hue marker.
        val hueAngle = hsv.hue * kotlin.math.PI.toFloat() / 180f
        val hueMarker = Offset(
            center.x + kotlin.math.cos(hueAngle) * radius * .815f,
            center.y + kotlin.math.sin(hueAngle) * radius * .815f,
        )
        drawCircle(Color.Black, 6.dp.toPx(), hueMarker)
        drawCircle(Color.White, 4.dp.toPx(), hueMarker)

        // Saturation/value marker. Keep it in the square safely inscribed inside the disc.
        val extent = radius * .46f
        val svMarker = Offset(
            center.x + (hsv.saturation - .5f) * extent * 2f,
            center.y + ((1f - hsv.value) - .5f) * extent * 2f,
        )
        drawCircle(Color.Black, 7.dp.toPx(), svMarker)
        drawCircle(Color.White, 4.5.dp.toPx(), svMarker)
        drawCircle(
            Color.hsv(hsv.hue, hsv.saturation, hsv.value),
            3.dp.toPx(),
            svMarker,
        )
    }
}

