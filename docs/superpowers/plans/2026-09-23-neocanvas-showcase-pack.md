# NeoCanvas Curated Showcase Pack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build four editable NeoCanvas showcase documents and an automated iPad pack of real advanced-feature screenshots, finished artwork, and honest NeoWorks marketing assets.

**Architecture:** A versioned JSON catalogue describes original raster components, native layers, capture scenes, captions, and provenance. A platform-neutral fixture builder converts decoded RGBA components into real `CanvasDocument` and `TileStore` content; a debug-only iOS launch configuration opens each fixture in a deterministic editor state. A separate XCTest suite captures the scenes, and a Python packager validates and indexes manual and marketing outputs without changing the existing screenshot pack.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, NeoCanvas core/renderer APIs, Swift/XCTest/XCUITest, Python 3 standard library, GitHub Actions, built-in image generation.

**Spec:** `docs/superpowers/specs/2026-09-23-neocanvas-showcase-pack-design.md`

## Global Constraints

- Work only in the existing `Canvas-Mac` repository on `ipad-gestures-phase1`; verify and fast-forward the live branch before implementation.
- Preserve the existing ten-image manual screenshot pack and ordinary application launch behaviour.
- Use only original generated artwork and the user-supplied NeoWorks Suite icons; never use competitor or unlicensed imagery.
- Keep manual captures free of advertising overlays and place branded variants only under `marketing/`.
- Identify generated components accurately; do not claim they were hand-painted entirely in NeoCanvas.
- Keep showcase fixtures out of Release application resources and out of customers' normal galleries.
- Never overwrite the user's supplied icon files.
- Use deterministic English (`en_GB`), landscape iPad capture, 9:41 status bar time, full battery, and a clean simulator.
- Each showcase document must have 8–15 meaningful named layers and remain editable after package round-trip.
- Required direct coverage is populated layers, selections, transformations, Liquify, object arrangement, and export dialogs.

## Review Focus

- A missing, corrupt, wrongly sized, or non-alpha source component must fail validation with the artwork/component path, never create a partly blank fixture. Covered by Task 2.
- Duplicate document, layer, group, scene, or output identifiers must fail before launch or packaging. Covered by Tasks 2, 3, and 6.
- A normal Release launch or a malformed launch argument must open the ordinary Gallery without bundled showcase content. Covered by Task 4.
- Liquify before/after screenshots must be visually different while the unrelated layers remain unchanged. Covered by Tasks 4 and 5.
- Attachment exports with XCTest-renamed filenames, duplicates, missing captures, or mismatched dimensions must be handled deterministically. Covered by Task 6.

## File Structure

New and changed responsibilities are intentionally separated:

- `assets/showcase/catalogue.json` — single source of truth for artworks, components, layers, scenes, captions, alt text, and provenance.
- `assets/showcase/source/<artwork>/*.png` — original generated raster components at 2048×1536.
- `assets/showcase/suite-icons/*.png` — non-destructive, normalized copies of approved NeoWorks icons.
- `assets/showcase/README.md` — rights/provenance and correct marketing wording.
- `scripts/validate-showcase-assets.py` — source catalogue, PNG, alpha, identifier, and digest validation.
- `scripts/package-showcase-screenshots.py` — attachment collection and final `manual/`/`marketing/` pack generation.
- `scripts/tests/test_validate_showcase_assets.py` — source validation tests.
- `scripts/tests/test_package_showcase_screenshots.py` — packaging and manifest tests.
- `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseFixtures.kt` — catalogue-facing models plus deterministic RGBA-to-document fixture builder.
- `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseScenes.kt` — scene names and editor-state preparation only.
- `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseFixturesTest.kt` — document, tiles, identifiers, and round-trip tests.
- `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseScenesTest.kt` — state preparation and Liquify isolation tests.
- `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosShowcaseLoader.kt` — debug-only bundle PNG/catalogue decoding and argument parsing.
- `ui/src/iosTest/kotlin/com/neoworksuite/neocanvas/ui/IosShowcaseLoaderTest.kt` — iOS parser and resource-error tests.
- `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/NeoCanvasApp.kt` — accepts an optional verified showcase launch and opens Editor only for that launch.
- `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/MainViewController.kt` — obtains the optional debug showcase launch configuration.
- `iosApp/project.yml` — debug resource inclusion and Release exclusion.
- `iosApp/NeoCanvasUITests/NeoCanvasShowcaseScreenshotTests.swift` — captures the 24-scene pack.
- `.github/workflows/ipad-build.yml` — dispatch input, capture job steps, validation, and artifact upload.

---

### Task 1: Produce Original Showcase Components and Provenance

**Files:**
- Create: `assets/showcase/catalogue.json`
- Create: `assets/showcase/README.md`
- Create: `assets/showcase/source/neon-metropolis/background.png`
- Create: `assets/showcase/source/neon-metropolis/subject.png`
- Create: `assets/showcase/source/neon-metropolis/foreground.png`
- Create: `assets/showcase/source/chromaflow-creature/background.png`
- Create: `assets/showcase/source/chromaflow-creature/subject.png`
- Create: `assets/showcase/source/chromaflow-creature/accents.png`
- Create: `assets/showcase/source/cosmic-muse/background.png`
- Create: `assets/showcase/source/cosmic-muse/subject.png`
- Create: `assets/showcase/source/cosmic-muse/atmosphere.png`
- Create: `assets/showcase/source/neoworks-launch/background.png`
- Create: `assets/showcase/source/neoworks-launch/lighting.png`
- Create: `assets/showcase/suite-icons/*.png`

**Interfaces:**
- Consumes: the nine user-supplied icon PNGs and the approved design specification.
- Produces: catalogue schema version `1`; four 2048×1536 artwork definitions; component PNGs; stable artwork/component/layer/scene ids; approved copy and provenance for Tasks 2–7.

- [ ] **Step 1: Copy the supplied icons non-destructively and normalize filenames**

Copy, never move or edit, the supplied files to:

```text
assets/showcase/suite-icons/neocanvas.png
assets/showcase/suite-icons/calendar.png
assets/showcase/suite-icons/dark-room.png
assets/showcase/suite-icons/database.png
assets/showcase/suite-icons/design.png
assets/showcase/suite-icons/pdf.png
assets/showcase/suite-icons/photo.png
assets/showcase/suite-icons/sheets.png
assets/showcase/suite-icons/writer.png
```

Record each original filename and SHA-256 digest in `catalogue.json` under `provenance.suppliedIcons`.

- [ ] **Step 2: Generate the Neon Metropolis components with the built-in image tool**

Use the supplied Design and NeoCanvas icons as style references, not edit targets. Make three separate 2048×1536 images with identical perspective and palette:

```text
Use case: ads-marketing
Asset type: editable digital-art showcase component
Primary request: original cinematic futuristic metropolis artwork for NeoCanvas, with a deep navy city, cyan and magenta glass-neon architecture, and clear visual separation for later editing
Input images: Design icon and NeoCanvas icon as colour/material references only
Composition/framing: landscape 4:3, central avenue, safe margins for iPad editor chrome
Lighting/mood: luminous cyan and magenta, polished but not overexposed
Constraints: no brands, no readable signage, no watermark, no interface, no copied characters
```

Generate `background.png` as the opaque sky/distant-city plate, `subject.png` as a transparent creative-figure/main-buildings plate, and `foreground.png` as transparent foreground lighting and atmosphere. Inspect all three at original detail and regenerate any component with pseudo-text, damaged edges, or mismatched perspective.

- [ ] **Step 3: Generate the Chromaflow Creature components**

```text
Use case: stylized-concept
Asset type: editable digital-art showcase component
Primary request: original friendly creature made from flowing cyan, violet, pink, and orange paint ribbons, designed to show visible Liquify deformation
Input images: Design icon as colour/material reference only
Composition/framing: landscape 4:3, creature centered, broad clean silhouette, generous edge clearance
Lighting/mood: playful, glossy, energetic
Constraints: no text, no logo, no watermark, no resemblance to known characters
```

Generate an opaque `background.png`, transparent `subject.png`, and transparent `accents.png`. Ensure the subject has at least two broad ribbon regions suitable for push, twirl, and pinch demonstrations.

- [ ] **Step 4: Generate the Cosmic Muse components**

```text
Use case: stylized-concept
Asset type: editable digital-art showcase component
Primary request: original inclusive space-inspired human portrait with orbital rings, nebula light, and illuminated particles in the NeoWorks neon-glass palette
Input images: Dark Room and Photo icons as colour/material references only
Composition/framing: landscape 4:3, portrait left of center, orbital negative space on the right
Lighting/mood: contemplative, premium, luminous rim light
Constraints: original adult subject, natural anatomy, no celebrity likeness, no text, no watermark
```

Generate an opaque `background.png`, transparent `subject.png`, and transparent `atmosphere.png`. Inspect face, hands if present, edge alpha, and particle cleanliness at original detail.

- [ ] **Step 5: Generate the NeoWorks Launch background components**

```text
Use case: ads-marketing
Asset type: editable suite campaign background
Primary request: clean futuristic NeoWorks launch-stage background with a subtle perspective grid and restrained cyan-violet glass lighting
Input images: all supplied suite icons as brand-family references only
Composition/framing: landscape 4:3, clear central hero zone and lower strip for suite icons
Lighting/mood: premium technology campaign, uncluttered
Constraints: no icons baked into the image, no text, no logos, no watermark
```

Generate opaque `background.png` and transparent `lighting.png`; the real supplied icons and editable NeoCanvas text will be placed as separate layers.

- [ ] **Step 6: Write the catalogue and provenance guide**

Use this top-level JSON contract exactly:

```json
{
  "schemaVersion": 1,
  "canvas": {"width": 2048, "height": 1536},
  "artworks": [],
  "captures": [],
  "provenance": {"generatedComponents": [], "suppliedIcons": []}
}
```

Every artwork entry has `id`, `title`, `documentId`, `components`, `groups`, `layers`, `marketingLine`, and `altText`. Every layer has `id`, `name`, `kind`, `source`, `groupId`, `opacity`, and `blendMode`. Use the exact layer names and artwork intent from the specification; add native text/shape/light layers until each document has 8–15 layers.

Document the approved phrases:

```text
Original artwork assembled and edited in NeoCanvas.
Created in NeoCanvas.
```

The second phrase is permitted only for final compositions that were actually assembled and edited in the app.

- [ ] **Step 7: Inspect every retained source at original detail**

Reject any source with pseudo-text, third-party marks, malformed anatomy, clipped alpha, mismatched lighting, or insufficient selection/Liquify clearance. Record the inspection result and final image SHA-256 digest in `catalogue.json`.

- [ ] **Step 8: Commit the approved source set**

```bash
git add assets/showcase
git commit -m "Add original NeoCanvas showcase artwork sources"
```

---

### Task 2: Validate the Showcase Catalogue and Source Images

**Files:**
- Create: `scripts/validate-showcase-assets.py`
- Create: `scripts/tests/test_validate_showcase_assets.py`

**Interfaces:**
- Consumes: `assets/showcase/catalogue.json` and every path referenced by `artworks[].components` and `provenance.suppliedIcons`.
- Produces: `validate_catalogue(root: Path) -> dict`; normalized validated catalogue; actionable `ValueError` messages used by local and CI gates.

- [ ] **Step 1: Write failing tests for valid source metadata and PNG dimensions**

```python
def test_validate_catalogue_accepts_complete_assets(self):
    root = make_valid_showcase(self.temp_path)
    result = MODULE.validate_catalogue(root)
    self.assertEqual(result["schemaVersion"], 1)
    self.assertEqual(len(result["artworks"]), 4)

def test_validate_catalogue_reports_bad_component_dimensions(self):
    root = make_valid_showcase(self.temp_path)
    write_png(root / "source/neon-metropolis/subject.png", 1024, 1024, rgba=True)
    with self.assertRaisesRegex(ValueError, "neon-metropolis/subject.png.*2048.*1536"):
        MODULE.validate_catalogue(root)
```

- [ ] **Step 2: Run tests and confirm the missing module failure**

Run: `python -m unittest scripts.tests.test_validate_showcase_assets -v`  
Expected: FAIL because `scripts/validate-showcase-assets.py` does not exist.

- [ ] **Step 3: Implement strict catalogue, PNG, alpha, digest, and identifier validation**

Implement these rules in `validate_catalogue`:

```python
EXPECTED_ARTWORKS = {
    "neon-metropolis", "chromaflow-creature", "cosmic-muse", "neoworks-launch"
}
EXPECTED_SIZE = (2048, 1536)

def require_unique(values: list[str], label: str) -> None:
    duplicates = sorted({value for value in values if values.count(value) > 1})
    if duplicates:
        raise ValueError(f"Duplicate {label}: {', '.join(duplicates)}")
```

Read IHDR without third-party dependencies, require colour type 6 (RGBA) for transparent components, validate file SHA-256 against provenance, require four artworks, require 8–15 unique layers per document, validate group references, and require unique capture filenames and scene ids. Errors include the offending path or id.

- [ ] **Step 4: Add tests for every Review Focus input owned by this task**

Add explicit tests for missing files, invalid PNG signature, missing alpha, digest mismatch, duplicate artwork ids, duplicate layer ids, unknown groups, duplicate scene ids, and layer counts outside 8–15.

- [ ] **Step 5: Run the focused test suite and live asset validation**

Run:

```bash
python -m unittest scripts.tests.test_validate_showcase_assets -v
python scripts/validate-showcase-assets.py --root assets/showcase
```

Expected: all tests pass and the command reports four artworks, all component counts, and no validation errors.

- [ ] **Step 6: Commit the validation gate**

```bash
git add scripts/validate-showcase-assets.py scripts/tests/test_validate_showcase_assets.py
git commit -m "Validate NeoCanvas showcase source assets"
```

---

### Task 3: Build Deterministic Editable NeoCanvas Fixtures

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseFixtures.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseFixturesTest.kt`

**Interfaces:**
- Consumes: `ShowcaseDefinition`, `ShowcaseLayerDefinition`, and `Map<String, ShowcaseRaster>` decoded by Task 4.
- Produces: `ShowcaseFixtureBuilder.build(definition, rasters): ShowcaseFixture`; `ShowcaseFixture(document: CanvasDocument, tiles: Map<TileKey, ByteArray>)`.

- [ ] **Step 1: Write failing fixture tests with synthetic RGBA rasters**

```kotlin
@Test
fun buildsStableEditableDocumentAndTiles() {
    val fixture = ShowcaseFixtureBuilder.build(sampleDefinition(), sampleRasters())
    assertEquals("showcase-neon-metropolis", fixture.document.id)
    assertEquals(listOf("Sky Gradient", "Distant City", "Title"), fixture.document.layers.map(Layer::name))
    assertTrue(fixture.tiles.isNotEmpty())
    assertTrue(fixture.document.layers.any { it.payload is LayerPayload.TextObject })
}

@Test
fun rejectsMissingRasterWithLayerAndSourceName() {
    val error = assertFailsWith<IllegalArgumentException> {
        ShowcaseFixtureBuilder.build(sampleDefinition(), emptyMap())
    }
    assertTrue(error.message.orEmpty().contains("background.png"))
}
```

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run: `./gradlew :ui:allTests --tests '*ShowcaseFixturesTest*'`  
Expected: FAIL because the fixture types do not exist.

- [ ] **Step 3: Implement focused catalogue-facing types and RGBA tiling**

Define:

```kotlin
data class ShowcaseRaster(val width: Int, val height: Int, val rgba: ByteArray)
data class ShowcaseFixture(val document: CanvasDocument, val tiles: Map<TileKey, ByteArray>)
data class ShowcaseLayerDefinition(
    val id: String,
    val name: String,
    val kind: ShowcaseLayerKind,
    val source: String? = null,
    val groupId: String? = null,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.Normal,
    val text: String? = null,
)
data class ShowcaseDefinition(
    val id: String,
    val documentId: String,
    val width: Int,
    val height: Int,
    val groups: List<LayerGroup>,
    val layers: List<ShowcaseLayerDefinition>,
)
```

`ShowcaseFixtureBuilder` splits RGBA pixels into 256×256 `TileKey` buffers, pads edge tiles with transparent pixels, skips fully transparent tiles, creates `TileAddress` sets matching emitted tiles, and creates editable text/shape layers natively. Reject dimension mismatch, byte-count mismatch, missing source, duplicate ids, unknown groups, and a tile key whose layer id disagrees with its document layer.

- [ ] **Step 4: Add round-trip and determinism tests**

Convert `TileKey` to `TileAddress`, call `NeoCanvasPackage.write`, read it back, and assert identical document metadata and tile bytes. Build twice and assert equal document and tile map. Add explicit duplicate-id and unknown-group tests from Review Focus.

- [ ] **Step 5: Run fixture and existing package tests**

Run:

```bash
./gradlew :ui:allTests --tests '*ShowcaseFixturesTest*'
./gradlew :core:allTests --tests '*NeoCanvasPackageTest*'
```

Expected: PASS.

- [ ] **Step 6: Commit the fixture builder**

```bash
git add ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseFixtures.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseFixturesTest.kt
git commit -m "Build deterministic layered showcase fixtures"
```

---

### Task 4: Add Debug-Only iPad Showcase Launch Scenes

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseScenes.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ShowcaseScenesTest.kt`
- Create: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosShowcaseLoader.kt`
- Create: `ui/src/iosTest/kotlin/com/neoworksuite/neocanvas/ui/IosShowcaseLoaderTest.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/NeoCanvasApp.kt`
- Modify: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/MainViewController.kt`
- Modify: `iosApp/project.yml`

**Interfaces:**
- Consumes: Task 3's `ShowcaseFixture`; arguments `--neocanvas-showcase <artwork-id>` and `--neocanvas-scene <scene-id>`.
- Produces: `ShowcaseLaunch(fixture, scene)`; `ShowcaseScenePreparer.prepare(state, scene)`; optional `showcaseLaunch` parameter on `NeoCanvasApp`; ordinary Gallery fallback.

- [ ] **Step 1: Write failing scene-preparation tests**

```kotlin
@Test
fun layersSceneOpensKnownLayerAndInspector() {
    val state = fixtureEditorState("neon-metropolis")
    ShowcaseScenePreparer.prepare(state, ShowcaseScene.Layers)
    assertEquals("city-main-buildings", state.activeLayerId)
    assertTrue(state.inspectorVisible)
    assertEquals(InspectorPanel.Layers, state.inspectorPanel)
}

@Test
fun liquifyAfterChangesOnlySubjectLayer() {
    val state = fixtureEditorState("chromaflow-creature")
    val before = state.tileStore.snapshot()
    ShowcaseScenePreparer.prepare(state, ShowcaseScene.LiquifyPush)
    val changedLayers = changedLayerIds(before, state.tileStore.snapshot())
    assertEquals(setOf("chromaflow-body"), changedLayers)
}
```

- [ ] **Step 2: Run scene tests and confirm the missing-type failure**

Run: `./gradlew :ui:allTests --tests '*ShowcaseScenesTest*'`  
Expected: FAIL because `ShowcaseScenePreparer` does not exist.

- [ ] **Step 3: Implement deterministic scene preparation**

Define exact scene ids:

```kotlin
enum class ShowcaseScene(val id: String) {
    Completed("completed"), Layers("layers"), SelectionRectangle("selection-rectangle"),
    SelectionLasso("selection-lasso"), TransformScale("transform-scale"),
    TransformRotate("transform-rotate"), Arrange("arrange"), LiquifyControls("liquify-controls"),
    LiquifyBefore("liquify-before"), LiquifyPush("liquify-push"),
    LiquifyTwirl("liquify-twirl"), ExportMenu("export-menu"),
    ExportPsd("export-psd"), ExportFormats("export-formats")
}
```

Define the launch boundary explicitly:

```kotlin
data class ShowcaseLaunchRequest(val artworkId: String, val scene: ShowcaseScene)
data class ShowcaseLaunch(val fixture: ShowcaseFixture, val scene: ShowcaseScene)
```

Prepare selection bounds, transform session values, known active layers, inspector panels, Liquify mode/size/strength, and deterministic Liquify strokes through public `EditorState` operations. Do not add screenshot-only pixels or fabricated dialogs.

- [ ] **Step 4: Write failing iOS launch parser tests**

```kotlin
@Test
fun parsesKnownDebugLaunch() {
    val request = parseShowcaseArguments(listOf("app", "--neocanvas-showcase", "cosmic-muse", "--neocanvas-scene", "layers"))
    assertEquals(ShowcaseLaunchRequest("cosmic-muse", ShowcaseScene.Layers), request)
}

@Test
fun malformedOrUnknownLaunchReturnsNull() {
    assertNull(parseShowcaseArguments(listOf("app", "--neocanvas-showcase", "unknown")))
}
```

- [ ] **Step 5: Implement the iOS loader and Release isolation**

In debug builds, read `ProcessInfo.processInfo.arguments`, decode the catalogue and PNG resources from `ShowcaseFixtures.bundle`, construct the fixture, and return `ShowcaseLaunch`. In non-debug builds, return `null` without reading the arguments.

Configure `iosApp/project.yml` so `assets/showcase` is copied only for Debug and excluded from Release using a dedicated `ShowcaseFixtures` resource group and Release `EXCLUDED_SOURCE_FILE_NAMES`. Add a build-time Release assertion that the generated `.app` contains no `catalogue.json` beneath Showcase resources.

- [ ] **Step 6: Wire optional launch state into the app**

Change the entry signatures to:

```kotlin
@Composable
fun NeoCanvasApp(
    fileActions: EditorFileActions = UnavailableEditorFileActions,
    state: EditorState = rememberEditorState(fileActions),
    showcaseLaunch: ShowcaseLaunch? = null,
)

fun MainViewController(updateLookup: NativeUpdateLookup? = null): UIViewController
```

`MainViewController` creates the showcase state only when the debug loader returns a valid launch. `NeoCanvasApp` starts at Editor and prepares the requested scene for that launch; otherwise it starts at Gallery exactly as before.

- [ ] **Step 7: Run scene, iOS, and regression tests**

Run:

```bash
./gradlew :ui:allTests --tests '*Showcase*'
./gradlew :ui:allTests --tests '*EditorStateTest*'
./gradlew :ui:allTests --tests '*LiquifyEditorStateTest*'
./scripts/prepare-ios.sh
xcodebuild -project iosApp/NeoCanvas.xcodeproj -scheme NeoCanvas -configuration Release -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build
```

Inspect the Release `.app` and assert that `catalogue.json`, `neon-metropolis`, and `suite-icons` are absent.

- [ ] **Step 8: Commit deterministic test-only launches**

```bash
git add ui/src iosApp/project.yml
git commit -m "Add deterministic iPad showcase scenes"
```

---

### Task 5: Capture the 24 Real iPad Showcase Screens

**Files:**
- Create: `iosApp/NeoCanvasUITests/NeoCanvasShowcaseScreenshotTests.swift`
- Modify only if required for accessibility: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/LayersPanel.kt`
- Modify only if required for accessibility: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/LiquifyPanel.kt`
- Modify only if required for accessibility: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ToolBar.kt`
- Modify only if required for accessibility: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt`

**Interfaces:**
- Consumes: Task 4's launch arguments and accessibility-ready scene states.
- Produces: 24 retained `XCTAttachment` PNGs with exact stable names.

- [ ] **Step 1: Write the expected capture inventory in the UI test**

Use exactly these files:

```text
01-neon-metropolis-completed.png
02-neon-metropolis-layers.png
03-neon-metropolis-selection-rectangle.png
04-neon-metropolis-selection-lasso.png
05-neon-metropolis-transform-scale.png
06-neon-metropolis-transform-rotate.png
07-chromaflow-completed.png
08-chromaflow-layers.png
09-chromaflow-liquify-controls.png
10-chromaflow-liquify-before.png
11-chromaflow-liquify-push.png
12-chromaflow-liquify-twirl.png
13-cosmic-muse-completed.png
14-cosmic-muse-layers.png
15-cosmic-muse-effects.png
16-cosmic-muse-mask.png
17-neoworks-launch-completed.png
18-neoworks-launch-layers.png
19-neoworks-launch-arrange.png
20-neoworks-launch-transform.png
21-neoworks-launch-export-menu.png
22-neoworks-launch-export-formats.png
23-neoworks-launch-export-psd.png
24-showcase-gallery.png
```

If production verification establishes that mask creation/editing is not exposed, replace `16-cosmic-muse-mask.png` with `16-cosmic-muse-colour-grade.png` in both catalogue and tests; do not simulate a mask UI.

- [ ] **Step 2: Implement one isolated app launch per state**

Use a helper with this contract:

```swift
private func launch(artwork: String, scene: String, readyIdentifier: String) -> XCUIApplication {
    let app = XCUIApplication()
    app.launchArguments = [
        "-AppleLanguages", "(en)", "-AppleLocale", "en_GB",
        "--neocanvas-showcase", artwork,
        "--neocanvas-scene", scene
    ]
    app.launch()
    XCTAssertTrue(app.descendants(matching: .any).matching(identifier: readyIdentifier).firstMatch.waitForExistence(timeout: 20))
    return app
}
```

Capture only after a scene-specific identifier exists. Set every attachment name exactly and use `.keepAlways`.

- [ ] **Step 3: Add only the accessibility identifiers the tests require**

Use semantic identifiers such as `showcase-scene-ready`, `layers-panel`, `selection-overlay`, `transform-overlay`, `liquify-panel`, `export-menu`, and `psd-compatibility-panel`. Do not add hidden automation buttons or screenshot-only product UI.

- [ ] **Step 4: Generate the project and run the showcase test on a clean iPad simulator**

Run:

```bash
./scripts/prepare-ios.sh
xcodebuild -project iosApp/NeoCanvas.xcodeproj -scheme NeoCanvas -configuration Debug -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5)' -resultBundlePath artifacts/NeoCanvas-Showcase-Screenshots.xcresult CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO test -only-testing:NeoCanvasUITests/NeoCanvasShowcaseScreenshotTests
```

Expected: PASS with 24 retained PNG attachments.

- [ ] **Step 5: Inspect all screenshots at original detail**

Reject and correct captures with clipped panels, unreadable layer names, inactive handles, blank tiles, inconsistent art, simulator permission alerts, or identical Liquify before/after pixels.

- [ ] **Step 6: Re-run the existing manual screenshot UI test**

Run the existing `NeoCanvasManualScreenshotTests/testCaptureManualScreenshotPack` target. Expected: PASS with the original ten filenames unchanged.

- [ ] **Step 7: Commit the UI capture suite**

```bash
git add iosApp/NeoCanvasUITests ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui
git commit -m "Capture advanced NeoCanvas showcase screens"
```

---

### Task 6: Package Manual and Marketing Collections

**Files:**
- Create: `scripts/package-showcase-screenshots.py`
- Create: `scripts/tests/test_package_showcase_screenshots.py`
- Create: `assets/showcase/marketing-layouts.json`

**Interfaces:**
- Consumes: exported XCTest attachments, Task 1 catalogue, `--commit`, `--device`, and `--directory`.
- Produces: `manual/*.png`, `marketing/*.png`, `manifest.json`, `captions.md`, `usage-guide.md`, and `contact-sheet.html`.

- [ ] **Step 1: Write failing tests for attachment collection and structured output**

```python
def test_build_pack_separates_manual_and_marketing(self):
    attachments, output, catalogue = make_complete_input(self.temp_path)
    manifest = MODULE.build_pack(attachments, output, catalogue, "abc123", "iPad Pro 13-inch")
    self.assertEqual(len(manifest["screenshots"]), 24)
    self.assertTrue((output / "manual/01-neon-metropolis-completed.png").is_file())
    self.assertTrue((output / "marketing/neon-metropolis-hero.png").is_file())

def test_build_pack_rejects_identical_liquify_before_after(self):
    attachments, output, catalogue = make_complete_input(self.temp_path)
    duplicate_png(attachments, "10-chromaflow-liquify-before.png", "11-chromaflow-liquify-push.png")
    with self.assertRaisesRegex(ValueError, "Liquify.*identical"):
        MODULE.build_pack(attachments, output, catalogue, "abc123", "iPad")
```

- [ ] **Step 2: Run tests and confirm the missing-module failure**

Run: `python -m unittest scripts.tests.test_package_showcase_screenshots -v`  
Expected: FAIL because the packager does not exist.

- [ ] **Step 3: Implement deterministic attachment resolution and validation**

Reuse the proven XCTest manifest traversal pattern from `package-manual-screenshots.py`, generalized to catalogue capture names. Reject duplicate matches, missing captures, invalid PNG signatures, mixed dimensions, duplicate file digests except explicitly allowed comparison baselines, and identical Liquify before/after content.

- [ ] **Step 4: Build clean manual output and metadata**

Copy unmodified real-app screenshots into `manual/`. Write manifest entries with:

```json
{
  "file": "manual/01-neon-metropolis-completed.png",
  "artwork": "neon-metropolis",
  "feature": "completed",
  "width": 2752,
  "height": 2064,
  "caption": "Neon Metropolis open in NeoCanvas.",
  "altText": "NeoCanvas on iPad displaying a neon city artwork.",
  "provenance": "real-app-screenshot"
}
```

Use measured dimensions rather than hard-coding the example values.

- [ ] **Step 5: Build four marketing layouts without changing manual captures**

Use Pillow `12.3.0`, pinned in the showcase CI job, for deterministic PNG composition. The layout contains a screenshot/artwork crop, NeoCanvas badge, marketing line, and restrained suite strip. Never place promotional pixels into `manual/`.

Create:

```text
marketing/neon-metropolis-hero.png
marketing/chromaflow-creature-hero.png
marketing/cosmic-muse-hero.png
marketing/neoworks-launch-hero.png
```

Generate captions, alt text, usage guidance, and an HTML contact sheet covering both collections.

- [ ] **Step 6: Add the remaining packaging edge-case tests**

Test XCTest-renamed attachments, duplicate names, missing files, invalid PNGs, mixed sizes, duplicate scene ids, missing caption/alt text/provenance, preservation of manual screenshot bytes, and marketing failure leaving manual output intact.

- [ ] **Step 7: Run all packaging tests and package a local exported result**

Run:

```bash
python -m unittest scripts.tests.test_package_showcase_screenshots -v
python scripts/package-showcase-screenshots.py --attachments-directory artifacts/showcase-attachments --directory artifacts/showcase-pack --catalogue assets/showcase/catalogue.json --commit "$(git rev-parse HEAD)" --device "iPad Pro 13-inch (M5)"
```

Expected: 24 manual captures, four marketing images, manifest, captions, guide, and contact sheet.

- [ ] **Step 8: Commit the packager**

```bash
git add scripts/package-showcase-screenshots.py scripts/tests/test_package_showcase_screenshots.py assets/showcase/marketing-layouts.json
git commit -m "Package NeoCanvas manual and marketing showcase assets"
```

---

### Task 7: Automate the Showcase Pack in GitHub Actions

**Files:**
- Modify: `.github/workflows/ipad-build.yml`

**Interfaces:**
- Consumes: workflow-dispatch Boolean input `capture_showcase_screenshots`; Tasks 2, 5, and 6 commands.
- Produces: artifact `NeoCanvas-Showcase-Pack-${{ github.sha }}` plus failure diagnostics.

- [ ] **Step 1: Add a workflow-dispatch input and source-validation step**

Add:

```yaml
capture_showcase_screenshots:
  description: Capture the curated artwork and advanced-feature manual pack
  required: false
  default: false
  type: boolean
```

The showcase job uses `if: github.event_name == 'workflow_dispatch' && inputs.capture_showcase_screenshots` and runs `python3 scripts/validate-showcase-assets.py --root assets/showcase` before preparing Xcode.

- [ ] **Step 2: Add the clean-simulator capture job**

Follow the existing manual job's Java, XcodeGen, simulator erase/boot, and status-bar setup. Install the pinned packager dependency with `python3 -m pip install 'Pillow==12.3.0'`. Run only `NeoCanvasShowcaseScreenshotTests`, save `artifacts/NeoCanvas-Showcase-Screenshots.xcresult`, and export attachments to `artifacts/showcase-attachments`.

- [ ] **Step 3: Package and upload success and diagnostic artifacts**

Run `package-showcase-screenshots.py` with `$GITHUB_SHA` and the selected simulator name. Upload `artifacts/showcase-pack/` for 30 days. On failure, upload the `.xcresult`, exported attachments, and partial pack for 14 days.

- [ ] **Step 4: Validate the workflow YAML and Python tests locally**

Run:

```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/ipad-build.yml', encoding='utf-8'))"
python -m unittest discover -s scripts/tests -p 'test_*showcase*.py' -v
```

If PyYAML is unavailable, validate the workflow with the repository's existing YAML checker or GitHub's workflow parser after push; do not add a production dependency solely for this check.

- [ ] **Step 5: Commit the CI workflow**

```bash
git add .github/workflows/ipad-build.yml
git commit -m "Automate curated NeoCanvas showcase pack"
```

---

### Task 8: Full Verification, Live CI Capture, and Delivery

**Files:**
- Modify only when evidence requires corrections: files introduced in Tasks 1–7.
- Produce locally: `outputs/NeoCanvas-Showcase-Pack-<short-sha>/`

**Interfaces:**
- Consumes: the complete implementation and live `ipad-gestures-phase1` branch.
- Produces: passing local verification, successful GitHub run, downloaded visually approved pack, and a clean synchronized branch.

- [ ] **Step 1: Re-fetch and reconcile concurrent branch movement safely**

Run:

```bash
git fetch origin
git status --short --branch
git log --oneline --decorate --max-count=12 --all
```

If origin moved, rebase only the showcase commits onto the live branch without resetting or discarding concurrent work. Resolve overlaps by preserving both current live functionality and the showcase changes.

- [ ] **Step 2: Run the complete relevant local test set**

Run:

```bash
python -m unittest discover -s scripts/tests -v
./gradlew :core:allTests :renderer:allTests :ui:allTests
./scripts/prepare-ios.sh
```

Expected: every command succeeds.

- [ ] **Step 3: Build Debug and Release iPad configurations**

Run unsigned simulator builds for Debug and Release. Confirm Debug contains the showcase bundle and Release contains none of `catalogue.json`, `neon-metropolis`, or `suite-icons`.

- [ ] **Step 4: Push the existing branch and dispatch showcase CI**

Push `ipad-gestures-phase1` without force, dispatch `.github/workflows/ipad-build.yml` with `capture_showcase_screenshots=true`, and monitor the run through completion. Never reset to an older run SHA.

- [ ] **Step 5: Download and inspect the successful artifact**

Extract to `outputs/NeoCanvas-Showcase-Pack-<short-sha>/`. Inspect all 24 manual screenshots, four marketing images, contact sheet, captions, guide, and manifest. Compare file counts, dimensions, commit SHA, device name, and digests against the CI run.

- [ ] **Step 6: Correct any visual or behavioural defects through focused commits**

For each defect, add or tighten the owning test first, make the smallest correction, rerun its focused checks, then repeat the complete capture. Do not hide a real app defect in the marketing compositor.

- [ ] **Step 7: Request whole-branch review and resolve findings**

Review the complete branch diff against the approved specification, with special attention to release isolation, claims/provenance, real feature states, current app behaviour, and binary asset size. Resolve confirmed findings and rerun affected checks.

- [ ] **Step 8: Verify the final branch and report delivery**

Run:

```bash
git fetch origin
git status --short --branch
git rev-parse HEAD
git rev-parse origin/ipad-gestures-phase1
```

Completion requires a clean tree, matching local/remote SHAs, a successful CI URL, and the absolute local output-pack path in the final report.
