import tempfile
import unittest
from pathlib import Path

from scripts.verify_ipad_integrity import verify_ipad_integrity


class IPadIntegrityContractTest(unittest.TestCase):
    def test_incomplete_fixture_fails_each_boundary(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = verify_ipad_integrity(Path(directory))
            self.assertTrue(any("module" in error.lower() for error in errors))
            self.assertTrue(any("workflow" in error.lower() for error in errors))
            self.assertTrue(any("ipad host" in error.lower() for error in errors))

    def test_runtime_sample_asset_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("core", "brushes", "renderer", "ui"):
                (root / name).mkdir()
            host = root / "iosApp/NeoCanvas"
            host.mkdir(parents=True)
            (host / "SampleArtwork.png").write_bytes(b"x")
            workflow = root / ".github/workflows"
            workflow.mkdir(parents=True)
            (workflow / "ipad-build.yml").write_text(
                "ipad-dev simulator physical iPad smoke Extended visual Upload iPad"
            )
            errors = verify_ipad_integrity(root)
            self.assertTrue(any("sample" in error.lower() for error in errors))


if __name__ == "__main__":
    unittest.main()
