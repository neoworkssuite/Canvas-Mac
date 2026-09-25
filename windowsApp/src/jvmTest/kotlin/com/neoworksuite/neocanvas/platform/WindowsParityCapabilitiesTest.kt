package com.neoworksuite.neocanvas.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsParityCapabilitiesTest {
    @Test
    fun professional_exports_expose_shared_windows_capabilities() {
        val actions = WindowsEditorFileActions()
        assertTrue(actions.supportsPsdImport)
        assertTrue(actions.supportsPsdExport)
        assertTrue(actions.supportsEditableObjectPsdFlattening)
        assertTrue(actions.supportsJpegExport)
        assertTrue(actions.supportsTiffExport)
        assertTrue(actions.supportsFontImport)
        assertTrue(actions.supportsPdfExport)
        assertTrue(actions.supportsUpdateChecks)
    }

    @Test
    fun external_urls_require_https() {
        val actions = WindowsEditorFileActions()
        assertFalse(actions.openExternalUrl("http://example.invalid"))
    }
}
