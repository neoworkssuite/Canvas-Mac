# NeoCanvas 1.0 release checklist

## Automated release gate

- [ ] Fast validation green on the exact release-candidate tree.
- [ ] Full `[ipad]` validation green on the exact release-candidate tree.
- [ ] iPad Simulator app builds and remains alive after launch.
- [ ] Three cold relaunches pass.
- [ ] Light and dark appearance screenshots are produced.
- [ ] ARM64 NeoCanvasKit framework builds.
- [ ] Unsigned Release-iphoneos NeoCanvas.app builds.
- [ ] Built app contains `PrivacyInfo.xcprivacy`.
- [ ] Built app contains compiled `Assets.car`.
- [ ] Built Info.plist reports `ITSAppUsesNonExemptEncryption = false`.

## Data safety

- [ ] Manual Save retires stale recovery snapshot.
- [ ] Failed Save keeps recovery available.
- [ ] Start fresh retires an offered recovery snapshot.
- [ ] Gallery Delete moves artwork to local trash.
- [ ] Gallery Stack survives relaunch and reconciles rename/delete.
- [ ] Save/load round-trip retains raster, editable objects, masks and groups.
- [ ] Undo/redo tested after brush, erase, Smudge, Liquify, transforms and Arrange.

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
- [ ] Confirm no unexpected network/account requirement.

## App Store handoff

- [ ] Create signed Archive with the NeoWorksSuite Apple Developer account.
- [ ] Confirm App Store Connect privacy answers match the checked-in privacy manifest.
- [ ] Add final screenshots, description, support URL and privacy URL.
- [ ] Increment `CURRENT_PROJECT_VERSION` for every uploaded build after build 1.
- [ ] Keep the physical iPad as the final acceptance judge.
