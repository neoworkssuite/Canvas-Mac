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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.renderer.LiquifyMode

@Composable
fun LiquifyPanel(state: EditorState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    "LIQUIFY",
                    color = NeoCanvasColors.paper,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .9.sp,
                )
                Text("Spatial warp · active raster layer", color = NeoCanvasColors.faint, fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { state.hideInspector() }) {
                Text("Done", color = NeoCanvasColors.accent)
            }
        }

        Text("MODE", color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .7.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LiquifyModeButton("Push", LiquifyMode.Push, state, Modifier.weight(1f))
            LiquifyModeButton("Pinch", LiquifyMode.Pinch, state, Modifier.weight(1f))
            LiquifyModeButton("Expand", LiquifyMode.Expand, state, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            LiquifyModeButton("Twirl L", LiquifyMode.TwirlLeft, state, Modifier.weight(1f))
            LiquifyModeButton("Twirl R", LiquifyMode.TwirlRight, state, Modifier.weight(1f))
            LiquifyModeButton("Smooth", LiquifyMode.Smooth, state, Modifier.weight(1f))
        }
        LiquifyModeButton("Reconstruct", LiquifyMode.Reconstruct, state, Modifier.fillMaxWidth())

        LiquifySlider(
            label = "Size",
            value = state.liquifySize,
            valueRange = 8f..320f,
            display = state.liquifySize.toInt().toString() + " px",
            onValueChange = { state.liquifySize = it },
        )
        LiquifySlider(
            label = "Power",
            value = state.liquifyStrength,
            valueRange = .01f..1f,
            display = (state.liquifyStrength * 100f).toInt().toString() + "%",
            onValueChange = { state.liquifyStrength = it },
        )
        LiquifyActionButton(
            label = "Reset Liquify Session",
            enabled = state.hasLiquifyBaseline,
            onClick = { state.resetLiquifyToSessionStart() },
        )

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(NeoCanvasColors.chrome).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                when (state.liquifyMode) {
                    LiquifyMode.Push -> "PUSH · drag artwork in the stroke direction"
                    LiquifyMode.Pinch -> "PINCH · pull pixels inward beneath the brush"
                    LiquifyMode.Expand -> "EXPAND · push pixels outward beneath the brush"
                    LiquifyMode.TwirlLeft -> "TWIRL LEFT · rotate pixels anticlockwise under the brush"
                    LiquifyMode.TwirlRight -> "TWIRL RIGHT · rotate pixels clockwise under the brush"
                    LiquifyMode.Smooth -> "SMOOTH · soften local distortion and hard transitions"
                    LiquifyMode.Reconstruct -> "RECONSTRUCT · locally restore pixels toward the session start"
                },
                color = NeoCanvasColors.paper,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Use the left Size and Power rails. Apple Pencil pressure changes the live warp radius. " +
                    "Selections constrain Liquify and each completed gesture is one Undo step.",
                color = NeoCanvasColors.muted,
                fontSize = 9.sp,
            )
        }

        Text(
            "Liquify previews live while you drag. Lift Pencil to commit; Reconstruct restores locally, " +
                "Reset restores the session baseline, and Undo restores the previous raster state.",
            color = NeoCanvasColors.faint,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun LiquifyModeButton(
    label: String,
    mode: LiquifyMode,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val selected = state.liquifyMode == mode
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) NeoCanvasColors.accent else NeoCanvasColors.panelRaised)
            .clickable {
                state.liquifyMode = mode
                state.statusMessage = "Liquify " + mode.displayName.lowercase()
            }
            .padding(horizontal = 9.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (selected) label + " ✓" else label,
            color = if (selected) NeoCanvasColors.ink else NeoCanvasColors.muted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LiquifyActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(if (enabled) NeoCanvasColors.panelRaised else NeoCanvasColors.chrome)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) NeoCanvasColors.muted else NeoCanvasColors.disabled,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LiquifySlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = NeoCanvasColors.faint, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text(display, color = NeoCanvasColors.paper, fontSize = 10.sp)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = studioSliderColors(),
        )
    }
}
