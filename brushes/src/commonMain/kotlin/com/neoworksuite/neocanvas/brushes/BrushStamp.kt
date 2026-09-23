package com.neoworksuite.neocanvas.brushes

enum class StampAngleMode { Fixed, Direction, Randomized, DirectionJitter }

enum class GrainMovement { Stamp, Canvas }

data class BrushAssetRef(val id: String, val sha256: String) {
    init {
        require(PORTABLE_ID.matches(id)) { "Brush asset id must use lowercase portable characters." }
        require(SHA256.matches(sha256)) { "Brush asset hash must be lowercase SHA-256." }
    }

    private companion object {
        val PORTABLE_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

data class BrushStamp(
    val shape: BrushAssetRef? = null,
    val grain: BrushAssetRef? = null,
    val angleMode: StampAngleMode = StampAngleMode.Fixed,
    val angleDegrees: Float = 0f,
    val angleJitter: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val spacingRatio: Float = .25f,
    val scatterAlong: Float = 0f,
    val scatterAcross: Float = 0f,
    val stampCount: Int = 1,
    val stampCountJitter: Float = 0f,
    val grainScale: Float = 1f,
    val grainMovement: GrainMovement = GrainMovement.Stamp,
    val hueJitter: Float = 0f,
    val saturationJitter: Float = 0f,
    val brightnessJitter: Float = 0f,
    val pressureScatter: Float = 0f,
    val pressureStampCount: Float = 0f,
    val startTaper: Float = 0f,
    val endTaper: Float = 0f,
) {
    init {
        require(angleDegrees.isFinite() && angleDegrees in -360f..360f) { "Stamp angle must be finite and between -360 and 360 degrees." }
        requireNormalized("Angle jitter", angleJitter)
        require(scaleX.isFinite() && scaleX in .05f..8f) { "Stamp horizontal scale must be between 0.05 and 8." }
        require(scaleY.isFinite() && scaleY in .05f..8f) { "Stamp vertical scale must be between 0.05 and 8." }
        require(spacingRatio.isFinite() && spacingRatio in .01f..4f) { "Stamp spacing ratio must be between 0.01 and 4." }
        requireNormalized("Along-stroke scatter", scatterAlong)
        requireNormalized("Across-stroke scatter", scatterAcross)
        require(stampCount in 1..8) { "Stamp count must be between one and eight." }
        requireNormalized("Stamp-count jitter", stampCountJitter)
        require(grainScale.isFinite() && grainScale in .05f..8f) { "Grain scale must be between 0.05 and 8." }
        require(hueJitter.isFinite() && hueJitter in 0f..0.5f) { "Hue jitter must be between zero and 0.5." }
        requireNormalized("Saturation jitter", saturationJitter)
        requireNormalized("Brightness jitter", brightnessJitter)
        requireNormalized("Pressure scatter", pressureScatter)
        requireNormalized("Pressure stamp count", pressureStampCount)
        requireNormalized("Start taper", startTaper)
        requireNormalized("End taper", endTaper)
    }

    private fun requireNormalized(label: String, value: Float) {
        require(value.isFinite() && value in 0f..1f) { "$label must be between zero and one." }
    }
}
