# NeoCanvas 1.0 release checklist

## Automated release gate

- [ ] Fast validation green on the exact release-candidate tree.
- [ ] Full `[ipad]` validation green on the exact release-candidate tree.
- [x] iPad Simulator app builds and remains alive after launch.
- [x] Three cold relaunches pass.
- [x] Light and dark appearance screenshots are produced.
- [x] ARM64 NeoCanvasKit framework builds.
- [x] Unsigned Release-iphoneos NeoCanvas.app builds.
- [x] Built app contains `PrivacyInfo.xcprivacy`.
- [x] Built app contains compiled `Assets.car`.
- [x] Built Info.plist reports `ITSAppUsesNonExemptEncryption = false`.

Evidence recorded 2026-09-22:

- Run [#263](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/35782422820) completed successfully at `a02c6f577f2a43f5f86c1fdf02704fddc7babf0e`, including shared tests, iPad Simulator build and smoke/visual validation, three cold relaunches, ARM64 framework build, unsigned device build, privacy/assets checks and encryption metadata.
- Run [#265](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/35785683464) completed fast validation successfully at `0807a6f03f60a200a5b01e218933bc6aa9ab0e08`.
- The exact final-candidate fast and full `[ipad]` items remain open until the final same-tree checkpoint completes.

## Data safety

- [x] Manual Save retires stale recovery snapshot.
- [x] Failed Save keeps recovery available.
- [x] Start fresh retires an offered recovery snapshot.
- [ ] Gallery Delete moves artwork to local trash.
- [ ] Gallery Stack survives relaunch and reconciles rename/delete.
- [x] Save/load round-trip retains raster, editable objects, masks and groups.
- [ ] Undo/redo tested after brush, erase, Smudge, Liquify, transforms and Arrange.

The checked recovery and save/load items are covered by the shared regression suite in Run #265. Gallery trash, Gallery Stack relaunch persistence and the full cross-tool Undo/Redo matrix remain physical-iPad acceptance items.

## Manual physical-iPad acceptance

> NeoCanvas 1.0 is intentionally scoped to iPad only. Do not enable iPhone distribution until a separate iPhone layout/input acceptance gate exists.

- [ ] Create, draw, save, close and reopen a new artwork.
- [ ] Force-close with unsaved edits and verify recovery.
- [ ] Test Apple Pencil pressure, fast strokes and large brushes.
- [ ] Test zoom/pan/rotation while drawing.
- [ ] Test portrait and landscape.
- [ ] Test Liquify Push/Pinch/Expand/Twirl and other Pro modes.
- [ ] Test text/shape direct manipulation and multi-object Arrange.
- [ ] Test Gallery rename, duplicate, Stack and trash deletion.
- [ ] Export PNG, JPEG, PDF, TIFF and PSD and open each output.
- [ ] Import a representative PSD and image.
- [ ] Confirm no account requirement; with update checks enabled, verify the only automatic network request is the App Store version lookup.
- [ ] Test automatic update prompt with a newer mocked App Store response and verify Open App Store / Later.
- [ ] Test manual Check for Updates in Settings.

## App Store handoff

- [ ] Create signed Archive with the NeoWorksSuite Apple Developer account.
- [ ] Confirm App Store Connect privacy answers match the checked-in privacy manifest.
- [ ] Add final screenshots, description, support URL and privacy URL.
- [ ] Increment `CURRENT_PROJECT_VERSION` for every uploaded build after build 1.
- [ ] Keep the physical iPad as the final acceptance judge.
