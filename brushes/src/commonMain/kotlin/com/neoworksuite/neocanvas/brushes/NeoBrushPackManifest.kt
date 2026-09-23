package com.neoworksuite.neocanvas.brushes

data class NeoBrushPackManifest(
    val id: String,
    val version: String,
    val name: String,
    val summary: String,
    val author: String,
    val website: String,
    val licence: String,
    val minimumAppVersion: String,
    val brushIds: List<String>,
    val schemaVersion: Int = 1,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{2,127}")))
        require(version.matches(SEMVER) && minimumAppVersion.matches(SEMVER))
        require(name.isNotBlank() && author.isNotBlank() && licence.isNotBlank())
        require(brushIds.size in 1..100 && brushIds.distinct().size == brushIds.size)
        require(schemaVersion == 1)
    }
    companion object { val SEMVER = Regex("[0-9]+\\.[0-9]+\\.[0-9]+") }
}
