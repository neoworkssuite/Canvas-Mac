package com.neoworksuite.neocanvas.core.store

import com.neoworksuite.neocanvas.core.model.AddRasterLayer
import com.neoworksuite.neocanvas.core.model.ApplyRasterPatch
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NeoCanvasPackageTest {
    @Test fun supplied_gallery_thumbnail_round_trips() {
        val thumbnail = byteArrayOf(1, 4, 9, 16)
        val bytes = NeoCanvasPackage.write(CanvasDocument.blank(8, 8), emptyMap(), thumbnail)
        assertContentEquals(thumbnail, NeoCanvasPackage.readThumbnail(bytes))
    }
    @Test
    fun package_round_trip_preserves_metadata_and_tiles() {
        val document = paintedDocument()
        val pixels = opaqueBlackTile()

        val loaded = NeoCanvasPackage.read(
            NeoCanvasPackage.write(document, mapOf(TileAddress("layer-1", 0, 0) to pixels)),
        )

        val success = assertIs<LoadResult.Success>(loaded)
        assertEquals("document-1", success.document.id)
        assertEquals(256, success.document.width)
        assertEquals(listOf("layer-1"), success.document.layers.map { it.id })
        assertEquals(setOf(TileAddress("layer-1", 0, 0)), success.tiles.keys)
        assertContentEquals(pixels, success.tiles.getValue(TileAddress("layer-1", 0, 0)))
        assertEquals(LayerBlendMode.Normal, success.document.layers.single().blendMode)
        assertEquals(false, success.document.layers.single().alphaLocked)
    }

    @Test fun blend_and_alpha_lock_round_trip_while_legacy_manifest_uses_defaults() {
        val document = paintedDocument().copy(layers = paintedDocument().layers.map {
            it.copy(alphaLocked = true, blendMode = LayerBlendMode.Multiply)
        })
        val loaded = assertIs<LoadResult.Success>(NeoCanvasPackage.read(
            NeoCanvasPackage.write(document, mapOf(TileAddress("layer-1", 0, 0) to opaqueBlackTile()))))
        assertTrue(loaded.document.layers.single().alphaLocked)
        assertEquals(LayerBlendMode.Multiply, loaded.document.layers.single().blendMode)

        val legacy = assertIs<LoadResult.Success>(NeoCanvasPackage.readMembers(mapOf(
            "manifest.json" to manifest().encodeToByteArray(),
            "thumb.png" to transparentThumbnail(),
            "assets/" to ByteArray(0),
        )))
        assertEquals(false, legacy.document.layers.single().alphaLocked)
        assertEquals(LayerBlendMode.Normal, legacy.document.layers.single().blendMode)
    }

    @Test
    fun future_format_version_returns_incompatible_error() {
        val result = NeoCanvasPackage.readMembers(
            mapOf(
                "manifest.json" to manifest(formatVersion = 999).encodeToByteArray(),
                "thumb.png" to transparentThumbnail(),
                "assets/" to ByteArray(0),
            ),
        )

        assertIs<LoadResult.Incompatible>(result)
    }

    @Test
    fun missing_tile_member_returns_corrupt_error() {
        val result = NeoCanvasPackage.readMembers(
            mapOf(
                "manifest.json" to manifest(tile = "layers/layer-1/0-0.png").encodeToByteArray(),
                "thumb.png" to transparentThumbnail(),
                "assets/" to ByteArray(0),
            ),
        )

        assertIs<LoadResult.Corrupt>(result)
    }

    @Test
    fun failed_replacement_keeps_original_and_creates_recovery_copy() {
        val files = FailingReplacementFiles().apply { put("drawing.neocanvas", byteArrayOf(3, 2, 1)) }
        val store = SafeDocumentStore(files)
        val document = paintedDocument()

        val result = store.save("drawing.neocanvas", document, mapOf(TileAddress("layer-1", 0, 0) to opaqueBlackTile()))

        assertIs<SaveResult.Failure>(result)
        assertContentEquals(byteArrayOf(3, 2, 1), files.read("drawing.neocanvas"))
        assertTrue(files.exists("drawing.neocanvas.recovery.neocanvas"))
        assertIs<LoadResult.Success>(NeoCanvasPackage.read(files.read("drawing.neocanvas.recovery.neocanvas")))
    }

    private fun manifest(formatVersion: Int = 1, tile: String? = null): String = """
        {"formatVersion":$formatVersion,"document":{"id":"document-1","width":256,"height":256},"layers":[{"id":"layer-1","name":"Ink","visible":true,"opacity":1,"type":"raster","tiles":${if (tile == null) "[]" else "[\"$tile\"]"}}]}
    """.trimIndent()

    private fun opaqueBlackTile(): ByteArray = ByteArray(NeoCanvasPackage.RGBA_TILE_BYTES).also {
        it[3] = 0xff.toByte()
    }

    private fun transparentThumbnail(): ByteArray = NeoCanvasPackage.transparentThumbnail()

    private fun paintedDocument(): CanvasDocument =
        ApplyRasterPatch("layer-1", addedTileAddresses = setOf(TileAddress("layer-1", 0, 0))).apply(
            AddRasterLayer("layer-1", "Ink").apply(CanvasDocument.blank(256, 256, id = "document-1")),
        )
}

private class FailingReplacementFiles : DocumentFileSystem {
    private val files = linkedMapOf<String, ByteArray>()

    fun put(path: String, bytes: ByteArray) { files[path] = bytes.copyOf() }

    override fun read(path: String): ByteArray = files.getValue(path).copyOf()

    override fun write(path: String, bytes: ByteArray) { files[path] = bytes.copyOf() }

    override fun replaceAtomically(source: String, target: String) {
        throw IllegalStateException("simulated replacement failure")
    }

    override fun exists(path: String): Boolean = path in files
}
