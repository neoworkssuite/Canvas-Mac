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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.renderer.RasterEffectSettings
import com.neoworksuite.neocanvas.renderer.RasterEffectType

@Composable
fun EffectsPanel(state: EditorState, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf(RasterEffectType.Blur) }
    var amount by remember { mutableFloatStateOf(.5f) }
    var secondary by remember { mutableFloatStateOf(0f) }
    var tertiary by remember { mutableFloatStateOf(0f) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InspectorHeading("FX", "Active layer")

        Text(
            "Effects are applied to the active layer and can be undone.",
            color = NeoCanvasColors.muted,
            fontSize = 11.sp,
        )

        EffectGrid(
            selected = selected,
            onSelect = {
                selected = it
                amount = when (it) {
                    RasterEffectType.HueSaturation, RasterEffectType.ColourBalance -> 0f
                    else -> .5f
                }
                secondary = 0f
                tertiary = 0f
            },
        )

        when (selected) {
            RasterEffectType.Blur -> EffectSlider("Blur amount", amount, 0f..1f) { amount = it }
            RasterEffectType.MotionBlur -> EffectSlider("Motion distance", amount, 0f..1f) { amount = it }
            RasterEffectType.Curves -> EffectSlider("Contrast curve", amount, 0f..1f) { amount = it }
            RasterEffectType.HueSaturation -> {
                EffectSlider("Saturation", amount, -1f..1f) { amount = it }
                EffectSlider("Hue shift", secondary, -1f..1f) { secondary = it }
                EffectSlider("Brightness", tertiary, -1f..1f) { tertiary = it }
            }
            RasterEffectType.ColourBalance -> {
                EffectSlider("Red / Cyan", amount, -1f..1f) { amount = it }
                EffectSlider("Green / Magenta", secondary, -1f..1f) { secondary = it }
                EffectSlider("Blue / Yellow", tertiary, -1f..1f) { tertiary = it }
            }
            RasterEffectType.GradientMap -> Text(
                "Maps shadows to black and highlights to the current NeoCanvas colour.",
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
            )
            RasterEffectType.Grayscale, RasterEffectType.Invert -> Unit
        }

        Button(
            onClick = {
                state.applyEffect(
                    selected,
                    RasterEffectSettings(amount = amount, secondary = secondary, tertiary = tertiary),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Apply ${effectName(selected)}")
        }

        Text(
            "More FX can be added here later without changing the editor layout.",
            color = NeoCanvasColors.faint,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun EffectGrid(
    selected: RasterEffectType,
    onSelect: (RasterEffectType) -> Unit,
) {
    val effects = listOf(
        RasterEffectType.Blur,
        RasterEffectType.MotionBlur,
        RasterEffectType.HueSaturation,
        RasterEffectType.ColourBalance,
        RasterEffectType.Curves,
        RasterEffectType.GradientMap,
        RasterEffectType.Grayscale,
        RasterEffectType.Invert,
    )
    effects.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { effect ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (selected == effect) NeoCanvasColors.accent else NeoCanvasColors.panelRaised)
                        .clickable { onSelect(effect) }
                        .padding(11.dp),
                ) {
                    Text(
                        effectSymbol(effect),
                        color = if (selected == effect) NeoCanvasColors.ink else NeoCanvasColors.accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        effectName(effect),
                        color = if (selected == effect) NeoCanvasColors.ink else NeoCanvasColors.paper,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = NeoCanvasColors.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                "${(value * 100).toInt()}%",
                color = NeoCanvasColors.paper,
                fontSize = 11.sp,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = studioSliderColors())
    }
}

private fun effectName(type: RasterEffectType): String = when (type) {
    RasterEffectType.Blur -> "Blur"
    RasterEffectType.MotionBlur -> "Motion Blur"
    RasterEffectType.HueSaturation -> "Hue / Saturation"
    RasterEffectType.ColourBalance -> "Colour Balance"
    RasterEffectType.Curves -> "Curves"
    RasterEffectType.GradientMap -> "Gradient Map"
    RasterEffectType.Grayscale -> "Grayscale"
    RasterEffectType.Invert -> "Invert"
}

private fun effectSymbol(type: RasterEffectType): String = when (type) {
    RasterEffectType.Blur -> "◌"
    RasterEffectType.MotionBlur -> "≋"
    RasterEffectType.HueSaturation -> "H/S"
    RasterEffectType.ColourBalance -> "RGB"
    RasterEffectType.Curves -> "⌁"
    RasterEffectType.GradientMap -> "▰"
    RasterEffectType.Grayscale -> "◐"
    RasterEffectType.Invert -> "◑"
}
