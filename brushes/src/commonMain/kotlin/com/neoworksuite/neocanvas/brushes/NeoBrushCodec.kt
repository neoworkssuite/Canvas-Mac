package com.neoworksuite.neocanvas.brushes

/** Strict, versioned and platform-neutral `.neobrush` codec. */
object NeoBrushCodec {
    private const val V1_HEADER = "NEOCANVAS_BRUSH=1"
    private const val V2_HEADER = "NEOCANVAS_BRUSH=2"
    private val commonFields = setOf(
        "id", "name", "spacing", "size", "opacity", "mode", "brushVersion", "tip",
        "pressureSize", "pressureOpacity", "category", "grain", "scatter", "rotation",
        "shapeRatio", "hardness", "wetMix", "jitter",
    )
    private val stampFields = setOf(
        "stamp.shape.id", "stamp.shape.sha256", "stamp.grain.id", "stamp.grain.sha256",
        "stamp.angleMode", "stamp.angleDegrees", "stamp.angleJitter", "stamp.scaleX", "stamp.scaleY",
        "stamp.spacingRatio", "stamp.scatterAlong", "stamp.scatterAcross", "stamp.stampCount",
        "stamp.stampCountJitter", "stamp.grainScale", "stamp.grainMovement", "stamp.hueJitter",
        "stamp.saturationJitter", "stamp.brightnessJitter", "stamp.pressureScatter",
        "stamp.pressureStampCount", "stamp.startTaper", "stamp.endTaper",
    )

    fun encode(brush: BrushDefinition): ByteArray = buildString {
        appendLine(if (brush.stamp == null) V1_HEADER else V2_HEADER)
        fun field(name: String, value: Any) = append(name).append('=').appendLine(escape(value.toString()))
        field("id", brush.id)
        field("name", brush.name)
        field("spacing", brush.spacing)
        field("size", brush.baseSize)
        field("opacity", brush.opacity)
        field("mode", brush.mode.name)
        field("brushVersion", brush.version)
        field("tip", brush.tip.name)
        field("pressureSize", brush.pressureSize)
        field("pressureOpacity", brush.pressureOpacity)
        field("category", brush.categoryId)
        field("grain", brush.dynamics.grain)
        field("scatter", brush.dynamics.scatter)
        field("rotation", brush.dynamics.rotation)
        field("shapeRatio", brush.dynamics.shapeRatio)
        field("hardness", brush.dynamics.hardness)
        field("wetMix", brush.dynamics.wetMix)
        field("jitter", brush.dynamics.jitter)
        brush.stamp?.let { stamp ->
            field("stamp.shape.id", stamp.shape?.id.orEmpty())
            field("stamp.shape.sha256", stamp.shape?.sha256.orEmpty())
            field("stamp.grain.id", stamp.grain?.id.orEmpty())
            field("stamp.grain.sha256", stamp.grain?.sha256.orEmpty())
            field("stamp.angleMode", stamp.angleMode.name)
            field("stamp.angleDegrees", stamp.angleDegrees)
            field("stamp.angleJitter", stamp.angleJitter)
            field("stamp.scaleX", stamp.scaleX)
            field("stamp.scaleY", stamp.scaleY)
            field("stamp.spacingRatio", stamp.spacingRatio)
            field("stamp.scatterAlong", stamp.scatterAlong)
            field("stamp.scatterAcross", stamp.scatterAcross)
            field("stamp.stampCount", stamp.stampCount)
            field("stamp.stampCountJitter", stamp.stampCountJitter)
            field("stamp.grainScale", stamp.grainScale)
            field("stamp.grainMovement", stamp.grainMovement.name)
            field("stamp.hueJitter", stamp.hueJitter)
            field("stamp.saturationJitter", stamp.saturationJitter)
            field("stamp.brightnessJitter", stamp.brightnessJitter)
            field("stamp.pressureScatter", stamp.pressureScatter)
            field("stamp.pressureStampCount", stamp.pressureStampCount)
            field("stamp.startTaper", stamp.startTaper)
            field("stamp.endTaper", stamp.endTaper)
        }
    }.encodeToByteArray()

    fun decode(bytes: ByteArray): BrushDefinition {
        require(bytes.size in 1..65_536) { "Brush file must be between 1 byte and 64 KiB." }
        val lines = bytes.decodeToString(throwOnInvalidSequence = true).lineSequence().filter { it.isNotEmpty() }.toList()
        val header = lines.firstOrNull()
        require(header == V1_HEADER || header == V2_HEADER) { "Unsupported or missing NeoCanvas brush version." }
        val expectedFields = if (header == V2_HEADER) commonFields + stampFields else commonFields
        val values = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val split = line.indexOf('=')
            require(split > 0) { "Malformed brush field." }
            val key = line.substring(0, split)
            require(key in expectedFields) { "Unknown brush field '$key'." }
            require(values.put(key, unescape(line.substring(split + 1))) == null) { "Duplicate brush field '$key'." }
        }
        require(values.keys == expectedFields) { "Brush file is missing required fields." }
        fun value(key: String) = requireNotNull(values[key])
        fun number(key: String) = value(key).toFloatOrNull()?.takeIf(Float::isFinite)
            ?: throw IllegalArgumentException("Brush field '$key' must be a finite number.")
        fun integer(key: String) = value(key).toIntOrNull()
            ?: throw IllegalArgumentException("Brush field '$key' must be an integer.")
        fun <T : Enum<T>> enumValue(key: String, entries: Array<T>): T = entries.firstOrNull { it.name == value(key) }
            ?: throw IllegalArgumentException("Brush field '$key' has an unsupported value.")
        fun asset(prefix: String): BrushAssetRef? {
            val id = value("$prefix.id")
            val hash = value("$prefix.sha256")
            require(id.isEmpty() == hash.isEmpty()) { "Brush asset reference is incomplete." }
            return if (id.isEmpty()) null else BrushAssetRef(id, hash)
        }
        return BrushDefinition(
            id = value("id"),
            name = value("name"),
            spacing = number("spacing"),
            baseSize = number("size"),
            opacity = number("opacity"),
            mode = enumValue("mode", BrushMode.entries.toTypedArray()),
            version = integer("brushVersion"),
            tip = enumValue("tip", BrushTip.entries.toTypedArray()),
            pressureSize = number("pressureSize"),
            pressureOpacity = number("pressureOpacity"),
            categoryId = value("category"),
            dynamics = BrushDynamics(
                grain = number("grain"),
                scatter = number("scatter"),
                rotation = number("rotation"),
                shapeRatio = number("shapeRatio"),
                hardness = number("hardness"),
                wetMix = number("wetMix"),
                jitter = number("jitter"),
            ),
            stamp = if (header == V2_HEADER) BrushStamp(
                shape = asset("stamp.shape"),
                grain = asset("stamp.grain"),
                angleMode = enumValue("stamp.angleMode", StampAngleMode.entries.toTypedArray()),
                angleDegrees = number("stamp.angleDegrees"),
                angleJitter = number("stamp.angleJitter"),
                scaleX = number("stamp.scaleX"),
                scaleY = number("stamp.scaleY"),
                spacingRatio = number("stamp.spacingRatio"),
                scatterAlong = number("stamp.scatterAlong"),
                scatterAcross = number("stamp.scatterAcross"),
                stampCount = integer("stamp.stampCount"),
                stampCountJitter = number("stamp.stampCountJitter"),
                grainScale = number("stamp.grainScale"),
                grainMovement = enumValue("stamp.grainMovement", GrainMovement.entries.toTypedArray()),
                hueJitter = number("stamp.hueJitter"),
                saturationJitter = number("stamp.saturationJitter"),
                brightnessJitter = number("stamp.brightnessJitter"),
                pressureScatter = number("stamp.pressureScatter"),
                pressureStampCount = number("stamp.pressureStampCount"),
                startTaper = number("stamp.startTaper"),
                endTaper = number("stamp.endTaper"),
            ) else null,
        )
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char -> when (char) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(char)
        } }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index++]
            if (char != '\\') { append(char); continue }
            require(index < value.length) { "Malformed brush escape." }
            append(when (val escaped = value[index++]) {
                '\\' -> '\\'
                'n' -> '\n'
                'r' -> '\r'
                else -> throw IllegalArgumentException("Unsupported brush escape '$escaped'.")
            })
        }
    }
}
