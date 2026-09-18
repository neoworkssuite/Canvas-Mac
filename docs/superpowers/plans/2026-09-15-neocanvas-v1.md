# NeoCanvas V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Build a local-only Android and Windows drawing studio that creates, edits, saves, reopens, and exports layered raster artwork.

**Architecture:** Kotlin Multiplatform and Compose Multiplatform share the model, renderer, brushes, and UI. Android and Windows hosts own input and local file access. Commands mutate documents, tiles store raster pixels, and .neocanvas is a versioned ZIP package.

**Tech Stack:** Kotlin 2.4.20, Gradle, Compose Multiplatform, JDK 17+, Kotlin test, Compose UI Test, Android API 26+.

**Spec:** docs/superpowers/specs/2026-09-15-neocanvas-design.md

## Global Constraints

- Support Android and Windows only in v1.
- Make Android tablet-first: optimize the primary layout for landscape tablets, preserve a large canvas, and collapse side panels on smaller screens rather than changing document capabilities.
- Default operation is local: no account, network, telemetry, cloud dependency, or automatic upload.
- Use original names, brushes, icons, and UI; do not copy Procreate assets, formats, or presentation.
- Use a dark, calm, canvas-first workspace with a compact upper toolbar, left brush controls, and a hideable right layer panel; keep NeoCanvas iconography, palette, spacing, and panel design distinct.
- Files use the .neocanvas extension and a versioned ZIP package.
- Every persisted mutation is a DocumentCommand.
- Preserve unsaved work and the last saved file when file operations fail.

---

## File structure

    settings.gradle.kts                         Modules
    core/src/commonMain/.../model/              Document, layer, commands, history
    core/src/commonMain/.../store/              Package and store contracts
    brushes/src/commonMain/.../                 Stroke samples and brush presets
    renderer/src/commonMain/.../                Tiles, compositing, PNG export
    ui/src/commonMain/.../                      Editor state and Compose workspace
    androidApp/src/main/.../                    Android host and adapters
    windowsApp/src/main/.../                    Windows host and adapters

### Task 1: Create a runnable Compose Multiplatform shell

**Files:**
- Create: settings.gradle.kts, build.gradle.kts, gradle.properties, README.md
- Create: core/build.gradle.kts, brushes/build.gradle.kts, renderer/build.gradle.kts, ui/build.gradle.kts
- Create: androidApp/build.gradle.kts, androidApp/src/main/AndroidManifest.xml, androidApp/src/main/kotlin/com/neoworksuite/neocanvas/MainActivity.kt
- Create: windowsApp/build.gradle.kts, windowsApp/src/main/kotlin/com/neoworksuite/neocanvas/Main.kt
- Create: ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/NeoCanvasApp.kt

**Interfaces:**
- Produces: @Composable fun NeoCanvasApp(), used by both hosts.

- [ ] **Step 1: Create settings and module registration**

    rootProject.name = "NeoCanvas"
    include(":core", ":brushes", ":renderer", ":ui", ":androidApp", ":windowsApp")

- [ ] **Step 2: Create minimal shared app and host entry points**

    @Composable fun NeoCanvasApp() = MaterialTheme {
        Box(Modifier.fillMaxSize()) { Text("NeoCanvas") }
    }
    fun main() = application {
        Window(onCloseRequest = ::exitApplication, title = "NeoCanvas") { NeoCanvasApp() }
    }

- [ ] **Step 3: Verify both build paths**

Run: ./gradlew :windowsApp:run :androidApp:testDebugUnitTest

Expected: a Windows window titled “NeoCanvas”; Android test task is successful.

- [ ] **Step 4: Commit**

    git add settings.gradle.kts build.gradle.kts gradle.properties README.md core brushes renderer ui androidApp windowsApp
    git commit -m "feat: scaffold NeoCanvas multiplatform app"

### Task 2: Build the immutable document model and history

**Files:**
- Create: core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/CanvasDocument.kt
- Create: core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/Layer.kt
- Create: core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/DocumentCommand.kt
- Create: core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/DocumentHistory.kt
- Create: core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/model/DocumentHistoryTest.kt

**Interfaces:**
- Produces: CanvasDocument(id, width, height, layers).
- Produces: DocumentCommand.apply(document): CanvasDocument.
- Produces: DocumentHistory.execute(command), undo(): Boolean, redo(): Boolean.

- [ ] **Step 1: Write failing history tests**

    @Test fun undo_restores_the_previous_layer_list() {
        val history = DocumentHistory(CanvasDocument.blank(100, 100))
        history.execute(AddRasterLayer("layer-1", "Ink"))
        history.undo()
        assertTrue(history.current.layers.isEmpty())
    }
    @Test fun opacity_outside_zero_to_one_is_rejected() {
        assertFailsWith<IllegalArgumentException> { SetLayerOpacity("layer-1", 1.1f) }
    }

- [ ] **Step 2: Run tests and observe failure**

Run: ./gradlew :core:allTests --tests '*DocumentHistoryTest*'

Expected: FAIL because model and history types do not exist.

- [ ] **Step 3: Implement minimal model and command boundary**

    sealed interface LayerPayload { data class Raster(val tileKeys: Set<TileKey>) : LayerPayload }
    data class Layer(val id: String, val name: String, val visible: Boolean = true, val opacity: Float = 1f, val payload: LayerPayload)
    sealed interface DocumentCommand { fun apply(document: CanvasDocument): CanvasDocument }
    class DocumentHistory(initial: CanvasDocument) {
        var current: CanvasDocument = initial; private set
        fun execute(command: DocumentCommand) { undoStates.add(current); current = command.apply(current); redoStates.clear() }
        fun undo(): Boolean = undoStates.removeLastOrNull()?.let { redoStates.add(current); current = it; true } ?: false
        fun redo(): Boolean = redoStates.removeLastOrNull()?.let { undoStates.add(current); current = it; true } ?: false
    }

- [ ] **Step 4: Add tests for rename, order, visibility, duplicate, and delete, then verify**

Run: ./gradlew :core:allTests

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

    git add core
    git commit -m "feat: add undoable NeoCanvas document model"

### Task 3: Add tile-based strokes and original brush presets

**Files:**
- Create: renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/TileKey.kt
- Create: renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/TileStore.kt
- Create: brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/StrokeSample.kt
- Create: brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/BrushDefinition.kt
- Create: brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/StrokeInterpolator.kt
- Create: renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/TileStoreTest.kt
- Create: brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/StrokeInterpolatorTest.kt

**Interfaces:**
- Produces: StrokeSample(x, y, timestampMillis, pressure, tiltX, tiltY, pointerKind).
- Produces: interpolate(from, to, spacing): List<StrokeSample>.
- Produces: TileStore.applyPatch(patch): Set<TileKey>.

- [ ] **Step 1: Write failing boundary and stroke-spacing tests**

    @Test fun x_256_is_stored_in_tile_one() { assertEquals(1, tileCoordinate(256)) }
    @Test fun interpolation_includes_end_point() {
        assertEquals(100f, interpolate(sample(0f), sample(100f), 10f).last().x)
    }

- [ ] **Step 2: Verify the tests fail**

Run: ./gradlew :renderer:allTests :brushes:allTests

Expected: FAIL because tile and interpolation implementations do not exist.

- [ ] **Step 3: Implement fixed 256px tiles and five original v1 presets**

    data class TileKey(val layerId: String, val x: Int, val y: Int)
    fun tileCoordinate(pixel: Int): Int = Math.floorDiv(pixel, 256)
    data class BrushDefinition(val id: String, val name: String, val spacing: Float, val baseSize: Float, val opacity: Float)
    object BuiltInBrushes { val pencil = BrushDefinition("neo.pencil", "Pencil", 1f, 4f, 1f) }

Implement Pencil, Ink, Soft Round, Flat Marker, and Eraser; apply each raster mutation through ApplyRasterPatch.

- [ ] **Step 4: Run all model, brush, and renderer tests**

Run: ./gradlew :core:allTests :brushes:allTests :renderer:allTests

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

    git add core brushes renderer
    git commit -m "feat: add tile-based drawing pipeline"

### Task 4: Persist safe local .neocanvas documents

**Files:**
- Create: core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/DocumentStore.kt
- Create: core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackage.kt
- Create: core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/SaveResult.kt
- Create: core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackageTest.kt
- Create: androidApp/src/main/kotlin/com/neoworksuite/neocanvas/platform/AndroidDocumentStore.kt
- Create: windowsApp/src/main/kotlin/com/neoworksuite/neocanvas/platform/WindowsDocumentStore.kt

**Interfaces:**
- Produces: DocumentStore.save(path, document, tiles): SaveResult and load(path): LoadResult.
- Produces: ZIP members manifest.json, layers/<id>/<x>-<y>.png, thumb.png, and assets/.

- [ ] **Step 1: Write failing serialization and compatibility tests**

    @Test fun package_round_trip_preserves_metadata_and_tiles() {
        val loaded = writeAndRead(CanvasDocument.blank(256, 256), onePaintedTile())
        assertEquals(256, loaded.document.width); assertEquals(setOf(TileKey("layer-1", 0, 0)), loaded.tiles.keys)
    }
    @Test fun future_format_version_returns_incompatible_error() {
        assertIs<LoadResult.Incompatible>(readPackage(manifestWithVersion(999)))
    }

- [ ] **Step 2: Verify failure**

Run: ./gradlew :core:allTests --tests '*NeoCanvasPackageTest*'

Expected: FAIL because the package reader/writer does not exist.

- [ ] **Step 3: Implement manifest validation and temporary-file replacement**

    sealed interface SaveResult { data object Success : SaveResult; data class Failure(val message: String) : SaveResult }
    // Write <target>.tmp beside target, close it, atomically replace target, or retain target and return Failure.

- [ ] **Step 4: Test recovery behavior and verify storage**

    @Test fun failed_replacement_keeps_original_and_creates_recovery_copy() {
        val result = storeWithReplacementFailure.save(path, changedDocument, tiles)
        assertIs<SaveResult.Failure>(result); assertEquals(originalBytes, path.readBytes()); assertTrue(recoveryPath.exists())
    }

Run: ./gradlew :core:allTests

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

    git add core androidApp windowsApp
    git commit -m "feat: save NeoCanvas documents locally"

### Task 5: Build the workspace and wire complete drawing flow

**Files:**
- Create: ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt
- Create: ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt
- Create: ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ToolBar.kt
- Create: ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/LayersPanel.kt
- Create: ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ColorPanel.kt
- Create: renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/PngExporter.kt
- Create: ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt
- Create: renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/PngExporterTest.kt
- Create: androidApp/src/main/kotlin/com/neoworksuite/neocanvas/platform/AndroidInputAdapter.kt
- Create: windowsApp/src/main/kotlin/com/neoworksuite/neocanvas/platform/WindowsInputAdapter.kt

**Interfaces:**
- Produces: EditorState with active layer, brush, colour, size, opacity, undo, redo, save, open, and export actions.
- Produces: normalizedPressure(reported: Float?): Float, returning 1f when absent.
- Produces: PngExporter.export(document, tiles, target): SaveResult.

- [ ] **Step 1: Write failing editor and export tests**

    @Test fun mouse_input_defaults_to_full_pressure() { assertEquals(1f, normalizedPressure(null)) }
    @Test fun eraser_keeps_selected_colour() {
        state.color = Color.Red; state.tool = Tool.Eraser; assertEquals(Color.Red, state.color)
    }
    @Test fun png_dimensions_match_document_and_hidden_layers_are_excluded() {
        val png = exportAndDecode(documentWithHiddenBlueLayer()); assertEquals(100, png.width); assertEquals(Color.Red, png.pixel(0, 0))
    }

- [ ] **Step 2: Verify failure**

Run: ./gradlew :ui:allTests :renderer:allTests

Expected: FAIL because editor state, input normalization, and exporter do not exist.

- [ ] **Step 3: Implement accessible workspace and visible gesture alternatives**

    class EditorState(val history: DocumentHistory) {
        var color: Color by mutableStateOf(Color.Black)
        var brushSize: Float by mutableFloatStateOf(4f)
        var brushOpacity: Float by mutableFloatStateOf(1f)
    }
    CanvasWorkspace(state, Modifier.fillMaxSize().semantics { contentDescription = "Drawing canvas" })

Provide brush/eraser, size, opacity, colour, undo/redo, hideable layer panel, pan/zoom, and Windows shortcuts.

- [ ] **Step 4: Connect host pointer input, ApplyRasterPatch, local open/save, and visible-layer PNG export**

    fun normalizedPressure(reported: Float?): Float = reported?.takeIf { it in 0f..1f } ?: 1f

Show actionable errors while retaining the active document on failure.

- [ ] **Step 5: Add the end-to-end local workflow test**

    @Test fun create_draw_save_reopen_and_export_png_preserves_visible_pixels() {
        val reopened = saveDrawReopenAndExport(); assertEquals(Color.Black, reopened.exportedPng.pixel(10, 10))
    }

- [ ] **Step 6: Run complete verification**

Run: ./gradlew check :androidApp:assembleDebug :windowsApp:packageDistributionForCurrentOS

Expected: BUILD SUCCESSFUL, Android debug APK and Windows package generated.

Manual: disconnect networking; create a 1024×1024 file, draw, erase, use layer actions/history, save/reopen/export; simulate save failure and confirm original and recovery copy remain readable.

- [ ] **Step 7: Commit**

    git add core brushes renderer ui androidApp windowsApp
    git commit -m "feat: deliver offline NeoCanvas v1"

## Final verification

- [ ] Execute ./gradlew check :androidApp:assembleDebug :windowsApp:packageDistributionForCurrentOS.
- [ ] Inspect a saved package for manifest.json, layers/, thumb.png, and assets/.
- [ ] Confirm the same document opens with matching visible artwork and layer metadata on Android and Windows.
- [ ] Confirm no code path introduces networking, account prompts, telemetry, or automatic upload.
