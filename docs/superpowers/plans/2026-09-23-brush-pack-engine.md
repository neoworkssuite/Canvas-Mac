# NeoCanvas Brush Pack Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a visibly richer V2 brush engine, secure `.neobrushpack` import/export, native iPad Files integration, the original 18-brush Neo Nature Studio pack, website download, and a validated test IPA.

**Architecture:** Extend the current data-only brush model without changing V1 rendering, add bounded grayscale stamp assets behind a small renderer interface, and treat a pack as an atomically validated ZIP-store container. The common modules own all parsing, validation, persistence, and rendering; iOS only supplies file picking/open-in and byte storage, while the website distributes the same signed artifact.

**Tech Stack:** Kotlin Multiplatform 2.4.20, Compose Multiplatform 1.10, common Kotlin tests, sparse raster tiles, UIKit document picker, XcodeGen, GitHub Actions, static website assets.

**Spec:** `docs/superpowers/specs/2026-09-23-brush-pack-engine-design.md`

## Global Constraints

- Existing V1 `.neobrush` files and their rendered output remain compatible.
- Packs and brushes are declarative data only; no scripts, shaders, executable plug-ins, or downloaded native code.
- Shape/grain images are 8-bit grayscale PNG, 8–512 px per side, at most 262,144 decoded pixels and 512 KiB compressed each.
- A brush references at most one shape and one grain asset.
- One pack is at most 25 MiB compressed, 75 MiB unpacked, 100 brushes, and 250 entries.
- Reject absolute paths, parent traversal, links, nested archives, encryption, duplicate normalized paths, undeclared entries, and checksum mismatches.
- A stamp emits at most eight sub-stamps per interpolated sample.
- Pencils and pens retain configured precision spacing.
- Official and community packs use the same parser and limits.
- First-release website packs are free; paid packs and StoreKit are excluded.
- No Procreate `.brush`/`.brushset` or Adobe `.abr` compatibility claims.

## Review Focus

- A valid archive using backslashes or Unicode-equivalent path spellings must not bypass duplicate/traversal detection; Task 3 pins normalized-path rejection.
- A pack update whose brush IDs change order must preserve favourites by stable ID; Task 4 pins reorder/update behavior.
- A V2 brush used with symmetry must derive identical mirrored stamp variation rather than fresh randomness; Task 2 pins symmetry determinism.
- A document open event arriving before the Compose editor is ready must be queued and delivered once; Task 6 pins cold-launch import delivery.
- Removing a pack while one of its brushes is active must select Graphite Pencil and must not corrupt raster artwork; Task 5 pins removal fallback.

---

### Task 1: Versioned Brush V2 Model and Asset Contracts

**Files:**
- Modify: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/BrushDefinition.kt`
- Modify: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushCodec.kt`
- Create: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/BrushStamp.kt`
- Modify: `brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushCodecTest.kt`
- Create: `brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/BrushStampTest.kt`

**Interfaces:**
- Consumes: existing `BrushDefinition`, `BrushDynamics`, and `NeoBrushCodec` V1 format.
- Produces: `BrushStamp`, `StampAngleMode`, `GrainMovement`, `BrushAssetRef`, and a backward-compatible V2 codec.

- [ ] **Step 1: Write failing model and codec tests**

Add tests that construct:

```kotlin
val stamp = BrushStamp(
    shape = BrushAssetRef("oak-leaf", "0".repeat(64)),
    grain = BrushAssetRef("paper-grain", "1".repeat(64)),
    angleMode = StampAngleMode.DirectionJitter,
    angleDegrees = 12f,
    angleJitter = .35f,
    scaleX = 1.25f,
    scaleY = .7f,
    spacingRatio = .24f,
    scatterAlong = .2f,
    scatterAcross = .55f,
    stampCount = 4,
    stampCountJitter = .25f,
    grainScale = 1.4f,
    grainMovement = GrainMovement.Canvas,
    hueJitter = .03f,
    saturationJitter = .08f,
    brightnessJitter = .06f,
    pressureScatter = .5f,
    pressureStampCount = .7f,
    startTaper = .2f,
    endTaper = .15f,
)
val brush = BuiltInBrushes.ink.copy(version = 2, stamp = stamp)
assertEquals(brush, NeoBrushCodec.decode(NeoBrushCodec.encode(brush)))
assertNull(NeoBrushCodec.decode(NeoBrushCodec.encode(BuiltInBrushes.ink)).stamp)
```

Also assert every numeric bound, `stampCount in 1..8`, lowercase asset IDs, and 64-character lowercase hexadecimal hashes.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew :brushes:desktopTest --tests '*NeoBrushCodecTest' --tests '*BrushStampTest'`

Expected: compilation fails because V2 types and `BrushDefinition.stamp` do not exist.

- [ ] **Step 3: Implement the minimal data model**

Create immutable enums/data classes with `init { require(...) }` bounds. Add `stamp: BrushStamp? = null` to `BrushDefinition`. Change codec header handling to accept `NEOCANVAS_BRUSH=1` and `=2`; encode V1 when `stamp == null`, V2 otherwise. Use `stamp.*` prefixed fields and require the exact field set for each version.

- [ ] **Step 4: Run focused and full brush tests**

Run: `./gradlew :brushes:desktopTest`

Expected: all brush tests pass, including unchanged V1 encoded fixtures.

- [ ] **Step 5: Commit**

```bash
git add brushes
git commit -m "Add versioned V2 brush stamp definitions"
```

### Task 2: Bounded Stamp Assets and Deterministic Rendering

**Files:**
- Create: `renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/BrushAsset.kt`
- Create: `renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/StampMaskSampler.kt`
- Modify: `renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/Rasterizer.kt`
- Create: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/StampMaskSamplerTest.kt`
- Modify: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/BrushQualityTest.kt`

**Interfaces:**
- Consumes: `BrushStamp` and `BrushAssetRef` from Task 1.
- Produces: `BrushAsset(id, width, height, coverage, nonEmptyRows)`, `BrushAssetResolver.resolve(ref)`, and V2 rendering through the existing `Rasterizer.stroke` entry point.

- [ ] **Step 1: Write failing asset-sampling and deterministic-output tests**

Define a 5×5 diamond mask in tests and assert transformed sampling, transparent-row rejection, direction alignment, pressure density, maximum eight sub-stamps, endpoint preservation, and colour-jitter bounds. Render the same stroke twice and compare bytes. Render vertical/both symmetry and assert mirrored coverage uses corresponding deterministic variation.

Add a legacy fixture test that hashes representative V1 Pencil, Ink, Water, and Dry Paint output before and after the renderer change.

- [ ] **Step 2: Run renderer tests and verify RED**

Run: `./gradlew :renderer:desktopTest --tests '*StampMaskSamplerTest' --tests '*BrushQualityTest'`

Expected: compilation fails because `BrushAsset` and resolver-aware rendering do not exist.

- [ ] **Step 3: Implement asset sampling and V2 stamp planning**

Add:

```kotlin
data class BrushAsset(
    val id: String,
    val width: Int,
    val height: Int,
    val coverage: ByteArray,
    val nonEmptyRows: List<IntRange?>,
)

fun interface BrushAssetResolver {
    fun resolve(ref: BrushAssetRef): BrushAsset?
}
```

Extend `Rasterizer.stroke(..., assetResolver: BrushAssetResolver = BrushAssetResolver { null })`. Keep the current path when `stamp == null`. For V2, resolve both assets before touching tiles, seed variation from stable brush/stroke coordinates, compute transformed rectangular bounds, use precomputed non-empty rows, and route final coverage through the existing alpha-lock/selection/blend path.

- [ ] **Step 4: Add work-budget tests**

Instrument an internal `StampWorkMetrics(samples, subStamps, maskPixelsVisited)` returned by a test-only planner. Assert the heaviest 2,000-pixel Nature stroke respects eight sub-stamps/sample, adaptive spacing, and final endpoint inclusion while a large Precision Pencil keeps configured spacing.

- [ ] **Step 5: Run renderer and dependent UI tests**

Run: `./gradlew :renderer:desktopTest :ui:desktopTest`

Expected: all tests pass and V1 hashes are unchanged.

- [ ] **Step 6: Commit**

```bash
git add renderer
git commit -m "Render deterministic image-based brush stamps"
```

### Task 3: Secure `.neobrushpack` Container and Validation

**Files:**
- Create: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushPack.kt`
- Create: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushPackManifest.kt`
- Create: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/ZipStoreCodec.kt`
- Create: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/Sha256.kt`
- Create: `brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushPackTest.kt`
- Create: `brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/ZipStoreCodecTest.kt`

**Interfaces:**
- Consumes: V2 brush codec from Task 1.
- Produces: `NeoBrushPackCodec.encode(pack)`, `decode(bytes, appVersion)`, `PackValidationError`, `NeoBrushPack`, and validated raw grayscale PNG payload records.

- [ ] **Step 1: Write failing happy-path and hostile-archive tests**

Build a two-brush pack fixture and assert exact round-trip order and hashes. Add explicit fixtures for `../evil`, `/absolute`, `folder\\..\\evil`, Unicode-normalized duplicates, duplicate ZIP names, symlink attributes, encryption, compression method other than Store, nested `.zip`, undeclared entry, hash mismatch, 101 brushes, 251 entries, 25 MiB compressed overflow, 75 MiB unpacked overflow, invalid JSON, unsupported schema, and minimum-app-version failure.

- [ ] **Step 2: Run pack tests and verify RED**

Run: `./gradlew :brushes:desktopTest --tests '*NeoBrushPackTest' --tests '*ZipStoreCodecTest'`

Expected: compilation fails because pack types/codecs do not exist.

- [ ] **Step 3: Implement the strict ZIP-store codec**

Implement only ZIP method 0 (Store), CRC32 verification, UTF-8 names, central-directory consistency, and fixed resource limits. Normalize separators and Unicode before path validation and duplicate detection. Do not extract to disk during decoding.

- [ ] **Step 4: Implement canonical manifest parsing and SHA-256 checks**

Use a small strict JSON reader scoped to the documented manifest schema. Canonical encoding sorts object keys but preserves ordered brush arrays. Validate every declared path/hash and reject every undeclared entry. Represent failures as stable error codes plus sanitized user text.

- [ ] **Step 5: Run all brush tests**

Run: `./gradlew :brushes:desktopTest`

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add brushes
git commit -m "Add secure NeoCanvas brush pack codec"
```

### Task 4: Pack-Aware Library Persistence and Atomic Lifecycle

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/InstalledBrushPack.kt`
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushLibraryStore.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushLibraryState.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorFileActions.kt`
- Modify: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosEditorFileActions.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/BrushPackLibraryTest.kt`

**Interfaces:**
- Consumes: validated `NeoBrushPack` from Task 3.
- Produces: `previewPack(bytes)`, `installPack(candidate, conflictPolicy)`, `removePack(id)`, `exportPack(id)`, `installedPacks`, and asset resolution for Task 2.

- [ ] **Step 1: Write failing migration/lifecycle tests**

Assert V1 snapshot migration, fresh install, byte-for-byte unchanged state on failed install, identical-version no-op, higher-version replace, stable-ID favourite preservation despite manifest reorder, author/signature conflict rename, removal cleanup, and restart persistence.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests '*BrushPackLibraryTest'`

Expected: compilation fails because pack lifecycle APIs do not exist.

- [ ] **Step 3: Implement the versioned library store**

Keep loose custom brushes and installed packs separate. Stage decoded pack data, verify it fully, compute the next snapshot, and invoke one atomic `saveBrushLibrary(bytes, assets)` platform action. Preserve the current snapshot codec decoder for migration and write only the new version after a successful mutation.

- [ ] **Step 4: Implement asset persistence and resolver**

Store assets beneath application support using `packs/<safe-pack-id>/<version>/<hash>.png`; write to a sibling staging directory, then atomically replace. Expose decoded assets through an LRU resolver with a fixed 32 MiB coverage-buffer ceiling.

- [ ] **Step 5: Run UI and renderer suites**

Run: `./gradlew :ui:desktopTest :renderer:desktopTest`

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add ui
git commit -m "Persist and manage installed brush packs"
```

### Task 5: Brush Library Pack UI

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushPackInstallSheet.kt`
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushPackManager.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushPanel.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/BrushPackUiStateTest.kt`

**Interfaces:**
- Consumes: Task 4 lifecycle API.
- Produces: library `+` actions, install confirmation state, pack information/removal/export actions, and status messages.

- [ ] **Step 1: Write failing UI-state tests**

Test menu actions, preview/install/cancel, update Replace wording, unsigned `Imported Pack` badge, verified `Official NeoWorks` badge, validation error text, and active-brush fallback to `neo.pencil` after pack removal. Assert raster tile snapshots remain unchanged during removal.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests '*BrushPackUiStateTest'`

Expected: compilation fails because pack UI state does not exist.

- [ ] **Step 3: Implement compact responsive UI**

Add a `+` overflow beside the Brush heading. Use an adaptive modal sheet with cover, metadata, preview grid, compatibility, licence, provenance, and one primary action. Add a pack context menu to imported category headers. Keep existing narrow/wide brush layouts and accessibility descriptions.

- [ ] **Step 4: Run UI tests**

Run: `./gradlew :ui:desktopTest`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add ui
git commit -m "Add brush pack installation and management UI"
```

### Task 6: Native iPad File Association, Files Picker, and Share Export

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorFileActions.kt`
- Modify: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosEditorFileActions.kt`
- Modify: `iosApp/project.yml`
- Modify: `iosApp/NeoCanvas/Info.plist`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/PendingBrushImportTest.kt`

**Interfaces:**
- Consumes: pack preview/install callback from Tasks 4–5.
- Produces: `openBrushFile(onResult)`, `shareBrushFile(name, bytes)`, custom UTIs, and queued cold-launch imports.

- [ ] **Step 1: Write failing pending-import routing tests**

Model `PendingExternalImportQueue` and test warm open, cold open before editor attachment, exactly-once delivery, cancellation, oversized-file rejection before decode, and unrelated document routing.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew :ui:desktopTest --tests '*PendingBrushImportTest'`

Expected: compilation fails because the queue and file actions do not exist.

- [ ] **Step 3: Implement iOS picker/export and UTIs**

Use `UIDocumentPickerViewController` for both identifiers and security-scoped URL access. Copy bytes before releasing scope. Use `UIActivityViewController` for export. Declare both exported UTIs as `public.data` and `public.content`, with filename extensions and role Editor. Route scene/application open URLs into the exactly-once queue.

- [ ] **Step 4: Verify generated Xcode metadata**

Run: `./scripts/verify-apple-scaffold.sh && ./scripts/generate-xcode-project.sh`

Expected: generated project contains both UTIs/document types and builds its plist without duplicate declarations.

- [ ] **Step 5: Run all shared tests**

Run: `./gradlew :core:desktopTest :brushes:desktopTest :renderer:desktopTest :ui:desktopTest`

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add ui iosApp
git commit -m "Integrate brush packs with iPad Files and sharing"
```

### Task 7: Original Neo Nature Studio Pack and Visual Quality Gate

**Files:**
- Create: `brushes/src/commonMain/resources/brush-packs/neo-nature-studio/manifest.json`
- Create: `brushes/src/commonMain/resources/brush-packs/neo-nature-studio/brushes/*.neobrush`
- Create: `brushes/src/commonMain/resources/brush-packs/neo-nature-studio/assets/*.png`
- Create: `brushes/src/commonMain/resources/brush-packs/neo-nature-studio/previews/*.png`
- Create: `brushes/src/commonMain/resources/brush-packs/neo-nature-studio/artwork/cover.png`
- Create: `brushes/src/commonMain/resources/brush-packs/neo-nature-studio/artwork/example.png`
- Create: `brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/NeoNatureStudioPackTest.kt`
- Create: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/NeoNatureStudioQualityTest.kt`
- Create: `tools/build-neo-nature-pack.kts`

**Interfaces:**
- Consumes: V2 codec/renderer and pack codec.
- Produces: reproducible `build/brush-packs/Neo-Nature-Studio.neobrushpack` and bundled installable pack.

- [ ] **Step 1: Write failing pack completeness and visual-distance tests**

Assert the exact 18 names from the spec, unique stable IDs, valid original assets, working-size metadata, previews, cover/example dimensions, licence, and successful pack decode. Render a canonical stroke for every brush and require pairwise perceptual hashes to differ beyond a documented threshold; also assert transparent/non-empty coverage bounds so a blank or solid asset fails.

- [ ] **Step 2: Run quality tests and verify RED**

Run: `./gradlew :brushes:desktopTest --tests '*NeoNatureStudioPackTest' :renderer:desktopTest --tests '*NeoNatureStudioQualityTest'`

Expected: tests fail because pack resources do not exist.

- [ ] **Step 3: Create the original asset set**

Use the image-generation skill for original cover/example art and initial grayscale source shapes, then normalize every production mask to bounded 8-bit grayscale PNG. Hand-tune all 18 V2 definitions against the canonical stroke scenes. Do not use competitor assets, copied settings, trademarks, or downloaded brush files.

- [ ] **Step 4: Add deterministic pack builder**

`tools/build-neo-nature-pack.kts` loads the source tree, recalculates SHA-256 values, validates through `NeoBrushPackCodec`, emits Store-method ZIP entries in canonical order, and decodes the result again before success.

- [ ] **Step 5: Iterate until visual quality tests pass**

Run: `./gradlew :brushes:desktopTest :renderer:desktopTest`

Expected: all 18 brushes pass distinctness, determinism, and performance checks.

- [ ] **Step 6: Commit**

```bash
git add brushes renderer tools
git commit -m "Add the Neo Nature Studio brush pack"
```

### Task 8: Website Download and User Documentation

**Files:**
- Modify: `C:/Users/Windows11/Documents/ChatGPT/The Neowork Website I want/website/dist/neocanvas.html`
- Modify: `C:/Users/Windows11/Documents/ChatGPT/The Neowork Website I want/website/dist/neocanvas.css`
- Modify: `C:/Users/Windows11/Documents/ChatGPT/The Neowork Website I want/website/test-neocanvas.cjs`
- Create: `C:/Users/Windows11/Documents/ChatGPT/The Neowork Website I want/website/dist/assets/neocanvas/brush-packs/Neo-Nature-Studio.neobrushpack`
- Create: `C:/Users/Windows11/Documents/ChatGPT/The Neowork Website I want/website/dist/assets/neocanvas/brush-packs/neo-nature-studio-cover.png`
- Create: `C:/Users/Windows11/Documents/ChatGPT/The Neowork Website I want/website/dist/assets/neocanvas/brush-packs/neo-nature-studio-example.png`
- Create: `docs/neo-brush-packs.md`
- Modify: `docs/feature-roadmap.md`

**Interfaces:**
- Consumes: final `.neobrushpack`, cover, and example artwork from Task 7.
- Produces: a direct free download page and in-repo user/support documentation.

- [ ] **Step 1: Write or update website route tests**

Assert the NeoCanvas brush page renders pack name/version/size/licence/compatibility, cover/example images, direct HTTPS download, SHA-256, and Files installation instructions. Assert there is no checkout, paid unlock, or unsupported Procreate/ABR claim.

- [ ] **Step 2: Run website tests and verify RED**

Run: `node test-neocanvas.cjs`

Expected: route/download assertions fail because the pack section is absent.

- [ ] **Step 3: Implement the existing-site section and publish asset**

Add a `Brush Packs` section beneath NeoCanvas using existing site components. Copy the verified pack unchanged, show its checksum, and use `Download → Files → tap → Open in NeoCanvas` instructions.

- [ ] **Step 4: Write app documentation**

Document import, install, update, remove, export/share, provenance badges, supported formats, limits, and troubleshooting in `docs/neo-brush-packs.md`.

- [ ] **Step 5: Run website build and repository docs checks**

Run: `node test-neocanvas.cjs && node test-public-content.cjs`

Expected: both tests pass and the pack URL references the exact artifact hash.

- [ ] **Step 6: Commit each repository**

```bash
git add docs
git commit -m "Document NeoCanvas brush pack workflows"
```

Commit the website changes in NeoWebsite with `Publish Neo Nature Studio brush pack`.

### Task 9: Compact Artist Colour Studio and New Colour Glyph

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ColorPanel.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ColourValues.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ToolBar.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorFileActions.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ColourDiscInteractionTest.kt`
- Modify: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ColorPanelTest.kt`

**Interfaces:**
- Consumes: existing HSV, harmony, palette, recent, primary/secondary, and inspector persistence behavior.
- Produces: `snapColourDisc(hsv)`, `ColourDiscZoomState`, persisted `ColourStudioMode`, direct harmony reticles, and `StudioColourGlyph`.

- [ ] **Step 1: Write failing interaction and persistence tests**

Test nearest snap targets for white, black, mid-grey, half/full saturation; zoom clamping and reset; all harmony reticle angles; last-mode restoration; minimum 44-point touch targets; and glyph geometry containing a hue ring, active centre, and offset secondary swatch.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests '*ColourDiscInteractionTest' --tests '*ColorPanelTest'`

Expected: compilation or assertions fail because snapping, zoom state, persisted mode, and the new glyph do not exist.

- [ ] **Step 3: Implement model behavior**

Add pure functions for snap selection and harmony reticle positions, a bounded `1f..2.5f` zoom state, and local mode persistence through the existing settings/file-actions mechanism. Preserve the current `Hsv` conversion behavior and make gesture updates atomic.

- [ ] **Step 4: Implement the compact UI and original glyph**

Centre the disc, use pinch input for the inner field, double-tap snapping, split previous/current reticle fill, and direct harmony reticles. Reduce decorative vertical spacing while keeping every interactive target at least 44 dp. Replace the toolbar colour circle with a Canvas-drawn sweep-gradient ring, active centre, and offset secondary swatch; retain the dynamic tooltip and accessibility description.

- [ ] **Step 5: Run the full UI suite**

Run: `./gradlew :ui:desktopTest`

Expected: all tests pass with existing palette, colour history, and toolbar tests unchanged.

- [ ] **Step 6: Commit**

```bash
git add ui
git commit -m "Upgrade Colour Studio picker and toolbar glyph"
```

### Task 10: Full Release Validation, Pack Artifact, and Test IPA

**Files:**
- Modify: `.github/workflows/ci.yml` only if new pack artifacts are not already collected.
- Modify: `docs/release-checklist.md`
- Test: all shared suites and existing iPad simulator validation.

**Interfaces:**
- Consumes: all preceding tasks.
- Produces: successful CI run, `Neo-Nature-Studio.neobrushpack`, unsigned physical-device `.ipa`, simulator app, and smoke-test evidence.

- [ ] **Step 1: Run the complete local/shared verification**

Run:

```bash
./gradlew :core:desktopTest :brushes:desktopTest :renderer:desktopTest :ui:desktopTest
./scripts/verify-release-contract.sh
./scripts/verify-apple-scaffold.sh
```

Expected: zero failures.

- [ ] **Step 2: Verify artifact reproducibility and contents**

Build the pack twice and compare SHA-256. Decode the shipped artifact, assert 18 brushes and all hashes, and list the archive to prove the documented layout and absence of undeclared files.

- [ ] **Step 3: Commit the validation checkpoint**

```bash
git commit --allow-empty -m "Validate Neo Nature Studio brush packs [ipad]"
git push origin ipad-gestures-phase1
```

- [ ] **Step 4: Watch the full CI run**

Require success for shared tests, simulator build, unsigned physical iPad build, simulator smoke test, extended visual validation, and evidence uploads. Any failure gets a reproducing test before its fix.

- [ ] **Step 5: Download and verify artifacts**

Download the device `.app`, simulator artifact, smoke evidence, and `.neobrushpack`. Wrap the physical `.app` as `Payload/NeoCanvas.app` in an unsigned IPA, verify archive structure, record SHA-256 values, and provide clickable local files plus the CI run URL.

- [ ] **Step 6: Final repository audit**

Run: `git status --short --branch && git rev-parse HEAD && git log -10 --oneline`

Expected: clean worktree, local branch matches `origin/ipad-gestures-phase1`, and final HEAD is the successful `[ipad]` validation commit.
