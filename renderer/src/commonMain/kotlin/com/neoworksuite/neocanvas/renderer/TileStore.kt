package com.neoworksuite.neocanvas.renderer

import com.neoworksuite.neocanvas.core.model.RasterTileCopy

/** Fixed RGBA8 layout used by all v1 in-memory tiles. */
object TileFormat {
    const val CHANNELS_PER_PIXEL: Int = 4
    const val BYTES_PER_TILE: Int = TILE_SIZE_PIXELS * TILE_SIZE_PIXELS * CHANNELS_PER_PIXEL
}

/**
 * An immutable batch of complete-tile replacements and removals.
 *
 * Brushes and rasterizers create complete RGBA tile buffers off the UI thread,
 * then commit the changed addresses in one patch.  Buffers are copied at this
 * boundary so callers cannot mutate an accepted patch afterwards.
 */
class RasterPatch private constructor(internal val changes: Map<TileKey, ByteArray?>) {
    /** Read through a pending stroke without changing the document or history. */
    fun previewTile(key: TileKey, existing: TileStore): ByteArray? =
        if (changes.containsKey(key)) changes[key]?.copyOf() else existing.read(key)

    val keys: Set<TileKey>
        get() = changes.keys.toSet()

    companion object {
        fun replace(key: TileKey, rgba: ByteArray): RasterPatch = of(mapOf(key to rgba))

        fun remove(key: TileKey): RasterPatch = RasterPatch(mapOf(key to null))

        fun of(replacements: Map<TileKey, ByteArray>, removals: Set<TileKey> = emptySet()): RasterPatch {
            require(replacements.keys.intersect(removals).isEmpty()) {
                "A tile patch cannot replace and remove the same tile."
            }
            val copiedChanges = linkedMapOf<TileKey, ByteArray?>()
            replacements.forEach { (key, rgba) ->
                copiedChanges[key] = checkedTileCopy(rgba)
            }
            removals.forEach { key -> copiedChanges[key] = null }
            return RasterPatch(copiedChanges)
        }
    }
}

/**
 * In-memory sparse raster storage.  It is independent from document history:
 * callers commit the matching core [ApplyRasterPatch][com.neoworksuite.neocanvas.core.model.ApplyRasterPatch]
 * command after applying a patch here.
 */
class TileStore(initialTiles: Map<TileKey, ByteArray> = emptyMap()) {
    private val tiles = linkedMapOf<TileKey, ByteArray>()

    init {
        initialTiles.forEach { (key, rgba) -> tiles[key] = checkedTileCopy(rgba) }
    }

    val keys: Set<TileKey>
        get() = tiles.keys.toSet()

    /** Returns a defensive copy, or null when the address has no allocated tile. */
    fun read(key: TileKey): ByteArray? = tiles[key]?.copyOf()

    /** A defensive full snapshot used by the editor to make raster edits undoable. */
    fun snapshot(): Map<TileKey, ByteArray> = tiles.mapValues { (_, pixels) -> pixels.copyOf() }

    /** Replaces all tile contents from a defensive snapshot. */
    fun restore(snapshot: Map<TileKey, ByteArray>) {
        tiles.clear()
        snapshot.forEach { (key, pixels) -> tiles[key] = checkedTileCopy(pixels) }
    }

    /** Defensive snapshot for one raster layer, used by lossless Deep Layers hibernation. */
    fun snapshotLayer(layerId: String): Map<TileKey, ByteArray> {
        require(layerId.isNotBlank()) { "Layer id must not be blank." }
        return tiles.entries
            .asSequence()
            .filter { (key, _) -> key.layerId == layerId }
            .associateTo(linkedMapOf()) { (key, pixels) -> key to pixels.copyOf() }
    }

    /** Removes one layer's resident tiles and returns the addresses released from RAM. */
    fun removeLayer(layerId: String): Set<TileKey> {
        require(layerId.isNotBlank()) { "Layer id must not be blank." }
        val removed = tiles.keys.filterTo(linkedSetOf()) { it.layerId == layerId }
        removed.forEach(tiles::remove)
        return removed
    }

    /**
     * Replaces only [layerId]'s resident buffers from a defensive snapshot.
     * Other layers are untouched.
     */
    fun restoreLayer(layerId: String, snapshot: Map<TileKey, ByteArray>): Set<TileKey> {
        require(layerId.isNotBlank()) { "Layer id must not be blank." }
        require(snapshot.keys.all { it.layerId == layerId }) {
            "A layer snapshot may only contain addresses for the requested layer."
        }

        val before = snapshotLayer(layerId)
        removeLayer(layerId)
        snapshot.forEach { (key, pixels) -> tiles[key] = checkedTileCopy(pixels) }

        val changed = linkedSetOf<TileKey>()
        (before.keys + snapshot.keys).forEach { key ->
            val previous = before[key]
            val next = snapshot[key]
            if (previous == null || next == null || !previous.contentEquals(next)) changed += key
        }
        return changed
    }

    fun residentTileCount(layerId: String): Int = tiles.keys.count { it.layerId == layerId }

    val estimatedResidentBytes: Long
        get() = tiles.size.toLong() * TileFormat.BYTES_PER_TILE

    /** Applies complete-tile replacements/removals and reports addresses whose stored state changed. */
    fun applyPatch(patch: RasterPatch): Set<TileKey> {
        val changed = linkedSetOf<TileKey>()
        patch.changes.forEach { (key, replacement) ->
            val previous = tiles[key]
            when {
                replacement == null && previous != null -> {
                    tiles.remove(key)
                    changed += key
                }
                replacement != null && (previous == null || !previous.contentEquals(replacement)) -> {
                    tiles[key] = replacement.copyOf()
                    changed += key
                }
            }
        }
        return changed
    }

    /** Copies the exact source tile buffers needed by a core [RasterTileCopy] plan. */
    fun copyTiles(copies: Iterable<RasterTileCopy>): Set<TileKey> {
        val replacements = linkedMapOf<TileKey, ByteArray>()
        copies.forEach { copy ->
            val source = tiles[copy.source]
                ?: throw IllegalArgumentException("Cannot copy missing source tile ${copy.source}.")
            replacements[copy.destination] = source
        }
        return applyPatch(RasterPatch.of(replacements))
    }
}

private fun checkedTileCopy(rgba: ByteArray): ByteArray {
    require(rgba.size == TileFormat.BYTES_PER_TILE) {
        "A tile must contain exactly ${TileFormat.BYTES_PER_TILE} RGBA bytes."
    }
    return rgba.copyOf()
}
