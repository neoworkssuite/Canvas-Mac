# NeoCanvas Brush Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 180 original grouped brushes, meaningful deterministic brush dynamics, and a responsive live brush test pad.

**Architecture:** Extend the data-only brushes module first, then teach the platform-neutral rasterizer to interpret the new bounded dynamics, and finally build the Compose picker/test pad against those stable APIs. The scratch pad owns a private tile store and is deliberately separate from `EditorState` history.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test, Gradle 8.x.

**Spec:** `docs/superpowers/specs/2026-09-17-neocanvas-brush-library-design.md`

## Global Constraints

- Exactly 18 categories and 180 paint presets, ten per category.
- Existing built-in brush IDs remain resolvable.
- All presets and rendering behaviour are original NeoCanvas work.
- Rendering is deterministic and local-only.
- Windows desktop and Android tablet builds remain supported.

---

### Task 1: Versioned catalogue and 180 presets

**Files:**
- Modify: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/BrushDefinition.kt`
- Create: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushLibrary.kt`
- Create: `brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/BrushCatalogTest.kt`

**Interfaces:**
- Produces: `BrushCategory`, `BrushDynamics`, `BuiltInBrushes.categories`, `BuiltInBrushes.paintBrushes`, `BrushCatalog.search(String)` and `BrushCatalog.inCategory(String)`.

- [ ] Write catalogue tests asserting 18 categories, ten paint brushes per category, 180 unique IDs, legacy ID compatibility, validation, grouping, and case-insensitive search.
- [ ] Run `./gradlew.bat :brushes:desktopTest` and confirm failures are caused by missing catalogue APIs.
- [ ] Add bounded model fields and generate named, data-driven original presets from explicit category templates.
- [ ] Run `./gradlew.bat :brushes:desktopTest` and confirm all brush tests pass.

### Task 2: Deterministic expressive renderer

**Files:**
- Modify: `renderer/src/commonMain/kotlin/com/neoworksuite/neocanvas/renderer/Rasterizer.kt`
- Modify: `renderer/src/commonTest/kotlin/com/neoworksuite/neocanvas/renderer/BrushQualityTest.kt`

**Interfaces:**
- Consumes: `BrushDefinition.dynamics` and extended `BrushTip` values.
- Produces: deterministic rendering for grain, scatter, rotation/aspect, hardness, and wet mix.

- [ ] Add failing tests that compare representative family output, check scatter footprint, and verify repeatability.
- [ ] Run `./gradlew.bat :renderer:desktopTest` and confirm the new assertions fail against the old rasterizer.
- [ ] Extend stamp placement and coverage calculation using deterministic coordinate/stamp hashing.
- [ ] Run `./gradlew.bat :renderer:desktopTest` and confirm all renderer tests pass.

### Task 3: Brush browser and isolated test pad

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushLibraryState.kt`
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushTestPad.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ColorPanel.kt`
- Create: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/BrushLibraryStateTest.kt`
- Modify: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/EditorStateTest.kt`

**Interfaces:**
- Consumes: grouped/searchable `BuiltInBrushes` and `Rasterizer.stroke`.
- Produces: `BrushLibraryState`, responsive `BrushPanel`, and `BrushTestPad` backed by a private `TileStore`.

- [ ] Add failing pure-state tests for filters, favourites, recents, and scratch-pad isolation expectations.
- [ ] Run `./gradlew.bat :ui:desktopTest` and confirm failures are caused by missing UI-state APIs.
- [ ] Implement pure picker state, compact category navigation, search, favourites, recents, and rendered previews.
- [ ] Implement the private scratch pad with pointer pressure and Clear without touching artwork history.
- [ ] Run `./gradlew.bat :ui:desktopTest` and confirm UI tests pass.

### Task 4: Cross-platform verification and packaging

**Files:**
- Verify only; fix affected source files if compilation exposes integration errors.

**Interfaces:**
- Consumes: completed brush model, renderer, and UI.
- Produces: test/build evidence and a packaged Windows installer.

- [ ] Run `./gradlew.bat :brushes:allTests :renderer:allTests :ui:allTests`.
- [ ] Run `./gradlew.bat :ui:compileKotlinDesktop :ui:compileDebugKotlinAndroid`.
- [ ] Run `./gradlew.bat :androidApp:assembleDebug :windowsApp:packageDistributionForCurrentOS`.
- [ ] Confirm the APK, EXE, and MSI outputs exist and report their full paths.

