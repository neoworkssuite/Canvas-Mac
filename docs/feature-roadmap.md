# NeoCanvas feature expansion

All stages retain local-only operation on Windows and Android tablets.

## Current increment: fill and colour sampling

- Implemented: exact-colour connected fill on the active visible layer, committed as one undoable raster patch.
- Implemented: eyedropper samples visible composited layers over the paper and returns to Brush.
- Added: boundary, cross-tile, no-op and other-layer isolation tests.
- Verified 2026-09-16: renderer and editor desktop tests pass; Windows and Android UI compile. The agent's Java loopback problem was resolved using JAVA_TOOL_OPTIONS with jdk.net.unixdomain.tmpdir pointing to the shorter workspace work directory.
- Limitations: fill uses exact matching and opaque colour; tolerance, reference-layer filling and background processing remain to be added.

## Following increments

Implemented since the initial increment: horizontal/vertical flips, 90-degree rotation, half/double resizing with pixel or premultiplied-alpha smooth sampling, brush tip differences, partial erasing, matching raster previews, stabilisation, symmetry guides, HSV/hex controls, local saved palettes, tap strokes, pen-pressure forwarding, pointer-anchored zoom, cached tile images, and undoable selection clearing. Palette tests cover normalized hex values, reload and failed saves. The palette is stored per host locally, separately from artwork. The left colour rail displays the active hex value and opens the palette on click.

Outstanding: ellipse/lasso selections, inversion/feathering, free transform handles, smudge, layer locks/groups/blends/masks, custom canvas and image import workflow, richer brush editing, shape/perspective tools, text, adjustments, recovery and file-workflow hardening, expanded export, animation and multipage support. Physical tablet input, rendering performance and visual acceptance remain manual checks; compilation alone does not verify them.

Rectangle selection now constrains brush, eraser and fill, with a visible outline and deselect control. Move selected artwork translates pixels on the active visible layer, clamps to the document, and commits one undoable edit on drag release. The outline previews the destination; live pixel previews and explicit apply/cancel controls remain future work. Regression tests cover overlapping moves, tile boundaries, other-layer isolation and undo/redo. UI interaction still requires manual acceptance on Windows and Android.

1. Selection masks: rectangle, ellipse and freehand; clear selection, invert and feather; constrain painting and fill.
2. Transform selected pixels: move, uniform scale, rotate and flip, with commit/cancel and undo.
3. Brush improvements: distinct brush tips, spacing, stabilisation, pressure response, soft erasing and smudge.
4. Layers: blend modes, alpha lock, clipping masks, layer masks and groups.
5. Drawing assistance: editable shapes, grids, symmetry and perspective guides.
6. Document workflow: custom canvas sizes, crop/resize, image import, reference window, gallery and recovery.
7. Colour and text: palettes, colour harmony, editable text and font selection.
8. Adjustments: colour corrections, blur, sharpen, clone and distortion tools.
9. Export expansion, time-lapse recording, animation and multipage sketchbooks.
10. Separately scoped 3D painting investigation after the 2D editor is stable.

Each increment must preserve undo, layer isolation and local document round trips. This roadmap is planned scope, not a claim that these features already exist.
