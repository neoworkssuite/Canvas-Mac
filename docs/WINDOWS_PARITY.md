# NeoCanvas Windows Parity Plan

Reference branch: `ipad-gestures-phase1`
Reference commit at project start: `4dce4d52f880be9193f9b3124bbb8be1601e8251`
Windows parity branch: `windows-parity-foundation`

## Goal

Bring the existing Windows NeoCanvas host to feature and commercial parity with the current Apple implementation without restarting or replacing the application. Shared Kotlin modules remain the source of truth for portable capabilities; Windows-specific hosting, input, packaging, update delivery, file integration, and hardware acceptance stay native to Windows.

## Working rules

- Preserve existing Windows functionality.
- Prefer shared `core`, `brushes`, `renderer`, and `ui` implementations over duplicated Windows-only logic.
- Port engine/state before adding Windows UI for a feature.
- Add regression coverage before or with parity changes.
- Keep ordinary commits on fast Windows CI.
- Reserve `[windows]` commits for deliberate physical-device acceptance checkpoints.
- Keep `.neocanvas` files and brush packs cross-platform compatible.

## Parity phases

1. Foundation: branch, Windows CI, packaging, baseline tests.
2. Core parity: document model, commands, history, persistence.
3. Gallery/editor UI parity.
4. Layers Pro: groups, masks, clipping masks, blend modes, locking.
5. Editable text, shapes, lines, direct transforms and inspector.
6. Arrange V2: multi-select, group/ungroup, alignment, smart guides and snapping.
7. Brush Studio, V2 stamps, brush packs and Neo Nature Studio.
8. Colour Studio parity.
9. Selection, crop, Smudge, QuickShape and drawing guides.
10. Live FX and expanded professional raster effects.
11. Liquify Pro.
12. Deep Layers and sparse history/memory management.
13. Version Tree.
14. Workbench Pro.
15. PSD, PNG, JPEG, TIFF, PDF and NeoCanvas interchange.
16. Windows input parity: pen pressure, eraser, mouse, touch and keyboard shortcuts.
17. Gallery, recovery, trash, Kids Mode and document lifecycle.
18. Windows-native update delivery.
19. Performance profiling and optimisation.
20. Commercial release gate and signed installer.

## Apple-to-Windows interaction mapping

| Apple behaviour | Windows equivalent |
| --- | --- |
| Apple Pencil / touch gestures | Pen, mouse, touch and keyboard shortcuts |
| Pencil QuickMenu | Pen barrel button/right-click configurable QuickMenu |
| iPad Files integration | Windows Explorer, file picker and file associations |
| App Store update lookup | NeoWorks Windows update service/release feed |
| iPad physical acceptance | Windows 11 desktop/tablet/pen hardware acceptance |

## Initial release gate

A Windows parity milestone is only complete when:

- shared regression tests pass on Windows;
- `windowsApp:jvmTest` passes;
- the Windows EXE package is produced by CI;
- the feature works using Windows-native input and file workflows;
- cross-platform NeoCanvas documents remain compatible;
- no existing Windows feature is removed or silently regressed.
