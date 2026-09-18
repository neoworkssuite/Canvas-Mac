package com.neoworksuite.neocanvas.core.store

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress

sealed interface SaveResult {
    data object Success : SaveResult

    /** The prior saved package is retained; [recoveryPath] points to the new recoverable package when available. */
    data class Failure(val message: String, val recoveryPath: String? = null) : SaveResult
}

sealed interface LoadResult {
    class Success(
        val document: CanvasDocument,
        tiles: Map<TileAddress, ByteArray>,
    ) : LoadResult {
        /** Independent tile buffers prevent a loaded package being mutated through its return value. */
        val tiles: Map<TileAddress, ByteArray> = tiles.mapValues { (_, pixels) -> pixels.copyOf() }
    }

    data class Incompatible(val message: String) : LoadResult
    data class Corrupt(val message: String) : LoadResult
    data class Failure(val message: String) : LoadResult
}
