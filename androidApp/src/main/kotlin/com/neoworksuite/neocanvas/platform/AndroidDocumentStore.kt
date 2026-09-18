package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.store.DocumentFileSystem
import com.neoworksuite.neocanvas.core.store.DocumentStore
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SafeDocumentStore
import com.neoworksuite.neocanvas.core.store.SaveResult
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Android local-file adapter. Callers supply paths obtained through an Android file picker or app storage. */
class AndroidDocumentStore(files: DocumentFileSystem = AndroidLocalFiles()) : DocumentStore {
    private val store = SafeDocumentStore(files)

    override fun save(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult =
        store.save(path, document, tiles)

    override fun saveWithThumbnail(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, thumbnailPng: ByteArray): SaveResult =
        store.saveWithThumbnail(path, document, tiles, thumbnailPng)

    override fun load(path: String): LoadResult = store.load(path)
}

private class AndroidLocalFiles : DocumentFileSystem {
    override fun read(path: String): ByteArray = File(path).readBytes()

    override fun write(path: String, bytes: ByteArray) {
        val target = File(path)
        target.parentFile?.mkdirs()
        target.outputStream().use { it.write(bytes) }
    }

    override fun replaceAtomically(source: String, target: String) {
        val sourcePath = File(source).toPath()
        val targetPath = File(target).toPath()
        try {
            Files.move(sourcePath, targetPath, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            // Android filesystems do not always expose atomic moves; a completed sibling file is still moved last.
            Files.move(sourcePath, targetPath, REPLACE_EXISTING)
        }
    }

    override fun exists(path: String): Boolean = File(path).exists()
}
