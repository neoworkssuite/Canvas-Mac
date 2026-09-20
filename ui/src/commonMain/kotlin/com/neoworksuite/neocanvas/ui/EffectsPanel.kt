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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neoworksuite.neocanvas.renderer.RasterEffectSettings
import com.neoworksuite.neocanvas.renderer.RasterEffectType
import kotlin.math.roundToInt

@Composable
fun EffectsPanel(state: EditorState, modifier: Modifier = Modifier) {
    val selected = state.effectPreviewType
    val settings = state.effectPreviewSettings

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected == null) {
            InspectorHeading("FX", "Choose an adjustment")
            Text(
                "Choose an effect to enter a focused live adjustment view. Nothing is changed until you start an effect.",
                color = NeoCanvasColors.muted,
                fontSize = 11.sp,
            )

            EffectSection(
                title = "ADJUSTMENTS",
                effects = listOf(
                    RasterEffectType.HueSaturation,
                    RasterEffectType.ColourBalance,
                    RasterEffectType.Curves,
                    RasterEffectType.GradientMap,
                    RasterEffectType.Grayscale,
                    RasterEffectType.Invert,
                ),
                currentColour = state.color,
            ) { next -> state.previewEffect(next, neutralEffectSettings(next)) }

            EffectSection(
                title = "BLUR",
                effects = listOf(
                    RasterEffectType.Blur,
                    RasterEffectType.MotionBlur,
                ),
                currentColour = state.color,
            ) { next -> state.previewEffect(next, neutralEffectSettings(next)) }

            EffectSection(
                title = "EFFECTS",
                effects = listOf(
                    RasterEffectType.Sharpen,
                    RasterEffectType.Noise,
                    RasterEffectType.Bloom,
                    RasterEffectType.Halftone,
                    RasterEffectType.ChromaticAberration,
                ),
                currentColour = state.color,
            ) { next -> state.previewEffect(next, neutralEffectSettings(next)) }

            Text(
                "Every effect tile stays the same size. Selecting one replaces this browser with only the controls for that effect.",
                color = NeoCanvasColors.faint,
                fontSize = 10.sp,
            )
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { state.commitEffectPreview() }) {
                Text("‹ FX", color = NeoCanvasColors.accent)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "LIVE PREVIEW",
                color = NeoCanvasColors.faint,
                fontSize = 8.sp,
                letterSpacing = .9.sp,
            )
        }

        LiveAdjustmentReadout(selected, settings.amount)

        Text(
            "Slide left or right on the canvas, or use the coloured controls below.",
            color = NeoCanvasColors.muted,
            fontSize = 11.sp,
        )

        when (selected) {
            RasterEffectType.Blur ->
                EffectSlider(
                    label = "Gaussian blur",
                    value = settings.amount,
                    range = 0f..1f,
                    colors = listOf(
                        Color(0xFF24262D),
                        Color(0xFF777C88),
                        Color(0xFFE9EBF0),
                    ),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.MotionBlur ->
                EffectSlider(
                    label = "Motion blur",
                    value = settings.amount,
                    range = 0f..1f,
                    colors = listOf(
                        Color(0xFF252832),
                        NeoCanvasColors.accent.copy(alpha = .55f),
                        NeoCanvasColors.paper,
                    ),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.Curves ->
                EffectSlider(
                    label = "Contrast curve",
                    value = settings.amount,
                    range = -1f..1f,
                    colors = listOf(
                        Color(0xFF555861),
                        Color(0xFFB8BBC2),
                        Color.White,
                    ),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.HueSaturation -> {
                EffectSlider(
                    label = "Saturation",
                    value = settings.amount,
                    range = -1f..1f,
                    colors = listOf(
                        Color(0xFF777777),
                        state.color.copy(alpha = .65f),
                        state.color,
                    ),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

                EffectSlider(
                    label = "Hue shift",
                    value = settings.secondary,
                    range = -1f..1f,
                    colors = hueSpectrum,
                ) { state.previewEffect(selected, settings.copy(secondary = it)) }

                EffectSlider(
                    label = "Brightness",
                    value = settings.tertiary,
                    range = -1f..1f,
                    colors = listOf(Color.Black, Color(0xFF777777), Color.White),
                ) { state.previewEffect(selected, settings.copy(tertiary = it)) }
            }

            RasterEffectType.ColourBalance -> {
                EffectSlider(
                    label = "Red / Cyan",
                    value = settings.amount,
                    range = -1f..1f,
                    colors = listOf(Color.Cyan, Color(0xFF777777), Color.Red),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

                EffectSlider(
                    label = "Green / Magenta",
                    value = settings.secondary,
                    range = -1f..1f,
                    colors = listOf(Color.Magenta, Color(0xFF777777), Color.Green),
                ) { state.previewEffect(selected, settings.copy(secondary = it)) }

                EffectSlider(
                    label = "Blue / Yellow",
                    value = settings.tertiary,
                    range = -1f..1f,
                    colors = listOf(Color.Yellow, Color(0xFF777777), Color.Blue),
                ) { state.previewEffect(selected, settings.copy(tertiary = it)) }
            }

            RasterEffectType.GradientMap -> {
                Text(
                    "Shadows map to black and highlights map to the current NeoCanvas colour.",
                    color = NeoCanvasColors.muted,
                    fontSize = 11.sp,
                )
                GradientPreview(listOf(Color.Black, state.color))
            }

            RasterEffectType.Sharpen ->
                EffectSlider(
                    label = "Sharpen",
                    value = settings.amount,
                    range = 0f..1f,
                    colors = listOf(Color(0xFF4C4F56), Color(0xFFAEB3BC), Color.White),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.Noise ->
                EffectSlider(
                    label = "Noise",
                    value = settings.amount,
                    range = 0f..1f,
                    colors = listOf(Color(0xFF25272C), Color(0xFF777B84), Color(0xFFE5E7EB)),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.Bloom ->
                EffectSlider(
                    label = "Bloom",
                    value = settings.amount,
                    range = 0f..1f,
                    colors = listOf(Color(0xFF24262D), Color(0xFFFFD66B), Color.White),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.Halftone ->
                EffectSlider(
                    label = "Halftone",
                    value = settings.amount,
                    range = 0f..1f,
                    colors = listOf(Color.White, Color(0xFF8B8B8B), Color.Black),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.ChromaticAberration ->
                EffectSlider(
                    label = "Chromatic Aberration",
                    value = settings.amount,
                    range = 0f..1f,
                    colors = listOf(Color.Cyan, Color.Magenta, Color.Yellow),
                ) { state.previewEffect(selected, settings.copy(amount = it)) }

            RasterEffectType.Grayscale -> {
                Text(
                    "Converts the active layer to luminance while preserving transparency.",
                    color = NeoCanvasColors.muted,
                    fontSize = 11.sp,
                )
                GradientPreview(listOf(Color.Black, Color(0xFF777777), Color.White))
            }

            RasterEffectType.Invert -> {
                Text(
                    "Inverts the RGB channels of the active layer.",
                    color = NeoCanvasColors.muted,
                    fontSize = 11.sp,
                )
                GradientPreview(listOf(Color.White, Color.Black))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { state.previewEffect(selected, neutralEffectSettings(selected)) },
            ) {
                Text("Reset", color = NeoCanvasColors.accent)
            }

            TextButton(onClick = { state.cancelEffectPreview() }) {
                Text("Cancel", color = NeoCanvasColors.muted)
            }
        }

        Text(
            "No Apply button — ‹ FX or leaving the panel commits the current preview as one undoable edit. Cancel reverts it.",
            color = NeoCanvasColors.faint,
            fontSize = 10.sp,
        )
    }
}

private val hueSpectrum = listOf(
    Color.Red,
    Color(0xFFFFA000),
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

private fun neutralEffectSettings(type: RasterEffectType): RasterEffectSettings =
    when (type) {
        RasterEffectType.GradientMap,
        RasterEffectType.Grayscale,
        RasterEffectType.Invert -> RasterEffectSettings(amount = 1f)
        RasterEffectType.Sharpen,
        RasterEffectType.Noise,
        RasterEffectType.Bloom,
        RasterEffectType.Halftone,
        RasterEffectType.ChromaticAberration -> RasterEffectSettings(amount = 0f)
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
        EffectIcon(type, selected = true, currentColour = NeoCanvasColors.accent, modifier = Modifier.size(26.dp))
        Text(
            effectName(type),
            color = NeoCanvasColors.paper,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 9.dp),
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
private fun EffectSection(
    title: String,
    effects: List<RasterEffectType>,
    currentColour: Color,
    onSelect: (RasterEffectType) -> Unit,
) {
    Text(title, color = NeoCanvasColors.faint, fontSize = 9.sp, letterSpacing = .8.sp)
    effects.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { effect ->
                Row(
                    Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(11.dp))
                        .background(NeoCanvasColors.panelRaised)
                        .clickable { onSelect(effect) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EffectIcon(
                        type = effect,
                        selected = false,
                        currentColour = currentColour,
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        effectName(effect),
                        color = NeoCanvasColors.paper,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EffectIcon(
    type: RasterEffectType,
    selected: Boolean,
    currentColour: Color,
    modifier: Modifier = Modifier,
) {
    val mono = if (selected) NeoCanvasColors.ink else NeoCanvasColors.accent
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = 1.7.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = s, tint: Color = mono) =
            drawLine(tint, Offset(w * x1, h * y1), Offset(w * x2, h * y2), width, StrokeCap.Round)

        when (type) {
            RasterEffectType.Blur -> {
                drawCircle(mono.copy(alpha = .30f), w * .34f, Offset(w * .50f, h * .50f))
                drawCircle(mono.copy(alpha = .55f), w * .23f, Offset(w * .50f, h * .50f), style = Stroke(s))
                drawCircle(mono, w * .10f, Offset(w * .50f, h * .50f))
            }

            RasterEffectType.MotionBlur -> {
                line(.10f, .33f, .76f, .33f, s, mono.copy(alpha = .45f))
                line(.18f, .50f, .90f, .50f, s * 1.2f)
                line(.10f, .67f, .70f, .67f, s, mono.copy(alpha = .65f))
                drawCircle(mono, w * .08f, Offset(w * .75f, h * .50f))
            }

            RasterEffectType.HueSaturation -> {
                val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta)
                val points = listOf(
                    .50f to .17f, .78f to .34f, .78f to .66f,
                    .50f to .83f, .22f to .66f, .22f to .34f,
                )
                points.forEachIndexed { index, point ->
                    drawCircle(colors[index], w * .105f, Offset(w * point.first, h * point.second))
                }
                drawCircle(if (selected) NeoCanvasColors.ink else NeoCanvasColors.paper, w * .08f, Offset(w * .50f, h * .50f))
            }

            RasterEffectType.ColourBalance -> {
                drawCircle(Color.Red.copy(alpha = .90f), w * .19f, Offset(w * .42f, h * .42f))
                drawCircle(Color.Green.copy(alpha = .90f), w * .19f, Offset(w * .60f, h * .42f))
                drawCircle(Color.Blue.copy(alpha = .90f), w * .19f, Offset(w * .51f, h * .60f))
            }

            RasterEffectType.Curves -> {
                line(.16f, .82f, .16f, .18f)
                line(.16f, .82f, .86f, .82f)
                val path = Path().apply {
                    moveTo(w * .18f, h * .75f)
                    cubicTo(w * .35f, h * .72f, w * .48f, h * .25f, w * .84f, h * .20f)
                }
                drawPath(path, mono, style = Stroke(s * 1.25f, cap = StrokeCap.Round))
            }

            RasterEffectType.GradientMap -> {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color.Black, currentColour)),
                    topLeft = Offset(w * .12f, h * .28f),
                    size = Size(w * .76f, h * .44f),
                    cornerRadius = CornerRadius(w * .08f, w * .08f),
                )
                drawRoundRect(
                    color = mono,
                    topLeft = Offset(w * .12f, h * .28f),
                    size = Size(w * .76f, h * .44f),
                    cornerRadius = CornerRadius(w * .08f, w * .08f),
                    style = Stroke(s),
                )
            }

            RasterEffectType.Sharpen -> {
                line(.20f, .50f, .80f, .50f)
                line(.50f, .20f, .50f, .80f)
                line(.28f, .28f, .72f, .72f)
                line(.72f, .28f, .28f, .72f)
                drawCircle(mono, w * .07f, Offset(w * .50f, h * .50f))
            }

            RasterEffectType.Noise -> {
                val dots = listOf(
                    .22f to .24f, .50f to .18f, .76f to .28f,
                    .30f to .52f, .62f to .48f, .82f to .60f,
                    .18f to .76f, .48f to .78f, .70f to .80f,
                )
                dots.forEachIndexed { index, point ->
                    drawCircle(
                        mono.copy(alpha = .45f + (index % 3) * .22f),
                        w * (.035f + (index % 2) * .015f),
                        Offset(w * point.first, h * point.second),
                    )
                }
            }

            RasterEffectType.Bloom -> {
                drawCircle(Color(0xFFFFD66B).copy(alpha = .30f), w * .30f, Offset(w * .50f, h * .50f))
                drawCircle(Color(0xFFFFE99A).copy(alpha = .65f), w * .17f, Offset(w * .50f, h * .50f))
                drawCircle(Color.White, w * .07f, Offset(w * .50f, h * .50f))
                line(.50f, .08f, .50f, .24f, s * .85f, mono)
                line(.50f, .76f, .50f, .92f, s * .85f, mono)
                line(.08f, .50f, .24f, .50f, s * .85f, mono)
                line(.76f, .50f, .92f, .50f, s * .85f, mono)
            }

            RasterEffectType.Halftone -> {
                val dots = listOf(
                    .25f to .25f, .50f to .25f, .75f to .25f,
                    .25f to .50f, .50f to .50f, .75f to .50f,
                    .25f to .75f, .50f to .75f, .75f to .75f,
                )
                dots.forEachIndexed { index, point ->
                    drawCircle(mono, w * if (index == 4) .085f else .055f, Offset(w * point.first, h * point.second))
                }
            }

            RasterEffectType.ChromaticAberration -> {
                drawCircle(Color.Red.copy(alpha = .75f), w * .22f, Offset(w * .42f, h * .50f), style = Stroke(s))
                drawCircle(Color.Green.copy(alpha = .75f), w * .22f, Offset(w * .50f, h * .50f), style = Stroke(s))
                drawCircle(Color.Blue.copy(alpha = .75f), w * .22f, Offset(w * .58f, h * .50f), style = Stroke(s))
            }

            RasterEffectType.Grayscale -> {
                drawCircle(Color(0xFF303030), w * .29f, Offset(w * .50f, h * .50f))
                drawArc(
                    color = Color(0xFFE0E0E0),
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * .21f, h * .21f),
                    size = Size(w * .58f, h * .58f),
                )
                drawCircle(mono, w * .29f, Offset(w * .50f, h * .50f), style = Stroke(s))
            }

            RasterEffectType.Invert -> {
                drawCircle(Color.White, w * .29f, Offset(w * .50f, h * .50f))
                drawArc(
                    color = Color.Black,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * .21f, h * .21f),
                    size = Size(w * .58f, h * .58f),
                )
                drawCircle(mono, w * .29f, Offset(w * .50f, h * .50f), style = Stroke(s))
            }
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: List<Color>,
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
                fontWeight = FontWeight.Medium,
            )
        }

        val update: (Float, Float) -> Unit = { x, width ->
            if (width > 0f) {
                val fraction = (x / width).coerceIn(0f, 1f)
                onChange(range.start + (range.endInclusive - range.start) * fraction)
            }
        }

        Canvas(
            Modifier.fillMaxWidth().height(30.dp).padding(top = 6.dp)
                .pointerInput(range, colors) {
                    detectTapGestures { point -> update(point.x, size.width.toFloat()) }
                }
                .pointerInput(range, colors) {
                    detectDragGestures(
                        onDragStart = { point -> update(point.x, size.width.toFloat()) },
                        onDrag = { change, _ ->
                            update(change.position.x, size.width.toFloat())
                            change.consume()
                        },
                    )
                },
        ) {
            val trackHeight = 10.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(colors),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
            )
            drawRoundRect(
                color = NeoCanvasColors.line.copy(alpha = .8f),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                style = Stroke(1.dp.toPx()),
            )

            val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val thumbX = size.width * fraction
            drawCircle(Color.Black.copy(alpha = .35f), 9.dp.toPx(), Offset(thumbX, size.height / 2f))
            drawCircle(Color.White, 7.dp.toPx(), Offset(thumbX, size.height / 2f))
            drawCircle(NeoCanvasColors.ink, 3.dp.toPx(), Offset(thumbX, size.height / 2f))
        }
    }
}

@Composable
private fun GradientPreview(colors: List<Color>) {
    Box(
        Modifier.fillMaxWidth().height(22.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(colors))
    )
}

internal fun effectName(type: RasterEffectType): String = when (type) {
    RasterEffectType.Blur -> "Gaussian Blur"
    RasterEffectType.MotionBlur -> "Motion Blur"
    RasterEffectType.HueSaturation -> "Hue / Saturation"
    RasterEffectType.ColourBalance -> "Colour Balance"
    RasterEffectType.Curves -> "Curves"
    RasterEffectType.GradientMap -> "Gradient Map"
    RasterEffectType.Sharpen -> "Sharpen"
    RasterEffectType.Noise -> "Noise"
    RasterEffectType.Bloom -> "Bloom"
    RasterEffectType.Halftone -> "Halftone"
    RasterEffectType.ChromaticAberration -> "Chromatic"
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
