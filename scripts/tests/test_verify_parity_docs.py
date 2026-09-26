import tempfile
import unittest
from pathlib import Path

from scripts.verify_parity_docs import verify_parity_docs


class ParityDocumentContractTest(unittest.TestCase):
    def test_missing_documents_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = verify_parity_docs(Path(directory))
            self.assertTrue(errors)

    def test_invalid_status_and_unlinked_green_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs").mkdir()
            (root / "docs/PLATFORM-PARITY.md").write_text(
                "| Feature | iPad | macOS | Windows | Android |\n"
                "|---|---|---|---|---|\n"
                "| Drawing | ✅ | maybe | 🔴 | N/A |\n"
            , encoding="utf-8")
            (root / "docs/PARITY-READY.md").write_text("PARITY-READY", encoding="utf-8")
            errors = verify_parity_docs(root)
            self.assertTrue(any("status" in error.lower() for error in errors))
            self.assertTrue(any("evidence" in error.lower() for error in errors))


if __name__ == "__main__":
    unittest.main()
