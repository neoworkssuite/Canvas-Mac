# NeoCanvas Design

## Purpose

NeoCanvas is an offline-first raster drawing and painting studio for Android and Windows. It belongs to NeoWorkSuite but does not require an account, a network connection, or cloud storage. Documents remain local until a person explicitly exports or copies them.

Android is tablet-first. The primary Android layout targets landscape tablets with room for a large uninterrupted canvas, a reachable upper toolbar, left-edge size and opacity controls, and an optional right-side layers panel. Smaller Android screens remain supported through collapsible panels, not a feature-reduced document model.

Version one focuses on a dependable, low-latency drawing workflow: freehand strokes, erasing, raster layers, canvas navigation, history, local files, and PNG export. It deliberately does not include collaboration, accounts, cloud sync, vector editing, animation, 3D, or an extension marketplace.

## Technology and repository layout

Use Kotlin and Compose Multiplatform. Android and Windows hosts share application logic and UI where it does not depend on a platform API. Platform modules own window lifecycle, file pickers, storage locations, and stylus/pointer input.

```
neocanvas/
  core/          Document model, commands, history, persistence contracts
  renderer/      Tile cache, raster compositing, viewport transforms
  brushes/       Brush definitions, stroke sampling, raster stamp generation
  ui/            Canvas workspace, tools, layer panel, document actions
  androidApp/    Android host and stylus adapter
  windowsApp/    Windows host and pointer adapter
  test-fixtures/ Small deterministic documents and rendered references
```

The `core`, `renderer`, and `brushes` modules must not import UI or host code. The UI depends on their public interfaces rather than their storage/rendering internals.

## Workspace

The editor has a central infinite-feeling viewport around a bounded raster document. Its visual direction is dark, calm, and canvas-first: a compact upper tool strip, left-edge brush size and opacity controls, and a right-side layer panel that can be hidden to preserve focus. NeoCanvas uses its own icons, rounded panels, spacing, colours, and arrangement rather than copying a competitor's visual assets or exact interface layout.

V1 interactions:

- Draw with a mouse, touch input, or supported pen/stylus.
- Pan and zoom the viewport.
- Select a brush or eraser; adjust size, opacity, and colour.
- Add, rename, hide/show, reorder, duplicate, and delete raster layers.
- Set layer opacity.
- Undo and redo document operations.
- Create, open, save, save-as, and export a flattened PNG.

Pressure is optional input: each host reports it when available and the brush engine falls back to `1.0` otherwise. Tilt and barrel buttons remain future input fields, not v1 behavior.

## Document model

`CanvasDocument` owns metadata, a list of layers, and document-local history metadata. Each layer has a stable UUID, name, visibility, opacity, ordering index, and a typed payload. V1 uses `RasterLayerPayload`; the sealed payload boundary reserves future `TextLayerPayload`, `VectorLayerPayload`, `GroupLayerPayload`, and `AdjustmentLayerPayload` without changing consumers.

Raster content is tile based rather than a single full-canvas bitmap. A tile has fixed pixel dimensions and is addressed by `(layerId, tileX, tileY)`. Only tiles touched by a stroke are loaded or modified. This preserves memory headroom for larger canvases and future high-resolution support.

All changes are explicit `DocumentCommand` values such as `AddLayer`, `MoveLayer`, `SetLayerOpacity`, `ApplyRasterPatch`, and `RenameLayer`. Commands are the sole mutation path. They provide undo/redo entries, reliable autosave boundaries, and a later collaboration/event-log seam without implementing sync now.

## Rendering and brush pipeline

The input adapter emits platform-neutral `StrokeSample` records: position, timestamp, pressure, tilt (optional), and pointer kind. A stroke processor smooths and interpolates samples, then the brush engine produces raster stamps. The renderer applies stamps to only the affected layer tiles and composites visible layers in order.

V1 ships with a small, original set of presets: pencil, ink, soft round paint, flat marker, and eraser. Brush definitions are data objects, not code-only presets, so custom brushes and import adapters can be added later. No Procreate brushes, assets, formats, or lookalike user interface elements are used.

## Files and recovery

The native extension is `.neocanvas`. It is a versioned ZIP package:

```
manifest.json       format version, canvas metadata, layer metadata
layers/<id>/<x>-<y>.png
thumb.png
assets/             reserved for future embedded resources
```

Writes use a temporary sibling file followed by an atomic replacement where the host permits it. A recovery copy is retained on unexpected exit and surfaced on next launch. Autosave writes only after a quiet period and never replaces an explicit saved document path without a successful complete package write.

PNG export composites the visible canvas at document resolution. V1 reports a clear error if the selected location is unwritable, storage is insufficient, or a package is incompatible/corrupt; it must not silently discard the active in-memory document.

## Extensibility boundaries

The following interfaces isolate future work:

- `DocumentStore` for local storage now and optional synced stores later.
- `LayerPayload` for new layer types.
- `BrushCatalog` and versioned `BrushDefinition` for custom/imported brush systems.
- `ExportAdapter` for PSD, JPEG, PDF, animation, and other formats.
- `InputAdapter` for pen button, hover, accessibility, and new platforms.
- `RenderEffect` for blend modes, masks, filters, and non-destructive adjustments.

Network, accounts, telemetry, and background upload code are specifically out of scope. Any later sync feature must be opt-in and implemented behind `DocumentStore`; it cannot change the local-only default.

## Errors, accessibility, and performance

The app preserves the in-memory document when file operations fail, provides actionable messages, and keeps an undoable state after recoverable renderer errors. Rendering stays on a dedicated worker path; UI state only receives frame-ready image data and status updates. Long operations such as save, export, and large thumbnail generation show progress and remain cancellable where safe.

All controls have accessible names and keyboard equivalents on Windows. Android supports touch targets suitable for fingers and does not make a stylus mandatory. Gesture alternatives are exposed as visible controls for pan, zoom, undo, and redo.

## Test strategy

- Unit tests: document commands, history, manifest migration/validation, brush interpolation, tile addressing.
- Renderer tests: deterministic fixture documents rendered to reference pixels.
- Integration tests: create → draw → save → reopen → export verifies preserved content and metadata.
- Host tests: Android and Windows input adapters map pressure and fallback behavior correctly.
- Manual acceptance: responsive drawing, layer reorder, undo/redo, recovery after an interrupted save, and local-only operation with networking disabled.

## Acceptance criteria for v1

1. A user can create a local document, draw and erase, manage raster layers, save it, reopen it, and export a correct PNG on Android and Windows.
2. The same `.neocanvas` file opens with the same visible artwork and layer metadata on both supported platforms.
3. No network request, account prompt, or cloud configuration is needed for ordinary operation.
4. An interrupted or failed save does not replace the last successful document or discard current work.
5. The public module interfaces above support later formats, layer types, and optional sync without a v1-breaking document-model rewrite.
