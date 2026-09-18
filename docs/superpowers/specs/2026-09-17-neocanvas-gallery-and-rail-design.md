# NeoCanvas Local Gallery and Editor Rail Design

**Date:** 2026-09-17  
**Status:** Approved design, awaiting written-spec review  
**Platforms:** Windows and Android tablets  
**Constraint:** Entirely local; no accounts, network, telemetry, or cloud storage

## Goal

Make a local artwork Gallery the first NeoCanvas screen, with the core organization and preview workflow artists expect from Procreate, while retaining an original NeoCanvas visual identity. Replace the editor's horizontal Size and Flow controls with tablet-friendly vertical controls and place Undo/Redo between them. Repair Windows packaging so the documented test command launches an application rather than an installer.

## Product Flow

NeoCanvas starts on `Gallery`. Opening or creating artwork transitions to `Editor`. The editor's Gallery control returns to the Gallery after resolving unsaved work. Recovery inspection happens before the Gallery is usable; a recovered snapshot opens in the editor and must be saved into the Gallery explicitly.

The app has these top-level destinations:

1. `GalleryRoot` — unstacked artwork and stacks.
2. `GalleryStack(stackId)` — artwork inside one stack.
3. `GalleryPreview(scope, artworkId)` — full-screen, non-editing preview.
4. `Editor(artworkId?)` — existing canvas editor; an absent ID is a new unsaved canvas.

Back from Editor follows the existing unsaved-changes contract. Back from Preview returns to its Gallery scope. Back from a Stack returns to GalleryRoot.

## Gallery Interface

The Gallery is a responsive thumbnail grid: four or more columns on wide Windows windows, two or three columns on tablets depending on orientation and available width. Artwork cards show a rendered thumbnail, editable display name, dimensions, and modified time. Stack cards show layered previews, a stack name, and item count.

The normal toolbar contains:

- NeoCanvas branding.
- `Select` for multi-select mode.
- `Import` for `.neocanvas` documents and supported images.
- `Photo`/`Image` for creating a canvas from a PNG or JPEG using the platform picker.
- `+` for the existing New Canvas dialog.

Single-item interactions:

- Tap artwork: open in Editor.
- Tap name: rename locally after validation.
- Long-press and drag: reorder within the current scope.
- Drop artwork over another artwork: create a new Stack.
- Drop artwork over a Stack: move it into that Stack.
- Swipe left: reveal Export, Duplicate, and Delete.
- Delete always opens a confirmation naming the artwork; cancellation changes nothing.
- Pinch-out or Preview action: enter full-screen Preview. Desktop also exposes a visible Preview action because pinch is not always available.

Stack interactions:

- Tap: enter stack.
- Tap name: rename.
- Long-press and drag: reorder stack at root.
- Swipe left: reveal Rename and Delete Stack.
- Deleting a non-empty stack requires choosing either `Unstack artwork` or `Delete stack and artwork`; the latter has a second explicit confirmation.
- Artwork can be dragged out using a visible `Move out of stack` action in addition to tablet drag gestures.

## Multi-select

Select mode displays a selection indicator on every item and changes the toolbar to:

- `Preview`
- `Export`
- `Duplicate`
- `Stack` (root only)
- `Move out` (inside a stack)
- `Delete`
- `Done`

Bulk Delete uses one confirmation with the exact item count. Bulk Duplicate preserves selection order and inserts copies adjacent to their sources. Bulk Stack creates one stack using the first selected artwork's position. Selection is cleared when leaving the current Gallery scope.

## Preview

Preview renders the stored Gallery thumbnail immediately, then loads a higher-resolution flattened preview asynchronously from the local document. Swipe left/right, arrow buttons, and keyboard arrows move through the current Gallery or selected subset. Double-tap, Enter, or `Open` transitions to Editor. Preview never mutates documents.

## Local Gallery Model

The shared UI consumes host-neutral types:

```kotlin
data class GalleryArtwork(
    val id: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val modifiedEpochMillis: Long,
    val thumbnailPng: ByteArray?,
    val stackId: String?,
    val order: Int,
)

data class GalleryStack(
    val id: String,
    val displayName: String,
    val order: Int,
)

data class GallerySnapshot(
    val artworks: List<GalleryArtwork>,
    val stacks: List<GalleryStack>,
)
```

`EditorFileActions` becomes a local gallery boundary with list, create/import, open, save, rename, duplicate, delete, reorder, create-stack, rename-stack, move-to-stack, unstack, export, and thumbnail operations. The UI never reads host paths and never performs filesystem writes directly.

## Storage

Each platform owns one Gallery directory:

- Windows: `%LOCALAPPDATA%\NeoCanvas\Gallery`
- Android: the existing app-local documents directory

Artwork remains one `.neocanvas` file per item. Cross-document metadata is stored in an atomically replaced `gallery-index.json`. Thumbnail PNGs are stored in `thumbnails/<artwork-id>.png`; missing or invalid thumbnails are regenerated lazily. The index contains only IDs, display names, stack membership, and order. Width, height, modified timestamp, and file existence are reconciled from documents/files on load.

Index writes use temporary-file plus atomic replacement. If the index is missing or corrupt, the host scans `.neocanvas` files, rebuilds a root-level ordering, preserves every readable artwork, and reports a non-blocking recovery message. Gallery mutations update files first only when recoverable, then atomically update the index. Delete moves files into an app-local Trash directory before index removal so an interrupted operation cannot silently destroy the only copy; confirmed deletion may be recovered until Trash cleanup on a later successful startup.

## Thumbnails

The renderer provides a deterministic thumbnail function that composites visible layers with opacity and blend modes, scales to fit within 320×240 while preserving aspect ratio, and encodes RGBA PNG. Saving an artwork updates its sidecar thumbnail after the document save succeeds. Gallery opening does not decode full-size tile sets unless a thumbnail is missing.

Existing `.neocanvas` package compatibility remains unchanged. The currently transparent internal `thumb.png` is replaced with the same generated preview on future saves; old transparent thumbnails remain readable and trigger sidecar regeneration.

## Import, Export, and Share

- Import `.neocanvas`: validate through `NeoCanvasPackage`, assign a new Gallery ID on collision, copy locally, and generate a thumbnail.
- Import PNG/JPEG: decode through the existing platform image adapter, create a canvas sized to the image within current safety limits, place it on one raster layer, save it to Gallery, and open Editor.
- Export single or selected artwork: use platform save/share boundaries. NeoCanvas remains local; the user explicitly chooses any external destination.
- Windows `Photo` is labelled `Image` and uses the existing image chooser.
- Android `Photo` uses the existing image picker contract.

Unsupported or corrupt imports produce an error without changing Gallery order or files.

## Editor Integration

Opening Gallery artwork resets editor history to the loaded document and associates the active editor with its Gallery artwork ID. Save writes back to that artwork and refreshes its thumbnail. Save As creates a new Gallery artwork. The editor's Gallery button requests navigation; if the canvas is dirty, the existing Save / Discard / Cancel dialog resolves first.

New canvas creation from Gallery creates an unsaved editor session. It enters the Gallery only after the first successful Save, preventing empty abandoned documents from cluttering the grid.

## Vertical Editor Rail

The wide editor rail changes to:

1. `SIZE` label and value.
2. Vertical Size slider, with larger values at the top.
3. Undo button.
4. Redo button.
5. Vertical Flow/Opacity slider, with larger values at the top.
6. `FLOW` label and value.
7. Existing color control at the bottom.

Fill mode replaces Size with a vertical Tolerance slider but retains Undo/Redo in the middle and Flow below. Sliders support click/tap-to-jump and vertical drag. Fine adjustment and hover enhancements are separate future work. Compact tablet layouts use the same rail whenever width permits; portrait layouts use a narrow overlay rail rather than reverting to horizontal sliders.

Undo and Redo are removed from the top toolbar to avoid duplicates. Keyboard shortcuts remain unchanged.

## Windows Build and Launch Repair

`packageExe` and `packageMsi` are installers and must never be documented as direct app launchers. Release verification produces:

- `windowsApp/build/compose/binaries/main/exe/NeoCanvas-0.1.0.exe` — installer.
- `windowsApp/build/compose/binaries/main/msi/NeoCanvas-0.1.0.msi` — installer.
- `windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe` — directly runnable application created by `createDistributable`.

Testing instructions use `:windowsApp:run` during development or the `main/app` executable after packaging. Verification must confirm that the runtime process remains alive and that the application window reaches Gallery.

## Error Handling and Safety

- No destructive Gallery action occurs without confirmation.
- Failed save, rename, duplicate, import, export, reorder, stack, or delete leaves the last valid index readable and reports an actionable local error.
- Gallery navigation never discards dirty editor state without the existing explicit Discard action.
- Name validation prevents path traversal, reserved Windows names, empty names, and case-insensitive collisions.
- Stack cycles are structurally impossible because stacks contain artwork only, not other stacks.
- Bulk actions are bounded and report partial failure without deleting successful source items.

## Testing

Tests are written before implementation and cover:

- Gallery is the post-recovery startup destination on both hosts.
- Dirty-editor navigation obeys Save / Discard / Cancel.
- Index round-trip, corrupt-index rebuild, ordering, rename collisions, and path validation.
- Duplicate, recoverable delete, bulk delete cancellation, and interrupted index replacement.
- Stack creation, membership, reorder, unstack, and non-empty stack deletion choices.
- Thumbnail dimensions, aspect ratio, blend-mode pixels, old-package fallback, and cache refresh after save.
- Multi-select ordering and action eligibility.
- Preview scope navigation and non-mutation.
- Image/document import success and failure isolation.
- Vertical slider direction, value mapping, and Undo/Redo placement semantics.
- Windows and Android compile/test suites.
- Windows `createDistributable`, EXE/MSI packaging, runnable executable existence, and launch smoke check.

## Delivery Sequence

1. Gallery model and host boundary.
2. Atomic index and thumbnail renderer.
3. Windows and Android Gallery storage adapters.
4. Startup routing and base Gallery grid.
5. Create/open/save/import/rename/duplicate/delete/export.
6. Reorder, multi-select, stacks, and preview.
7. Vertical editor rail.
8. Windows portable distribution and launch repair.
9. Full automated verification and manual tablet acceptance notes.

No feature in this specification adds a network capability.
