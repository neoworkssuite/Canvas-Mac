# NeoCanvas Custom Brushes Design

## Goal

Add a local-only Brush Studio that edits a copy of any preset, saves original custom brushes, persists favourites/recents/custom brushes, and imports or exports a versioned NeoCanvas brush format on Windows and Android.

## Design

- Built-in brushes remain immutable and retain stable IDs.
- Brush Studio edits a working copy and creates a `user.*` brush with a new stable local ID.
- `NeoBrushCodec` owns a strict, versioned UTF-8 text format. It validates every decoded `BrushDefinition`, rejects unknown versions/fields, and has no platform dependencies.
- `BrushLibraryState` merges built-ins with custom brushes and serializes UI state through `BrushLibrarySnapshotCodec`.
- `EditorFileActions` remains the only file boundary. Hosts persist the library locally and expose explicit import/export operations.
- Windows uses `%LOCALAPPDATA%/NeoCanvas/Brushes`; Android uses the app-local external/files directory. No network permission or service is introduced.
- Import never replaces a brush silently: colliding IDs receive a new `user.*` ID.

## Acceptance Criteria

- Any paint brush can be edited without mutating its built-in definition.
- Studio exposes spacing, grain, scatter, rotation, shape ratio, hardness, wet mix, jitter, and pressure response.
- Saving creates a selectable custom brush and survives a new `BrushLibraryState` instance.
- Favourites and recents survive reload and ignore missing IDs safely.
- A `.neobrush` round trip preserves all brush fields.
- Malformed or unsupported brush files are rejected without changing the library.
- Windows and Android tests/builds pass and distributable outputs are generated.

