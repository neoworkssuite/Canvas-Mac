package com.neoworksuite.neocanvas.brushes

/** Strict, versioned and platform-neutral `.neobrush` codec. */
object NeoBrushCodec {
    private const val HEADER = "NEOCANVAS_BRUSH=1"
    private val fields = setOf(
        "id", "name", "spacing", "size", "opacity", "mode", "brushVersion", "tip",
        "pressureSize", "pressureOpacity", "category", "grain", "scatter", "rotation",
        "shapeRatio", "hardness", "wetMix", "jitter",
    )

    fun encode(brush: BrushDefinition): ByteArray = buildString {
        appendLine(HEADER)
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
    }.encodeToByteArray()

    fun decode(bytes: ByteArray): BrushDefinition {
        require(bytes.size in 1..65_536) { "Brush file must be between 1 byte and 64 KiB." }
        val lines = bytes.decodeToString(throwOnInvalidSequence = true).lineSequence().filter { it.isNotEmpty() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported or missing NeoCanvas brush version." }
        val values = linkedMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val split = line.indexOf('=')
            require(split > 0) { "Malformed brush field." }
            val key = line.substring(0, split)
            require(key in fields) { "Unknown brush field '$key'." }
            require(values.put(key, unescape(line.substring(split + 1))) == null) { "Duplicate brush field '$key'." }
        }
        require(values.keys == fields) { "Brush file is missing required fields." }
        fun value(key: String) = requireNotNull(values[key])
        fun number(key: String) = value(key).toFloatOrNull()?.takeIf(Float::isFinite)
            ?: throw IllegalArgumentException("Brush field '$key' must be a finite number.")
        fun integer(key: String) = value(key).toIntOrNull()
            ?: throw IllegalArgumentException("Brush field '$key' must be an integer.")
        fun <T : Enum<T>> enumValue(key: String, entries: Array<T>): T = entries.firstOrNull { it.name == value(key) }
            ?: throw IllegalArgumentException("Brush field '$key' has an unsupported value.")
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
