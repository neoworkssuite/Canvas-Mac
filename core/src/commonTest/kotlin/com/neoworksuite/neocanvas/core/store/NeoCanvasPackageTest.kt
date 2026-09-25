package com.neoworksuite.neocanvas.core.store

import com.neoworksuite.neocanvas.core.model.AddRasterLayer
import com.neoworksuite.neocanvas.core.model.ApplyRasterPatch
import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.model.Layer
import com.neoworksuite.neocanvas.core.model.LayerBlendMode
import com.neoworksuite.neocanvas.core.model.LayerGroup
import com.neoworksuite.neocanvas.core.model.LayerMask
import com.neoworksuite.neocanvas.core.model.LayerPayload
import com.neoworksuite.neocanvas.core.model.LineCap
import com.neoworksuite.neocanvas.core.model.LineMarker
import com.neoworksuite.neocanvas.core.model.LineStyle
import com.neoworksuite.neocanvas.core.model.ShapeKind
import com.neoworksuite.neocanvas.core.model.TextAlignment
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

    @Test
    fun groups_and_masks_round_trip_while_legacy_files_keep_flat_defaults() {
        val paintAddress = TileAddress("layer-1", 0, 0)
        val maskAddress = TileAddress("mask-layer-1", 0, 0)
        val document = CanvasDocument(
            "group-mask",
            256,
            256,
            layers = listOf(
                Layer(
                    "layer-1",
                    "Ink",
                    payload = LayerPayload.Raster(setOf(paintAddress)),
                    groupId = "group-1",
                    mask = LayerMask(
                        "mask-layer-1",
                        setOf(maskAddress),
                        enabled = true,
                        inverted = true,
                    ),
                ),
            ),
            groups = listOf(
                LayerGroup(
                    "group-1",
                    "Characters",
                    opacity = .75f,
                    collapsed = true,
                ),
            ),
        )
        val paint = opaqueBlackTile()
        val mask = ByteArray(NeoCanvasPackage.RGBA_TILE_BYTES).also { pixels ->
            for (i in pixels.indices step 4) {
                pixels[i] = 255.toByte()
                pixels[i + 1] = 255.toByte()
                pixels[i + 2] = 255.toByte()
                pixels[i + 3] = 255.toByte()
            }
        }

        val loaded = assertIs<LoadResult.Success>(
            NeoCanvasPackage.read(
                NeoCanvasPackage.write(
                    document,
                    mapOf(paintAddress to paint, maskAddress to mask),
                ),
            ),
        )

        assertEquals("group-1", loaded.document.layers.single().groupId)
        assertEquals("Characters", loaded.document.groups.single().name)
        assertEquals(.75f, loaded.document.groups.single().opacity)
        assertTrue(loaded.document.groups.single().collapsed)
        assertEquals("mask-layer-1", loaded.document.layers.single().mask!!.id)
        assertTrue(loaded.document.layers.single().mask!!.inverted)
        assertContentEquals(mask, loaded.tiles.getValue(maskAddress))

        val legacy = assertIs<LoadResult.Success>(NeoCanvasPackage.readMembers(mapOf(
            "manifest.json" to manifest().encodeToByteArray(),
            "thumb.png" to transparentThumbnail(),
            "assets/" to ByteArray(0),
        )))
        assertTrue(legacy.document.groups.isEmpty())
        assertEquals(null, legacy.document.layers.single().groupId)
        assertEquals(null, legacy.document.layers.single().mask)
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
    fun format_v2_round_trips_editable_text_and_shapes_while_v1_raster_stays_readable() {
        val document = CanvasDocument(
            "objects-v2", 1024, 768,
            layers = listOf(
                Layer("text-1", "Headline", payload = LayerPayload.TextObject(
                    text = "Own your software again.",
                    fontSize = 72f,
                    colorArgb = 0xff112233.toInt(),
                    x = 100f, y = 90f, width = 720f, height = 160f,
                    rotationDegrees = -4f,
                    alignment = TextAlignment.Center,
                    bold = true,
                    italic = true,
                    lineSpacing = 1.65f,
                    tracking = 2.5f,
                    baselineOffset = -6f,
                    underline = true,
                    uppercase = true,
                )),
                Layer("shape-1", "Frame", payload = LayerPayload.ShapeObject(
                    kind = ShapeKind.Rectangle,
                    x = 80f, y = 70f, width = 760f, height = 220f,
                    fillArgb = null,
                    strokeArgb = 0xff00aaff.toInt(),
                    strokeWidth = 8f,
                    rotationDegrees = 2f,
                    cornerRadius = 28f,
                )),
            ),
        )
        val loaded = assertIs<LoadResult.Success>(NeoCanvasPackage.read(NeoCanvasPackage.write(document, emptyMap())))
        assertEquals(2, NeoCanvasPackage.FORMAT_VERSION)
        val text = assertIs<LayerPayload.TextObject>(loaded.document.layers[0].payload)
        assertEquals("Own your software again.", text.text)
        assertEquals(TextAlignment.Center, text.alignment)
        assertTrue(text.bold)
        assertTrue(text.italic)
        assertEquals(1.65f, text.lineSpacing)
        assertEquals(2.5f, text.tracking)
        assertEquals(-6f, text.baselineOffset)
        assertTrue(text.underline)
        assertTrue(text.uppercase)
        val shape = assertIs<LayerPayload.ShapeObject>(loaded.document.layers[1].payload)
        assertEquals(ShapeKind.Rectangle, shape.kind)
        assertEquals(null, shape.fillArgb)
        assertEquals(8f, shape.strokeWidth)
        assertEquals(28f, shape.cornerRadius)

        val legacy = assertIs<LoadResult.Success>(NeoCanvasPackage.readMembers(mapOf(
            "manifest.json" to manifest(formatVersion = 1).encodeToByteArray(),
            "thumb.png" to transparentThumbnail(),
            "assets/" to ByteArray(0),
        )))
        assertIs<LayerPayload.Raster>(legacy.document.layers.single().payload)
    }

    @Test
    fun line_style_round_trips_while_legacy_and_unknown_values_use_safe_defaults() {
        val line = LayerPayload.ShapeObject(
            kind = ShapeKind.Line,
            x = 10f,
            y = 20f,
            width = 120f,
            height = -40f,
            fillArgb = null,
            strokeArgb = 0xff336699.toInt(),
            strokeWidth = 7f,
            lineStyle = LineStyle.Dashed,
            lineCap = LineCap.Square,
            startMarker = LineMarker.Arrow,
            endMarker = LineMarker.None,
            angleSnapping = false,
        )
        val document = CanvasDocument(
            id = "styled-line",
            width = 300,
            height = 200,
            layers = listOf(Layer("line-1", "Line", payload = line)),
        )

        val roundTrip = assertIs<LoadResult.Success>(
            NeoCanvasPackage.read(NeoCanvasPackage.write(document, emptyMap())),
        )
        assertEquals(line, roundTrip.document.layers.single().payload)

        val legacyManifest = shapeManifest()
        val legacy = assertIs<LoadResult.Success>(NeoCanvasPackage.readMembers(mapOf(
            "manifest.json" to legacyManifest.encodeToByteArray(),
            "thumb.png" to transparentThumbnail(),
            "assets/" to ByteArray(0),
        )))
        val legacyLine = assertIs<LayerPayload.ShapeObject>(legacy.document.layers.single().payload)
        assertEquals(LineStyle.Solid, legacyLine.lineStyle)
        assertEquals(LineCap.Round, legacyLine.lineCap)
        assertEquals(LineMarker.None, legacyLine.startMarker)
        assertEquals(LineMarker.None, legacyLine.endMarker)
        assertTrue(legacyLine.angleSnapping)

        val unknown = assertIs<LoadResult.Success>(NeoCanvasPackage.readMembers(mapOf(
            "manifest.json" to legacyManifest.replace(
                "\"cornerRadius\":0",
                "\"cornerRadius\":0,\"lineStyle\":\"FutureStyle\",\"lineCap\":\"FutureCap\",\"startMarker\":\"FutureMarker\",\"endMarker\":\"Arrow\",\"angleSnapping\":false",
            ).encodeToByteArray(),
            "thumb.png" to transparentThumbnail(),
            "assets/" to ByteArray(0),
        )))
        val unknownLine = assertIs<LayerPayload.ShapeObject>(unknown.document.layers.single().payload)
        assertEquals(LineStyle.Solid, unknownLine.lineStyle)
        assertEquals(LineCap.Round, unknownLine.lineCap)
        assertEquals(LineMarker.None, unknownLine.startMarker)
        assertEquals(LineMarker.Arrow, unknownLine.endMarker)
        assertEquals(false, unknownLine.angleSnapping)
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

    private fun shapeManifest(): String = """
        {"formatVersion":2,"document":{"id":"legacy-line","width":300,"height":200},"layers":[{"id":"line-1","name":"Line","visible":true,"opacity":1,"type":"shape","shapeKind":"Line","x":10,"y":20,"width":120,"height":-40,"fillEnabled":false,"fillArgb":0,"strokeEnabled":true,"strokeArgb":-13408615,"strokeWidth":7,"rotationDegrees":0,"cornerRadius":0}]}
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
