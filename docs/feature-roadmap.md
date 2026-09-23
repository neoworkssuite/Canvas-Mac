# NeoCanvas 1.0 status

NeoCanvas has moved from feature expansion into **commercial release hardening**.

This document reflects the current `ipad-gestures-phase1` product rather than the early prototype roadmap.

## Implemented 1.0 creative workflow

- Apple Pencil drawing with pressure-sensitive brushes, eraser, directional Smudge and configurable finger painting.
- Sparse-tile raster engine with undo/redo and bounded local layer storage.
- Brush Library and Brush Studio.
- Fill and configurable Eyedropper sampling.
- QuickShape and drawing assistance: grid, symmetry and perspective guides.
- Rectangle, ellipse, lasso and automatic selections.
- Move, scale, rotate, flip and selection transforms with live previews.
- Editable Text and Shape objects with styling, direct manipulation, smart guides, snapping and multi-object Arrange.
- Layer opacity, visibility, lock, alpha lock, clipping, blend modes, masks and groups.
- Deep Layers memory management.
- Live adjustments and FX.
- Liquify Pro spatial warping.
- Workbench references, notes and colour cards.
- Version Tree with branches and visual comparisons.
- Local Gallery with rename, duplicate, trash deletion and persistent Stack.
- Native `.neocanvas` save/load plus recovery snapshots.
- PSD import/export with compatibility reporting.
- PNG, JPEG, PDF and lossless TIFF export.
- Pencil-friendly QuickMenu, canvas-only mode and multi-finger productivity gestures.
- Portrait and landscape iPad layouts.

## 1.0 commercial release gate

NeoCanvas 1.0 is release-candidate quality only when all of the following remain true:

1. Shared core/brush/renderer/UI automated tests pass.
2. Full Apple CI builds both simulator and ARM64 physical-device targets.
3. Simulator cold-launch, light/dark appearance and relaunch stability checks pass.
4. Built iPad app contains the AppIcon asset catalog and privacy manifest.
5. Save/load, recovery, Gallery trash and persistent Stack workflows are verified.
6. Primary export formats produce valid output.
7. No known reproducible data-loss or startup-crash issue remains.
8. Final physical-iPad acceptance is completed with Apple Pencil.

## After 1.0

Post-1.0 work should be driven by customer value and measured product feedback rather than delaying the first commercial release. Candidates include animation/time-lapse, multipage sketchbooks, additional vector tooling, richer colour management and separately scoped 3D workflows.
# Brush packs

NeoCanvas now includes versioned image-stamp brushes, secure `.neobrushpack` import/export, iPad Files integration, and the free 18-brush Neo Nature Studio landscape collection. Future pack work may add more free first-party collections; paid packs and StoreKit unlocks remain out of scope for the first release.
