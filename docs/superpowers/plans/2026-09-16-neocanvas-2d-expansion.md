# NeoCanvas 2D Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand NeoCanvas from a dependable raster editor into a fuller local-only drawing studio on Windows and Android tablets.

**Architecture:** Preserve the existing immutable document-command history and sparse tile renderer. Add selection masks, transform sessions, layer compositing metadata, gesture state, raster tools, embedded resources, and timeline data through versioned core types so Windows and Android continue to open identical `.neocanvas` packages.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.9, sparse RGBA8 tiles, ZIP-based `.neocanvas` packages, platform-local storage.

**Spec:** `docs/superpowers/specs/2026-09-15-neocanvas-design.md`, extended by the explicitly approved feature list in `docs/feature-roadmap.md`.

## Global Constraints

- Remain local-only: no network, account, telemetry, or cloud dependency.
- Android is tablet-first; every tool also works with Windows mouse/pen.
- Every pixel or metadata mutation is undoable and preserves layer isolation.
- Existing `.neocanvas` packages load with backward-compatible defaults.
- Long operations report errors without discarding the in-memory document.
- Each task uses a failing behavioral test before production changes and runs core, renderer, UI, Windows, and Android verification proportionate to its scope.

---

### Task 1: Direct transform session

**Files:** `ui/.../TransformSession.kt`, `EditorState.kt`, `CanvasWorkspace.kt`; `renderer/.../RasterMove.kt`; matching UI/renderer tests.

**Interfaces:** Produces `TransformSession(bounds, translation, scale, rotationDegrees)` and editor methods `beginTransform`, `updateTransform`, `applyTransform`, `cancelTransform`.

- [x] Write tests proving preview leaves stored pixels unchanged, Apply creates one undo entry, Cancel restores the exact pixels, and locked/oversized transforms are rejected.
- [x] Run `:ui:desktopTest :renderer:desktopTest` and confirm failures name missing transform-session behavior.
- [x] Implement corner scale handles, rotation handle, move-inside-bounds, numeric transform controls, live raster preview, Apply and Cancel.
- [x] Run the focused tests, then Windows and Android UI compilation.

### Task 2: Selection masks

**Files:** `ui/.../CanvasSelection.kt`, `SelectionMask.kt`, `EditorState.kt`, `CanvasWorkspace.kt`; renderer selection tests.

**Interfaces:** Produces `SelectionMask.contains(x,y)`, rectangle/ellipse/lasso constructors, `combine(Add|Subtract|Intersect)`, `invert`, and feathered coverage.

- [ ] Write tests for ellipse boundaries, closed lasso filling, invert, add/subtract, feather coverage, and tile-edge painting constraints.
- [ ] Run tests and confirm failure against rectangular-only selection.
- [ ] Implement mask operations, toolbar selection-mode dropdown, overlay rendering, and mask-aware brush/fill/transform behavior.
- [ ] Run renderer/UI tests and both platform builds.

### Task 3: Layer compositing and organization

**Files:** core `Layer.kt`, `DocumentCommand.kt`, package codec/migration; renderer compositor; UI layer panel and tests.

**Interfaces:** Produces `BlendMode`, alpha lock, clipping, raster masks, `GroupLayerPayload`, merge-down and flatten commands.

- [x] Write package round-trip and compositing reference-pixel tests covering old-package defaults.
- [ ] Implement blend/alpha-lock first, then masks/clipping, groups, merge and flatten as separate commands.
- [ ] Add layer UI controls and nesting while preserving real thumbnails and lock behavior.
- [ ] Run full core/renderer/UI/platform verification after each metadata boundary.

### Task 4: Tablet gestures and input

**Files:** `CanvasWorkspace.kt`, Android/Windows input adapters and host tests.

**Interfaces:** Produces multi-pointer viewport transform handling and palm-rejection classification without changing document coordinates.

- [ ] Test pinch anchoring, two-finger pan, rotation reset, stylus-plus-palm rejection and gesture undo/redo routing.
- [ ] Implement gestures with visible alternatives and no accidental paint commits during navigation.
- [ ] Verify pointer mapping at zoom/pan and compile both hosts; retain physical-device checks in acceptance notes.

### Task 5: Brush studio, smudge and wet mixing

**Files:** brushes definitions/catalog persistence; renderer smudge/mix rasterizer; brush UI and host storage tests.

**Interfaces:** Produces local custom brush presets, tilt fields, embedded texture references, `SmudgeTool`, and deterministic wet mixing.

- [ ] Test preset round trips, invalid texture rejection, tilt fallback, cross-tile smudge, mixing, undo and locked layers.
- [ ] Implement saved custom presets and texture import before pixel tools.
- [ ] Implement smudge and wet mixing through raster patches and shared live previews.
- [ ] Verify deterministic renderer output and both platforms.

### Task 6: Fill, canvas and drawing assistance

**Files:** renderer flood fill/crop/resize/shape tools; UI dialogs/overlays; tests.

**Interfaces:** Produces tolerance/reference fill, crop/resize commands, line/rectangle/ellipse snapping, grids and perspective guides.

- [ ] Test perceptual tolerance boundaries, visible-reference sampling, resize interpolation and guide snapping.
- [ ] Implement fill settings, crop/resize dialogs, editable grid, shape commit and perspective overlays.
- [ ] Verify undo, local round trips and platform compilation.

### Task 7: Reference images, text and adjustments

**Files:** embedded asset package codec, text layer payload, adjustment renderer, reference panel and tests.

**Interfaces:** Produces embedded references, editable `TextLayerPayload`, font fallback metadata, destructive/non-destructive adjustment contracts, clone source and distortion patches.

- [ ] Write package migration, missing-font fallback, filter reference-pixel and clone isolation tests.
- [ ] Implement reference window and embedded assets, then text, colour adjustments, blur/sharpen, clone and distortion.
- [ ] Verify package compatibility, undo and both hosts.

### Task 8: Gallery and export expansion

**Files:** host library adapters, thumbnail cache, `ExportAdapter`, PNG/JPEG/PDF/layered exporters, UI gallery and tests.

**Interfaces:** Produces thumbnail gallery operations, rename/delete-with-confirmation, external Android document export, and cancellable format adapters.

- [ ] Test gallery ordering/rename/delete safety and byte-level export validity.
- [ ] Implement thumbnail gallery and external backup before additional export formats.
- [ ] Add progress/cancellation and verify Windows installer and Android APK.

### Task 9: Time-lapse, animation and multipage

**Files:** core timeline/page models and commands, package migration, renderer onion skin/export, timeline/page UI and tests.

**Interfaces:** Produces bounded time-lapse events, animation frames with onion skinning, and ordered sketchbook pages.

- [ ] Write migration and deterministic frame/page round-trip tests with size limits.
- [ ] Implement time-lapse capture/playback, then frame timeline/onion skinning, then pages.
- [ ] Add GIF/image-sequence/PDF exports behind adapters and run full verification.

### Task 10: Acceptance and performance hardening

**Files:** performance fixtures, recovery docs, packaging assets and manual acceptance checklist.

**Interfaces:** Produces measurable latency/memory baselines and release artifacts.

- [ ] Add large-document/many-layer benchmarks and interrupted-save integration tests.
- [ ] Profile preview caching, compositing and background file work; remove UI-thread hotspots.
- [ ] Run all automated tests, package Windows EXE/MSI and Android APK, then record remaining physical-device checks without claiming they were automated.
