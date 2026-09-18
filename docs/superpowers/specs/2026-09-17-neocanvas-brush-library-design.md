# NeoCanvas Brush Library Design

## Goal

Build a local-only, expandable brush library for NeoCanvas with 180 original presets across 18 familiar art-tool categories and an isolated live test pad that uses the same renderer as the canvas.

## Scope

- Categories: Pencils, Pens, Inks, Markers, Pastels, Oils, Paints, Gouache, Watercolors, Charcoals, Basics, Lettering, Comics, Design, Grunge, Street Art, Digital, Creative.
- Ten original NeoCanvas presets per category, for exactly 180 paint presets.
- Keep the eraser as a separate utility preset so it does not distort category counts.
- Stable IDs and data-only definitions so future local custom/imported catalogs can coexist.
- Searchable, grouped brush picker with category navigation, favourites, recent brushes, preview strokes, and an isolated scratch pad.
- Responsive layout: category/list/test pad columns when space permits; stacked sections on narrower tablet widths.
- No network services, cloud storage, extracted IPA content, Procreate names, Procreate assets, or copied proprietary settings.

## Brush Model

Each `BrushDefinition` has a category, stable identity, tip shape, spacing, base size, opacity, pressure response, and bounded dynamics for grain, scatter, rotation, shape ratio, edge hardness, and wet mix. All numeric inputs are validated. `BrushCatalog` exposes ordered groups and case-insensitive search without coupling the renderer to UI state.

The renderer stays deterministic: a brush stroke drawn twice with identical input produces identical tiles. Texture and scatter use coordinate/stamp-derived pseudo-noise rather than runtime randomness. Existing built-in IDs remain available for document compatibility.

## Picker and Test Pad

The brush panel has three conceptual regions:

1. Category navigation with counts and All/Favourites/Recent filters.
2. A searchable list of brushes with rendered stroke previews.
3. A scratch pad with Clear, current brush name, and current size/flow information.

The scratch pad owns a private `TileStore`; drawing and clearing it cannot mutate `EditorState.document`, canvas tiles, or history. Selecting a brush updates the editor selection and the scratch-pad renderer. The pad uses the active colour, size, opacity, and pointer pressure.

## Acceptance Criteria

- The catalogue contains exactly 18 ordered categories and 180 paint presets, ten in each category.
- Brush IDs and category IDs are unique and stable; all definitions pass validation.
- Renderer output visibly differs for representative pencil, ink, marker, wet, grain, and scatter dynamics and remains deterministic.
- Search matches brush and category names case-insensitively.
- Favourites and recent brushes are local in-memory UI state for this iteration and do not alter artwork.
- Scratch-pad clear/draw actions never add commands to the artwork history.
- Desktop and Android Compose sources compile, tests pass, and the Windows distribution packages.

