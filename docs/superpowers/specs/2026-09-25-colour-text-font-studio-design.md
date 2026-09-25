# Colour, Toolbar, and Text Studio Design

## Goal

Make colour history reflect colours actually committed to artwork, clarify the Smudge and Undo controls, and upgrade editable text with richer typography and a legally distributable font library.

## Behaviour

- Selecting, previewing, swapping, or restoring a colour does not change Recent Colours.
- A colour enters Recent only after a successful paint stroke, fill, editable shape/line insertion, editable text insertion, or applying the current colour to an editable object.
- Eraser, Smudge, Liquify, eyedropper sampling, cancelled operations, and rejected edits do not add a recent colour.
- Recent Colours remain deduplicated, newest first, and capped at 12.
- Smudge uses a recognisable fingertip/blending glyph. Undo and Redo use conventional mirrored curved arrows and retain accessible names.
- Editable text adds tracking, baseline, opacity, underline, uppercase rendering, font search/category presentation, and live font-name previews while preserving size, leading, bold, italic, colour, and alignment.
- Existing documents load with defaults for every new typography field.
- The initial expanded font library uses platform-safe generic/system families plus bundled OFL font files whose copyright and licence notices ship with the app.
- User-installed font discovery/import is represented as a separate platform capability. Missing fonts fall back to System without destroying the stored family name.
- NeoCanvas never embeds a user-imported raw font file in a shared document by default.

## Compatibility

The package format remains readable through optional fields with defaults. Canvas rendering and iOS export apply the same text attributes as closely as their platform APIs permit.

