package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.brushes.*
import kotlin.test.*

class BrushPackLibraryTest {
    private fun pack(version: String = "1.0.0", order: List<String> = listOf("oak", "grass")) = NeoBrushPack(
        NeoBrushPackManifest("com.neoworks.nature", version, "Nature Studio", "Landscape brushes", "NeoWorks", "https://neoworkssuite.com", "Free pack", "1.0.0", order),
        LinkedHashMap(order.associateWith { id -> BuiltInBrushes.ink.copy(id = id, name = id.replaceFirstChar(Char::uppercase), categoryId = "com.neoworks.nature") }),
    )

    @Test fun install_update_reorder_and_restart_preserve_stable_favourites() {
        var snapshot: ByteArray? = null
        val state = BrushLibraryState(onPersist = { snapshot = it })
        assertEquals(PackInstallResult.Installed, state.installPack(pack()))
        state.toggleFavourite("oak")
        assertEquals(PackInstallResult.Replaced, state.installPack(pack("1.1.0", listOf("grass", "oak"))))
        val restored = BrushLibraryState(initialSnapshot = snapshot)
        assertTrue(restored.isFavourite("oak"))
        assertEquals(listOf("grass", "oak"), restored.allBrushes.takeLast(2).map { it.id })
    }

    @Test fun failed_install_is_atomic_and_removal_cleans_references() {
        var writes = 0
        val state = BrushLibraryState(onPersist = { writes++ })
        state.installPack(pack())
        val before = state.allBrushes.map { it.id }
        assertFailsWith<IllegalArgumentException> { state.installPack(pack().copy(brushes = linkedMapOf())) }
        assertEquals(before, state.allBrushes.map { it.id })
        state.toggleFavourite("oak")
        assertTrue(state.removePack("com.neoworks.nature"))
        assertFalse(state.isFavourite("oak"))
        assertTrue(writes >= 2)
    }

    @Test fun restart_restores_a_pack_larger_than_the_legacy_two_megabyte_snapshot_limit() {
        var snapshot: ByteArray? = null
        val artwork = linkedMapOf("example.png" to ByteArray(1_100_000) { (it % 251).toByte() })
        val large = pack().copy(artwork = artwork)
        BrushLibraryState(onPersist = { snapshot = it }).installPack(large)
        assertTrue(snapshot!!.size > 2_000_000)
        val restored = BrushLibraryState(initialSnapshot = snapshot)
        assertEquals(1, restored.installedPacks.size)
        assertContentEquals(artwork.getValue("example.png"), restored.installedPacks.single().pack.artwork.getValue("example.png"))
    }
}
