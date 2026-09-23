package com.neoworksuite.neocanvas.ui

import kotlin.test.*

class PendingBrushImportTest {
    @Test fun cold_launch_delivers_once_when_editor_attaches() {
        val queue = PendingExternalImportQueue(maxBytes = 16)
        val received = mutableListOf<ByteArray>()
        assertTrue(queue.offer("sample.neobrushpack", byteArrayOf(1, 2)))
        queue.attach { received += it.bytes }
        queue.attach { received += it.bytes }
        assertEquals(1, received.size)
    }

    @Test fun warm_open_cancel_oversize_and_unrelated_files_are_bounded() {
        val queue = PendingExternalImportQueue(maxBytes = 4)
        var received: PendingBrushImport? = null
        queue.attach { received = it }
        assertFalse(queue.offer("notes.txt", byteArrayOf(1)))
        assertFalse(queue.offer("huge.neobrush", ByteArray(5)))
        assertTrue(queue.offer("brush.neobrush", byteArrayOf(1)))
        assertEquals("brush.neobrush", received?.name)
        queue.cancel(); assertNull(queue.pending)
    }
}
