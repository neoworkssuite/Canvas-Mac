# NeoCanvas manual screenshot pack

The manual screenshot workflow captures the real compiled iPad app in a clean Simulator. It produces lossless, unannotated PNG originals suitable for the website, user manual, support articles, and later callout overlays.

## Run the pack

1. Open **Actions → NeoCanvas CI → Run workflow** on GitHub.
2. Select `ipad-gestures-phase1`.
3. Enable **Capture and upload the NeoCanvas website/manual screenshot pack**.
4. Run the workflow.
5. Download the `NeoCanvas-Manual-Screenshot-Pack-<commit>` artifact.

The optional capture is isolated from ordinary push validation. A pack is uploaded only after all required screenshots pass validation. Failed runs upload an XCTest result bundle and any partial screenshots as a diagnostics artifact.

## Pack contents

The pack contains Gallery light and dark appearances, New Canvas, the editor, Brush Library, Colour Studio, Layers, FX and Settings. For this release, also capture the explicit Custom Size fields, the active-colour toolbar swatch, an editable QuickShape line with endpoint handles and its Line controls, plus both Left and Right interface placements. `manifest.json` records the source commit, Simulator model, filenames, and pixel dimensions. `contact-sheet.html` provides a quick visual index.

Keep these source PNGs unedited. Add arrows, numbers, captions, and crops to derived copies so the manual can be updated without repeating the capture.
