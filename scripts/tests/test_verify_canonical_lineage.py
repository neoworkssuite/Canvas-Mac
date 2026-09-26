from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).parents[1] / "verify-canonical-lineage.py"
SPEC = importlib.util.spec_from_file_location("verify_canonical_lineage", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


IPAD_SHA = "b" * 40
MAIN_SHA = "a" * 40
WINDOWS_SHA = "c" * 40

AUDIT = f"""| `neoworkssuite/Canvas-Mac` | `ipad-gestures-phase1` | `{IPAD_SHA}` |
| `neoworkssuite/Canvas-Mac` | `main` | `{MAIN_SHA}` |
| `neoworkssuite/Canvas-Mac` | `windows-parity-foundation` | `{WINDOWS_SHA}` |
"""


def complete_refs() -> dict[str, str]:
    return {
        "refs/heads/main": MAIN_SHA,
        "refs/heads/ipad-dev": IPAD_SHA,
        "refs/heads/ipad-gestures-phase1": IPAD_SHA,
        "refs/heads/windows-parity-foundation": WINDOWS_SHA,
        "refs/tags/archive/pre-consolidation-ipad": IPAD_SHA,
        "refs/tags/archive/pre-consolidation-macos": "d" * 40,
        "refs/tags/archive/pre-consolidation-windows": WINDOWS_SHA,
    }


class VerifyCanonicalLineageTest(unittest.TestCase):
    def test_complete_lineage_has_no_errors(self) -> None:
        self.assertEqual([], MODULE.verify_lineage(complete_refs(), AUDIT))

    def test_ipad_dev_must_contain_recorded_ipad_sha(self) -> None:
        refs = complete_refs()
        refs["refs/heads/ipad-dev"] = "e" * 40
        errors = MODULE.verify_lineage(refs, AUDIT)
        self.assertTrue(any("ipad-dev" in error for error in errors), errors)

    def test_main_must_equal_recorded_baseline(self) -> None:
        refs = complete_refs()
        refs["refs/heads/main"] = "e" * 40
        errors = MODULE.verify_lineage(refs, AUDIT)
        self.assertTrue(any("main" in error for error in errors), errors)

    def test_each_required_branch_and_tag_must_exist(self) -> None:
        for name in complete_refs():
            with self.subTest(name=name):
                refs = complete_refs()
                del refs[name]
                errors = MODULE.verify_lineage(refs, AUDIT)
                self.assertTrue(any(name in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
