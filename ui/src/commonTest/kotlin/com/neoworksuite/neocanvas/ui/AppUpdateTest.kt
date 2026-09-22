package com.neoworksuite.neocanvas.ui

import com.neoworksuite.neocanvas.core.model.CanvasDocument
import com.neoworksuite.neocanvas.core.model.DocumentHistory
import com.neoworksuite.neocanvas.core.model.TileAddress
import com.neoworksuite.neocanvas.core.store.LoadResult
import com.neoworksuite.neocanvas.core.store.SaveResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateTest {
    @Test
    fun semantic_release_versions_compare_numerically() {
        assertTrue(compareReleaseVersions("1.1.0", "1.0.9") > 0)
        assertTrue(compareReleaseVersions("2.0", "1.99.99") > 0)
        assertEquals(0, compareReleaseVersions("1.0.0", "1.0"))
        assertTrue(compareReleaseVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun app_store_lookup_parser_extracts_version_url_and_notes() {
        val parsed = parseAppStoreLookup(
            """{"resultCount":1,"results":[{"version":"1.2.0","trackViewUrl":"https:\/\/apps.apple.com\/gb\/app\/neocanvas\/id123","releaseNotes":"Faster brushes\nNew tools"}]}"""
        )
        requireNotNull(parsed)
        assertEquals("1.2.0", parsed.version)
        assertTrue(parsed.storeUrl.startsWith("https://apps.apple.com/"))
        assertEquals("Faster brushes\nNew tools", parsed.releaseNotes)
        assertNull(parseAppStoreLookup("""{"resultCount":0,"results":[]}"""))
    }

    @Test
    fun newer_release_prompts_and_app_store_handoff_is_explicit() {
        var opened: String? = null
        val actions = object : EditorFileActions {
            override val supportsUpdateChecks = true
            override fun checkForUpdate(onResult: (Result<AppUpdateInfo?>) -> Unit) {
                onResult(Result.success(AppUpdateInfo(
                    version = "1.1.0",
                    storeUrl = "https://apps.apple.com/app/id123",
                    releaseNotes = "Commercial update",
                )))
            }
            override fun openExternalUrl(url: String): Boolean {
                opened = url
                return true
            }
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>) = SaveResult.Success
            override fun open() = LoadResult.Failure("unused")
            override fun exportPng(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>) = SaveResult.Success
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)

        state.checkForUpdates(manual = true)

        assertEquals("1.1.0", state.updateAvailable?.version)
        assertTrue(state.openAvailableUpdate())
        assertEquals("https://apps.apple.com/app/id123", opened)
        state.dismissUpdateNotice()
        assertNull(state.updateAvailable)
    }

    @Test
    fun current_release_does_not_prompt() {
        val actions = object : EditorFileActions {
            override val supportsUpdateChecks = true
            override fun checkForUpdate(onResult: (Result<AppUpdateInfo?>) -> Unit) {
                onResult(Result.success(AppUpdateInfo(
                    version = NeoCanvasReleaseInfo.marketingVersion,
                    storeUrl = "https://apps.apple.com/app/id123",
                )))
            }
            override fun save(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>) = SaveResult.Success
            override fun open() = LoadResult.Failure("unused")
            override fun exportPng(document: CanvasDocument, tiles: Map<TileAddress, ByteArray>) = SaveResult.Success
        }
        val state = EditorState(DocumentHistory(CanvasDocument.blank(16, 16)), actions)

        state.checkForUpdates(manual = true)

        assertNull(state.updateAvailable)
        assertEquals("NeoCanvas is up to date", state.statusMessage)
        assertFalse(state.updateCheckInProgress)
    }
}
