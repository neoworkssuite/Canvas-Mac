package com.neoworksuite.neocanvas.core.store

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress

/** Local document boundary. Implementations never upload or otherwise leave the selected local path. */
interface DocumentStore {
    fun save(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult
    fun saveWithThumbnail(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, thumbnailPng: ByteArray): SaveResult =
        save(path, document, tiles)
    fun load(path: String): LoadResult
}

/** Minimal host file boundary needed to make a completed package replacement recoverable and testable. */
interface DocumentFileSystem {
    fun read(path: String): ByteArray
    fun write(path: String, bytes: ByteArray)

    /** Must leave [target] unchanged if it throws. Hosts use an atomic sibling replacement where available. */
    fun replaceAtomically(source: String, target: String)
    fun exists(path: String): Boolean
}

/**
 * A filesystem-independent local store. The complete ZIP is written to a sibling temporary file
 * before replacement. A replacement failure retains the last save and writes a separate recovery
 * package, preserving the active in-memory document for the caller.
 */
class SafeDocumentStore(private val files: DocumentFileSystem) : DocumentStore {
    override fun save(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult {
        return saveBytes(path, document, tiles, NeoCanvasPackage.transparentThumbnail())
    }

    override fun saveWithThumbnail(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, thumbnailPng: ByteArray): SaveResult =
        saveBytes(path, document, tiles, thumbnailPng)

    private fun saveBytes(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, thumbnailPng: ByteArray): SaveResult {
        if (path.isBlank()) return SaveResult.Failure("Choose a file location before saving.")
        val bytes = try {
            NeoCanvasPackage.write(document, tiles, thumbnailPng)
        } catch (error: Exception) {
            return SaveResult.Failure("Could not prepare NeoCanvas document: ${error.message ?: "invalid document"}")
        }
        val temporaryPath = "$path.tmp"
        val recoveryPath = "$path.recovery.neocanvas"
        return try {
            files.write(temporaryPath, bytes)
            files.replaceAtomically(temporaryPath, path)
            SaveResult.Success
        } catch (error: Exception) {
            val savedRecovery = try {
                files.write(recoveryPath, bytes)
                recoveryPath
            } catch (_: Exception) {
                null
            }
            SaveResult.Failure(
                message = "Could not replace the saved document; the previous file was kept.",
                recoveryPath = savedRecovery,
            )
        }
    }

    override fun load(path: String): LoadResult {
        if (path.isBlank()) return LoadResult.Failure("Choose a file to open.")
        val bytes = try {
            files.read(path)
        } catch (error: Exception) {
            return LoadResult.Failure("Could not read NeoCanvas document: ${error.message ?: "unreadable file"}")
        }
        return NeoCanvasPackage.read(bytes)
    }
}
