# NeoCanvas Repository Consolidation Design

## Purpose

Consolidate the existing NeoCanvas implementations into one permanent, history-preserving repository at `neoworkssuite/NeoCanvas`. This is a migration of working software, not a rewrite. The iPad implementation remains the product reference, while macOS, Windows, and Android become controlled platform implementations built around the same shared Kotlin Multiplatform code.

## Verified Starting State

The audit must refresh these values immediately before each migration action because development can continue concurrently.

| Repository | Default branch | Verified reference | Status |
|---|---|---|---|
| `neoworkssuite/Canvas-Mac` | `main` | `ipad-gestures-phase1` at `7d429d0c675a68a07ba28427c92621aa1997808a` | Accessible; primary source of truth |
| `neoworkssuite/Canvas_android` | `main` | `74176a28397205b33907ca9e8cb312bdd418875a` | Accessible; Android source |
| `christianrobertson36/neoworks` | Unknown | Unknown | Inaccessible with current credentials; GitHub returns 404 |
| `neoworkssuite/NeoCanvas` | N/A | N/A | Does not yet exist |

Verified `Canvas-Mac` branches include `main`, `apple-platform`, `ipad-gestures-phase1`, `windows-export-parity`, `windows-host-parity`, `windows-parity-foundation`, `windows-parity-phase1`, and `windows-release-contract`.

The complete migration audit will enumerate all branches, tags, modules, workflows, build scripts, platform hosts, duplicated code, and feature differences. The inaccessible `christianrobertson36/neoworks` repository is a hard audit gap: no claim of complete Windows consolidation may be made until access is restored or the user explicitly accepts that source as unavailable.

## Governing Constraints

- Do not rewrite or replace NeoCanvas.
- Do not delete, archive, force-push, or destructively rewrite any original repository or useful branch during migration.
- Preserve the full `Canvas-Mac` lineage as the canonical repository foundation.
- Preserve the `ipad-gestures-phase1` history and functionality.
- Prefer existing working implementations over architectural tidiness.
- Keep shared code shared where technically sensible; do not create four independent application copies.
- Do not mark a platform or feature complete from file presence alone. Require build or test evidence.
- Re-read every remote HEAD immediately before creating preservation references or importing history.
- Complete migration work through small, reviewable commits with CI evidence.

## Chosen Migration Strategy

### Canonical history

Create `neoworkssuite/NeoCanvas` from the complete `Canvas-Mac` Git lineage rather than copying the current files into a new empty repository. The canonical repository initially retains the existing KMP layout and commit ancestry. The current accepted cross-platform baseline becomes `main`; the current iPad source-of-truth lineage becomes `ipad-dev` without deleting `ipad-gestures-phase1`.

Other repository histories are imported with traceable parentage. Prefer a history-preserving merge using dedicated migration branches and `--allow-unrelated-histories` where practical. If direct merging would create uncontrolled path collisions, first preserve the external history on an import branch, then relocate only its platform-specific tree with an explicit migration commit. Do not squash the imported history.

### Safety references

Before canonical repository creation or integration, create immutable annotated preservation tags in each source repository when permissions allow. Use repository-qualified names where a single canonical repository will eventually contain several origins, for example:

- `archive/pre-consolidation-ipad`
- `archive/pre-consolidation-macos`
- `archive/pre-consolidation-windows`
- `archive/pre-consolidation-android`
- `archive/pre-consolidation-legacy-windows`

If a tag name already exists, verify its target and never move it silently. Create a timestamped alternative when the existing target is different. Original branches remain available throughout migration.

## Repository Shape

Retain the current KMP modules (`core`, `renderer`, `brushes`, `ui`) as the shared foundation unless the audit proves a move is necessary. Platform hosts live in clearly named platform directories or existing host modules:

```text
NeoCanvas/
  core/
  renderer/
  brushes/
  ui/
  platforms/
    ipad/
    macos/
    windows/
    android/
  docs/
  scripts/
  .github/workflows/
  README.md
  CHANGELOG.md
  VERSION
```

The exact host-directory migration is evidence-driven. Existing build-system expectations take precedence over cosmetic conformity. Shared document models, file formats, renderer logic, brush definitions, colour handling, layers, history, selections, transforms, common UI policy, and shared tests stay in shared modules. UIKit/AppKit, Windows host integration, Android lifecycle, packaging, native file APIs, input bridges, and platform update mechanisms remain platform-specific.

## Branch and Delivery Model

- `main`: accepted cross-platform baseline. It advances only when required platform CI for the integrated change passes or an explicit documented exception exists.
- `ipad-dev`: primary active development branch, created from the verified latest `ipad-gestures-phase1` commit.
- `ipad-gestures-phase1`: retained as historical lineage until the user separately authorises retirement after migration proof.
- Short-lived feature or migration branches: used for significant isolated work.
- Existing Windows branches: retained until their useful commits are inventoried and integrated.

The normal feature flow is:

```text
ipad-dev → iPad implementation and acceptance → PARITY-READY
         → macOS / Windows / Android implementation and validation
         → required cross-platform CI → main
```

## Migration Phases

### 1. Complete audit

Create `docs/MIGRATION-AUDIT.md` with repository visibility, default branches, every branch and tag HEAD, ancestry relationships, module and workflow inventories, build scripts, platform hosts, duplicated code, unique features, stale experiments, and evidence quality. Do not label a branch obsolete merely because it is old.

### 2. Preserve source states

Refresh remote HEADs, create preservation tags, verify each tag resolves to the recorded SHA, and push without moving or deleting source branches. Record every preservation reference in the audit.

### 3. Establish canonical repository

Create `neoworkssuite/NeoCanvas`, push the complete `Canvas-Mac` lineage, preserve its branches and tags, set `main` as default, and create `ipad-dev` from the latest verified iPad HEAD. Confirm the canonical clone contains the expected ancestry before further integration.

### 4. Consolidate Windows

Compare all `windows-*` branches and, once accessible, the historical NeoWorks source. Produce `docs/WINDOWS-MIGRATION.md` recording evidence, unique features, selected baseline, migrated commits, intentionally retained legacy code, validation state, and outstanding parity work. Never remove the old Windows implementation during this operation.

### 5. Separate macOS responsibility

Identify common Apple code versus UIKit- and AppKit-specific code. Create or retain a proper macOS host and CI target without duplicating common UI or renderer modules. Record the result in `docs/MACOS-PARITY.md`.

### 6. Integrate Android

Preserve Android history on an import branch, compare its implementation against shared/current iPad functionality, and migrate its host into the canonical architecture without blindly overwriting either source. Record the result in `docs/ANDROID-PARITY.md`.

### 7. Verify iPad reference integrity

Confirm that Apple Pencil, gestures, Gallery, canvas creation, storage, layers, brushes, undo/redo, imports, exports, typography, and recent release improvements survive consolidation. Existing iPad simulator, physical ARM64, smoke, and visual validation remain authoritative.

### 8. Publish parity and product documentation

Create `docs/PLATFORM-PARITY.md` using only `✅ implemented/tested`, `🟡 implemented but needs validation`, `🔴 missing/broken`, and `N/A`. Add a root README, build instructions, migration-document links, development policy, and versioning explanation.

### 9. Redirect old repositories only after proof

Only after the canonical repository is cloneable and its relevant builds are verified may source-repository READMEs receive a non-destructive migration notice. Do not archive or delete them.

## Versioning

Add one root `VERSION` using SemVer. Determine the initial value from existing release metadata and demonstrated readiness; do not declare `1.0.0` merely because consolidation occurred. Each platform retains a monotonically increasing native build number while displaying the same NeoCanvas product version. Document how product and platform build values are derived in the README or a focused versioning document.

## Continuous Integration

CI is split by cost and responsibility:

- Shared validation: core, document model, renderer, brushes, common UI, format/contract tests.
- iPad: simulator build, ARM64 device build validation, smoke tests, and existing visual/liveness evidence.
- macOS: native target compile and relevant tests.
- Windows: shared tests, Windows host compile, platform tests, and release-contract verification.
- Android: unit tests plus APK/AAB-compatible build validation.

Use branch and path filters so ordinary iPad work receives fast feedback without unnecessarily running every expensive packaging job. Cross-platform integration and `main` promotion require the applicable full matrix.

## Validation and Failure Handling

Every migration phase records its input SHA, output SHA, commands or workflow run, and observed result. A platform remains yellow or red when its native runner, signing prerequisites, legacy source, or packaging toolchain is unavailable. Documentation must name the exact blocker and the precise access or action needed.

Before declaring migration success, verify:

1. `neoworkssuite/NeoCanvas` exists and can be cloned.
2. `main` and `ipad-dev` point to documented ancestry.
3. Current iPad commits and retained branches are present.
4. iPad shared, simulator, device, smoke, and visual jobs pass.
5. macOS, Windows, and Android targets are present and have truthful build states.
6. Shared validation passes.
7. Migration, platform, parity, version, and build documentation exists and matches commands that were actually run.
8. Original repositories remain available.
9. Preservation references resolve to their recorded SHAs.
10. Any inaccessible or failing platform is explicitly reported rather than hidden.

## Audit and Migration Blockers

The current credentials cannot resolve `christianrobertson36/neoworks`. The likely causes are a private repository without collaborator access, a renamed/transferred repository, or an incorrect owner/name. Required resolution is one of:

- grant the authenticated GitHub account read access;
- transfer or mirror the repository into `neoworkssuite` while preserving history; or
- provide the corrected repository URL.

Until resolved, the canonical repository may be established and accessible sources may be audited, but Windows consolidation cannot be called complete.

## Final Handoff

The final report provides the canonical URL, default branch, iPad development branch, current HEAD, per-platform build and CI status, all retained source repositories, preservation references, parity summary, blockers, and the exact next recommended development task. No success statement is made from file movement alone.
