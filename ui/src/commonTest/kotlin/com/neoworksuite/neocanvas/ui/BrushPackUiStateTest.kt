package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.brushes.*
import kotlin.test.*

class BrushPackUiStateTest {
    private fun pack(version: String = "1.0.0") = NeoBrushPack(
        NeoBrushPackManifest("com.neo.pack", version, "Test Pack", "Test", "Community Artist", "https://example.com", "Free", "1.0.0", listOf("pack.leaf")),
        linkedMapOf("pack.leaf" to BuiltInBrushes.ink.copy(id = "pack.leaf", name = "Leaf", categoryId = "com.neo.pack")),
    )

    @Test fun preview_install_update_cancel_and_badges_are_explicit() {
        val library = BrushLibraryState()
        val manager = BrushPackManager(library)
        manager.preview(NeoBrushPackCodec.encode(pack()))
        assertEquals("Imported Pack", manager.pending!!.provenanceLabel)
        assertEquals("Install", manager.primaryActionLabel)
        manager.cancel(); assertNull(manager.pending)
        manager.preview(NeoBrushPackCodec.encode(pack())); manager.install()
        manager.preview(NeoBrushPackCodec.encode(pack("1.1.0")))
        assertEquals("Replace", manager.primaryActionLabel)
    }

    @Test fun removal_requests_graphite_fallback_only_for_active_pack_brush() {
        val library = BrushLibraryState(); val manager = BrushPackManager(library)
        manager.preview(NeoBrushPackCodec.encode(pack())); manager.install()
        assertEquals("neo.pencil", manager.remove("com.neo.pack", "pack.leaf"))
        assertNull(manager.remove("missing", "neo.ink"))
    }

    @Test fun validation_errors_are_sanitized_for_the_install_sheet() {
        val manager = BrushPackManager(BrushLibraryState())
        manager.preview("damaged".encodeToByteArray())
        assertNull(manager.pending)
        assertTrue(manager.errorMessage!!.contains("could not", ignoreCase = true))
        assertFalse(manager.errorMessage!!.contains("\\"))
    }
}
