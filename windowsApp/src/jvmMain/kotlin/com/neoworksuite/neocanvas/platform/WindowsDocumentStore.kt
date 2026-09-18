package com.neoworksuite.neocanvas.platform

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.DocumentFileSystem
import com.neoworksuite.neocanvas.core.store.DocumentStore
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SafeDocumentStore
import com.neoworksuite.neocanvas.core.store.SaveResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class WindowsDocumentStore(files: DocumentFileSystem = WindowsLocalFiles()) : DocumentStore {
    private val store = SafeDocumentStore(files)
    override fun save(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): SaveResult = store.save(path, document, tiles)
    override fun saveWithThumbnail(path: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>, thumbnailPng: ByteArray): SaveResult =
        store.saveWithThumbnail(path, document, tiles, thumbnailPng)
    override fun load(path: String): LoadResult = store.load(path)
}

private class WindowsLocalFiles : DocumentFileSystem {
    override fun read(path: String): ByteArray = Files.readAllBytes(Path.of(path))
    override fun write(path: String, bytes: ByteArray) { Path.of(path).let { target -> target.parent?.let(Files::createDirectories); Files.write(target, bytes) } }
    override fun replaceAtomically(source: String, target: String) {
        try { Files.move(Path.of(source), Path.of(target), ATOMIC_MOVE, REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(Path.of(source), Path.of(target), REPLACE_EXISTING) }
    }
    override fun exists(path: String): Boolean = Files.exists(Path.of(path))
}
