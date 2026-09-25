# Release Typography Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent user-font import, pair kerning, justified and outlined text, vertical orientation, and non-scaling text-box resize handles to NeoCanvas, then produce a fully validated unsigned iPad IPA.

**Architecture:** Extend the core text payload with optional, bounded typography fields and normalize range-level kerning in a focused core helper. Add a platform-neutral font-library boundary to `EditorFileActions`, implement it on iOS with Files/Core Text/application-support storage, merge imported faces into the existing catalogue, and drive one normalized layout model through Compose canvas rendering and iOS export. Treat text-box resize as a preview session that creates a single undoable `UpdateTextLayer` command on commit.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Kotlin test, UIKit document picker, Core Text, Skia/Compose Canvas, CoreGraphics, Gradle, Xcode/GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-25-release-typography-upgrade-design.md`

## Global Constraints

- Preserve branch `ipad-gestures-phase1` and fast-forward from its live remote HEAD before product changes.
- Existing `.neocanvas` documents must load with no visual change.
- Imported font files remain local, capped at 32 MiB, and are never embedded in artwork packages or exports.
- Missing fonts preserve their stored identity and resolve through deterministic System fallback.
- Kerning ranges use UTF-16 offsets, are normalized, ordered, non-overlapping, bounded to current text, and capped at 256 entries.
- Text-box resize changes layout bounds without changing font size or object transform.
- One completed handle drag creates exactly one undo step; cancellation creates none.
- Live canvas, PNG/JPEG/TIFF/PDF, and flattened PSD output must agree for all supported typography attributes.
- No proprietary font is added to the repository or application bundle.

## Review Focus

- A malformed package with overlapping or out-of-bounds UTF-16 kerning entries must open safely with normalized entries rather than crash or reject the artwork; Task 1 pins this in `NeoCanvasPackageTest`.
- A duplicate, corrupt, oversized, or partially copied font import must leave neither a catalogue entry nor an orphaned file; Tasks 3 and 4 pin validation and rollback.
- Deleting text across a surrogate pair must not split UTF-16 ranges or leave invalid kerning offsets; Task 2 pins insertion/deletion remapping.
- Justification must not stretch the last paragraph line or single-token lines, and vertical layout must remain bounded; Task 5 pins layout behaviour.
- Cancelling or interrupting a text-box drag must restore the exact original bounds and add no history entry; Task 6 pins session cancellation and undo count.

---

### Task 1: Backward-compatible typography model and package format

**Files:**
- Create: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/TextTypography.kt`
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/Layer.kt`
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackage.kt`
- Test: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackageTest.kt`
- Create: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/model/TextTypographyTest.kt`

**Interfaces:**
- Produces: `enum class TextOrientation { Horizontal, Vertical }`
- Produces: `data class TextKerningRange(val startUtf16: Int, val endUtf16: Int, val adjustment: Float)`
- Produces: `normalizeKerningRanges(text: String, ranges: List<TextKerningRange>): List<TextKerningRange>`
- Produces: `TextAlignment.Justified` and optional `TextObject` fields `fontStyle`, `kerning`, `outline`, `outlineWidth`, and `orientation`.
- Test helper: `loadLegacyTextJsonWithoutAdvancedTypographyFields()` constructs a minimal version-2 package through `NeoCanvasPackage.readMembers` using a text-layer JSON object that omits every new field.

- [ ] **Step 1: Write failing model and package tests**

Add tests that construct the desired API and assert round-trip/default behaviour:

```kotlin
@Test fun advanced_typography_round_trips() {
    val payload = LayerPayload.TextObject(
        text = "AV canvas",
        fontFamily = "Imported Family",
        fontStyle = "Semibold",
        alignment = TextAlignment.Justified,
        kerning = listOf(TextKerningRange(0, 2, -1.5f)),
        outline = true,
        outlineWidth = 2.25f,
        orientation = TextOrientation.Vertical,
    )
    val document = CanvasDocument.blank(800, 600).copy(
        layers = listOf(Layer("text", "Text", payload = payload)),
    )
    val loaded = assertIs<LoadResult.Success>(NeoCanvasPackage.read(NeoCanvasPackage.write(document, emptyMap())))
    assertEquals(payload, loaded.document.layers.single().payload)
}

@Test fun legacy_text_defaults_to_existing_rendering() {
    val loaded = loadLegacyTextJsonWithoutAdvancedTypographyFields()
    val text = assertIs<LayerPayload.TextObject>(loaded.document.layers.single().payload)
    assertEquals("Regular", text.fontStyle)
    assertEquals(emptyList(), text.kerning)
    assertFalse(text.outline)
    assertEquals(1f, text.outlineWidth)
    assertEquals(TextOrientation.Horizontal, text.orientation)
}

@Test fun malformed_kerning_is_normalized_and_capped() {
    val normalized = normalizeKerningRanges(
        "A\uD83D\uDE00V",
        listOf(TextKerningRange(-2, 1, 2f), TextKerningRange(1, 2, 4f)) +
            List(300) { TextKerningRange(0, 1, it.toFloat()) },
    )
    assertTrue(normalized.size <= 256)
    assertTrue(normalized.all { it.startUtf16 >= 0 && it.endUtf16 <= 4 && it.startUtf16 < it.endUtf16 })
    assertTrue(normalized.none { it.startUtf16 == 2 || it.endUtf16 == 2 })
}
```

- [ ] **Step 2: Run core tests and verify RED**

Run: `./gradlew :core:desktopTest --tests "*TextTypographyTest" --tests "*NeoCanvasPackageTest"`

Expected: compilation fails because `TextOrientation`, `TextKerningRange`, `Justified`, and the new payload fields do not exist.

- [ ] **Step 3: Implement bounded typography types and normalization**

Create `TextTypography.kt` with the exact public model and a normalizer that clamps finite adjustment values to `-64f..64f`, rejects empty/invalid/surrogate-splitting ranges, resolves overlap deterministically by keeping the last valid entry, sorts by start/end, and takes at most 256 entries. Extend `TextObject` with:

```kotlin
val fontStyle: String = "Regular",
val kerning: List<TextKerningRange> = emptyList(),
val outline: Boolean = false,
val outlineWidth: Float = 1f,
val orientation: TextOrientation = TextOrientation.Horizontal,
```

Add `Justified` to `TextAlignment`, validate `fontStyle`, `outlineWidth in .25f..32f`, and require stored kerning to equal its normalized form.

- [ ] **Step 4: Add optional JSON fields with defensive defaults**

Serialize `fontStyle`, `kerning`, `outline`, `outlineWidth`, and `orientation`. On read, use current-behaviour defaults for absent or malformed optional values and normalize kerning against the decoded text. Keep `FORMAT_VERSION` unchanged because all additions are optional and older readers already ignore unknown object fields.

- [ ] **Step 5: Run core suite and verify GREEN**

Run: `./gradlew :core:desktopTest`

Expected: all core tests pass, including legacy package fixtures.

- [ ] **Step 6: Commit Task 1**

```bash
git add core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/TextTypography.kt core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/Layer.kt core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackage.kt core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/model/TextTypographyTest.kt core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackageTest.kt
git commit -m "Extend editable text typography model"
```

### Task 2: Selection-aware kerning and editor mutations

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/TextEditSelection.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TextEditSelectionTest.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Consumes: Task 1 `TextKerningRange` and normalization.
- Produces: `data class TextEditSelection(val startUtf16: Int, val endUtf16: Int)`
- Produces: `remapKerningAfterEdit(oldText, newText, oldSelection, ranges)`
- Produces: `EditorState.setActiveTextSelection(...)`, `setActiveTextKerning(Float)`, and advanced whole-object setters.

- [ ] **Step 1: Write failing selection/remapping tests**

```kotlin
@Test fun deleting_selected_text_discards_intersecting_kerning_and_shifts_following_ranges() {
    val result = remapKerningAfterEdit(
        oldText = "ABCD",
        newText = "AD",
        oldSelection = TextEditSelection(1, 3),
        ranges = listOf(TextKerningRange(0, 1, -1f), TextKerningRange(1, 3, 2f), TextKerningRange(3, 4, 3f)),
    )
    assertEquals(listOf(TextKerningRange(0, 1, -1f), TextKerningRange(1, 2, 3f)), result)
}

@Test fun edit_across_surrogate_pair_never_leaves_split_range() {
    val result = remapKerningAfterEdit(
        "A\uD83D\uDE00V", "AV", TextEditSelection(1, 3),
        listOf(TextKerningRange(1, 3, 2f), TextKerningRange(3, 4, -1f)),
    )
    assertEquals(listOf(TextKerningRange(1, 2, -1f)), result)
}
```

Add an `EditorStateTest` proving one kerning change is undoable and only affects the selected UTF-16 range.

- [ ] **Step 2: Run focused UI tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests "*TextEditSelectionTest" --tests "*EditorStateTest"`

Expected: compilation fails for the new APIs.

- [ ] **Step 3: Implement selection normalization and edit remapping**

Keep selection as transient editor state, clamp it to valid UTF-16 code-point boundaries, and implement a single-contiguous-edit remapper using longest common prefix/suffix boundaries. Intersecting ranges are removed; ranges after the edit shift by the UTF-16 delta; survivors pass through `normalizeKerningRanges`.

- [ ] **Step 4: Add undoable EditorState setters**

Update `setActiveTextContent` to remap kerning before executing `UpdateTextLayer`. Add setters for font style, kerning, outline, outline width, orientation, and justified alignment. Locked/hidden objects remain unchanged. Avoid executing a command when the normalized value matches the current payload.

- [ ] **Step 5: Run UI suite and verify GREEN**

Run: `./gradlew :ui:desktopTest`

Expected: all UI tests pass.

- [ ] **Step 6: Commit Task 2**

```bash
git add ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/TextEditSelection.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TextEditSelectionTest.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt
git commit -m "Add selection-aware text kerning"
```

### Task 3: Platform-neutral imported-font catalogue and UI flow

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorFileActions.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/TextFontCatalogue.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ObjectPanel.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TextFontCatalogueTest.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/FontLibraryFlowTest.kt`

**Interfaces:**
- Produces: `data class ImportedFontFace(val id: String, val family: String, val style: String, val sourceName: String)`
- Produces: `data class ImportedFontFile(val name: String, val bytes: ByteArray)` with a 32 MiB invariant.
- Produces: `EditorFileActions.listImportedFonts()`, `openFontFile(...)`, `installFont(...)`, and `removeImportedFont(...)`.
- Produces: merged `textFontChoices(imported)` and deterministic `resolveTextFontChoice(family, style, imported)`.

- [ ] **Step 1: Write failing catalogue and flow tests**

```kotlin
@Test fun imported_faces_merge_into_search_without_duplicates() {
    val imported = listOf(
        ImportedFontFace("acme-regular", "Acme", "Regular", "Acme.ttf"),
        ImportedFontFace("acme-bold", "Acme", "Bold", "Acme-Bold.ttf"),
    )
    val choices = textFontChoices(imported)
    assertEquals(1, choices.count { it.name == "Acme" })
    assertEquals(listOf("Bold", "Regular"), choices.single { it.name == "Acme" }.styles.sorted())
}

@Test fun missing_font_keeps_identity_and_resolves_to_system() {
    val resolved = resolveTextFontChoice("Removed Family", "Black", emptyList())
    assertTrue(resolved.missing)
    assertEquals("Removed Family", resolved.requestedFamily)
    assertEquals("System", resolved.renderFamily)
}
```

Use a fake `EditorFileActions` in `FontLibraryFlowTest` to prove cancellation is silent, success refreshes the catalogue, failure sets an actionable status message, and removal does not alter the active document's stored family.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests "*TextFontCatalogueTest" --tests "*FontLibraryFlowTest"`

Expected: compilation fails because the font-library interfaces do not exist.

- [ ] **Step 3: Add the host boundary and catalogue merge**

Add default unavailable implementations to `EditorFileActions` so non-iOS hosts remain source-compatible. Extend `TextFontChoice` with `styles`, `imported`, and stable identity. Merge by normalized family, combine unique styles, preserve bundled/system ordering, and append imported families alphabetically.

- [ ] **Step 4: Add EditorState import/removal orchestration and Text Studio controls**

Add `refreshImportedFonts`, `importFont`, and `removeImportedFont`. In `ObjectPanel`, add **Import Font**, show Imported and Missing Font states, list actual styles for the selected family, and require an explicit Remove action. Bind the text field through `TextFieldValue` so cursor/selection changes call `setActiveTextSelection` without mutating content.

- [ ] **Step 5: Run UI suite and verify GREEN**

Run: `./gradlew :ui:desktopTest`

Expected: all UI tests pass and unavailable hosts show no import action.

- [ ] **Step 6: Commit Task 3**

```bash
git add ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorFileActions.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/TextFontCatalogue.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ObjectPanel.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TextFontCatalogueTest.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/FontLibraryFlowTest.kt
git commit -m "Add imported font library workflow"
```

### Task 4: Persistent iPad font import and Core Text registration

**Files:**
- Modify: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosEditorFileActions.kt`
- Create: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosFontLibrary.kt`
- Modify: `iosApp/NeoCanvas/Info.plist`
- Modify: `iosApp/project.yml`
- Create: `ui/src/iosTest/kotlin/com/neoworksuite/neocanvas/ui/IosFontLibraryTest.kt`
- Create: `ui/src/iosTest/resources/fonts/TestFont-OFL.ttf`

**Interfaces:**
- Consumes: Task 3 font-library boundary.
- Produces: `IosFontLibrary` backed by `Application Support/NeoCanvas/Fonts` plus atomic `catalogue.json`.
- Produces: process registration with Core Text and metadata-derived family/style identities.
- Test helpers: `testFontFile()` reads the small OFL fixture as `ImportedFontFile`; `testLibrary(registrar)` builds an `IosFontLibrary` over a temporary directory with injected metadata and registration adapters; `fontDirectoryEntries()` exposes that temporary directory only inside the test fixture.

- [ ] **Step 1: Write failing iOS font-library tests**

Use a temporary directory and injected `FontMetadataReader`/`FontRegistrar` fakes:

```kotlin
@Test fun failed_registration_rolls_back_file_and_catalogue() {
    val library = testLibrary(registrar = FontRegistrar { false })
    val result = library.install(ImportedFontFile("Broken.ttf", validTestFontBytes()))
    assertIs<FontInstallResult.Failure>(result)
    assertTrue(library.list().isEmpty())
    assertTrue(library.fontDirectoryEntries().isEmpty())
}

@Test fun duplicate_identity_is_rejected_without_second_copy() {
    val library = testLibrary()
    assertIs<FontInstallResult.Success>(library.install(testFontFile()))
    assertIs<FontInstallResult.Failure>(library.install(testFontFile()))
    assertEquals(1, library.fontDirectoryEntries().size)
}
```

Also test 32 MiB rejection, TTC exposure of multiple faces, reload persistence, and explicit removal.

- [ ] **Step 2: Run iOS tests in CI-compatible compilation and verify RED**

Run: `./gradlew :ui:compileTestKotlinIosSimulatorArm64`

Expected: compilation fails because `IosFontLibrary` does not exist.

- [ ] **Step 3: Implement atomic storage, metadata validation, and registration**

Use security-scoped URL access while copying the picked file. Copy to a temporary file inside the owned font directory, reject invalid size/extension/metadata/identity, move atomically to its final SHA-256-based filename, register with `CTFontManagerRegisterFontsForURL`, then atomically replace the JSON catalogue. Roll back the final file if registration or catalogue persistence fails. On launch, validate catalogue entries, register existing files once, and omit broken entries with a compatibility message.

- [ ] **Step 4: Implement the iPad Files picker bridge**

Present `UIDocumentPickerViewController` for the supported font UTTypes, return cancellation as `Result.success(null)`, and route selected bytes to the library. Add any required imported type declarations/document access keys to `Info.plist` and keep `project.yml` as the source for generated Xcode configuration.

- [ ] **Step 5: Run iOS compilation and shared regression tests**

Run: `./gradlew :ui:compileKotlinIosArm64 :ui:compileTestKotlinIosSimulatorArm64 :ui:desktopTest`

Expected: all compilation and tests pass.

- [ ] **Step 6: Commit Task 4**

```bash
git add ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosEditorFileActions.kt ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosFontLibrary.kt ui/src/iosTest/kotlin/com/neoworksuite/neocanvas/ui/IosFontLibraryTest.kt iosApp/NeoCanvas/Info.plist iosApp/project.yml
git commit -m "Persist imported fonts on iPad"
```

### Task 5: Unified advanced text layout for canvas and export

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditableTextLayout.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/LayerPreview.kt`
- Modify: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosEditorFileActions.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditableTextLayoutTest.kt`
- Modify: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/PngExporterTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4 normalized typography and resolved font identities.
- Produces: `layoutEditableText(request: EditableTextLayoutRequest): EditableTextLayoutResult` containing positioned lines/runs/columns and outline intent.
- Produces: shared rules for wrapping, final-line justification, vertical bounds, and kerning distribution.
- Test helpers: `fakeMetricsRequest` and `fakeVerticalMetricsRequest` construct requests with a deterministic glyph-metrics lambda returning fixed advances, so tests do not depend on host fonts.

- [ ] **Step 1: Write failing layout tests**

```kotlin
@Test fun justified_layout_expands_interword_spaces_except_final_line() {
    val result = layoutEditableText(fakeMetricsRequest("one two three four", width = 120f, justified = true))
    assertTrue(result.lines.first().spaceExpansion > 0f)
    assertEquals(0f, result.lines.last().spaceExpansion)
}

@Test fun token_without_spaces_is_not_artificially_stretched() {
    val result = layoutEditableText(fakeMetricsRequest("NeoCanvas", width = 400f, justified = true))
    assertEquals(0f, result.lines.single().spaceExpansion)
}

@Test fun vertical_layout_advances_down_then_moves_columns_right_to_left() {
    val result = layoutEditableText(fakeVerticalMetricsRequest("ABCD", height = 80f))
    assertTrue(result.runs.zipWithNext().all { (a, b) -> b.x <= a.x || b.y > a.y })
    assertTrue(result.bounds.width <= result.request.boxWidth)
}
```

Add assertions that kerning changes only the targeted run, outline width is retained, and opacity/underline/uppercase remain present.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests "*EditableTextLayoutTest" :renderer:desktopTest --tests "*PngExporterTest"`

Expected: compilation fails because normalized layout types do not exist.

- [ ] **Step 3: Implement normalized layout and Compose rendering**

Build layout from injected glyph metrics so rules are deterministic and testable. Use Compose/Skia paragraph drawing for horizontal solid text where it matches the normalized result; use positioned runs for custom kerning, outline, justification, and vertical orientation. Draw outline with stroke-only glyph paths where supported, preserving transparent interiors and applying layer opacity once.

- [ ] **Step 4: Apply the same layout to iOS export**

Resolve imported/bundled/system fonts through Core Text, construct attributed runs with `NSKernAttributeName`, and draw normalized positions through CoreGraphics/CoreText. Implement stroke-only text with the text drawing mode and render vertical columns from normalized positions. Route PNG, JPEG, TIFF, PDF, and flattened PSD through this path.

- [ ] **Step 5: Run renderer/UI suites and verify GREEN**

Run: `./gradlew :renderer:desktopTest :ui:desktopTest :ui:compileKotlinIosArm64`

Expected: all suites pass; legacy text snapshots remain unchanged.

- [ ] **Step 6: Commit Task 5**

```bash
git add ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditableTextLayout.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/LayerPreview.kt ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosEditorFileActions.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditableTextLayoutTest.kt renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/PngExporterTest.kt
git commit -m "Render advanced editable typography"
```

### Task 6: Text-box resize handles and single-step history

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/TextBoxResizeSession.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TextBoxResizeSessionTest.kt`
- Modify: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Produces: `enum class TextBoxHandle { Left, Right, Top, Bottom }`
- Produces: `TextBoxResizeSession.begin(payload, handle)`, `preview(deltaCanvas)`, `commit()`, and `cancel()`.
- Consumes: `EditorState` command history and active editable-text selection.
- Test helper: `editorWithText()` returns an `EditorState` whose blank document contains one active, unlocked `TextObject` and whose history starts empty.

- [ ] **Step 1: Write failing geometry/history tests**

```kotlin
@Test fun right_handle_changes_width_without_scaling_font() {
    val original = LayerPayload.TextObject("Wrap me", x = 40f, width = 200f, height = 100f, fontSize = 32f)
    val preview = TextBoxResizeSession.begin(original, TextBoxHandle.Right).preview(Offset(75f, 0f))
    assertEquals(275f, preview.width)
    assertEquals(32f, preview.fontSize)
    assertEquals(40f, preview.x)
}

@Test fun cancelled_drag_restores_bounds_and_adds_no_history() {
    val state = editorWithText()
    val before = state.document
    state.beginTextBoxResize(TextBoxHandle.Left)
    state.previewTextBoxResize(Offset(80f, 0f))
    state.cancelTextBoxResize()
    assertEquals(before, state.document)
    assertFalse(state.canUndo)
}
```

Add tests for minimum width/height based on font size, zoom-independent canvas deltas, left/top position adjustment, lock rejection, and one undo after many preview updates.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests "*TextBoxResizeSessionTest" --tests "*EditorStateTest"`

Expected: compilation fails because resize-session APIs do not exist.

- [ ] **Step 3: Implement the pure resize session and EditorState lifecycle**

Keep the original payload immutable in the session. Clamp width and height to `maxOf(24f, fontSize * .75f)`. Preview via transient state only; commit one `UpdateTextLayer` if geometry changed; cancellation clears preview. Any active transform, document replacement, panel close, or layer deletion cancels safely.

- [ ] **Step 4: Draw and hit-test distinct text-box handles**

In `CanvasWorkspace`, draw side handles as pills and top/bottom handles as compact squares, with accessibility labels `Resize text box left/right/top/bottom`. Convert pointer motion through the existing viewport transform into canvas-space deltas. Keep transform handles visually separate and preserve handed-interface layout.

- [ ] **Step 5: Run UI suite and verify GREEN**

Run: `./gradlew :ui:desktopTest`

Expected: all UI tests pass, including transform regressions.

- [ ] **Step 6: Commit Task 6**

```bash
git add ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/TextBoxResizeSession.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TextBoxResizeSessionTest.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt
git commit -m "Add editable text box resize handles"
```

### Task 7: Text Studio accessibility, visual fixtures, and release verification

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ObjectPanel.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/SettingsPanel.kt`
- Modify: `iosApp/NeoCanvasUITests/NeoCanvasManualScreenshotTests.swift`
- Modify: `scripts/verify-release-contract.py`
- Modify: `.github/workflows/ipad-build.yml`
- Modify: `docs/font-licenses.md`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TypographyControlPolicyTest.kt`

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: accessible compact Text Studio, visual-validation fixtures, release contract checks, verified branch commit, and unsigned IPA.

- [ ] **Step 1: Write failing control/release-contract tests**

Add a pure presentation policy test asserting stable labels and availability:

```kotlin
@Test fun advanced_typography_controls_have_unique_accessible_labels() {
    val labels = typographyControlPresentations(fontImportSupported = true).map { it.accessibilityLabel }
    assertEquals(labels.size, labels.toSet().size)
    assertTrue(labels.containsAll(listOf("Import Font", "Justify Text", "Outline Text", "Vertical Text")))
}
```

Extend `verify-release-contract.py` to assert the iOS build declares the supported font types and that no files under the runtime imported-font directory can enter the source/resource bundle.

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests "*TypographyControlPolicyTest"; python scripts/verify-release-contract.py`

Expected: the Kotlin test fails for the missing policy and the contract check fails until font declarations/exclusions are complete.

- [ ] **Step 3: Finish compact controls, copy, and licensing disclosure**

Expose Kerning only for a valid cursor/range, show outline width only while Outline is enabled, group the four alignments without wrapping labels, and expose Horizontal/Vertical as mutually exclusive controls. Add imported-font responsibility and non-embedding language to `docs/font-licenses.md` and the in-app licensing copy.

- [ ] **Step 4: Add deterministic visual-validation scenarios**

Extend the UI test fixture to capture horizontal baseline text, justified multi-line text, outlined text, vertical text, imported-font selection, missing-font fallback, and text-box handles. Do not use proprietary fonts in fixtures; use a bundled OFL font copied through the import path.

- [ ] **Step 5: Run all local validation**

Run:

```bash
./gradlew :core:desktopTest :brushes:desktopTest :renderer:desktopTest :ui:desktopTest
python scripts/verify-release-contract.py
git diff --check
```

Expected: all tests and contract checks pass; `git diff --check` reports no new whitespace errors.

- [ ] **Step 6: Commit release-facing changes**

```bash
git add ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ObjectPanel.kt ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/SettingsPanel.kt ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TypographyControlPolicyTest.kt iosApp/NeoCanvasUITests/NeoCanvasManualScreenshotTests.swift scripts/verify-release-contract.py .github/workflows/ipad-build.yml docs/font-licenses.md
git commit -m "Validate release typography upgrade [ipad]"
```

- [ ] **Step 7: Push and verify authoritative CI**

Run: `git push origin ipad-gestures-phase1`

Wait for both **Fast validation** and **Full iPad validation** on the pushed HEAD. If either fails, reproduce where possible, add a failing regression test, fix through TDD, push the corrective commit, and wait for the new definitive run.

- [ ] **Step 8: Download and verify the unsigned IPA**

Download the physical-iPad artifact from the definitive successful run into `outputs/`, retain the `.ipa` extension, verify the ZIP opens, and assert it contains:

```text
Payload/NeoCanvas.app/NeoCanvas
Payload/NeoCanvas.app/Info.plist
Payload/NeoCanvas.app/PrivacyInfo.xcprivacy
```

Confirm imported test fonts are not embedded beyond the existing six bundled OFL font resources. Record the exact branch HEAD, CI run number, byte size, and SHA-256 in the final handoff.
