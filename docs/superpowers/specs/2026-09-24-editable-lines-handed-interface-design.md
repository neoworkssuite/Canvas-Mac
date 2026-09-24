# NeoCanvas Editable Lines and Handed Interface Design

## Objective

Improve the existing iPad workspace without redesigning NeoCanvas: restore the clear active-colour toolbar swatch, make QuickShape lines permanently editable, and let artists place drawing controls for left- or right-handed use. Preserve all completed brush-pack, colour-panel performance, document, and release work.

## Product decisions

- A held QuickShape line becomes a native `ShapeObject` line, not a permanently rasterized stroke.
- Native lines remain editable after other actions, save/load, duplication, app relaunch, and document reopen.
- Existing documents and existing shape objects open with backward-compatible defaults.
- Normal brush strokes remain raster strokes; only a successfully recognized held line is promoted.
- Handedness changes workspace controls and panel anchoring, never artwork coordinates or document content.
- The top-bar colour control returns to the pre-rainbow active-colour swatch while retaining primary/secondary colour state and the responsive Colour Studio.

## Competitor-informed scope

NeoCanvas will combine the most useful focused behaviours rather than cloning another product. Procreate QuickShape provides endpoint manipulation and 15-degree constrained rotation; Concepts provides numeric length and angle; Affinity exposes stroke width, dash, cap, and arrowhead controls. NeoCanvas 1.0 will implement those line-specific controls but will not add a general vector pen/node subsystem, arbitrary Bézier editing, multi-stroke appearances, or pressure-profile curves.

References:

- <https://help.procreate.com/pocket/handbook/guides/quickshape>
- <https://concepts.app/downloads/concepts-manual-4.1.2.pdf>
- <https://affinity.help/designer2ipad/en-US.lproj/pages/Panels/strokePanel.html>
- <https://help.procreate.com/procreate/handbook/actions/actions-preferences>

## Editable line model

Extend `LayerPayload.ShapeObject` with version-compatible line properties:

- line style: solid, dashed, or dotted;
- line cap: round, square, or flat/butt;
- start and end marker: none or arrow;
- angle snapping enabled, default true;
- dash and marker values use safe bounded defaults.

For a line, existing `x`/`y` are the start point and `x + width`/`y + height` are the end point. This keeps current rendering and transforms compatible. Length is `sqrt(width² + height²)` and angle is normalized from `atan2(height, width)` into 0–359.9 degrees. Numeric length edits retain the start point and angle; numeric angle edits retain the start point and length. Endpoint dragging updates the same deltas. A reverse-direction action swaps endpoints and start/end markers.

The document codec must treat all new fields as optional when decoding older packages. Unknown enum values fall back safely rather than rejecting the document. The encoded package remains data-only.

## QuickShape promotion

When hold-to-snap recognizes a line, the preview remains immediate. On commit, NeoCanvas creates one native line layer using the active brush colour, opacity, and an appropriate bounded stroke width. The raster preview is not also committed. The created line becomes active, opens the object editor affordance, and participates in the same undo transaction as the original gesture.

Circles, triangles, and squares retain their current behaviour in this scope. Promotion failure falls back to the current raster commit without losing the stroke.

## Line editing experience

Selecting a native line shows two Pencil-friendly endpoint handles on the canvas and a focused Line section in Object properties:

- Length numeric field and slider;
- Angle numeric field and 15-degree snap toggle;
- stroke width and opacity;
- solid, dashed, and dotted style;
- round, square, and flat cap;
- optional start and end arrowheads;
- reverse direction;
- stroke colour from the current primary colour.

Dragging either handle is live and undo-coalesced into one edit on release. Numeric inputs reject non-finite or out-of-range values without changing the object and show a concise status message. Locked or hidden lines cannot be edited.

Renderer, layer thumbnails, Gallery previews, export, PSD compatibility reporting, transforms, selection bounds, hit testing, duplication, arrange, undo/redo, save/load, and version comparisons must all use the same line geometry and style.

## Handed interface

Add a persisted `InterfaceSide` preference with `Automatic`, `Left`, and `Right` values. Automatic preserves the existing layout for compatibility.

- Right-handed mode places the vertical drawing rail on the left and anchors non-centred inspectors to the right.
- Left-handed mode places the rail on the right and anchors non-centred inspectors to the left.
- Compact layouts mirror reachable drawing controls and panel anchoring where space permits; the top toolbar keeps semantic reading order.
- Brush, colour, layer, FX, Liquify, object, PSD, settings, version, and Workbench panels must avoid covering the preferred drawing-hand edge when an opposite anchor is available.
- The setting applies immediately, persists across launches, and is reset by Reset Preferences.

This mirrors control placement only. It does not mirror icons containing direction, text alignment, canvas content, transform direction, or artwork coordinates.

## Colour toolbar control

Replace the rainbow-ring canvas with the prior lightweight swatch control. The primary colour fills the large circle, a small overlapping secondary swatch may remain, and the active border indicates whether Colour Studio is open. The control must not allocate or redraw a sweep gradient. Its accessibility description states the active colour and action.

## Commands and undo

All line mutations use document commands, not direct payload replacement from UI code. One drag gesture, one numeric confirmation, one style selection, and one reverse action each create exactly one undo step. Undo/redo must restore geometry, style, markers, selection, and rendered bounds consistently.

## Validation

Use test-driven development for:

- length and angle derivation in every quadrant;
- endpoint, length, angle, reverse, and 15-degree snapping edits;
- invalid numeric input and bounded stroke values;
- legacy document decode and new save/load round trip;
- line style/cap/arrow rendering and bounds;
- QuickShape promotion without a duplicate raster stroke;
- undo/redo coalescing;
- left/right/automatic preference persistence and reset;
- rail order and panel anchoring in regular and compact layouts;
- active-colour swatch semantics and absence of the rainbow rendering path.

Run the complete shared suite, release-contract verifier, iPad Simulator build, ARM64 device build, simulator smoke tests, and extended visual validation. Physical iPad acceptance covers Apple Pencil endpoint dragging, fast QuickShape creation, portrait/landscape handed layouts, panel reachability, and colour-icon visibility.

## Non-goals

- General-purpose vector paths or Bézier nodes.
- Curved QuickShape promotion.
- Dimension annotations, CAD constraints, unit calibration, or engineering scale.
- Multiple strokes/fills or editable pressure profiles on shape lines.
- Mirroring Gallery, documents, artwork, or language direction.
