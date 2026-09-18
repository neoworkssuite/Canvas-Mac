# NeoCanvas Gallery and Editor Rail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a local-only Gallery as NeoCanvas's startup screen on Windows and Android, including thumbnails, preview, bulk actions, stacks and drag ordering; replace the editor rail with vertical Size/Undo/Redo/Flow controls; and produce a directly runnable Windows distribution.

**Architecture:** Add immutable Gallery metadata and an index codec to core, deterministic thumbnail rendering to renderer, a host-neutral Gallery boundary/state machine to UI, and filesystem adapters in each host. Keep `.neocanvas` as the artwork source of truth while `gallery-index.json` owns cross-document ordering, names and stack membership; route the shared Compose app between Gallery, Preview and Editor.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.9, sparse RGBA8 tiles, ZIP `.neocanvas` packages, atomic local filesystem writes, Windows Compose Desktop packaging, Android app-local storage.

**Spec:** `docs/superpowers/specs/2026-09-17-neocanvas-gallery-and-rail-design.md`

## Global Constraints

- Remain entirely local: no accounts, network, telemetry, or cloud dependencies.
- Support Windows mouse/keyboard and Android tablet touch/stylus from shared UI.
- Preserve backward compatibility with every existing v1 `.neocanvas` file.
- Never discard dirty editor state without explicit Save or Discard.
- Confirm every destructive Gallery operation and make file deletion recoverable through app-local Trash.
- Store stacks as one level only; stacks contain artwork, never other stacks.
- Use test-first development: observe the targeted test fail before production code is added.
- This workspace has no `.git` repository; replace commit steps with named verification checkpoints and do not initialize Git without user authorization.

## File Structure

### Core

- Create `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryModel.kt` — immutable artwork, stack and snapshot types plus normalization.
- Create `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryIndexCodec.kt` — deterministic JSON index encoding/decoding and validation.
- Create `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryModelTest.kt`.
- Create `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryIndexCodecTest.kt`.
- Modify `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackage.kt` — accept generated thumbnail bytes while reading old packages unchanged.

### Renderer

- Create `renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/GalleryThumbnail.kt` — flatten, aspect-fit and PNG encode.
- Create `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/GalleryThumbnailTest.kt`.

### Shared UI

- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryActions.kt` — host boundary and result types.
- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryState.kt` — destinations, selection, pending confirmations and commands.
- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryScreen.kt` — responsive root/stack grid.
- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryArtworkCard.kt` — swipe, rename and drag behavior.
- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryPreview.kt` — full-screen non-mutating preview.
- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryDialogs.kt` — confirmations and mutation errors.
- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/AppCoordinator.kt` — recovery-gated Gallery/editor navigation.
- Create `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/VerticalRail.kt` — vertical slider primitive and editor rail.
- Modify `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/NeoCanvasApp.kt` — startup routing and recovery gating.
- Modify `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt` — Gallery artwork association and safe return.
- Modify `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorFileActions.kt` — extend `GalleryActions` or delegate to it.
- Modify `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ToolBar.kt` — Gallery button, remove duplicate top Undo/Redo, delegate rail.
- Replace `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/LocalLibraryDialogs.kt` after Gallery parity is proven.
- Add focused tests under `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/gallery/` and `VerticalRailTest.kt`.

### Hosts

- Create `windowsApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/platform/WindowsGalleryStore.kt`.
- Create `androidApp/src/main/kotlin/com/neoworksuite/neocanvas/platform/AndroidGalleryStore.kt`.
- Modify both `*EditorFileActions.kt` adapters to delegate managed artwork operations.
- Add `WindowsGalleryStoreTest.kt` and replace/extend `LocalLibraryTest.kt`.
- Modify `windowsApp/build.gradle.kts` only if needed to make `createDistributable` an explicit documented artifact.
- Create `windowsApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/platform/WindowsLaunchContract.kt` — distinct runtime and installer paths.
- Create `scripts/test-windows.ps1` as a convenience wrapper with an explicit working directory check and direct app-image launch.

---

### Task 1: Immutable Gallery model and ordering rules

**Files:**
- Create: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryModel.kt`
- Test: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryModelTest.kt`

**Interfaces:**
- Produces: `GalleryArtwork`, `GalleryStack`, `GallerySnapshot.normalized()`, `GallerySnapshot.reorderArtwork(...)`, `GallerySnapshot.createStack(...)`, `GallerySnapshot.moveArtwork(...)`, `GallerySnapshot.unstack(...)`.
- Consumes: no host or renderer types.

- [ ] **Step 1: Write failing model tests**

```kotlin
@Test fun normalization_repairs_duplicate_order_without_losing_items() {
    val snapshot = GallerySnapshot(
        artworks = listOf(art("a", order = 4), art("b", order = 4)),
        stacks = emptyList(),
    ).normalized()
    assertEquals(listOf("a", "b"), snapshot.artworks.sortedBy { it.order }.map { it.id })
    assertEquals(listOf(0, 1), snapshot.artworks.sortedBy { it.order }.map { it.order })
}

@Test fun create_stack_moves_only_selected_root_artwork_in_selection_order() {
    val result = root("a", "b", "c").createStack("stack-1", "Ideas", listOf("c", "a"))
    assertEquals("stack-1", result.artworks.single { it.id == "c" }.stackId)
    assertEquals(listOf("c", "a"), result.artworks.filter { it.stackId == "stack-1" }.sortedBy { it.order }.map { it.id })
    assertEquals(listOf("b"), result.artworks.filter { it.stackId == null }.map { it.id })
}
```

- [ ] **Step 2: Run red test**

Run: `./gradlew.bat :core:desktopTest --tests "*GalleryModelTest*"`  
Expected: compilation failure because Gallery model types do not exist.

- [ ] **Step 3: Implement validated immutable types**

```kotlin
data class GalleryArtwork(
    val id: String, val displayName: String, val width: Int, val height: Int,
    val modifiedEpochMillis: Long, val thumbnailPng: ByteArray?, val stackId: String?, val order: Int,
)
data class GalleryStack(val id: String, val displayName: String, val order: Int)
data class GallerySnapshot(val artworks: List<GalleryArtwork>, val stacks: List<GalleryStack>) {
    fun normalized(): GallerySnapshot
    fun reorderArtwork(id: String, targetOrder: Int, stackId: String?): GallerySnapshot
    fun createStack(id: String, name: String, artworkIds: List<String>): GallerySnapshot
    fun moveArtwork(ids: List<String>, stackId: String?): GallerySnapshot
    fun unstack(stackId: String): GallerySnapshot
}
```

Validate nonblank IDs/names, positive dimensions, unique IDs, existing stack references and nonnegative order. Copy byte arrays and lists at construction boundaries.

- [ ] **Step 4: Run green test and full core tests**

Run: `./gradlew.bat :core:allTests`  
Expected: all tests pass.

- [ ] **Step 5: Record checkpoint `gallery-model-green` in the plan notes**

Record the command and result because Git is unavailable.

### Task 2: Gallery index codec and interrupted-write recovery contract

**Files:**
- Create: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryIndexCodec.kt`
- Test: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/gallery/GalleryIndexCodecTest.kt`

**Interfaces:**
- Consumes: `GallerySnapshot` from Task 1.
- Produces: `GalleryIndexCodec.encode(snapshot): ByteArray`, `GalleryIndexCodec.decode(bytes): GalleryIndexResult`.

- [ ] **Step 1: Write codec failure/round-trip tests**

```kotlin
@Test fun index_round_trip_is_deterministic_and_does_not_store_thumbnail_bytes() {
    val source = GallerySnapshot(listOf(art("a", thumbnail = byteArrayOf(1, 2))), listOf(stack("s")))
    val first = GalleryIndexCodec.encode(source)
    val second = GalleryIndexCodec.encode(source)
    assertContentEquals(first, second)
    assertFalse(first.decodeToString().contains("thumbnailPng"))
    assertEquals(source.withoutThumbnails(), assertIs<GalleryIndexResult.Success>(GalleryIndexCodec.decode(first)).snapshot)
}

@Test fun unknown_version_is_incompatible_and_malformed_json_is_corrupt() {
    assertIs<GalleryIndexResult.Incompatible>(GalleryIndexCodec.decode("{\"version\":99}".encodeToByteArray()))
    assertIs<GalleryIndexResult.Corrupt>(GalleryIndexCodec.decode("{".encodeToByteArray()))
}
```

- [ ] **Step 2: Run red test**

Run: `./gradlew.bat :core:desktopTest --tests "*GalleryIndexCodecTest*"`  
Expected: compilation failure for missing codec.

- [ ] **Step 3: Implement version-one deterministic JSON codec**

Use sorted stacks/artwork and escape the same JSON characters supported by `NeoCanvasPackage`. Persist only ID, display name, stack ID and order. Treat unknown version as incompatible and malformed/duplicate/cyclic data as corrupt; normalize order after successful decode.

- [ ] **Step 4: Verify codec and core suite**

Run: `./gradlew.bat :core:allTests`  
Expected: all pass.

- [ ] **Step 5: Record checkpoint `gallery-index-green`**

### Task 3: Deterministic thumbnails and package thumbnail migration

**Files:**
- Create: `renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/GalleryThumbnail.kt`
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackage.kt`
- Test: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/GalleryThumbnailTest.kt`
- Test: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackageTest.kt`

**Interfaces:**
- Consumes: `PngExporter.render`, package document/tiles.
- Produces: `GalleryThumbnail.render(document, tiles, maxWidth = 320, maxHeight = 240): PngImage`; `NeoCanvasPackage.write(..., thumbnailPng: ByteArray = transparentThumbnail())`; `NeoCanvasPackage.readThumbnail(bytes): ByteArray`.

- [ ] **Step 1: Write failing aspect/blend/package tests**

```kotlin
@Test fun wide_canvas_thumbnail_fits_320_by_240_without_distortion() {
    val image = GalleryThumbnail.render(wideDocument(640, 320), wideTiles(), 320, 240)
    assertEquals(320, image.width)
    assertEquals(160, image.height)
    assertContentEquals(expectedBlendedCenterPixel, image.rgbaAt(160, 80))
}

@Test fun supplied_thumbnail_round_trips_while_old_default_remains_readable() {
    val custom = GalleryThumbnail.render(document, tiles).encode()
    val bytes = NeoCanvasPackage.write(document, tiles, custom)
    assertContentEquals(custom, NeoCanvasPackage.readThumbnail(bytes))
    assertIs<LoadResult.Success>(NeoCanvasPackage.read(NeoCanvasPackage.write(document, tiles)))
}
```

- [ ] **Step 2: Run red tests**

Run: `./gradlew.bat :renderer:desktopTest --tests "*GalleryThumbnailTest*" :core:desktopTest --tests "*NeoCanvasPackageTest*"`  
Expected: missing API failures.

- [ ] **Step 3: Implement bounded bilinear thumbnail scaling**

Flatten with the existing compositor, calculate `scale = min(maxWidth / width, maxHeight / height, 1f)`, and sample destination pixel centers bilinearly. Return at least 1×1. Pass the encoded thumbnail into package `thumb.png`; keep default transparent thumbnail for old callers.

- [ ] **Step 4: Verify renderer/core**

Run: `./gradlew.bat :core:allTests :renderer:allTests`  
Expected: all pass.

- [ ] **Step 5: Record checkpoint `gallery-thumbnail-green`**

### Task 4: Shared Gallery boundary and state machine

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryActions.kt`
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryState.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryStateTest.kt`

**Interfaces:**
- Consumes: core Gallery model and existing `LoadResult`/`SaveResult`.
- Produces: `GalleryActions`, `GalleryDestination`, `GalleryPendingAction`, `GalleryState`.

- [ ] **Step 1: Write failing state-transition tests**

```kotlin
@Test fun gallery_is_initial_destination_and_open_transitions_only_after_success() {
    val actions = FakeGalleryActions(snapshot = root("a"), openResult = successDocument())
    val state = GalleryState(actions)
    assertEquals(GalleryDestination.Root, state.destination)
    state.openArtwork("a")
    assertIs<GalleryDestination.Editor>(state.destination)
    assertEquals("a", state.activeArtworkId)
}

@Test fun bulk_delete_waits_for_confirmation_and_cancel_changes_nothing() {
    val actions = FakeGalleryActions(snapshot = root("a", "b"))
    val state = GalleryState(actions).apply { toggleSelection("a"); toggleSelection("b") }
    state.requestDeleteSelection()
    assertIs<GalleryPendingAction.DeleteArtworks>(state.pendingAction)
    state.cancelPendingAction()
    assertEquals(setOf("a", "b"), state.snapshot.artworks.map { it.id }.toSet())
    assertEquals(0, actions.deleteCalls)
}
```

- [ ] **Step 2: Run red test**

Run: `./gradlew.bat :ui:desktopTest --tests "*GalleryStateTest*"`  
Expected: missing Gallery boundary/state failures.

- [ ] **Step 3: Implement exact boundary**

```kotlin
interface GalleryActions {
    fun loadGallery(): GalleryLoadResult
    fun openArtwork(id: String): LoadResult
    fun loadPreview(id: String): Result<ByteArray>
    fun saveArtwork(id: String?, displayName: String, document: CanvasDocument, tiles: Map<TileAddress, ByteArray>): GallerySaveResult
    fun importDocument(): GalleryMutationResult
    fun importImage(onResult: (GalleryMutationResult) -> Unit)
    fun renameArtwork(id: String, name: String): GalleryMutationResult
    fun duplicateArtwork(ids: List<String>): GalleryMutationResult
    fun deleteArtwork(ids: List<String>): GalleryMutationResult
    fun exportArtwork(ids: List<String>): GalleryMutationResult
    fun writeSnapshot(snapshot: GallerySnapshot): GalleryMutationResult
}

sealed interface GalleryLoadResult {
    data class Success(val snapshot: GallerySnapshot, val warning: String? = null) : GalleryLoadResult
    data class Failure(val message: String) : GalleryLoadResult
}

sealed interface GalleryMutationResult {
    data class Success(val snapshot: GallerySnapshot) : GalleryMutationResult
    data class Failure(val message: String) : GalleryMutationResult
}

sealed interface GallerySaveResult {
    data class Success(val artworkId: String, val snapshot: GallerySnapshot) : GallerySaveResult
    data class Failure(val message: String) : GallerySaveResult
}
```

`FakeGalleryActions` lives in the UI test source set and keeps a real snapshot plus explicit read/mutation counters. `GalleryState` owns selection, root/stack scope, preview index, pending confirmations, error text and refresh-after-mutation. It never manipulates paths.

- [ ] **Step 4: Verify UI state tests**

Run: `./gradlew.bat :ui:desktopTest --tests "*GalleryStateTest*"`  
Expected: pass.

- [ ] **Step 5: Record checkpoint `gallery-state-green`**

### Task 5: Windows and Android managed Gallery stores

**Files:**
- Create: `windowsApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/platform/WindowsGalleryStore.kt`
- Create: `androidApp/src/main/kotlin/com/neoworksuite/neocanvas/platform/AndroidGalleryStore.kt`
- Modify: `WindowsEditorFileActions.kt`, `AndroidEditorFileActions.kt`
- Test: `WindowsGalleryStoreTest.kt`, `LocalLibraryTest.kt`

**Interfaces:**
- Consumes: `GalleryActions`, codec, thumbnail renderer, `DocumentStore`.
- Produces: complete local filesystem implementation on both hosts.

- [ ] **Step 1: Write failing filesystem contract tests for both hosts**

Use temporary directories and the same assertions on each adapter. Each host test declares `private fun runGalleryStoreContract(create: (File) -> GalleryActions)` and supplies its concrete store factory:

```kotlin
@Test fun save_duplicate_rename_reorder_and_reopen_preserve_gallery() {
    val store = create(tempDir)
    val a = store.saveArtwork(null, "First", document, tiles).artworkId
    val b = store.duplicateArtwork(listOf(a)).snapshot.artworks.single { it.id != a }.id
    assertIs<GalleryMutationResult.Failure>(store.renameArtwork(b, "first"))
    assertIs<GalleryMutationResult.Success>(store.renameArtwork(b, "Second"))
    store.writeSnapshot(store.loadGallery().snapshot.reorderArtwork(b, 0, null))
    assertEquals(listOf(b, a), create(tempDir).loadGallery().snapshot.artworks.sortedBy { it.order }.map { it.id })
}

@Test fun corrupt_index_rebuilds_from_documents_and_interrupted_delete_keeps_trash_copy() {
    writeValidArtwork("a.neocanvas")
    indexFile.writeText("{")
    assertEquals(listOf("a"), store.loadGallery().snapshot.artworks.map { it.id })
    store.deleteArtwork(listOf("a"))
    assertTrue(trashDir.listFiles().orEmpty().any { it.extension == "neocanvas" })
}
```

- [ ] **Step 2: Run red platform tests**

Run: `./gradlew.bat :windowsApp:jvmTest --tests "*WindowsGalleryStoreTest*" :androidApp:testDebugUnitTest --tests "*LocalLibraryTest*"`  
Expected: missing store/API failures.

- [ ] **Step 3: Implement shared store algorithm in each host**

Use UUID filenames, an atomic `gallery-index.tmp` replacement, `thumbnails/<id>.png`, and `trash/<timestamp>-<id>.neocanvas`. Validate display names with Unicode letters/numbers/spaces/underscore/hyphen/parentheses, reject Windows reserved basenames case-insensitively, and detect name collisions case-insensitively. Rebuild metadata from readable package manifests if index decode fails.

- [ ] **Step 4: Connect existing Save/Open adapters**

Windows uses `%LOCALAPPDATA%/NeoCanvas/Gallery`; Android uses its injected local directory. Existing chooser-based external Save As/Open remain Import/Export boundaries, not Gallery storage.

- [ ] **Step 5: Verify both host tests**

Run: `./gradlew.bat :windowsApp:jvmTest :androidApp:testDebugUnitTest`  
Expected: pass.

- [ ] **Step 6: Record checkpoint `gallery-stores-green`**

### Task 6: Gallery-first routing and safe editor navigation

**Files:**
- Modify: `NeoCanvasApp.kt`, `EditorState.kt`, `ToolBar.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryRoutingTest.kt`

**Interfaces:**
- Consumes: `GalleryState` and host Gallery actions.
- Produces: recovery-gated Gallery startup and dirty-safe Gallery return.

- [ ] **Step 1: Write failing routing tests**

```kotlin
@Test fun startup_reaches_gallery_after_recovery_check() = runTest {
    val coordinator = AppCoordinator(editorState(actions), GalleryState(actions))
    coordinator.finishRecoveryCheck(null)
    assertEquals(AppDestination.Gallery, coordinator.destination)
}

@Test fun gallery_request_from_dirty_editor_waits_for_user_choice() {
    val coordinator = dirtyEditorCoordinator()
    coordinator.requestGallery()
    assertEquals(AppDestination.Editor, coordinator.destination)
    assertEquals(PendingDocumentAction.Gallery, coordinator.editor.pendingDocumentAction)
    coordinator.editor.discardAndContinue()
    assertEquals(AppDestination.Gallery, coordinator.destination)
}
```

- [ ] **Step 2: Run red test**

Run: `./gradlew.bat :ui:desktopTest --tests "*GalleryRoutingTest*"`  
Expected: missing coordinator/destination failures.

- [ ] **Step 3: Implement `AppCoordinator` and route composables**

Add `Gallery` to pending document actions, preserve the existing Save/Discard/Cancel dialog, and call a stored callback only after safe resolution. Opening artwork resets editor history and associates `activeGalleryArtworkId`. Successful save updates Gallery and thumbnail before marking editor clean.

- [ ] **Step 4: Verify routing and existing safety tests**

Run: `./gradlew.bat :ui:desktopTest --tests "*GalleryRoutingTest*" :ui:desktopTest --tests "*DocumentSafetyTest*"`  
Expected: pass.

- [ ] **Step 5: Record checkpoint `gallery-routing-green`**

### Task 7: Responsive Gallery grid and single-item actions

**Files:**
- Create: `GalleryScreen.kt`, `GalleryArtworkCard.kt`, `GalleryDialogs.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/gallery/GalleryLayoutTest.kt`

**Interfaces:**
- Consumes: Gallery state/coordinator.
- Produces: responsive grid, new/import/image/open/rename/swipe/export/duplicate/delete UI.

- [ ] **Step 1: Write failing pure layout and action-eligibility tests**

```kotlin
@Test fun gallery_columns_are_tablet_and_desktop_responsive() {
    assertEquals(2, galleryColumnCount(700f))
    assertEquals(3, galleryColumnCount(1000f))
    assertEquals(5, galleryColumnCount(1800f))
}

@Test fun swipe_actions_include_export_duplicate_and_delete_in_that_order() {
    assertEquals(listOf(GalleryCardAction.Export, GalleryCardAction.Duplicate, GalleryCardAction.Delete), galleryCardActions())
}
```

- [ ] **Step 2: Run red tests**

Run: `./gradlew.bat :ui:desktopTest --tests "*GalleryLayoutTest*"`  
Expected: missing layout/action functions.

- [ ] **Step 3: Implement Gallery composables**

Use `LazyVerticalGrid(GridCells.Fixed(galleryColumnCount(width)))`, original NeoCanvas colors, 4:3 thumbnail beds, inline name editing, long-press drag state, and left-swipe action tray. Provide visible action buttons for mouse/keyboard parity. Wire New Canvas, Import, Image, Open, Rename, Export, Duplicate and confirmed Delete to state methods.

- [ ] **Step 4: Verify shared UI and both compilers**

Run: `./gradlew.bat :ui:desktopTest :ui:compileDebugKotlinAndroid :windowsApp:compileKotlinJvm`  
Expected: pass.

- [ ] **Step 5: Record checkpoint `gallery-grid-green`**

### Task 8: Multi-select, stacks and drag ordering

**Files:**
- Modify: `GalleryState.kt`, `GalleryScreen.kt`, `GalleryArtworkCard.kt`, `GalleryDialogs.kt`
- Test: `GalleryBulkAndStackTest.kt`

**Interfaces:**
- Consumes: snapshot mutation rules and host `writeSnapshot`.
- Produces: selection mode, bulk actions, one-level stacks, drag reorder/move.

- [ ] **Step 1: Write failing behavior tests**

```kotlin
@Test fun selected_artwork_can_be_stacked_then_moved_out_without_reordering_other_root_items() {
    val state = stateWithRoot("a", "b", "c")
    state.enterSelection(); state.toggleSelection("b"); state.toggleSelection("c")
    state.stackSelection("References")
    val stack = state.snapshot.stacks.single()
    assertEquals(listOf("b", "c"), state.artworkIn(stack.id).map { it.id })
    state.moveSelectionOutOfStack(listOf("c"))
    assertEquals(listOf("a", "c"), state.rootArtwork().map { it.id })
}

@Test fun nonempty_stack_delete_requires_unstack_or_second_destructive_confirmation() {
    val state = stateWithStack("s", "a")
    state.requestDeleteStack("s")
    assertIs<GalleryPendingAction.ResolveNonEmptyStack>(state.pendingAction)
    state.chooseDeleteStackAndArtwork()
    assertIs<GalleryPendingAction.ConfirmDeleteStackAndArtwork>(state.pendingAction)
}
```

- [ ] **Step 2: Run red tests**

Run: `./gradlew.bat :ui:desktopTest --tests "*GalleryBulkAndStackTest*"`  
Expected: missing bulk/stack operations.

- [ ] **Step 3: Implement toolbar selection mode and stack cards**

Bulk toolbar order: Preview, Export, Duplicate, Stack/Move Out, Delete, Done. Long-press drag reorders in scope; hovering over another artwork creates a stack, hovering over a stack moves artwork into it. Add visible `Move out` for accessibility and desktop parity. Persist each accepted mutation through `writeSnapshot`; revert in-memory snapshot and show error on failure.

- [ ] **Step 4: Verify tests and platform compilation**

Run: `./gradlew.bat :ui:desktopTest :ui:compileDebugKotlinAndroid :windowsApp:compileKotlinJvm`  
Expected: pass.

- [ ] **Step 5: Record checkpoint `gallery-bulk-stacks-green`**

### Task 9: Full-screen Gallery preview

**Files:**
- Create: `GalleryPreview.kt`
- Modify: `GalleryState.kt`, `GalleryScreen.kt`
- Test: `GalleryPreviewStateTest.kt`

**Interfaces:**
- Consumes: ordered scope/selection, cached thumbnail bytes and `GalleryActions.loadPreview`.
- Produces: previous/next navigation and Open transition without mutation.

- [ ] **Step 1: Write failing preview tests**

```kotlin
@Test fun preview_wraps_within_selection_and_never_writes_gallery() {
    val actions = FakeGalleryActions(root("a", "b", "c"))
    val state = GalleryState(actions)
    state.preview(listOf("a", "c"), "c")
    state.previewNext()
    assertEquals("a", state.previewArtworkId)
    state.previewPrevious()
    assertEquals("c", state.previewArtworkId)
    assertEquals(0, actions.mutationCalls)
}
```

- [ ] **Step 2: Run red test**

Run: `./gradlew.bat :ui:desktopTest --tests "*GalleryPreviewStateTest*"`  
Expected: preview API missing.

- [ ] **Step 3: Implement preview UI and controls**

Show stored thumbnail immediately, then load a higher-resolution preview asynchronously through `GalleryActions.loadPreview` from a keyed `LaunchedEffect`; expose swipe, arrow buttons, keyboard Left/Right, Escape/back and Enter/Open. Double-tap opens editor. Preview loading is read-only and must not increment mutation counters.

- [ ] **Step 4: Verify preview and UI suite**

Run: `./gradlew.bat :ui:desktopTest :ui:compileDebugKotlinAndroid`  
Expected: pass.

- [ ] **Step 5: Record checkpoint `gallery-preview-green`**

### Task 10: Vertical Size / Undo / Redo / Flow rail

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/VerticalRail.kt`
- Modify: `ToolBar.kt`, `NeoCanvasApp.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/VerticalRailTest.kt`

**Interfaces:**
- Produces: `verticalSliderValue(pointerY, height, range): Float`, `VerticalRailSlider`, revised `StudioRail`.

- [ ] **Step 1: Write failing slider mapping tests**

```kotlin
@Test fun vertical_slider_maps_top_to_max_and_bottom_to_min() {
    assertEquals(96f, verticalSliderValue(0f, 200f, 1f..96f))
    assertEquals(1f, verticalSliderValue(200f, 200f, 1f..96f))
    assertEquals(48.5f, verticalSliderValue(100f, 200f, 1f..96f), .01f)
}
```

- [ ] **Step 2: Run red test**

Run: `./gradlew.bat :ui:desktopTest --tests "*VerticalRailTest*"`  
Expected: missing mapping function.

- [ ] **Step 3: Implement vertical controls**

Use a Canvas track with pointer input for tap/drag, clamped inverse-Y mapping, accessible content descriptions and a visible thumb. Layout Size/Tolerance above, then Undo and Redo buttons, then Flow, followed by existing color controls. Remove Undo/Redo from `StudioTopBar`; keep Ctrl+Z/Ctrl+Y.

- [ ] **Step 4: Verify rail and editor regression tests**

Run: `./gradlew.bat :ui:desktopTest :ui:compileDebugKotlinAndroid :windowsApp:compileKotlinJvm`  
Expected: pass.

- [ ] **Step 5: Record checkpoint `vertical-rail-green`**

### Task 11: Windows runnable distribution and truthful launch commands

**Files:**
- Modify: `windowsApp/build.gradle.kts`
- Create: `windowsApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/platform/WindowsLaunchContract.kt`
- Create: `scripts/test-windows.ps1`
- Test: `windowsApp/src/jvmTest/kotlin/com/neoworksuite/neocanvas/platform/WindowsLaunchContractTest.kt`

**Interfaces:**
- Produces: portable app at `windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe`; installers remain separate.

- [ ] **Step 1: Write failing launch artifact test**

```kotlin
@Test fun documented_runtime_path_is_not_the_installer_path() {
    assertEquals("windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe", WindowsLaunchContract.runtimeRelativePath)
    assertTrue(WindowsLaunchContract.installerRelativePath.endsWith("NeoCanvas-0.1.0.exe"))
    assertNotEquals(WindowsLaunchContract.runtimeRelativePath, WindowsLaunchContract.installerRelativePath)
}
```

- [ ] **Step 2: Run red test**

Run: `./gradlew.bat :windowsApp:jvmTest --tests "*WindowsLaunchContractTest*"`  
Expected: missing launch contract.

- [ ] **Step 3: Implement launch contract and PowerShell helper**

`scripts/test-windows.ps1` must begin with `Clear-Host`, resolve the repository root from `$PSScriptRoot`, set the bundled Java path, run `:windowsApp:createDistributable`, verify the runtime EXE exists, and `Start-Process` that runtime EXE. It must never launch `main/exe/NeoCanvas-0.1.0.exe` as the app.

- [ ] **Step 4: Build every Windows artifact**

Run: `./gradlew.bat :windowsApp:createDistributable :windowsApp:packageDistributionForCurrentOS`  
Expected: runtime EXE, installer EXE and MSI all exist at their distinct paths.

- [ ] **Step 5: Run launch smoke check**

Run the portable runtime EXE, confirm process remains alive for at least five seconds, confirm one NeoCanvas window is present when UI inspection is available, then close the test process. If window inspection is unavailable, report that manual visual confirmation remains instead of claiming it.

- [ ] **Step 6: Record checkpoint `windows-launch-green`**

### Task 12: Full acceptance, migration cleanup and delivery

**Files:**
- Modify: `LocalLibraryDialogs.kt` (remove after no call sites)
- Modify: `docs/feature-roadmap.md`
- Modify: this plan's checkbox/checkpoint notes.

**Interfaces:**
- Consumes all earlier tasks.
- Produces release artifacts and an honest acceptance report.

- [ ] **Step 1: Run old-package and legacy local-library migration tests**

Run: `./gradlew.bat :core:allTests :renderer:allTests :ui:desktopTest :windowsApp:jvmTest :androidApp:testDebugUnitTest`  
Expected: all pass, including legacy `.neocanvas` and Android filename-library migration.

- [ ] **Step 2: Run platform compilation and Android APK**

Run: `./gradlew.bat :ui:compileDebugKotlinAndroid :androidApp:assembleDebug :windowsApp:compileKotlinJvm`  
Expected: pass and `androidApp/build/outputs/apk/debug/androidApp-debug.apk` exists.

- [ ] **Step 3: Package Windows runtime and installers**

Run: `./gradlew.bat :windowsApp:createDistributable :windowsApp:packageDistributionForCurrentOS`  
Expected: all three Windows artifacts exist.

- [ ] **Step 4: Verify requirement checklist**

Check startup Gallery, thumbnail grid, rename, open, new, import image/document, export, duplicate, confirmed delete, selection, bulk actions, reorder, stack/unstack, preview, dirty navigation, vertical rail, shortcuts, Windows portable launch, EXE/MSI and Android APK. Record any physical-device-only gesture checks explicitly.

- [ ] **Step 5: Remove obsolete dialog only after call-site scan**

Run: `rg -n "LocalLibraryDialogs|localDocuments|namingLocalCopy" ui androidApp windowsApp`  
Expected: no production call sites before deleting the obsolete dialog/state. Run UI tests again after deletion.

- [ ] **Step 6: Record final checkpoint `gallery-release-verified` and hand off exact commands**

Provide PowerShell commands with `Clear-Host` and the full working directory, distinguishing portable runtime from installers.
