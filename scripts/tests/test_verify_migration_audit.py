from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).parents[1] / "verify-migration-audit.py"
SPEC = importlib.util.spec_from_file_location("verify_migration_audit", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


REQUIRED_BRANCHES = (
    "main",
    "apple-platform",
    "ipad-gestures-phase1",
    "windows-export-parity",
    "windows-host-parity",
    "windows-parity-foundation",
    "windows-parity-phase1",
    "windows-release-contract",
)


def complete_audit() -> str:
    branches = "\n".join(
        f"| `neoworkssuite/Canvas-Mac` | `{branch}` | "
        f"`{'a' * 39}{index:x}` |"
        for index, branch in enumerate(REQUIRED_BRANCHES)
    )
    return f"""# NeoCanvas Migration Audit

Collected: 2026-09-26T12:00:00+01:00

## Repository visibility and default branches

- `neoworkssuite/Canvas-Mac`
- `neoworkssuite/Canvas_android`
- `christianrobertson36/neoworks`

## Branch and tag HEADs

| Repository | Ref | SHA |
|---|---|---|
{branches}
| `neoworkssuite/Canvas_android` | `main` | `{'b' * 40}` |
| `christianrobertson36/neoworks` | `main` | `{'c' * 40}` |

## Ancestry relationships

Evidence recorded from merge-base and branch containment checks.

## Module inventory

`core`, `renderer`, `brushes`, and `ui` are shared modules.

## Workflow inventory

GitHub Actions workflow paths and triggers are recorded here.

## Build scripts and targets

Gradle, Xcode, Windows, and Android commands are recorded here.

## Platform hosts

iPad, macOS, Windows, and Android host evidence is recorded here.

## Duplicated code and unique features

Duplicated implementations and repository-specific capabilities are recorded here.

## Stale experiments and evidence quality

Unbuilt findings remain 🟡 until validated.

## Credential boundary

Use the active `neoworkssuite` identity for canonical writes; temporarily switch
to `christianrobertson36` for the private legacy repository and switch back.
"""


class VerifyMigrationAuditTest(unittest.TestCase):
    def verify(self, text: str) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "audit.md"
            path.write_text(text, encoding="utf-8")
            return MODULE.verify_audit(path)

    def test_complete_audit_has_no_errors(self) -> None:
        self.assertEqual([], self.verify(complete_audit()))

    def test_reports_each_missing_repository(self) -> None:
        for repository in (
            "neoworkssuite/Canvas-Mac",
            "neoworkssuite/Canvas_android",
            "christianrobertson36/neoworks",
        ):
            with self.subTest(repository=repository):
                errors = self.verify(complete_audit().replace(repository, "missing/repo"))
                self.assertTrue(any(repository in error for error in errors), errors)

    def test_reports_each_missing_required_canvas_mac_branch(self) -> None:
        for branch in REQUIRED_BRANCHES:
            with self.subTest(branch=branch):
                errors = self.verify(complete_audit().replace(f"`{branch}`", "`missing-branch`"))
                self.assertTrue(any(branch in error for error in errors), errors)

    def test_rejects_document_without_a_full_sha(self) -> None:
        audit = complete_audit()
        for character in ("a", "b", "c"):
            audit = audit.replace(character * 40, character * 12)
        errors = self.verify(audit)
        self.assertTrue(any("40-character SHA" in error for error in errors), errors)

    def test_reports_missing_module_and_workflow_sections(self) -> None:
        audit = complete_audit().replace("## Module inventory", "## Removed modules")
        audit = audit.replace("## Workflow inventory", "## Removed workflows")
        errors = self.verify(audit)
        self.assertTrue(any("Module inventory" in error for error in errors), errors)
        self.assertTrue(any("Workflow inventory" in error for error in errors), errors)

    def test_reports_missing_active_identity_note(self) -> None:
        audit = complete_audit().replace("active `neoworkssuite` identity", "unspecified identity")
        errors = self.verify(audit)
        self.assertTrue(any("active identity" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
