# Release Typography Upgrade Design

## Goal

Bring NeoCanvas's editable-text workflow to release-quality parity with the highest-value Procreate controls while keeping existing documents readable, imported fonts local and legally safe, and live canvas rendering consistent with exported output.

## Scope

This release adds:

- persistent import of `.ttf`, `.otf`, and `.ttc` font files through the iPad Files picker;
- kerning applied at a text cursor or selected character range;
- justified paragraph alignment;
- editable outline text with an adjustable outline width;
- horizontal and vertical text orientation; and
- text-box handles that resize the wrapping area without scaling the glyphs.

Mixed font families, sizes, colours, and other arbitrary rich-text spans remain outside this release. Kerning is the only range-level attribute in scope. Existing whole-object controls remain whole-object controls.

## User Experience

### Font library

The Text Studio includes an **Import Font** action. It opens the iPad Files picker for `.ttf`, `.otf`, and `.ttc` files. A valid font is copied into an application-owned font directory, registered for the running process, and shown in an **Imported** section of the searchable font list. Imported fonts survive relaunches.

NeoCanvas derives the displayed family and style names from font metadata rather than the filename. Importing the same font data twice does not create duplicates. A conflicting font with the same internal identity is rejected with a clear message. Invalid, unreadable, unsupported, or oversized files leave the library unchanged and produce an actionable error.

An imported font can be removed from the library only when the user explicitly chooses **Remove Font**. Documents that reference a removed or unavailable font retain the stored family/style identity and render with System fallback. Reimporting the font restores its use without editing the document.

Imported raw font data is never embedded in a `.neocanvas` document or export. The user remains responsible for the licence of imported fonts. Bundled OFL fonts and their notices remain unchanged.

### Typography controls

The Text Studio keeps its current font search, previews, size, tracking, leading, baseline, opacity, bold, italic, underline, uppercase, colour, and alignment controls.

It adds:

- **Kerning**, enabled when the insertion cursor sits between two characters or a character range is selected. The value is stored as sparse character-range adjustments so unrelated text is unaffected.
- **Justify**, alongside Left, Centre, and Right. The final line of a paragraph remains left-aligned. A paragraph with no expandable word spaces renders as left-aligned rather than stretching glyphs.
- **Outline**, with an on/off control and a width control. Outline mode draws the glyph stroke in the active text colour and leaves the glyph interior transparent. Underline and opacity continue to combine with it.
- **Orientation**, with Horizontal and Vertical options. Vertical text advances top-to-bottom in columns progressing right-to-left. Latin glyphs remain upright; this release does not implement language-specific glyph rotation rules.

Text editing exposes a stable cursor/selection range so the kerning control always targets an explicit location. Editing text remaps surviving kerning adjustments by character offset and discards adjustments whose characters were deleted.

### Text-box interaction

An active editable-text object shows distinct text-box handles. Dragging a side handle changes the wrapping width; dragging a top or bottom handle changes the clipping/layout height. Font size and object scale do not change. Existing transform handles remain responsible for scaling, rotation, and movement, so the two interactions are visually and behaviourally distinct.

Text-box dimensions remain positive and are clamped to a usable minimum based on the current font size. Handles respect canvas zoom and handed-interface placement. Tapping away commits the edit and removes the handles.

## Data Model and Compatibility

`TextObject` gains backward-compatible fields for:

- font style identity;
- sparse kerning ranges;
- justified alignment through an additional enum value;
- outline enabled and outline width; and
- text orientation.

Every field is optional in the package representation and receives the current NeoCanvas behaviour as its default: regular style, no kerning adjustments, existing alignment, solid fill, and horizontal orientation. Existing documents therefore load without visual changes.

Unknown font identities do not invalidate a document. The original identity remains serialized while rendering resolves through a deterministic System fallback. Values are validated and bounded during model construction and package loading; malformed optional values fall back safely rather than making the entire artwork impossible to open.

Sparse kerning entries use UTF-16 text offsets because iPad text input and selection APIs expose `NSRange`. Entries are normalized, ordered, non-overlapping, bounded to the current text, and limited in count to prevent malformed packages from causing excessive work.

## Architecture

The common UI layer owns platform-neutral font records, import results, typography state, package fields, text layout intent, and Text Studio presentation. `EditorFileActions` provides the host boundary for choosing, validating, persisting, registering, listing, and removing user fonts. The iOS implementation uses the document picker, application-support storage, Core Text registration, and font metadata APIs.

Font resolution uses one catalogue that merges generic/system choices, bundled OFL families, and validated imported records. A document stores stable font identity rather than a private filesystem path. The catalogue resolves that identity to a platform font for canvas and export.

Canvas rendering and iOS export consume the same normalized text-layout model. Platform-specific drawing may use different APIs, but line breaking, orientation, justification rules, kerning ranges, outline semantics, opacity, and fallback selection must agree. The implementation must not silently show a feature in the editor that disappears from PNG, JPEG, TIFF, PDF, or flattened PSD output.

Text-box resizing is a dedicated edit operation and history command. One completed drag produces one undoable history entry rather than one entry per pointer movement. Cancelled drags restore the original geometry.

## Performance and Limits

Font discovery and metadata parsing do not run during canvas drawing or pointer movement. The imported-font catalogue is loaded once, cached, and refreshed only after an import or removal. Font previews are lazy and reuse resolved typefaces.

An imported file is capped at 32 MiB. Collection fonts are registered as one source file while exposing each valid face. Registration failure rolls back the copied file and catalogue entry. Text layout caches include the font identity, style, content, box geometry, orientation, alignment, spacing, kerning, outline, and scale-relevant values in their keys.

Text-box handle drags update a lightweight preview and commit layout once per frame at most. They must not rasterize the whole canvas or create document-history entries until the drag ends.

## Error Handling

- Cancelled file selection is silent and makes no changes.
- Unsupported extension, invalid metadata, excessive size, duplicate identity, copy failure, or registration failure produces a concise user-visible message.
- Failed imports leave no partial catalogue record or orphaned application-owned file.
- Missing fonts render with System and display a **Missing Font** notice in Text Studio without changing the stored identity.
- Unsupported or malformed package values fall back to defaults and add a compatibility notice where the existing load pipeline supports notices.
- Export either renders the normalized text successfully or returns an explicit failure; it must not omit text silently.

## Accessibility and Localisation

Every new control has a stable accessibility label, selected/enabled state, and a touch target consistent with the current Premium Pill controls. Labels fit the supported compact iPad layout and use the existing localisation mechanism. Font names from metadata are treated as user content and are not translated.

Vertical orientation and justified alignment remain usable with right-to-left text, but language-specific vertical shaping is not claimed in this release. The System fallback must contain the widest platform-provided script coverage available on the device.

## Verification

Automated coverage must include:

- package round trips and old-package defaults for every new field;
- malformed and excessive kerning data normalization;
- font import validation, duplicate handling, persistence, removal, and rollback;
- missing-font fallback without loss of stored identity;
- kerning remapping after insertion and deletion;
- justified final-line and no-space behaviour;
- outline and vertical layout in common rendering and iOS export;
- text-box resize geometry, minimum sizes, cancellation, and single-step undo;
- accessibility semantics for the new controls; and
- regression coverage for all existing text attributes.

Release validation includes the existing full iPad workflow, simulator smoke tests, representative visual screenshots for horizontal, justified, outlined, vertically oriented, imported-font, and missing-font cases, and archive inspection confirming that imported test fonts are not embedded in `.neocanvas` documents.

## Release Acceptance

The feature is complete when all automated and full iPad validation jobs pass, old artworks retain their appearance, imported fonts survive relaunch, missing fonts fall back safely, every new property appears consistently in supported exports, text-box resizing remains responsive, and a fresh unsigned test IPA is produced from the verified branch HEAD.
