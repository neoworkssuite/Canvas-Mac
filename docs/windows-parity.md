# NeoCanvas Windows parity

Windows parity work is based on the same Kotlin/Compose Multiplatform tree as the current iPad product. Do not replace the existing application or fork the creative engine into an unrelated Windows implementation.

## Reference

- Apple development reference: `ipad-gestures-phase1`
- Reference commit captured for this parity pass: `4dce4d52f880be9193f9b3124bbb8be1601e8251`
- Windows integration branch: `windows-parity-phase1`
- Windows CI: `.github/workflows/windows-build.yml`

The shared `core`, `brushes`, `renderer`, and `ui` modules are the source of truth for cross-platform creative behaviour.

## Already shared with Windows

Because `windowsApp` launches the shared `NeoCanvasApp`, Windows already receives the current shared editor implementation, including the modern Gallery/editor shell, layers, masks and groups, clipping, blend modes, selections, transforms, editable shapes/text/lines, Arrange, snapping, QuickShape, Smudge, Liquify, FX, Colour Studio, Workbench, Version Tree, Deep Layers, brush packs, and the shared NeoCanvas document model.

These features still require Windows build and hardware acceptance before they are considered release-ready.

## Windows host capabilities already present

- Native Windows Compose desktop host
- Local `.neocanvas` library and thumbnails
- Save / Save As / open
- Recovery storage
- Gallery stack persistence
- Local trash behaviour
- Version Tree storage and branches
- Workbench storage
- Deep Layers storage
- PNG export
- PSD import/export
- PNG/JPEG image import
- Palette persistence
- Windows executable and EXE installer packaging

## Phase 1 host parity

- Windows GitHub Actions validation
- Portable application artifact
- EXE installer artifact
- Custom brush-library persistence
- `.neobrush` and `.neobrushpack` import/export
- Shared editor preference persistence
- Safe HTTP/HTTPS external-link handling
- Windows host regression tests

## Remaining Windows-specific parity

1. Font-aware editable text rasterization for Windows exports
2. JPEG export
3. PDF export
4. TIFF export
5. Editable-object-safe PSD flattening
6. Windows update lookup/download/install flow
7. Windows file associations and launch-by-document
8. Surface Pen / Wacom pressure, eraser and barrel-button acceptance
9. Mouse/right-click and keyboard equivalents for Pencil-oriented gestures
10. High-DPI, multi-monitor and resize validation
11. Large-canvas/large-layer memory and brush performance validation
12. Signed release packaging and commercial release gate

## Release rule

A shared feature is not called Windows-complete solely because it exists in common code. It must pass:

1. shared desktop tests;
2. Windows host tests;
3. Windows runnable application packaging;
4. Windows installer packaging;
5. manual Windows 11 acceptance for relevant UI/input changes.

Use `[windows]` in commit messages for deliberate physical Windows acceptance checkpoints.
