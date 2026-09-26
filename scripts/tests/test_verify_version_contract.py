from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).parents[1] / "verify-version-contract.py"
SPEC = importlib.util.spec_from_file_location("verify_version_contract", SCRIPT_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class VerifyVersionContractTest(unittest.TestCase):
    def fixture(self, version: str = "0.1.0") -> Path:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        (root / "docs").mkdir()
        (root / "VERSION").write_text(version + "\n", encoding="utf-8")
        (root / "docs" / "VERSIONING.md").write_text(
            f"Product version: `{version}`\n\nApple build: `1`\nAndroid build: `1`\nWindows build: `1`\n",
            encoding="utf-8",
        )
        (root / "README.md").write_text(
            "iPad macOS Windows Android\n\nPARITY-READY\n\n"
            "docs/MIGRATION-AUDIT.md docs/PLATFORM-PARITY.md docs/VERSIONING.md\n",
            encoding="utf-8",
        )
        return root

    def test_complete_contract_has_no_errors(self) -> None:
        self.assertEqual([], MODULE.verify_version_contract(self.fixture()))

    def test_version_must_be_semver(self) -> None:
        errors = MODULE.verify_version_contract(self.fixture("version-one"))
        self.assertTrue(any("SemVer" in error for error in errors), errors)

    def test_documented_product_version_must_match(self) -> None:
        root = self.fixture()
        path = root / "docs" / "VERSIONING.md"
        path.write_text(path.read_text().replace("`0.1.0`", "`0.2.0`"), encoding="utf-8")
        errors = MODULE.verify_version_contract(root)
        self.assertTrue(any("documented product version" in error for error in errors), errors)

    def test_native_build_values_are_numeric_and_positive(self) -> None:
        root = self.fixture()
        path = root / "docs" / "VERSIONING.md"
        path.write_text(path.read_text().replace("Apple build: `1`", "Apple build: `zero`"), encoding="utf-8")
        errors = MODULE.verify_version_contract(root)
        self.assertTrue(any("Apple build" in error for error in errors), errors)

    def test_readme_links_platforms_and_policy(self) -> None:
        root = self.fixture()
        (root / "README.md").write_text("NeoCanvas\n", encoding="utf-8")
        errors = MODULE.verify_version_contract(root)
        self.assertTrue(any("README" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
