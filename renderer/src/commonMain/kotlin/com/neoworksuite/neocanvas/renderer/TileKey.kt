package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.TileAddress

/**
 * Renderer-facing spelling of the core-owned raster tile address.
 *
 * This is intentionally an alias rather than a renderer model class: core
 * never imports renderer, and document commands can remain renderer-neutral.
 */
typealias TileKey = TileAddress

/** The width and height, in pixels, of every v1 raster tile. */
const val TILE_SIZE_PIXELS: Int = 256

/** Returns the fixed-grid coordinate containing [pixel], including negative viewport coordinates. */
fun tileCoordinate(pixel: Int): Int {
    val quotient = pixel / TILE_SIZE_PIXELS
    val remainder = pixel % TILE_SIZE_PIXELS
    return if (remainder < 0) quotient - 1 else quotient
}
