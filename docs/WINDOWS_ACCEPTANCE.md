# NeoCanvas Windows Hardware Acceptance

Use this checklist only for deliberate `[windows]` acceptance checkpoints. Ordinary commits use the fast Windows CI path.

## Acceptance build

## Automated prerequisites

Before this checklist is used, Windows CI must already have passed the shared regression suite, created the runnable distributable, launched the packaged `NeoCanvas.exe --windows-self-test`, verified its PASS report, and produced the normal EXE installer. A `[windows]` checkpoint then builds both EXE and MSI acceptance installers.

- Install the EXE or MSI from the `NeoCanvas-Windows-Acceptance-*` artifact.
- Run `scripts/windows-acceptance.ps1` after installation and keep its report with the test evidence.
- Test on Windows 11 with real mouse/keyboard and, when available, Surface Pen or Wacom hardware.

## Gate A — install, launch and files

- [ ] EXE installer completes without warnings.
- [ ] MSI installer completes without warnings.
- [ ] Start-menu shortcut opens NeoCanvas.
- [ ] Desktop/start-menu icon uses NeoCanvas artwork.
- [ ] Double-clicking a `.neocanvas` file opens that artwork directly in the editor.
- [ ] Open from inside NeoCanvas works.
- [ ] Save and Save As preserve the document.
- [ ] Reinstall/upgrade keeps user artwork, preferences, brushes, fonts, Workbench and Version Tree data.
- [ ] Uninstall does not delete user artwork.

## Gate B — mouse and keyboard

- [ ] Right-click on the canvas opens QuickMenu without painting.
- [ ] QuickMenu Undo/Redo/Pick/Brush/Eraser/Layers all work.
- [ ] Ctrl+Z Undo and Ctrl+Y Redo work.
- [ ] Ctrl+S saves, Ctrl+Shift+S saves a copy.
- [ ] Ctrl+O opens a document and Ctrl+N opens New Canvas.
- [ ] Keyboard shortcuts do not fire while typing/editing text.

## Gate C — pen

- [ ] Light pressure creates visibly lighter/smaller response where the chosen brush supports pressure.
- [ ] Medium and heavy pressure transition smoothly with no sudden jumps.
- [ ] Pen input does not become a touch gesture.
- [ ] Flipping an eraser-capable pen temporarily erases without changing the selected Brush tool.
- [ ] Returning to the pen tip resumes the previous brush.
- [ ] Barrel-button/right-click behaviour opens QuickMenu where the Windows driver exposes it as secondary click.
- [ ] Long strokes remain continuous and do not lag behind the cursor.

## Gate D — touch

- [ ] One-finger painting follows the Finger Painting setting.
- [ ] Two-finger pinch zoom is smooth.
- [ ] Two-finger pan is smooth.
- [ ] Rotation obeys the Canvas Rotation setting.
- [ ] Two-finger tap Undo works.
- [ ] Three-finger tap Redo works.
- [ ] Three-finger scrub clears as designed.
- [ ] Four-finger tap toggles canvas-only mode.
- [ ] Touch navigation never leaves accidental brush marks.

## Gate E — display and layout

- [ ] 100% Windows scaling: no clipped controls.
- [ ] 125% scaling: no clipped controls.
- [ ] 150% scaling: no clipped controls.
- [ ] 200% scaling: no clipped controls.
- [ ] Moving NeoCanvas between monitors with different DPI remains usable.
- [ ] Gallery, New Canvas, Layers, Brush Studio, Colour Studio, FX, Liquify, Workbench and Settings fit on screen.
- [ ] Floating panels do not resize the artwork canvas.

## Gate F — current Mac/iPad feature parity

- [ ] Groups, masks and clipping masks.
- [ ] Blend modes and locking.
- [ ] Editable text, shapes and editable lines.
- [ ] Advanced text: imported fonts, styles, justify, outline, vertical text, kerning and text-box resize handles.
- [ ] Multi-object Arrange, smart guides and snapping.
- [ ] Brush Studio, `.neobrush`, `.neobrushpack` and Neo Nature Studio.
- [ ] Colour Studio modes and palettes.
- [ ] Smudge, QuickShape, guides, automatic selection and crop.
- [ ] Live FX.
- [ ] Liquify Pro.
- [ ] Deep Layers.
- [ ] Version Tree.
- [ ] Workbench Pro.

## Gate G — interchange

- [ ] Import and save `.neocanvas` documents.
- [ ] PSD import preserves supported layers.
- [ ] PSD export opens in an independent PSD reader/editor.
- [ ] PNG export.
- [ ] JPEG export.
- [ ] TIFF export.
- [ ] PDF export.
- [ ] Imported TTF/OTF/TTC fonts survive relaunch.

## Gate H — resilience and performance

- [ ] Recovery restores work after a forced close.
- [ ] Version Tree restore creates a safety version first.
- [ ] 4000×4000 artwork can be edited without UI lock-up.
- [ ] Large brush strokes remain responsive.
- [ ] Transform previews remain interactive on large artwork.
- [ ] Repeated undo/redo does not corrupt pixels or editable objects.
- [ ] Closing with unsaved work prompts correctly.

## Acceptance result

Record device model, Windows build, input device/driver, display scale and the tested commit SHA. A release checkpoint is accepted only when all applicable gates pass; unavailable hardware should be recorded as `NOT TESTED`, never assumed to pass.
