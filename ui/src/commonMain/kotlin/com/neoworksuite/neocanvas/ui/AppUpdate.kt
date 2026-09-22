package com.neoworksuite.neocanvas.ui

data class AppUpdateInfo(
    val version: String,
    val storeUrl: String,
    val releaseNotes: String? = null,
)

internal fun compareReleaseVersions(left: String, right: String): Int {
    fun parts(value: String): List<Int> = value
        .substringBefore('-')
        .split('.')
        .map { component -> component.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(left)
    val b = parts(right)
    val count = maxOf(a.size, b.size)
    for (index in 0 until count) {
        val av = a.getOrElse(index) { 0 }
        val bv = b.getOrElse(index) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

internal fun parseAppStoreLookup(json: String): AppUpdateInfo? {
    if (!Regex("\"resultCount\"\\s*:\\s*[1-9]\\d*").containsMatchIn(json)) return null

    fun stringValue(key: String): String? {
        val pattern = "\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\""
        val match = Regex(pattern).find(json) ?: return null
        return match.groupValues[1]
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    val version = stringValue("version")?.trim().orEmpty()
    val storeUrl = stringValue("trackViewUrl")?.trim().orEmpty()
    if (version.isBlank() || !storeUrl.startsWith("https://")) return null
    return AppUpdateInfo(
        version = version,
        storeUrl = storeUrl,
        releaseNotes = stringValue("releaseNotes")?.takeIf(String::isNotBlank),
    )
}
