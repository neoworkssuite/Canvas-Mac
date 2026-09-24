# Editable Lines and Handed Interface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the active-colour swatch, promote held QuickShape lines to permanently editable objects, and add persistent left/right-handed workspace layouts.

**Architecture:** Extend the existing native shape payload and document codec with optional bounded line-style data, then route QuickShape line commits through document commands. Keep geometry calculations in a focused pure Kotlin helper, UI controls in the existing object/settings panels, and handed layout decisions in a pure layout policy consumed by `NeoCanvasApp`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, existing NeoCanvas command/history and package codecs, Kotlin Test, GitHub Actions Apple CI.

**Spec:** `docs/superpowers/specs/2026-09-24-editable-lines-handed-interface-design.md`

## Global Constraints

- Work only on `ipad-gestures-phase1`; fetch and fast-forward before every task and never force-push.
- Preserve existing `.neocanvas` documents, brush packs, colour-panel performance, Gallery, exports, and iPad-only targeting.
- Normal brush strokes remain raster; only successfully recognized held lines become native objects.
- Handedness never changes artwork coordinates, document content, text direction, or directional icon meaning.
- Every production change follows RED → GREEN → full-suite verification with a focused commit.
- Windows loopback may prevent local Gradle; use the ordinary GitHub Actions fast lane as the authoritative runner when it does.

## Review Focus

- Zero-length or non-finite line edits are rejected while preserving the last valid object — pinned in Task 2.
- Legacy line payloads without new fields decode to solid/round/no-marker defaults — pinned in Task 1.
- Reversing an arrowed line swaps endpoints and markers without changing visual length — pinned in Task 2.
- Handed layout changes during an open inspector move it safely without closing or changing artwork — pinned in Task 5.
- A failed QuickShape promotion commits exactly one raster fallback stroke, never both representations — pinned in Task 3.

---

### Task 1: Backward-Compatible Line Style Model and Codec

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/Layer.kt`
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackage.kt`
- Test: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackageTest.kt`
- Create: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/model/ShapeObjectLineStyleTest.kt`

**Interfaces:**
- Produces: `LineStyle`, `LineCap`, `LineMarker`; optional `ShapeObject.lineStyle`, `lineCap`, `startMarker`, `endMarker`, and `angleSnapping` fields.

- [ ] **Step 1: Write failing model and legacy-codec tests**

```kotlin
@Test fun legacy_line_uses_safe_style_defaults() {
    val decoded = decodeLegacyLineFixture()
    assertEquals(LineStyle.Solid, decoded.lineStyle)
    assertEquals(LineCap.Round, decoded.lineCap)
    assertEquals(LineMarker.None, decoded.startMarker)
    assertEquals(LineMarker.None, decoded.endMarker)
}
```

- [ ] **Step 2: Run the focused core tests and verify RED**

Run: `./gradlew :core:desktopTest`
Expected: compilation fails because line-style fields and enums do not exist.

- [ ] **Step 3: Add bounded enums, defaults, validation, and optional codec keys**

```kotlin
enum class LineStyle { Solid, Dashed, Dotted }
enum class LineCap { Round, Square, Butt }
enum class LineMarker { None, Arrow }
```

Decode unknown or absent values with the defaults above; reject non-finite geometry and negative stroke widths using existing validation conventions.

- [ ] **Step 4: Run core tests and the legacy fixture round trip**

Run: `./gradlew :core:desktopTest`
Expected: PASS, including byte-compatible loading of existing documents.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "Extend editable line document model [ipad]"
```

### Task 2: Pure Line Geometry and Undoable Editing Commands

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditableLineGeometry.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/DocumentCommand.kt`
- Test: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/model/DocumentHistoryTest.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditableLineGeometryTest.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Consumes: Task 1 line-style fields.
- Produces: `lineMetrics(shape): LineMetrics`, `lineWithLength`, `lineWithAngle`, `lineWithEndpoint`, `reversedLine`, and EditorState command methods.

- [ ] **Step 1: Write failing literal geometry and mutation tests**

```kotlin
@Test fun length_and_angle_are_derived_in_all_quadrants() {
    assertEquals(LineMetrics(5f, 53.1301f), lineMetrics(line(3f, 4f)), 0.001f)
    assertEquals(225f, lineMetrics(line(-4f, -4f)).angleDegrees, 0.001f)
}

@Test fun invalid_length_preserves_line() {
    assertEquals(original, lineWithLength(original, Float.NaN))
    assertEquals(original, lineWithLength(original, 0f))
}
```

- [ ] **Step 2: Run focused UI tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests '*EditableLineGeometryTest*'`
Expected: unresolved geometry API.

- [ ] **Step 3: Implement geometry with normalized angles and 15-degree snapping**

```kotlin
internal fun normalizedAngle(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
internal fun snappedAngle(degrees: Float, enabled: Boolean): Float =
    if (enabled) (normalizedAngle(degrees) / 15f).roundToInt() * 15f else normalizedAngle(degrees)
```

Add command-backed EditorState methods so one confirmed field edit or completed drag creates one history entry. Reverse swaps endpoints and markers.

- [ ] **Step 4: Run geometry, history, save/load, and full shared tests**

Run: `./gradlew :core:desktopTest :ui:desktopTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core ui
git commit -m "Add precise editable line geometry [ipad]"
```

### Task 3: QuickShape Line Promotion

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/QuickShapeTest.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Consumes: `QuickShapeResult`, Task 1 `ShapeObject`, Task 2 command API.
- Produces: `commitQuickShape(result, colour, width, opacity): Boolean`.

- [ ] **Step 1: Write failing promotion, undo, and fallback tests**

```kotlin
@Test fun held_line_commits_one_editable_object_and_no_raster_stroke() {
    assertTrue(state.commitQuickShape(lineResult, Color.Red, 6f, 1f))
    assertIs<LayerPayload.ShapeObject>(state.document.layers.last().payload)
    assertTrue(state.tileStore.keys.isEmpty())
    assertTrue(state.undo())
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests '*QuickShape*'`
Expected: missing promotion API or raster-only result.

- [ ] **Step 3: Promote recognized lines atomically and retain single raster fallback**

Use the detected endpoints, active colour, opacity, and clamped brush width. Open the object editor after success; if validation fails, call only the existing raster commit path.

- [ ] **Step 4: Run QuickShape and full UI tests**

Run: `./gradlew :ui:desktopTest`
Expected: PASS with no duplicate raster/object output.

- [ ] **Step 5: Commit**

```bash
git add ui
git commit -m "Promote QuickShape lines to editable objects [ipad]"
```

### Task 4: Line Handles, Properties, Rendering, and Export

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ObjectPanel.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/LayerPreview.kt`
- Modify: `renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/PngExporter.kt`
- Test: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/EditableObjectRasterizerTest.kt`
- Test: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/PngExporterTest.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Consumes: Task 1 style fields and Task 2 mutation commands.
- Produces: Pencil-friendly endpoint overlay and complete Line property section.

- [ ] **Step 1: Write failing rendering/bounds and command-consumer tests**

```kotlin
@Test fun dashed_arrow_line_bounds_include_marker_tip() {
    val bounds = renderedBounds(arrowLine)
    assertTrue(bounds.right > maxOf(arrowLine.x, arrowLine.x + arrowLine.width))
}
```

- [ ] **Step 2: Verify RED in renderer and UI suites**

Run: `./gradlew :renderer:desktopTest :ui:desktopTest`
Expected: style and marker behaviour absent.

- [ ] **Step 3: Implement shared styled-line drawing and focused controls**

Use one rendering helper for canvas, thumbnail, and export paths. Add endpoint handles plus Length, Angle, Snap 15°, Width, Opacity, Style, Cap, Start, End, Reverse, and Use Primary Colour controls. Coalesce handle drag into one command on release.

- [ ] **Step 4: Run renderer/UI/full shared tests**

Run: `./gradlew :core:desktopTest :renderer:desktopTest :ui:desktopTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add renderer ui
git commit -m "Add editable line controls and rendering [ipad]"
```

### Task 5: Persistent Handed Workspace Policy

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/InterfaceLayoutPolicy.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/SettingsPanel.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/NeoCanvasApp.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/InterfaceLayoutPolicyTest.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EyedropperPreferencesTest.kt`

**Interfaces:**
- Produces: `InterfaceSide { Automatic, Left, Right }` and `workspacePlacement(side, compact): WorkspacePlacement`.

- [ ] **Step 1: Write failing placement, persistence, reset, and open-panel tests**

```kotlin
@Test fun left_handed_mode_places_rail_right_and_panels_left() {
    assertEquals(WorkspacePlacement(railAtStart = false, panelsAtEnd = false),
        workspacePlacement(InterfaceSide.Left, compact = false))
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew :ui:desktopTest --tests '*InterfaceLayoutPolicyTest*'`
Expected: missing policy types.

- [ ] **Step 3: Implement pure policy, persisted setting, and immediate Compose placement**

Regular layout changes Row order; overlays use `CenterStart` or `CenterEnd`. Compact layout preserves top-bar reading order and moves available overlays away from the chosen drawing-hand edge. Reset returns Automatic.

- [ ] **Step 4: Run preference and full UI tests**

Run: `./gradlew :ui:desktopTest`
Expected: PASS across automatic/left/right and compact/regular cases.

- [ ] **Step 5: Commit**

```bash
git add ui
git commit -m "Add left and right handed workspace layouts [ipad]"
```

### Task 6: Restore Lightweight Active-Colour Swatch

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ToolBar.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/ToolbarPolicyTest.kt`

**Interfaces:**
- Consumes: existing primary/secondary colours and inspector state.
- Produces: lightweight colour-swatch semantics with no sweep-gradient drawing.

- [ ] **Step 1: Write a failing toolbar policy test**

```kotlin
@Test fun colour_button_description_reports_active_colour() {
    assertEquals("Colour #336699. Open Colour Studio", colourButtonDescription(Color(0xFF336699)))
}
```

- [ ] **Step 2: Verify RED**

Run: `./gradlew :ui:desktopTest --tests '*ToolbarPolicyTest*'`
Expected: missing description helper.

- [ ] **Step 3: Restore the bordered primary swatch and optional small secondary swatch**

Remove `Brush.sweepGradient` from `StudioColourButton`; retain the active border and clickable Colour Studio behaviour.

- [ ] **Step 4: Run UI tests and compile checks**

Run: `./gradlew :ui:desktopTest`
Expected: PASS and no colour-button gradient path.

- [ ] **Step 5: Commit**

```bash
git add ui
git commit -m "Restore active colour toolbar swatch [ipad]"
```

### Task 7: Documentation, Full Apple Validation, and Updated IPA

**Files:**
- Modify: `docs/feature-roadmap.md`
- Modify: `docs/release-checklist.md`
- Modify: `docs/manual-screenshot-pack.md`
- Modify: `.github/workflows/ipad-build.yml` only if new visual evidence needs an existing workflow step extended.

**Interfaces:**
- Consumes: Tasks 1–6 exact candidate tree.
- Produces: green CI evidence, simulator/device artifacts, smoke evidence, and verified unsigned IPA.

- [ ] **Step 1: Update feature and physical acceptance documentation**

Document editable-line controls, handed layouts, colour swatch, compatibility, and physical Apple Pencil checks without marking unchecked physical evidence complete.

- [ ] **Step 2: Run the complete shared test gate**

Run: `./gradlew :core:desktopTest :brushes:desktopTest :renderer:desktopTest :ui:desktopTest`
Expected: PASS. If Windows loopback blocks Gradle, push an ordinary checkpoint and require the GitHub fast job to pass.

- [ ] **Step 3: Trigger exact-tree full iPad validation**

Commit message contains `[ipad]`; wait for simulator build, ARM64/device build, smoke launch, appearance/relaunch checks, and evidence uploads to succeed on the same SHA.

- [ ] **Step 4: Package and verify the unsigned IPA**

Place the device app at `Payload/NeoCanvas.app`, ZIP it as `.ipa`, and assert archive entries include `Payload/NeoCanvas.app/Info.plist`, `NeoCanvas`, and `PrivacyInfo.xcprivacy`, with no unexpected archive root.

- [ ] **Step 5: Final audit and handoff**

```bash
git status --short --branch
git rev-parse HEAD
git rev-parse origin/ipad-gestures-phase1
```

Report the CI URL, exact commit, local IPA link, SHA-256, and remaining physical-iPad checks.
