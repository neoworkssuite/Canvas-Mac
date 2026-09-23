package com.neoworksuite.neocanvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal data class StarterColourPalette(val name: String, val colours: List<String>)

internal val paletteSwatchCornerRadius = 6.dp

internal val starterColourPalettes = listOf(
    StarterColourPalette("Essential", listOf("#111827", "#FFFFFF", "#EF4444", "#F59E0B", "#FDE047", "#22C55E", "#3B82F6", "#8B5CF6")),
    StarterColourPalette("Portrait", listOf("#3B2118", "#6B3F2A", "#9A6248", "#C98D6B", "#E8B99A", "#F6D7C3", "#A14F5A", "#5E2D38")),
    StarterColourPalette("Landscape", listOf("#162A46", "#315A7D", "#79A9C2", "#D9E8DC", "#264D36", "#56805C", "#B4A46A", "#76533A")),
    StarterColourPalette("Neo Neon", listOf("#07111E", "#00E5FF", "#2563FF", "#7C3AED", "#D946EF", "#FF2D8D", "#FF8A00", "#D9FF00")),
)

private enum class ColourStudioMode(val label: String) {
    Disc("DISC"),
    Classic("CLASSIC"),
    Harmony("HARMONY"),
    Value("VALUE"),
    Palettes("PALETTES"),
}

private enum class ValueMode { RGB, HSB, Hex }

@Composable
fun ColorPanel(state: EditorState, modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(ColourStudioMode.Disc) }
    var hsv by remember { mutableStateOf(colorHsv(state.color)) }

    LaunchedEffect(state.color) {
        val next = colorHsv(state.color)
        hsv = next.copy(
            hue = if (next.saturation == 0f) hsv.hue else next.hue,
            saturation = if (next.value == 0f) hsv.saturation else next.saturation,
        )
    }

    fun choose(next: Hsv) {
        hsv = next
        state.color = Color.hsv(next.hue, next.saturation, next.value)
    }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("COLOUR STUDIO", color = NeoCanvasColors.paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Primary, harmony and palettes", color = NeoCanvasColors.faint, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            ColourPanelMenu(state)
        }

        PrimarySecondaryHeader(state)

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(NeoCanvasColors.chrome)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ColourStudioMode.entries.forEach { option ->
                Box(
                    modifier = Modifier.weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (mode == option) NeoCanvasColors.accent else Color.Transparent)
                        .clickable { mode = option },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        color = if (mode == option) NeoCanvasColors.ink else NeoCanvasColors.muted,
                        fontSize = 8.sp,
                        fontWeight = if (mode == option) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = .35.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        when (mode) {
            ColourStudioMode.Disc -> DiscMode(hsv, ::choose)
            ColourStudioMode.Classic -> ClassicMode(hsv, ::choose)
            ColourStudioMode.Harmony -> HarmonyMode(hsv, ::choose)
            ColourStudioMode.Value -> ValueModePanel(state, hsv, ::choose)
            ColourStudioMode.Palettes -> PalettesMode(state)
        }

        if (mode != ColourStudioMode.Palettes) {
            RecentAndPaletteStrip(state)
        }
    }
}

@Composable
private fun PrimarySecondaryHeader(state: EditorState) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NeoCanvasColors.panelRaised)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColourRole("PRIMARY", state.color, true) { }
        Text(
            "⇄",
            color = NeoCanvasColors.accent,
            fontSize = 19.sp,
            modifier = Modifier.padding(horizontal = 10.dp).clickable { state.swapPrimarySecondaryColors() }
                .semantics { contentDescription = "Swap primary and secondary colours" },
        )
        ColourRole("SECONDARY", state.secondaryColor, false) { state.swapPrimarySecondaryColors() }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("PREVIOUS", color = NeoCanvasColors.faint, fontSize = 8.sp, letterSpacing = .7.sp)
            ColourDot(state.previousColor, 24.dp, false) { state.usePreviousColor() }
        }
    }
}

@Composable
private fun ColourRole(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
        ColourDot(color, 30.dp, active, onClick)
        Column(Modifier.padding(start = 6.dp)) {
            Text(label, color = if (active) NeoCanvasColors.accent else NeoCanvasColors.faint, fontSize = 8.sp)
            Text(colorHex(color), color = NeoCanvasColors.paper, fontSize = 9.sp)
        }
    }
}

@Composable
private fun DiscMode(hsv: Hsv, choose: (Hsv) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ColourWheel(hsv, Modifier.size(210.dp), choose)
        Text(
            "H " + hsv.hue.toInt() + "°   S " + (hsv.saturation * 100).toInt() + "%   B " + (hsv.value * 100).toInt() + "%",
            color = NeoCanvasColors.muted,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ClassicMode(hsv: Hsv, choose: (Hsv) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ClassicPad(hsv, Modifier.fillMaxWidth().height(180.dp), choose)
        ColourValueSlider(
            "Hue",
            hsv.hue / 360f,
            Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
        ) { choose(hsv.copy(hue = it * 360f)) }
        ColourValueSlider(
            "Saturation",
            hsv.saturation,
            Brush.horizontalGradient(listOf(Color.hsv(hsv.hue, 0f, hsv.value), Color.hsv(hsv.hue, 1f, hsv.value))),
        ) { choose(hsv.copy(saturation = it)) }
        ColourValueSlider(
            "Brightness",
            hsv.value,
            Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsv.hue, hsv.saturation, 1f))),
        ) { choose(hsv.copy(value = it)) }
    }
}

@Composable
private fun HarmonyMode(hsv: Hsv, choose: (Hsv) -> Unit) {
    var harmony by remember { mutableStateOf(ColourHarmony.Complementary) }
    var expanded by remember { mutableStateOf(false) }
    val colours = harmonyHues(hsv.hue, harmony).map { Color.hsv(it, hsv.saturation.coerceAtLeast(.55f), hsv.value.coerceAtLeast(.65f)) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Box {
            Text(
                harmony.label + "  ▾",
                color = NeoCanvasColors.paper,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(NeoCanvasColors.panelRaised)
                    .clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            DropdownMenu(expanded, { expanded = false }, containerColor = NeoCanvasColors.panelRaised) {
                ColourHarmony.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option.label, color = if (option == harmony) NeoCanvasColors.accent else NeoCanvasColors.paper)
                        },
                        trailingIcon = { if (option == harmony) Text("✓", color = NeoCanvasColors.accent) },
                        onClick = { harmony = option; expanded = false },
                    )
                }
            }
        }

        ColourWheel(hsv, Modifier.size(184.dp), choose)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colours.forEach { colour ->
                ColourDot(colour, 34.dp, colorHex(colour) == colorHex(Color.hsv(hsv.hue, hsv.saturation, hsv.value))) {
                    val picked = colorHsv(colour)
                    choose(picked)
                }
            }
        }
        Text("Tap a harmony colour to make it primary", color = NeoCanvasColors.faint, fontSize = 9.sp)
    }
}

@Composable
private fun ValueModePanel(state: EditorState, hsv: Hsv, choose: (Hsv) -> Unit) {
    var valueMode by remember { mutableStateOf(ValueMode.RGB) }
    var expanded by remember { mutableStateOf(false) }
    var hex by remember(state.color) { mutableStateOf(colorHex(state.color)) }

    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Text(
                valueMode.name + "  ▾",
                color = NeoCanvasColors.paper,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(NeoCanvasColors.panelRaised)
                    .clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            DropdownMenu(expanded, { expanded = false }, containerColor = NeoCanvasColors.panelRaised) {
                ValueMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name, color = if (option == valueMode) NeoCanvasColors.accent else NeoCanvasColors.paper) },
                        onClick = { valueMode = option; expanded = false },
                    )
                }
            }
        }

        when (valueMode) {
            ValueMode.RGB -> {
                val r = state.color.red
                val g = state.color.green
                val b = state.color.blue
                ColourValueSlider("Red", r, Brush.horizontalGradient(listOf(Color(0f, g, b), Color(1f, g, b)))) {
                    state.color = Color(it, g, b)
                }
                ColourValueSlider("Green", g, Brush.horizontalGradient(listOf(Color(r, 0f, b), Color(r, 1f, b)))) {
                    state.color = Color(r, it, b)
                }
                ColourValueSlider("Blue", b, Brush.horizontalGradient(listOf(Color(r, g, 0f), Color(r, g, 1f)))) {
                    state.color = Color(r, g, it)
                }
                Text(
                    "R " + (r * 255).roundToInt() + "   G " + (g * 255).roundToInt() + "   B " + (b * 255).roundToInt(),
                    color = NeoCanvasColors.muted,
                    fontSize = 10.sp,
                )
            }
            ValueMode.HSB -> {
                ColourValueSlider(
                    "Hue",
                    hsv.hue / 360f,
                    Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                ) { choose(hsv.copy(hue = it * 360f)) }
                ColourValueSlider(
                    "Saturation",
                    hsv.saturation,
                    Brush.horizontalGradient(listOf(Color.hsv(hsv.hue, 0f, hsv.value), Color.hsv(hsv.hue, 1f, hsv.value))),
                ) { choose(hsv.copy(saturation = it)) }
                ColourValueSlider(
                    "Brightness",
                    hsv.value,
                    Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsv.hue, hsv.saturation, 1f))),
                ) { choose(hsv.copy(value = it)) }
            }
            ValueMode.Hex -> {
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        parseColorHex(it)?.let { parsed -> state.color = parsed }
                    },
                    singleLine = true,
                    label = { Text("Hex #RRGGBB") },
                    isError = parseColorHex(hex) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Valid hex values apply instantly", color = NeoCanvasColors.faint, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PalettesMode(state: EditorState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ACTIVE PALETTE", color = NeoCanvasColors.paper, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(state.palette.size.toString() + " saved colours", color = NeoCanvasColors.faint, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            PaletteMenu(state)
        }

        Text("STARTER PALETTES", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
        starterColourPalettes.forEach { palette ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(palette.name.uppercase(), color = NeoCanvasColors.muted, fontSize = 8.sp, letterSpacing = .5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    palette.colours.forEach { hex ->
                        val colour = parseColorHex(hex) ?: Color.Transparent
                        PaletteSwatch(colour, 32.dp, hex == colorHex(state.color)) { state.color = colour }
                    }
                }
            }
        }

        Text("SAVED COLOURS", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
        if (state.palette.isEmpty()) {
            Text(
                "Save the current colour to start your local palette.",
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            state.palette.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { hex ->
                        val colour = parseColorHex(hex) ?: Color.Transparent
                        PaletteSwatch(colour, 36.dp, hex == colorHex(state.color)) { state.color = colour }
                    }
                }
            }
        }

        Text("RECENT", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
        RecentRow(state, 34.dp)
    }
}

@Composable
private fun RecentAndPaletteStrip(state: EditorState) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NeoCanvasColors.panelRaised)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("RECENT", color = NeoCanvasColors.faint, fontSize = 8.sp, letterSpacing = .7.sp)
            Spacer(Modifier.weight(1f))
            if (state.recentColors.isNotEmpty()) {
                Text("Clear", color = NeoCanvasColors.muted, fontSize = 9.sp, modifier = Modifier.clickable { state.clearRecentColors() })
            }
        }
        RecentRow(state, 26.dp)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIVE PALETTE", color = NeoCanvasColors.faint, fontSize = 8.sp, letterSpacing = .7.sp)
            Spacer(Modifier.weight(1f))
            Text("+ Add", color = NeoCanvasColors.accent, fontSize = 9.sp, modifier = Modifier.clickable { state.addPaletteColor() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            state.palette.take(8).forEach { hex ->
                val colour = parseColorHex(hex) ?: Color.Transparent
                PaletteSwatch(colour, 25.dp, hex == colorHex(state.color)) { state.color = colour }
            }
        }
    }
}

@Composable
private fun RecentRow(state: EditorState, size: androidx.compose.ui.unit.Dp) {
    if (state.recentColors.isEmpty()) {
        Text("No recent colours yet", color = NeoCanvasColors.faint, fontSize = 9.sp)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        state.recentColors.take(10).forEach { hex ->
            val colour = parseColorHex(hex) ?: Color.Transparent
            PaletteSwatch(colour, size, hex == colorHex(state.color)) { state.color = colour }
        }
    }
}

@Composable
private fun ColourPanelMenu(state: EditorState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            "•••",
            color = NeoCanvasColors.muted,
            fontSize = 18.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = true }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
        DropdownMenu(expanded, { expanded = false }, containerColor = NeoCanvasColors.panelRaised) {
            DropdownMenuItem(
                text = { Text("Set secondary from primary", color = NeoCanvasColors.paper) },
                onClick = { expanded = false; state.setSecondaryFromPrimary() },
            )
            DropdownMenuItem(
                text = { Text("Swap primary / secondary", color = NeoCanvasColors.paper) },
                onClick = { expanded = false; state.swapPrimarySecondaryColors() },
            )
            DropdownMenuItem(
                text = { Text("Add primary to palette", color = NeoCanvasColors.paper) },
                onClick = { expanded = false; state.addPaletteColor() },
            )
            DropdownMenuItem(
                text = { Text("Clear recent colours", color = NeoCanvasColors.paper) },
                onClick = { expanded = false; state.clearRecentColors() },
            )
        }
    }
}

@Composable
private fun PaletteMenu(state: EditorState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            "•••",
            color = NeoCanvasColors.muted,
            fontSize = 18.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = true }
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
        DropdownMenu(expanded, { expanded = false }, containerColor = NeoCanvasColors.panelRaised) {
            DropdownMenuItem(
                text = { Text("Add current colour", color = NeoCanvasColors.paper) },
                onClick = { expanded = false; state.addPaletteColor() },
            )
            val activeHex = colorHex(state.color)
            if (activeHex in state.palette) {
                DropdownMenuItem(
                    text = { Text("Remove current swatch", color = Color(0xFFFF8A8A)) },
                    onClick = { expanded = false; state.removePaletteColor(activeHex) },
                )
            }
        }
    }
}

@Composable
private fun ColourDot(color: Color, size: androidx.compose.ui.unit.Dp, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick)
            .semantics { contentDescription = "Use colour " + colorHex(color) },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Canvas(Modifier.size(size * .62f)) {
                drawCircle(NeoCanvasColors.paper, style = Stroke(1.6.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PaletteSwatch(color: Color, size: androidx.compose.ui.unit.Dp, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(paletteSwatchCornerRadius)).background(color).clickable(onClick = onClick)
            .semantics { contentDescription = "Use palette colour " + colorHex(color) },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Canvas(Modifier.size(size * .68f)) {
                drawRoundRect(
                    color = NeoCanvasColors.paper,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = Stroke(1.6.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun ClassicPad(hsv: Hsv, modifier: Modifier, onHsv: (Hsv) -> Unit) {
    val currentOnHsv by rememberUpdatedState(onHsv)
    Canvas(
        modifier.clip(RoundedCornerShape(10.dp))
            .pointerInput(hsv.hue) {
                fun choose(position: Offset) {
                    currentOnHsv(
                        hsv.copy(
                            saturation = (position.x / size.width).coerceIn(0f, 1f),
                            value = (1f - position.y / size.height).coerceIn(0f, 1f),
                        ),
                    )
                }
                detectTapGestures { choose(it) }
            }
            .pointerInput(hsv.hue) {
                fun choose(position: Offset) {
                    currentOnHsv(
                        hsv.copy(
                            saturation = (position.x / size.width).coerceIn(0f, 1f),
                            value = (1f - position.y / size.height).coerceIn(0f, 1f),
                        ),
                    )
                }
                detectDragGestures(
                    onDragStart = { choose(it) },
                    onDrag = { change, _ -> choose(change.position); change.consume() },
                )
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val marker = Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height)
        drawCircle(Color.Black, 8.dp.toPx(), marker)
        drawCircle(Color.White, 5.dp.toPx(), marker)
    }
}

@Composable
private fun ColourValueSlider(label: String, value: Float, brush: Brush, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = NeoCanvasColors.muted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text((value.coerceIn(0f, 1f) * 100).roundToInt().toString(), color = NeoCanvasColors.paper, fontSize = 9.sp)
        }
        Canvas(
            Modifier.fillMaxWidth().height(24.dp)
                .pointerInput(label) {
                    fun set(position: Offset) { latest((position.x / size.width).coerceIn(0f, 1f)) }
                    detectTapGestures { set(it) }
                }
                .pointerInput(label) {
                    fun set(position: Offset) { latest((position.x / size.width).coerceIn(0f, 1f)) }
                    detectDragGestures(
                        onDragStart = { set(it) },
                        onDrag = { change, _ -> set(change.position); change.consume() },
                    )
                },
        ) {
            val y = size.height / 2f
            drawLine(brush, Offset(6.dp.toPx(), y), Offset(size.width - 6.dp.toPx(), y), 8.dp.toPx(), StrokeCap.Round)
            val x = 6.dp.toPx() + (size.width - 12.dp.toPx()) * value.coerceIn(0f, 1f)
            drawCircle(Color.Black, 7.dp.toPx(), Offset(x, y))
            drawCircle(Color.White, 4.5.dp.toPx(), Offset(x, y))
        }
    }
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
                    currentOnHsv(
                        Hsv(
                            hue,
                            if (hsv.saturation < .02f) 1f else hsv.saturation,
                            if (hsv.value < .02f) 1f else hsv.value,
                        ),
                    )
                } else {
                    val extent = radius * .46f
                    currentOnHsv(
                        hsv.copy(
                            saturation = ((dx / (extent * 2f)) + .5f).coerceIn(0f, 1f),
                            value = (1f - ((dy / (extent * 2f)) + .5f)).coerceIn(0f, 1f),
                        ),
                    )
                }
            }
            detectTapGestures { select(it) }
        }.pointerInput(hsv) {
            fun select(position: Offset) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val dx = position.x - center.x
                val dy = position.y - center.y
                val radius = minOf(size.width, size.height).toFloat() / 2f
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance >= radius * .67f) {
                    val hue = ((kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI.toFloat()) + 360f) % 360f
                    currentOnHsv(Hsv(hue, hsv.saturation.coerceAtLeast(.02f), hsv.value.coerceAtLeast(.02f)))
                } else {
                    val extent = radius * .46f
                    currentOnHsv(
                        hsv.copy(
                            saturation = ((dx / (extent * 2f)) + .5f).coerceIn(0f, 1f),
                            value = (1f - ((dy / (extent * 2f)) + .5f)).coerceIn(0f, 1f),
                        ),
                    )
                }
            }
            detectDragGestures(
                onDragStart = { select(it) },
                onDrag = { change, _ -> select(change.position); change.consume() },
            )
        },
    ) {
        val radius = minOf(size.width, size.height).toFloat() / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val hues = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        drawCircle(Brush.sweepGradient(hues), radius * .96f)
        drawCircle(NeoCanvasColors.panel, radius * .67f)
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
        val hueAngle = hsv.hue * kotlin.math.PI.toFloat() / 180f
        val hueMarker = Offset(
            center.x + kotlin.math.cos(hueAngle) * radius * .815f,
            center.y + kotlin.math.sin(hueAngle) * radius * .815f,
        )
        drawCircle(Color.Black, 6.dp.toPx(), hueMarker)
        drawCircle(Color.White, 4.dp.toPx(), hueMarker)
        val extent = radius * .46f
        val svMarker = Offset(
            center.x + (hsv.saturation - .5f) * extent * 2f,
            center.y + ((1f - hsv.value) - .5f) * extent * 2f,
        )
        drawCircle(Color.Black, 7.dp.toPx(), svMarker)
        drawCircle(Color.White, 4.5.dp.toPx(), svMarker)
        drawCircle(Color.hsv(hsv.hue, hsv.saturation, hsv.value), 3.dp.toPx(), svMarker)
    }
}
