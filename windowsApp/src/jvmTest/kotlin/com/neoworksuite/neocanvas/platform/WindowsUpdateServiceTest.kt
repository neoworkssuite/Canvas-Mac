package com.neoworksuite.neocanvas.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsUpdateServiceTest {
    @Test
    fun prefers_windows_installer_asset_from_latest_release() {
        val json = """
            {
              "tag_name": "v1.2.3",
              "html_url": "https://github.com/neoworkssuite/Canvas-Mac/releases/tag/v1.2.3",
              "body": "Windows parity release\\nIncludes pen improvements.",
              "assets": [
                {"browser_download_url": "https://github.com/neoworkssuite/Canvas-Mac/releases/download/v1.2.3/NeoCanvas-1.2.3.exe"},
                {"browser_download_url": "https://github.com/neoworkssuite/Canvas-Mac/releases/download/v1.2.3/NeoCanvas.zip"}
              ]
            }
        """.trimIndent()

        val update = WindowsUpdateService.parseLatestRelease(json)!!
        assertEquals("1.2.3", update.version)
        assertEquals(
            "https://github.com/neoworkssuite/Canvas-Mac/releases/download/v1.2.3/NeoCanvas-1.2.3.exe",
            update.storeUrl,
        )
        assertEquals("Windows parity release\nIncludes pen improvements.", update.releaseNotes)
    }

    @Test
    fun falls_back_to_release_page_when_no_installer_asset_exists() {
        val json = """
            {
              "tag_name": "1.1.0",
              "html_url": "https://github.com/neoworkssuite/Canvas-Mac/releases/tag/1.1.0",
              "assets": []
            }
        """.trimIndent()

        assertEquals(
            "https://github.com/neoworkssuite/Canvas-Mac/releases/tag/1.1.0",
            WindowsUpdateService.parseLatestRelease(json)?.storeUrl,
        )
    }

    @Test
    fun invalid_release_metadata_is_ignored() {
        assertNull(WindowsUpdateService.parseLatestRelease("{\"tag_name\":\"\"}"))
    }
}
