# Android parity

## Preserved history

The complete public `neoworkssuite/Canvas_android` main lineage is preserved in
the canonical repository as `imports/canvas-android-main`. The refreshed source
head is `74176a28397205b33907ca9e8cb312bdd418875a`; its original commit graph is
reachable from that isolated branch and remains available in the source repo.

The canonical runtime keeps current shared iPad/KMP code. Android-only commits
were replayed individually so their authorship remains traceable without
overwriting newer `core`, `brushes`, `renderer`, or `ui` behavior.

## Integrated host behavior

- Android lifecycle state survives tablet configuration changes.
- Android Storage Access Framework opens `.neocanvas`, image, PSD, brush, and
  brush-pack files.
- FileProvider-backed sharing covers PNG, JPEG, PDF, PSD, TIFF, brushes, and
  brush packs.
- Local Gallery, recovery, versions, Workbench, Deep Layers, palettes, and
  preferences use app-private Android storage.
- The host consumes the canonical shared document, renderer, brush, and UI
  modules rather than copied implementations.

## Status

| Capability | Status | Evidence |
|---|---|---|
| Shared Android host contract | 🟡 | Local contract passes; canonical workflow pending |
| Android unit tests and lint | 🟡 | Canonical workflow pending |
| Debug APK | 🟡 | Canonical workflow pending |
| Play Store AAB/signing | 🔴 | Release keystore and store publishing are not configured |
| Hardware stylus validation | 🟡 | Input bridge is present but no physical-device run is recorded |

The source repository's run `36038830556` passed at the imported head. Canonical
statuses move to green only when the integrated branch passes its own workflow.
