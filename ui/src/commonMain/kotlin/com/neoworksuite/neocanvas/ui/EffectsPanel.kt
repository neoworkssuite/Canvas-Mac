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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.renderer.RasterEffectSettings
import com.neoworksuite.neocanvas.renderer.RasterEffectType
import kotlin.math.roundToInt

@Composable
fun EffectsPanel(state: EditorState, modifier: Modifier = Modifier) {
    val selected = state.effectPreviewType ?: RasterEffectType.Blur
    val settings = state.effectPreviewSettings

    LaunchedEffect(state.inspectorVisible, state.inspectorPanel, state.activeLayerId) {
        if (
            state.inspectorVisible &&
            state.inspectorPanel == InspectorPanel.Effects &&
            state.effectPreviewType == null
        ) {
            state.previewEffect(RasterEffectType.Blur, neutralEffectSettings(RasterEffectType.Blur))
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InspectorHeading("FX", "Live adjustment")

        Text(
            "Slide left or right on the canvas, or use the controls below. Changes preview instantly. Tap FX again or open another studio panel to commit.",
            color = NeoCanvasColors.muted,
            fontSize = 11.sp,
        )

        LiveAdjustmentReadout(selected, settings.amount)

        EffectGrid(
            selected = selected,
            onSelect = { next ->
                if (next != selected) {
                    state.commitEffectPreview()
                    state.previewEffect(next, neutralEffectSettings(next))
                }
            },
        )

        when (selected) {
            RasterEffectType.Blur ->
                EffectSlider("Gaussian blur", settings.amount, 0f..1f) {
                    state.previewEffect(selected, settings.copy(amount = it))
                }

            RasterEffectType.MotionBlur ->
                EffectSlider("Motion blur", settings.amount, 0f..1f) {
                    state.previewEffect(selected, settings.copy(amount = it))
                }

            RasterEffectType.Curves ->
                EffectSlider("Contrast curve", settings.amount, -1f..1f) {
                    state.previewEffect(selected, settings.copy(amount = it))
                }

            RasterEffectType.HueSaturation -> {
                EffectSlider("Saturation", settings.amount, -1f..1f) {
                    state.previewEffect(selected, settings.copy(amount = it))
                }
                EffectSlider("Hue shift", settings.secondary, -1f..1f) {
                    state.previewEffect(selected, settings.copy(secondary = it))
                }
                EffectSlider("Brightness", settings.tertiary, -1f..1f) {
                    state.previewEffect(selected, settings.copy(tertiary = it))
                }
            }

            RasterEffectType.ColourBalance -> {
                EffectSlider("Red / Cyan", settings.amount, -1f..1f) {
                    state.previewEffect(selected, settings.copy(amount = it))
                }
                EffectSlider("Green / Magenta", settings.secondary, -1f..1f) {
                    state.previewEffect(selected, settings.copy(secondary = it))
                }
                EffectSlider("Blue / Yellow", settings.tertiary, -1f..1f) {
                    state.previewEffect(selected, settings.copy(tertiary = it))
                }
            }

            RasterEffectType.GradientMap -> Text(
                "Maps shadows to black and highlights to the current NeoCanvas colour. The canvas preview is live.",
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
            )

            RasterEffectType.Grayscale,
            RasterEffectType.Invert -> Text(
                "This adjustment previews immediately at full strength.",
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    state.previewEffect(selected, neutralEffectSettings(selected))
                },
            ) {
                Text("Reset", color = NeoCanvasColors.accent)
            }

            TextButton(onClick = { state.hideInspector(commitEffects = false) }) {
                Text("Cancel", color = NeoCanvasColors.muted)
            }
        }

        Text(
            "There is no Apply button. Leaving FX commits the current preview as one undoable edit.",
            color = NeoCanvasColors.faint,
            fontSize = 10.sp,
        )
    }
}

private fun neutralEffectSettings(type: RasterEffectType): RasterEffectSettings =
    when (type) {
        RasterEffectType.GradientMap,
        RasterEffectType.Grayscale,
        RasterEffectType.Invert -> RasterEffectSettings(amount = 1f)
        else -> RasterEffectSettings(amount = 0f, secondary = 0f, tertiary = 0f)
    }

@Composable
private fun LiveAdjustmentReadout(type: RasterEffectType, amount: Float) {
    val percent = when {
        !effectHasContinuousStrength(type) -> 100
        type in signedEffects -> (amount * 100f).roundToInt()
        else -> (amount.coerceIn(0f, 1f) * 100f).roundToInt()
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(NeoCanvasColors.panelRaised)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            effectName(type),
            color = NeoCanvasColors.paper,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (percent > 0 && type in signedEffects) "+$percent%" else "$percent%",
            color = NeoCanvasColors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
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
            val percent = (value * 100f).roundToInt()
            Text(
                if (range.start < 0f && percent > 0) "+$percent%" else "$percent%",
                color = NeoCanvasColors.paper,
                fontSize = 11.sp,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = studioSliderColors())
    }
}

internal fun effectName(type: RasterEffectType): String = when (type) {
    RasterEffectType.Blur -> "Gaussian Blur"
    RasterEffectType.MotionBlur -> "Motion Blur"
    RasterEffectType.HueSaturation -> "Hue / Saturation"
    RasterEffectType.ColourBalance -> "Colour Balance"
    RasterEffectType.Curves -> "Curves"
    RasterEffectType.GradientMap -> "Gradient Map"
    RasterEffectType.Grayscale -> "Grayscale"
    RasterEffectType.Invert -> "Invert"
}

private fun effectHasContinuousStrength(type: RasterEffectType): Boolean =
    type !in setOf(RasterEffectType.GradientMap, RasterEffectType.Grayscale, RasterEffectType.Invert)

private val signedEffects = setOf(
    RasterEffectType.HueSaturation,
    RasterEffectType.ColourBalance,
    RasterEffectType.Curves,
)

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
