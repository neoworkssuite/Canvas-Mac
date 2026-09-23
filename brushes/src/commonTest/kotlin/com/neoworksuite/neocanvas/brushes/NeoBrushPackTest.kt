package com.neoworksuite.neocanvas.brushes

import kotlin.test.*

class NeoBrushPackTest {
    private fun pack() = NeoBrushPack(
        manifest = NeoBrushPackManifest(
            id = "com.neoworks.nature-test",
            version = "1.0.0",
            name = "Nature Test",
            summary = "Two original landscape brushes.",
            author = "NeoWorks",
            website = "https://neoworkssuite.com",
            licence = "NeoWorks Free Brush Pack Licence",
            minimumAppVersion = "1.0.0",
            brushIds = listOf("oak", "grass"),
        ),
        brushes = linkedMapOf(
            "oak" to BuiltInBrushes.ink.copy(id = "oak", name = "Oak", version = 2, stamp = BrushStamp()),
            "grass" to BuiltInBrushes.ink.copy(id = "grass", name = "Grass", version = 2, stamp = BrushStamp()),
        ),
        assets = linkedMapOf("leaf.png" to byteArrayOf(1, 2, 3)),
    )

    @Test fun pack_round_trip_preserves_manifest_order_brushes_and_assets() {
        val expected = pack()
        val decoded = NeoBrushPackCodec.decode(NeoBrushPackCodec.encode(expected), "1.0.0")
        assertEquals(expected.manifest, decoded.manifest)
        assertEquals(expected.brushes, decoded.brushes)
        assertContentEquals(expected.assets.getValue("leaf.png"), decoded.assets.getValue("leaf.png"))
    }

    @Test fun invalid_hash_undeclared_member_and_minimum_version_fail_closed() {
        val bytes = NeoBrushPackCodec.encode(pack())
        assertFailsWith<PackValidationException> { NeoBrushPackCodec.decode(bytes.copyOf().also { it[40] = (it[40] + 1).toByte() }, "1.0.0") }
        assertFailsWith<PackValidationException> {
            NeoBrushPackCodec.decode(NeoBrushPackCodec.encode(pack().copy(
                manifest = pack().manifest.copy(minimumAppVersion = "9.0.0"),
            )), "1.0.0")
        }
    }
}
