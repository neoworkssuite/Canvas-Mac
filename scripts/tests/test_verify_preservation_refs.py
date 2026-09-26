from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).parents[1] / "verify-preservation-refs.py"
SPEC = importlib.util.spec_from_file_location("verify_preservation_refs", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


AUDIT = """## Preservation references

| Repository | Reference | Target SHA | Created UTC | Verification |
|---|---|---|---|---|
| `neoworkssuite/Canvas-Mac` | `archive/pre-consolidation-ipad` | `1111111111111111111111111111111111111111` | `2026-09-26T10:00:00Z` | `matching` |
| `neoworkssuite/Canvas-Mac` | `archive/pre-consolidation-macos` | `2222222222222222222222222222222222222222` | `2026-09-26T10:00:00Z` | `matching` |
| `neoworkssuite/Canvas-Mac` | `archive/pre-consolidation-windows` | `3333333333333333333333333333333333333333` | `2026-09-26T10:00:00Z` | `matching` |
| `neoworkssuite/Canvas_android` | `archive/pre-consolidation-android` | `4444444444444444444444444444444444444444` | `2026-09-26T10:00:00Z` | `matching` |
| `christianrobertson36/neoworks` | `archive/pre-consolidation-legacy-windows` | `5555555555555555555555555555555555555555` | `2026-09-26T10:00:00Z` | `matching` |
"""


class VerifyPreservationRefsTest(unittest.TestCase):
    def test_parses_complete_preservation_mapping(self) -> None:
        refs = MODULE.expected_preservation_refs(AUDIT)
        self.assertEqual(5, len(refs))
        self.assertEqual("neoworkssuite/Canvas-Mac", refs[0].repository)
        self.assertEqual("archive/pre-consolidation-ipad", refs[0].name)
        self.assertEqual("1" * 40, refs[0].target_sha)

    def test_rejects_duplicate_name_with_different_sha(self) -> None:
        conflicting = AUDIT + (
            "| `neoworkssuite/Canvas-Mac` | `archive/pre-consolidation-ipad` | "
            "`aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` | `2026-09-26T10:01:00Z` | `conflict` |\n"
        )
        with self.assertRaisesRegex(ValueError, "different targets"):
            MODULE.expected_preservation_refs(conflicting)

    def test_existing_conflicting_tag_is_never_a_creation_candidate(self) -> None:
        expected = MODULE.expected_preservation_refs(AUDIT)[0]
        remote = {expected.name: "f" * 40}
        with self.assertRaisesRegex(ValueError, "refusing to move"):
            MODULE.creation_candidates([expected], remote)

    def test_missing_tag_is_a_creation_candidate(self) -> None:
        expected = MODULE.expected_preservation_refs(AUDIT)[0]
        self.assertEqual([expected], MODULE.creation_candidates([expected], {}))

    def test_matching_tag_requires_no_creation(self) -> None:
        expected = MODULE.expected_preservation_refs(AUDIT)[0]
        remote = {expected.name: expected.target_sha}
        self.assertEqual([], MODULE.creation_candidates([expected], remote))


if __name__ == "__main__":
    unittest.main()
