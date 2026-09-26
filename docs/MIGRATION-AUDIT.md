# NeoCanvas Migration Audit

Collected: 2026-09-26T10:46:12+01:00

This audit is the pre-write evidence record for consolidating NeoCanvas into
`neoworkssuite/NeoCanvas`. All GitHub references were read from the API at the
time above. They must be refreshed again immediately before a tag, branch, or
repository write because development may continue concurrently.

Status vocabulary in this document is evidence-based:

- ✅ present and validated by the cited workflow or command
- 🟡 present but not validated in the target consolidated repository
- 🔴 missing or broken

## Repository visibility and default branches

| Repository | Visibility | Default | Archived | Observed default HEAD | Evidence |
|---|---|---|---|---|---|
| `neoworkssuite/Canvas-Mac` | Public | `main` | No | `59d93a0f0384edd65c9fd763b437a9e7ca66e0e7` | GitHub repository and ref APIs |
| `neoworkssuite/Canvas_android` | Public | `main` | No | `74176a28397205b33907ca9e8cb312bdd418875a` | GitHub repository and ref APIs |
| `christianrobertson36/neoworks` | Private | `main` | No | `803fc4ba359042a0843761286982b80e839395ae` | GitHub repository and ref APIs under the stored legacy identity |

`neoworkssuite/NeoCanvas` did not exist when the approved design was prepared.
Its state is deliberately re-checked in Task 3 rather than assumed here.

## Branch and tag HEADs

### `neoworkssuite/Canvas-Mac`

| Repository | Ref | SHA |
|---|---|---|
| `neoworkssuite/Canvas-Mac` | `apple-platform` | `2c5d93a07446b47c7bcf87c147d76ff6dfec125d` |
| `neoworkssuite/Canvas-Mac` | `ipad-gestures-phase1` | `bbc11c370c0872effee19d8e676d86117b064673` |
| `neoworkssuite/Canvas-Mac` | `main` | `59d93a0f0384edd65c9fd763b437a9e7ca66e0e7` |
| `neoworkssuite/Canvas-Mac` | `windows-export-parity` | `cea53ea034e0a844d1dbd22e5631c13211165962` |
| `neoworkssuite/Canvas-Mac` | `windows-host-parity` | `e05bc6bf13fef9ec62c77dda3082b86d948393a2` |
| `neoworkssuite/Canvas-Mac` | `windows-parity-foundation` | `65120e86cf8a9f97f6873e597781938f30d31394` |
| `neoworkssuite/Canvas-Mac` | `windows-parity-phase1` | `eb4253756153a1c318238577e7f86c285cab80eb` |
| `neoworkssuite/Canvas-Mac` | `windows-release-contract` | `21c55ae6d65245420951818e3029594d9dd03444` |

The tags API returned no tags.

### `neoworkssuite/Canvas_android`

| Repository | Ref | SHA |
|---|---|---|
| `neoworkssuite/Canvas_android` | `main` | `74176a28397205b33907ca9e8cb312bdd418875a` |

The tags API returned no tags.

### `christianrobertson36/neoworks`

| Repository | Ref | SHA |
|---|---|---|
| `christianrobertson36/neoworks` | `accessibility-semantics-polish` | `01823eef0cedca1de3265f17fee9887548ae4de4` |
| `christianrobertson36/neoworks` | `calendar-workflow-polish` | `ad2009bb2691ab2076d2ecee9a1eeed779b0a838` |
| `christianrobertson36/neoworks` | `creative-app-polish` | `f77796b901f9e571082e520b1459b92126e2d7bb` |
| `christianrobertson36/neoworks` | `data-calendar-dialog-polish` | `6b062883f1f2630e9294d161caf2373b15dc8cd9` |
| `christianrobertson36/neoworks` | `data-calendar-toolbar-dark-polish` | `9447f32065773038a38efead9215526e79312910` |
| `christianrobertson36/neoworks` | `database-workflow-polish` | `d368aef9097d2d382c9d16c7e89843800a67176c` |
| `christianrobertson36/neoworks` | `design-workspace-polish` | `a31da3bd21cc6b4ed44a20a1623ce4882f710f60` |
| `christianrobertson36/neoworks` | `feature/ai-avatar-studio` | `c1d35b10ca326787bdd33a19959587dc8daac7e8` |
| `christianrobertson36/neoworks` | `feature/neoworks-darkroom-native-core` | `554468630cc6f623efc3a80f4ca2a13625279809` |
| `christianrobertson36/neoworks` | `feature/preview-build-polish` | `694271d73582144a1bd72e10b4949c96cc9bf831` |
| `christianrobertson36/neoworks` | `feature/psd-support-phase1` | `276fcd28bbf9aba22092ca6f8a6cc3768bf33066` |
| `christianrobertson36/neoworks` | `installer/shared-dotnet-runtime` | `44fb8bfaed01df48b2d0968667f2264f215c8fb3` |
| `christianrobertson36/neoworks` | `launch-experience-polish` | `b0ea992c8e384046b99178e1f647b69f9be90797` |
| `christianrobertson36/neoworks` | `launcher-campaign-polish` | `ea7e9db53e1edde743e20218b68bc4f65d3a2b33` |
| `christianrobertson36/neoworks` | `launcher-kickstarter-polish` | `f2c2d0afedb3ccc34a9cfc60a6284a2426472d77` |
| `christianrobertson36/neoworks` | `launcher-module-tile-polish` | `ac5115dc1d5102240e566f6734fc69897157f437` |
| `christianrobertson36/neoworks` | `main` | `803fc4ba359042a0843761286982b80e839395ae` |
| `christianrobertson36/neoworks` | `pdf-preview-polish` | `edcefa8ab961a33732f1cdc51dd9dadc4e840977` |
| `christianrobertson36/neoworks` | `pdf-toolbar-dark-polish` | `d8fa338241c010ce321d61a5c8ff696087db3a6a` |
| `christianrobertson36/neoworks` | `phase1-creative-colour-management` | `c7815fae4faaa76e3c86b03353bb24ca86beb472` |
| `christianrobertson36/neoworks` | `phase2-raw-lens-foundation` | `42ccaf3447af651f78e8991ea886fbf32c01c27f` |
| `christianrobertson36/neoworks` | `phase2-raw-lens-latest-photo` | `55d9862964221b92d32c622d576a84fc8cd1e64a` |
| `christianrobertson36/neoworks` | `photo-professional-layers` | `acb529c6da11a4bc8dc667eb5c45d9bbe8edc147` |
| `christianrobertson36/neoworks` | `photo-start-polish` | `58c8f7cfab6d1bccba36d6f290e3847831730be1` |
| `christianrobertson36/neoworks` | `photo-utility-polish` | `fddaae592b749726b3c77074c4689ba902605d16` |
| `christianrobertson36/neoworks` | `productivity-app-polish` | `bcd477ec364c59d90bf4926be174b7cccbfdf5d6` |
| `christianrobertson36/neoworks` | `resolution-accessibility-polish` | `25e97176a53b62dcb6046df38a17be5b9f3f52a5` |
| `christianrobertson36/neoworks` | `runtime-qa-photo-pdf-fixes` | `aa600bed06c49988d0f2271318e355da5c2a1bc8` |
| `christianrobertson36/neoworks` | `screenshot-sample-content` | `be72fcb59c5bcc4af9538312c7028969e65fb395` |
| `christianrobertson36/neoworks` | `shared-toolbar-contrast-cleanup` | `6140d06731569422dba4b78115273b0f5a869691` |
| `christianrobertson36/neoworks` | `sheet-chart-polish` | `7e63483961869e430c211fcf10daa5df66c0e27e` |
| `christianrobertson36/neoworks` | `sheet-toolbar-dark-polish` | `28b6dcc8f2fea19594393b598542f60238935792` |
| `christianrobertson36/neoworks` | `sheet-workflow-polish` | `a36ac2102546fdecc1b009deb409479273ee8e01` |
| `christianrobertson36/neoworks` | `ui-modernization-2026` | `a9df5eab32f22f12a9543675b5288c557bcd9485` |
| `christianrobertson36/neoworks` | `ui-modernization-completion-checkpoint` | `299ed9cd224b3a979b006daf882047c30761e1f1` |
| `christianrobertson36/neoworks` | `ui-polish-phase2` | `a18848c03ab27d5e9b32596399f4c16e7674d286` |
| `christianrobertson36/neoworks` | `ui-polish-phase2-safety` | `4a9066e5387c98051aabda65070700f4998d030b` |
| `christianrobertson36/neoworks` | `version-truth-cleanup` | `e6edc757e883324c0d871de09997161559fe0655` |
| `christianrobertson36/neoworks` | `writer-3-office-experience` | `e98bce40411890f26d6f4d6b1c2283c6ca020409` |
| `christianrobertson36/neoworks` | `writer-toolbar-dark-polish` | `999c540950e7204c9f511d3d90960456764ad10` |
| `christianrobertson36/neoworks` | `writer-workflow-polish` | `336c823521870af2336921522eef08ecfb293b1f` |

The tags API returned no tags. The repository is the wider NeoWorks Windows
suite, not a dedicated NeoCanvas repository; only demonstrated Design/Photo
capabilities are candidates for comparison, not wholesale import into the
NeoCanvas runtime.

## Ancestry relationships

GitHub compare results use each named branch as the base and
`ipad-gestures-phase1` as the head:

| Base branch | Relationship | iPad-only commits | Base-only commits | Merge base |
|---|---:|---:|---:|---|
| `main` | iPad ahead | 348 | 0 | `59d93a0f0384edd65c9fd763b437a9e7ca66e0e7` |
| `apple-platform` | iPad ahead | 352 | 0 | `2c5d93a07446b47c7bcf87c147d76ff6dfec125d` |
| `windows-export-parity` | Diverged | 21 | 12 | `0e319a3f7632edade7cfb8c1cf0e38d10115f931` |
| `windows-host-parity` | Diverged | 24 | 4 | `4dce4d52f880be9193f9b3124bbb8be1601e8251` |
| `windows-parity-foundation` | Diverged | 13 | 61 | `a57bc4f18eb3d616fbe294b5a505572809600163` |
| `windows-parity-phase1` | Diverged | 21 | 7 | `0e319a3f7632edade7cfb8c1cf0e38d10115f931` |
| `windows-release-contract` | Diverged | 24 | 10 | `4dce4d52f880be9193f9b3124bbb8be1601e8251` |

Ruling for later Windows integration: `windows-parity-foundation` is the
most complete demonstrated Windows-specific lineage (61 base-only commits and
successful run `36193129797`), but it is not automatically the merge winner.
Task 5 must compare its capabilities with export/release-contract branches and
current iPad shared changes before selecting commits.

`apple-platform` is already an ancestor of iPad and has no unique commits.
It is lineage evidence, not evidence that a native macOS host exists.

## Module inventory

### Canvas-Mac / current iPad reference

The root Gradle project includes `core`, `brushes`, `renderer`, `ui`,
`androidApp`, and `windowsApp`. Shared responsibilities observed in source and
tests are:

| Module | Responsibility | Evidence quality |
|---|---|---|
| `core` | document/layer/history model, gallery model, safe package storage | ✅ covered by common tests and iPad CI run `36233232274` |
| `brushes` | catalog, stroke interpolation, brush/pack codecs | ✅ covered by common tests and the same iPad run |
| `renderer` | tiled raster operations, selections, transforms, liquify, exports | ✅ covered by common tests and the same iPad run |
| `ui` | shared editor state, tools, panels, gallery and platform-neutral policy | ✅ covered by common tests and the same iPad run |
| `androidApp` | Android lifecycle and file/library adapter | 🟡 present in Canvas-Mac but separate Android repo is newer and must be reconciled |
| `windowsApp` | JVM/Compose Windows host and native adapters | 🟡 present; advanced branches have separate validated work |
| `iosApp` | UIKit/Swift iPad host, XcodeGen project, native bridges | ✅ validated at iPad HEAD by run `36233232274` |

### Android repository

The Android repository contains `core`, `brushes`, `renderer`, `ui`, and
`androidApp`, with no Windows or Apple host. Its tests cover the same shared
contracts plus Android local-library behavior. This is a parallel KMP copy and
therefore a divergence risk, not a desired permanent architecture.

### Legacy NeoWorks repository

This is a .NET solution (`NeoWorks.sln`) with multiple suite applications.
Relevant candidates are `src/NeoWorks.Design`, `src/NeoWorks.Photo`,
`src/NeoWorks.Photo.Native`, and `src/NeoWorks.Photo.Stability`. They do not
share NeoCanvas's Kotlin APIs and must be treated as product-behavior reference
material unless an independently reusable asset or algorithm is proven.

## Workflow inventory

| Repository | Workflow | State | Latest relevant evidence |
|---|---|---|---|
| Canvas-Mac | `.github/workflows/ipad-build.yml` | Active | `36233232274`, success at `bbc11c370c0872effee19d8e676d86117b064673` |
| Canvas-Mac | `.github/workflows/windows-build.yml` | Active | `36193129797`, success at Windows foundation `65120e86cf8a9f97f6873e597781938f30d31394` |
| Canvas_android | `.github/workflows/android-build.yml` | Active | `36038830556`, success at `74176a28397205b33907ca9e8cb312bdd418875a` |
| neoworks | `.github/workflows/basic-installer.yml` | Active | Not NeoCanvas-specific; 🟡 not revalidated here |
| neoworks | `.github/workflows/build.yml` | Active | Not NeoCanvas-specific; 🟡 not revalidated here |
| neoworks | `.github/workflows/publish-preview-r2.yml` | Active | Suite publishing only; N/A to canonical app build |
| neoworks | `.github/workflows/publish-r2.yml` | Active | Suite publishing only; N/A to canonical app build |
| neoworks | `.github/workflows/release-candidate.yml` | Active | Not NeoCanvas-specific; 🟡 not revalidated here |

Local baseline note: the managed Windows shell initially had no Java. Microsoft
OpenJDK 17.0.10 was installed for the current user, matching CI's Java 17
contract, but Gradle cannot establish its required local loopback channel in
this managed execution environment. No application test failure was observed;
GitHub Actions remains authoritative for Gradle builds until that host limit is
removed.

## Build scripts and targets

- Root: Gradle wrapper `8.14.4`, Kotlin Multiplatform/Compose build files, and
  `settings.gradle.kts` module registration.
- iPad: `iosApp/project.yml`, Swift/UIKit host sources, XcodeGen generation,
  simulator and ARM64 device lanes in `ipad-build.yml`.
- Windows: `windowsApp/build.gradle.kts`, `scripts/test-windows.ps1`, Compose
  desktop distribution tasks, packaging and release contracts on Windows
  branches.
- Android: `androidApp/build.gradle.kts`, Android manifest and test sources;
  the separate Android repository validates an Android artifact in its workflow.
- macOS: no distinct macOS target, AppKit lifecycle, bundle target, or macOS
  workflow was found in the audited roots. 🔴 Native macOS validation is absent.
- Legacy Windows suite: `NeoWorks.sln`, `build.ps1`, installer and publishing
  scripts. These are evidence for .NET products, not a drop-in NeoCanvas host.

## Platform hosts

| Platform | Host evidence | Current status |
|---|---|---|
| iPad | `iosApp`, UIKit/Swift bridges, XcodeGen, simulator/device/smoke/visual workflow | ✅ source HEAD run `36233232274` succeeded |
| macOS | Common Apple ancestry only; no explicit AppKit/macOS target or workflow | 🔴 host and native compile proof missing |
| Windows | `windowsApp` plus five diverged parity branches; foundation CI succeeded | 🟡 strong implementation exists but is not reconciled with current iPad shared code |
| Android | dedicated Android repository and successful workflow | 🟡 implementation is validated in its source repo but not yet integrated into canonical history |

## Duplicated code and unique features

### Duplicated or divergent areas

- `Canvas-Mac` and `Canvas_android` both contain `core`, `brushes`, `renderer`,
  `ui`, `androidApp`, Gradle wrapper/configuration, and overlapping tests.
- Windows branches repeat shared trees at different historical points while
  carrying branch-only host work. A whole-tree winner would discard newer iPad
  behavior or Windows-only capabilities.
- Legacy NeoWorks Design/Photo implements conceptually overlapping creative
  tools in C#/WPF/C++, but it is not source-compatible with NeoCanvas KMP.

### Demonstrated unique candidates

- Windows foundation: native file association/open-by-path, secondary-button
  QuickMenu, host-specific updates, pen eraser classification, keyboard/file
  shortcuts, native document picker, local font import, packaged self-test,
  hardware acceptance probe, EXE packaging, and PDF export work.
- Windows export/release branches: font-safe PDF/export behavior, current Text
  Studio styling, installer version alignment, and a release-contract gate.
- Android: Android lifecycle, manifest/packaging, share/file integration and
  local library behavior validated independently at `74176a...`.
- Legacy NeoWorks Photo/Design: advanced text/vector/shape/transform UI,
  professional layers and masks, color precision, smart filters, import/place,
  native photo routines, stability tooling, and installer practices. These are
  🟡 comparison candidates only; file names do not prove portable behavior.

## Stale experiments and evidence quality

- No branch is classified obsolete from age or naming alone.
- `main` and `apple-platform` are strict ancestors of iPad and remain valuable
  lineage even though they contain no unique commits.
- The five Windows branches are divergent. Their unique commits and successful
  Windows foundation run make them active migration evidence, not disposable
  experiments.
- The legacy suite's many polish/feature branches are preserved in the source
  repository. Only NeoCanvas-relevant creative behavior should be assessed;
  Calendar, Writer, Sheets, PDF-suite and launcher branches remain outside this
  product migration unless a shared dependency is demonstrated.
- All source repositories were unarchived and accessible during collection.
- No preservation tags existed at initial collection time. Task 2 refreshed
  every target without finding drift, then created the annotated tags below.

## Preservation references

| Repository | Reference | Target SHA | Created UTC | Verification |
|---|---|---|---|---|
| `neoworkssuite/Canvas-Mac` | `archive/pre-consolidation-ipad` | `bbc11c370c0872effee19d8e676d86117b064673` | `2026-09-26T09:56:19Z` | `matching` |
| `neoworkssuite/Canvas-Mac` | `archive/pre-consolidation-macos` | `2c5d93a07446b47c7bcf87c147d76ff6dfec125d` | `2026-09-26T09:56:19Z` | `matching` |
| `neoworkssuite/Canvas-Mac` | `archive/pre-consolidation-windows` | `65120e86cf8a9f97f6873e597781938f30d31394` | `2026-09-26T09:56:19Z` | `matching` |
| `neoworkssuite/Canvas_android` | `archive/pre-consolidation-android` | `74176a28397205b33907ca9e8cb312bdd418875a` | `2026-09-26T09:56:19Z` | `matching` |
| `christianrobertson36/neoworks` | `archive/pre-consolidation-legacy-windows` | `803fc4ba359042a0843761286982b80e839395ae` | `2026-09-26T09:56:19Z` | `matching` |

Verification is against each annotated tag's peeled commit, not merely its tag
object SHA. Existing branches were not moved or deleted.

## Credential boundary

GitHub CLI has two stored credentials with `repo` and `workflow` scopes. Use the
active `neoworkssuite` identity for Canvas-Mac, Canvas_android, and canonical
writes. Temporarily switch to `christianrobertson36` only for private legacy
reads or preservation writes, and switch back in a `finally` path. The audit
collection ended with `neoworkssuite` active. Tokens must never appear in
documents, commits, or chat output.

## Audit conclusion

The safest foundation is the full `Canvas-Mac` lineage with the refreshed iPad
HEAD as `ipad-dev`. Android should enter through an ancestry-preserving import
branch. Windows needs selective integration beginning with the validated
foundation plus export/release-contract comparison. macOS requires a real host
rather than relabelling the existing iPad target. The private NeoWorks suite is
valuable as feature reference and preserved history, but wholesale merging it
into the NeoCanvas runtime would add unrelated products and is not justified by
the evidence.

Task 1 audit tooling and this document were committed as
`abfc2b74970eb210f5462deb392fd418aa6b776f` on the isolated
`codex/repository-consolidation` branch. Workflow run `36233934850` was
dispatched against that exact commit: fast validation completed successfully;
full iPad validation was still running when Task 1's local contract closed and
must be reconciled before the final report.
