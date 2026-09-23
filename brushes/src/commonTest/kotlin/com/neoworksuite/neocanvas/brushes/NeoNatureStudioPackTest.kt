package com.neoworksuite.neocanvas.brushes

import kotlin.test.*

class NeoNatureStudioPackTest {
    @Test fun pack_contains_the_exact_eighteen_original_brushes_and_round_trips() {
        val expected = listOf(
            "Oak Canopy", "Distant Tree Line", "Pine Tree Builder", "Pine Bough", "Branch and Twig", "Rough Bark",
            "Dense Leaf Cluster", "Fine Leaves", "Broad Tropical Leaves", "Hedge Builder", "Fern", "Moss and Ground Cover",
            "Wild Grass", "Meadow Grass", "Rock and Gravel", "Cloud Builder", "Mountain Texture", "Water Reflection",
        )
        val pack = NeoNatureStudio.pack
        assertEquals(expected, pack.brushes.values.map { it.name })
        assertEquals(18, pack.brushes.keys.distinct().size)
        assertTrue(pack.brushes.values.all { it.version == 2 && it.stamp != null })
        val decoded = NeoBrushPackCodec.decode(NeoBrushPackCodec.encode(pack), "1.0.0")
        assertEquals(expected, decoded.brushes.values.map { it.name })
        assertEquals("NeoWorks Free Brush Pack Licence", decoded.manifest.licence)
    }
}
