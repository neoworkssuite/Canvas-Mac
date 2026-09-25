# Colour, Toolbar, and Text Studio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver usage-based recent colours, clearer painting controls, and a materially richer editable-text and font experience.

**Architecture:** Keep colour-use recording in `EditorState`, extend the core text payload with backward-compatible optional properties, and make common canvas rendering plus iOS export consume the same model. Keep font catalogue metadata and licensing separate from panel presentation.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, CoreGraphics/UIKit, Gradle, Kotlin test.

**Spec:** `docs/superpowers/specs/2026-09-25-colour-text-font-studio-design.md`

## Global Constraints

- Preserve branch `ipad-gestures-phase1` and all existing work.
- Keep old `.neocanvas` packages readable.
- Do not redistribute proprietary fonts.
- Recent colours record successful canvas use only.
- All document mutations remain undoable.

## Review Focus

- A colour selection without a committed edit must leave Recent unchanged.
- Erasing or smudging must not record the active colour.
- Old text payload JSON without new fields must load with stable defaults.
- Typography values must be validated and survive package round trips.
- Missing font families must render through a deterministic fallback.

---

### Task 1: Usage-based colour history

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Produces: `recordUsedColour(Color)` invoked only after successful colour-bearing mutations.

- [ ] Add tests proving selection alone and non-paint tools do not record, while successful paint/object operations do.
- [ ] Run the focused tests and observe the old selection-driven assertions fail.
- [ ] Move recent-history mutation from the colour setter to successful commits.
- [ ] Run the focused tests green.

### Task 2: Backward-compatible typography model

**Files:**
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/model/Layer.kt`
- Modify: `core/src/commonMain/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackage.kt`
- Test: `core/src/commonTest/kotlin/com/neoworksuite/neocanvas/core/store/NeoCanvasPackageTest.kt`

**Interfaces:**
- Produces: text tracking, baseline, underline, uppercase fields with validated defaults.

- [ ] Add round-trip and legacy-package tests for the new fields.
- [ ] Run focused core tests and observe failure.
- [ ] Extend model serialization/deserialization with optional defaults.
- [ ] Run focused core tests green.

### Task 3: Text Studio controls and rendering

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ObjectPanel.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/CanvasWorkspace.kt`
- Modify: `ui/src/iosMain/kotlin/com/neoworksuite/neocanvas/ui/IosEditorFileActions.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Consumes: Task 2 text fields.
- Produces: undoable setters and matching canvas/export rendering.

- [ ] Add editor-state tests for each new text setter and undo.
- [ ] Run focused tests red.
- [ ] Add controls, font catalogue/search, setters, and rendering.
- [ ] Run focused UI and exporter tests green.

### Task 4: Font licensing and catalogue

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/TextFontCatalogue.kt`
- Create: `docs/font-licenses.md`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/SettingsPanel.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/TextFontCatalogueTest.kt`

**Interfaces:**
- Produces: curated legal catalogue with stable generic fallbacks and visible attribution.

- [ ] Add catalogue tests for unique names, categories, and fallback mappings.
- [ ] Run them red.
- [ ] Add the curated catalogue and licence documentation/settings entry.
- [ ] Run tests green.

### Task 5: Clear toolbar glyphs

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ToolBar.kt`
- Test: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/StudioMenuPresentationTest.kt`

**Interfaces:**
- Produces: recognisable Smudge, Undo, and Redo visuals with unchanged actions and semantics.

- [ ] Add presentation/accessibility assertions.
- [ ] Run focused tests red where applicable.
- [ ] redraw the glyphs and preserve accessible labels.
- [ ] Run tests green.

### Task 6: Full verification and release artefacts

**Files:**
- Modify: release documentation only if verification reveals required notes.

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: verified commit, pushed branch, CI result, and unsigned test IPA.

- [ ] Run all repository validation and package tests.
- [ ] Build the iOS archive/IPA using the established release workflow.
- [ ] Verify IPA contents, size, and SHA-256.
- [ ] Commit, push, and confirm GitHub validation.
