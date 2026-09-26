# NeoCanvas Repository Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the history-preserving canonical `neoworkssuite/NeoCanvas` repository with iPad as the reference implementation and truthful macOS, Windows, and Android platform integration and CI status.

**Architecture:** Use the complete `Canvas-Mac` Git lineage as the canonical foundation, create `ipad-dev` from the refreshed iPad source-of-truth HEAD, and preserve every source state before importing other histories. Keep the existing KMP `core`, `brushes`, `renderer`, and `ui` modules shared; integrate platform hosts incrementally and document build evidence rather than restructuring for appearance.

**Tech Stack:** Git/GitHub CLI, Kotlin Multiplatform, Compose Multiplatform, Gradle, Xcode/XcodeGen, Windows JVM host, Android Gradle Plugin, GitHub Actions, Python release-contract checks.

**Spec:** `docs/superpowers/specs/2026-09-26-neocanvas-repository-consolidation-design.md`

## Global Constraints

- Never delete, archive, force-push, or rewrite an original repository, branch, tag, or useful commit.
- Refresh every remote HEAD immediately before creating a preservation reference or migration branch.
- Use the `neoworkssuite` GitHub identity for `Canvas-Mac`, `Canvas_android`, and canonical-repository writes.
- Temporarily select the stored `christianrobertson36` identity only for private `neoworks` reads or preservation writes, then switch back immediately.
- Never print, persist, or commit GitHub tokens.
- Preserve the complete `Canvas-Mac` ancestry and the `ipad-gestures-phase1` branch.
- Keep shared KMP modules shared unless the audit supplies a concrete platform constraint.
- Do not mark a feature or platform green without a corresponding passing test, build, or workflow result.
- Do not update legacy READMEs until the canonical repository is cloneable and the reference iPad build is green.
- Work through small, reviewable commits and record source/output SHAs in migration documents.

## Review Focus

- A source branch moving during audit must be detected by the pre-write refresh; preservation references must target the newly recorded SHA rather than a stale handoff SHA.
- A preservation tag that already exists at another commit must never be moved; create a timestamped alternative and document both targets.
- Importing unrelated Android or legacy Windows history must retain reachable ancestry and must not overwrite shared KMP files silently.
- Platform parity must remain yellow/red when native validation is unavailable or failing, even when a host directory exists.
- Switching GitHub identities must always finish with `neoworkssuite` active, including when a legacy audit command fails.

These conditions are pinned respectively by Task 2 Steps 4–6, Task 2 Steps 1–6, Task 3 Steps 1–7 plus Tasks 5 and 7 import verification, Tasks 5–9 evidence contracts, and Task 1 Steps 4–6 plus Task 10 Step 3.

---

### Task 1: Complete the evidence-backed migration audit

**Files:**
- Create: `docs/MIGRATION-AUDIT.md`
- Create: `scripts/verify-migration-audit.py`
- Create: `scripts/tests/test_verify_migration_audit.py`

**Interfaces:**
- Produces: `docs/MIGRATION-AUDIT.md` containing repository metadata, branch/tag tables, module/workflow inventories, platform host evidence, ancestry notes, and unique-feature findings.
- Produces: `verify_audit(path: Path) -> list[str]`, returning validation errors for missing required repositories, branches, SHAs, or audit sections.
- Consumes: GitHub API output for `neoworkssuite/Canvas-Mac`, `neoworkssuite/Canvas_android`, and private `christianrobertson36/neoworks`.

- [ ] **Step 1: Write the failing audit-contract tests**

Add tests asserting that an audit missing any required repository, the eight named `Canvas-Mac` branches, a 40-character SHA, module/workflow sections, or the active-identity note returns an error; a complete fixture returns no errors.

- [ ] **Step 2: Run the audit tests and verify RED**

Run: `python -m unittest scripts.tests.test_verify_migration_audit -v`

Expected: FAIL because `scripts/verify-migration-audit.py` and `verify_audit` do not exist.

- [ ] **Step 3: Implement the audit validator**

Implement `verify_audit(path: Path) -> list[str]` and a CLI that exits non-zero with concise diagnostics. Validate structure and evidence fields; do not encode branch conclusions that belong in the generated audit.

- [ ] **Step 4: Refresh all accessible repository evidence**

Run `gh repo view`, branch/tag APIs, workflow APIs, recursive tree queries, and relevant commit comparisons for all three source repositories. Use `try/finally` around the temporary `christianrobertson36` account switch and verify `neoworkssuite` is active afterward.

- [ ] **Step 5: Write `docs/MIGRATION-AUDIT.md`**

Record exact collection time, default branches, every branch/tag HEAD, shared/platform modules, workflows, scripts, package/build targets, Windows branch relationships, Android structure, macOS/iPad hosts, duplicates, unique features, likely dead experiments, and evidence level. Label unbuilt features `🟡`, not `✅`.

- [ ] **Step 6: Validate the audit and current working tree**

Run:

```powershell
python -m unittest scripts.tests.test_verify_migration_audit -v
python scripts/verify-migration-audit.py docs/MIGRATION-AUDIT.md
git diff --check
```

Expected: all tests pass, validator exits 0, and no whitespace errors are reported.

- [ ] **Step 7: Commit and push Task 1**

```powershell
git add docs/MIGRATION-AUDIT.md scripts/verify-migration-audit.py scripts/tests/test_verify_migration_audit.py
git commit -m "Audit NeoCanvas repositories before consolidation"
git push origin ipad-gestures-phase1
```

Record the commit and authoritative CI result in the audit.

### Task 2: Create and verify source preservation references

**Files:**
- Modify: `docs/MIGRATION-AUDIT.md`
- Create: `scripts/verify-preservation-refs.py`
- Create: `scripts/tests/test_verify_preservation_refs.py`

**Interfaces:**
- Consumes: repository/branch SHAs recorded by Task 1.
- Produces: annotated preservation tags and a documented mapping of repository, reference, SHA, timestamp, and verification result.
- Produces: `expected_preservation_refs(audit_text: str) -> list[PreservationRef]` and CLI verification against GitHub.

- [ ] **Step 1: Write failing preservation-reference tests**

Test parsing a complete audit mapping, rejecting duplicate names with different SHAs, and refusing to propose moving an existing tag.

- [ ] **Step 2: Run tests and verify RED**

Run: `python -m unittest scripts.tests.test_verify_preservation_refs -v`

Expected: FAIL because the verifier does not exist.

- [ ] **Step 3: Implement non-mutating preservation verification**

Implement parsing and remote comparison. The script reports `missing`, `matching`, or `conflict`; it never creates, moves, or deletes a tag.

- [ ] **Step 4: Refresh source HEADs and reconcile audit drift**

Re-run the Task 1 branch queries. If any source moved, update `MIGRATION-AUDIT.md`, commit that evidence update, and use the refreshed SHAs.

- [ ] **Step 5: Create annotated source tags**

Create `archive/pre-consolidation-ipad`, `archive/pre-consolidation-macos`, the most-advanced-Windows reference determined by audit, `archive/pre-consolidation-android`, and `archive/pre-consolidation-legacy-windows`. If a name conflicts, use `archive/pre-consolidation-<platform>-YYYYMMDD-HHMMSS` without moving the existing tag.

- [ ] **Step 6: Verify every pushed reference**

Run `python scripts/verify-preservation-refs.py docs/MIGRATION-AUDIT.md` under the appropriate stored identity for each source. Expected: every recorded reference resolves to its recorded commit, and the active identity returns to `neoworkssuite`.

- [ ] **Step 7: Commit Task 2 documentation**

Commit the verifier, tests, and audit mapping as `Preserve NeoCanvas pre-consolidation sources` and push it to `ipad-gestures-phase1`.

### Task 3: Establish the canonical repository and branch lineage

**Files:**
- Modify: `docs/MIGRATION-AUDIT.md`
- Create: `scripts/verify-canonical-lineage.py`
- Create: `scripts/tests/test_verify_canonical_lineage.py`

**Interfaces:**
- Consumes: verified preservation references from Task 2.
- Produces: `https://github.com/neoworkssuite/NeoCanvas` with complete `Canvas-Mac` ancestry.
- Produces: `main`, `ipad-dev`, retained `ipad-gestures-phase1`, relevant Windows branches, and source tags.

- [ ] **Step 1: Write failing lineage-verification tests**

Test that verification fails when canonical `ipad-dev` does not contain the recorded iPad SHA, when `main` differs from the recorded baseline, or when a required retained branch/tag is absent.

- [ ] **Step 2: Run tests and verify RED**

Run: `python -m unittest scripts.tests.test_verify_canonical_lineage -v`

Expected: FAIL because the lineage verifier does not exist.

- [ ] **Step 3: Implement read-only canonical lineage verification**

Implement a CLI that queries exact remote refs and uses Git ancestry checks in a temporary mirror clone. It must not create or update refs.

- [ ] **Step 4: Re-check canonical repository absence or state**

If `neoworkssuite/NeoCanvas` now exists, stop and audit it rather than overwriting it. If absent, create it under `neoworkssuite` without initializing replacement files.

- [ ] **Step 5: Push the complete `Canvas-Mac` lineage**

Use a mirror or explicit ref push from a fresh source mirror. Do not force-update any existing canonical ref. Verify object ancestry and source/canonical SHA equality.

- [ ] **Step 6: Create `ipad-dev` safely**

Refresh `ipad-gestures-phase1`, create `ipad-dev` at that exact SHA, push it, retain the old branch, and configure canonical `main` as the default branch.

- [ ] **Step 7: Run lineage verification**

Run `python scripts/verify-canonical-lineage.py docs/MIGRATION-AUDIT.md`. Expected: all required refs exist and every recorded source commit is reachable.

- [ ] **Step 8: Commit and push Task 3 tooling/evidence**

Commit as `Create canonical NeoCanvas repository structure`, then push the commit to canonical `ipad-dev` and the retained source branch.

### Task 4: Add unified product documentation and version truth

**Files:**
- Modify: `README.md`
- Create: `VERSION`
- Create: `CHANGELOG.md`
- Create: `docs/VERSIONING.md`
- Create: `scripts/verify-version-contract.py`
- Create: `scripts/tests/test_verify_version_contract.py`

**Interfaces:**
- Produces: one SemVer product version and documented per-platform build-number mapping.
- Produces: root build/navigation documentation linking every migration and parity document.

- [ ] **Step 1: Write failing version-contract tests**

Assert valid SemVer syntax, agreement between `VERSION` and documented product version, monotonically numeric native build values where present, and required README platform/development-policy links.

- [ ] **Step 2: Run tests and verify RED**

Run: `python -m unittest scripts.tests.test_verify_version_contract -v`

Expected: FAIL because the unified files and verifier do not exist.

- [ ] **Step 3: Determine the starting product version from evidence**

Inspect existing Apple, Windows, and Android version declarations. Choose the lowest truthful consolidated product version and explain why; do not default to `1.0.0` without release evidence.

- [ ] **Step 4: Implement documentation and version contract**

Write the iPad-first/PARITY-READY policy, supported platforms, exact build commands proven by the audit, document links, and platform build-number rules.

- [ ] **Step 5: Verify and commit**

Run the version tests, release contract, and `git diff --check`. Commit as `Add unified NeoCanvas product versioning` and push to canonical `ipad-dev`.

### Task 5: Consolidate the Windows implementation

**Files:**
- Create: `docs/WINDOWS-MIGRATION.md`
- Modify/Create: `windowsApp/**` only as selected by the audit
- Modify: `settings.gradle.kts`
- Modify: `scripts/test-windows.ps1`
- Modify/Create: `.github/workflows/windows-build.yml`
- Test: platform and shared tests identified by the selected Windows baseline

**Interfaces:**
- Consumes: audited `windows-*` branches and private `christianrobertson36/neoworks` history.
- Produces: one canonical Windows host using shared `core`, `renderer`, `brushes`, and `ui` contracts wherever compatible.
- Produces: truthful Windows build/parity status and retained import branches for source ancestry.

- [ ] **Step 1: Create isolated import branches for every Windows source**

Import branch histories without modifying `ipad-dev`. Verify each source HEAD is reachable and record it in `WINDOWS-MIGRATION.md`.

- [ ] **Step 2: Write failing tests for each chosen unique capability**

From the audit, add focused tests for useful behavior missing from the selected baseline before migrating that behavior. Include release-contract assertions for host packaging, filesystem integration, stylus/input bridge, and updater only when the source actually supplies them.

- [ ] **Step 3: Verify RED on the selected baseline**

Run the smallest relevant shared or Windows test command and record the expected missing behavior.

- [ ] **Step 4: Integrate the selected Windows baseline**

Prefer the most advanced working `Canvas-Mac` Windows parity lineage. Bring across only legacy features proven unique and useful. Resolve shared-code conflicts in favor of current iPad/shared behavior unless the legacy implementation fixes a documented cross-platform defect.

- [ ] **Step 5: Run Windows and shared validation**

Run `scripts/test-windows.ps1`, the shared Gradle suites, and Windows release-contract verification. Record unavailable native packaging as yellow with the exact blocker.

- [ ] **Step 6: Complete Windows migration documentation**

Document every examined source, baseline decision, migrated feature/commit, intentionally retained obsolete code, validation evidence, and outstanding parity item.

- [ ] **Step 7: Commit and push Task 5**

Commit as `Consolidate Windows host and parity work`, push to a canonical migration branch, obtain CI, then merge into `ipad-dev` without squashing away imported history.

### Task 6: Separate and validate the macOS platform host

**Files:**
- Create: `docs/MACOS-PARITY.md`
- Create/Modify: `platforms/macos/**` or the audited existing macOS host path
- Modify: `ui/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Create/Modify: `.github/workflows/macos-build.yml`
- Test: macOS host and shared UI tests

**Interfaces:**
- Consumes: common Apple/KMP code and audited `apple-platform` ancestry.
- Produces: an AppKit/macOS lifecycle host with shared canvas/UI modules and explicit macOS CI.

- [ ] **Step 1: Preserve/import the audited macOS lineage**

Verify `apple-platform` ancestry and create a canonical migration branch without deleting the source branch.

- [ ] **Step 2: Write failing macOS target/contract checks**

Pin required target presence, bundle metadata, native file APIs, menus, and shared-module linkage. Tests must distinguish presence from successful compilation.

- [ ] **Step 3: Verify RED**

Run the focused macOS compile/contract command on a macOS runner and confirm the intended missing or failing boundary.

- [ ] **Step 4: Separate UIKit and AppKit responsibilities minimally**

Move only host-specific lifecycle, menu, file, and input code. Keep common Apple and Compose behavior shared.

- [ ] **Step 5: Build and document macOS status**

Run shared tests and the macOS native compile. Populate `MACOS-PARITY.md` with exact workflow evidence and unresolved items.

- [ ] **Step 6: Commit and push Task 6**

Commit as `Separate macOS platform host`, validate CI, and integrate into canonical `ipad-dev`.

### Task 7: Integrate the Android repository history and host

**Files:**
- Create: `docs/ANDROID-PARITY.md`
- Create/Modify: `platforms/android/**` or audited `androidApp/**`
- Modify: `settings.gradle.kts`
- Modify: Android Gradle files selected by audit
- Create/Modify: `.github/workflows/android-build.yml`
- Test: Android unit/build contract tests

**Interfaces:**
- Consumes: `neoworkssuite/Canvas_android` `main` and canonical shared KMP modules.
- Produces: reachable Android source ancestry and a canonical Android host that depends on shared modules rather than copied implementations.

- [ ] **Step 1: Import Android history on an isolated canonical branch**

Refresh Android `main`, import its complete history, and verify the recorded source SHA remains reachable.

- [ ] **Step 2: Write failing parity/host tests**

Pin Android lifecycle, file/share integration, touch/stylus boundary, shared document compatibility, and APK/AAB build configuration based on actual audited behavior.

- [ ] **Step 3: Verify RED before integration**

Run focused Android unit/build checks and confirm the missing canonical integration.

- [ ] **Step 4: Integrate without overwriting shared iPad/KMP behavior**

Retain useful Android host code, adapt it to canonical shared contracts, and keep platform-specific lifecycle and packaging local to Android.

- [ ] **Step 5: Build and document Android status**

Run Android tests and build checks. Populate `ANDROID-PARITY.md` with evidence, unique retained work, gaps, and blockers.

- [ ] **Step 6: Commit and push Task 7**

Commit as `Integrate Android platform target`, validate CI, and integrate into canonical `ipad-dev` without squashing source history.

### Task 8: Verify iPad reference integrity after consolidation

**Files:**
- Modify: `.github/workflows/ipad-build.yml`
- Modify: `scripts/verify-release-contract.py`
- Modify: `docs/MIGRATION-AUDIT.md`
- Test: existing shared, iPad smoke, visual, and release-contract suites

**Interfaces:**
- Consumes: canonical shared modules plus current iPad host/history.
- Produces: authoritative evidence that consolidation did not regress the reference implementation.

- [ ] **Step 1: Extend the release contract with canonical-lineage assertions**

Add checks for required iPad host resources, workflow stages, shared modules, and absence of imported runtime user assets from the app bundle.

- [ ] **Step 2: Run the contract against an intentionally incomplete fixture and verify RED**

Use the script's test fixture rather than modifying production files; confirm each new assertion can fail for the intended reason.

- [ ] **Step 3: Update canonical workflow paths only where migration changed them**

Preserve simulator, ARM64 device, smoke, visual, and artifact stages. Avoid unrelated build-system redesign.

- [ ] **Step 4: Run authoritative iPad CI**

Require shared tests, release contract, simulator build, physical ARM64 build, smoke test, and extended visual validation to pass on the same canonical commit.

- [ ] **Step 5: Record evidence and commit**

Record run ID, commit SHA, jobs, and artifacts in the audit. Commit as `Preserve iPad reference implementation`.

### Task 9: Add the cross-platform parity matrix and unified CI policy

**Files:**
- Create: `docs/PLATFORM-PARITY.md`
- Create: `docs/PARITY-READY.md`
- Create: `.github/workflows/shared-validation.yml`
- Modify: platform workflows from Tasks 5–8
- Modify: `README.md`
- Modify: `scripts/verify-release-contract.py`

**Interfaces:**
- Consumes: platform evidence from Tasks 5–8.
- Produces: feature-by-platform matrix using only `✅`, `🟡`, `🔴`, and `N/A` with linked evidence.
- Produces: documented rules for declaring an iPad feature `PARITY-READY` and promoting to `main`.

- [ ] **Step 1: Write failing parity-document contract checks**

Assert required feature rows, four platform columns, allowed statuses, evidence links for green cells, and mandatory development-policy text.

- [ ] **Step 2: Verify RED**

Run the release-contract tests and confirm the missing parity documents/workflow fail.

- [ ] **Step 3: Populate the parity matrix from actual results**

Do not infer green status. Use yellow for implemented but unvalidated and red for missing/broken behavior.

- [ ] **Step 4: Configure cost-aware CI**

Run shared validation broadly; use path/branch filters for platform packaging; require the applicable full matrix for `main` promotion.

- [ ] **Step 5: Verify workflows and documentation**

Run shared tests and trigger each available platform workflow. Validate YAML and release contracts locally where supported.

- [ ] **Step 6: Commit and push Task 9**

Commit as `Add cross-platform parity matrix and CI`, then wait for the applicable canonical workflows.

### Task 10: Add non-destructive source redirects after canonical proof

**Files:**
- Modify: source repository `README.md` files only after proof
- Modify: `docs/MIGRATION-AUDIT.md`

**Interfaces:**
- Consumes: successful canonical clone, lineage checks, and iPad authoritative CI.
- Produces: clear notices directing future development to `https://github.com/neoworkssuite/NeoCanvas` without deleting or archiving sources.

- [ ] **Step 1: Verify redirect preconditions**

Confirm canonical clone succeeds, `ipad-dev` contains the latest iPad reference commit, preservation refs resolve, and authoritative iPad CI is green.

- [ ] **Step 2: Add concise README notices**

Add a prominent notice to `Canvas-Mac`, `Canvas_android`, and `neoworks` stating that the repository is retained for history and future NeoCanvas development lives in the canonical repository. Do not alter other product documentation in the NeoWorks suite.

- [ ] **Step 3: Push each README commit under the correct identity**

Use separate commits and verify the active GitHub identity before and after the private legacy push.

- [ ] **Step 4: Re-run source availability checks**

Confirm all three source repositories and their original branches remain available and unarchived.

### Task 11: Final migration verification and report

**Files:**
- Modify: `docs/MIGRATION-AUDIT.md`
- Modify: `docs/PLATFORM-PARITY.md`
- Create: `docs/MIGRATION-REPORT.md`

**Interfaces:**
- Consumes: all prior task evidence.
- Produces: final canonical URL, branches, HEAD, platform/CI status, retained sources, preservation references, parity summary, blockers, and next recommended task.

- [ ] **Step 1: Run the complete verification suite**

Run audit, preservation, lineage, version, release-contract, shared test, and platform-specific validation commands. Query actual GitHub Actions conclusions for the final HEAD.

- [ ] **Step 2: Perform a clean canonical clone test**

Clone into a temporary directory, verify default branch, fetch `ipad-dev`, resolve required tags/branches, and execute documented build-discovery commands. Remove only the explicitly created temporary directory after verifying its resolved absolute path.

- [ ] **Step 3: Reconcile every documentation claim with evidence**

Downgrade any unsupported green claim. Record exact blockers rather than hiding unavailable platforms.

- [ ] **Step 4: Write `docs/MIGRATION-REPORT.md`**

Include all eleven required final-report fields plus source/canonical SHA mappings and workflow links.

- [ ] **Step 5: Commit final migration evidence**

Commit as `Complete NeoCanvas repository consolidation`, push to canonical `ipad-dev`, and require final CI.

- [ ] **Step 6: Promote only an accepted cross-platform baseline to `main`**

Fast-forward or merge according to repository protection rules only when required CI is green and documented blockers are compatible with the spec. Do not delete `ipad-gestures-phase1`, migration branches, preservation tags, or original repositories.
