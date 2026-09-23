package com.neoworksuite.neocanvas.brushes

import java.io.File
import kotlin.test.*

class NeoNaturePackArtifactTest {
    @Test fun build_reproducible_installable_pack_artifact() {
        val source = File("src/commonMain/resources/brush-packs/neo-nature-studio/artwork")
            .takeIf(File::isDirectory) ?: File("brushes/src/commonMain/resources/brush-packs/neo-nature-studio/artwork")
        val artwork = linkedMapOf(
            "cover.png" to File(source, "cover.png").readBytes(),
            "example.png" to File(source, "example.png").readBytes(),
        )
        val pack = NeoNatureStudio.pack.copy(artwork = artwork)
        val first = NeoBrushPackCodec.encode(pack)
        val second = NeoBrushPackCodec.encode(pack)
        assertContentEquals(first, second)
        val decoded = NeoBrushPackCodec.decode(first, "1.0.0")
        assertEquals(18, decoded.brushes.size)
        assertEquals(setOf("cover.png", "example.png"), decoded.artwork.keys)
        val output = File("build/brush-packs/Neo-Nature-Studio.neobrushpack")
        output.parentFile.mkdirs(); output.writeBytes(first)
        assertEquals(Sha256.hex(first), Sha256.hex(output.readBytes()))
    }
}
