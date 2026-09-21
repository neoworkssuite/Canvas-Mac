package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.renderer.PngImage

data class VersionComparison(
    val version: LocalVersionEntry,
    val currentThumbnail: PngImage,
    val versionThumbnail: PngImage,
    val currentWidth: Int,
    val currentHeight: Int,
    val versionWidth: Int,
    val versionHeight: Int,
    val currentLayerCount: Int,
    val versionLayerCount: Int,
) {
    val dimensionsChanged: Boolean
        get() = currentWidth != versionWidth || currentHeight != versionHeight

    val layerDelta: Int
        get() = currentLayerCount - versionLayerCount
}
