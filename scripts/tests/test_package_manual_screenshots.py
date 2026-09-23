import importlib.util
import json
import struct
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "package-manual-screenshots.py"
SPEC = importlib.util.spec_from_file_location("package_manual_screenshots", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


def write_png(path: Path, width: int = 1366, height: int = 1024) -> None:
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + struct.pack(">I", 13)
        + b"IHDR"
        + struct.pack(">II", width, height)
        + b"\x08\x06\x00\x00\x00"
    )


class PackageManualScreenshotsTest(unittest.TestCase):
    def test_build_pack_rejects_a_missing_required_screen(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in MODULE.REQUIRED_SCREENSHOTS[:-1]:
                write_png(directory / name)

            with self.assertRaisesRegex(ValueError, MODULE.REQUIRED_SCREENSHOTS[-1]):
                MODULE.build_pack(directory, "abc123", "iPad Pro 13-inch")

    def test_build_pack_records_real_png_dimensions_and_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in MODULE.REQUIRED_SCREENSHOTS:
                write_png(directory / name)

            manifest = MODULE.build_pack(directory, "abc123", "iPad Pro 13-inch")

            self.assertEqual(manifest["commit"], "abc123")
            self.assertEqual(manifest["device"], "iPad Pro 13-inch")
            self.assertEqual(len(manifest["screenshots"]), len(MODULE.REQUIRED_SCREENSHOTS))
            self.assertEqual(manifest["screenshots"][0]["width"], 1366)
            self.assertEqual(manifest["screenshots"][0]["height"], 1024)
            stored = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(stored, manifest)
            self.assertIn(MODULE.REQUIRED_SCREENSHOTS[0], (directory / "contact-sheet.html").read_text(encoding="utf-8"))

    def test_build_pack_rejects_non_png_content(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name in MODULE.REQUIRED_SCREENSHOTS:
                write_png(directory / name)
            (directory / MODULE.REQUIRED_SCREENSHOTS[0]).write_text("not an image", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "valid PNG"):
                MODULE.build_pack(directory, "abc123", "iPad Pro 13-inch")


if __name__ == "__main__":
    unittest.main()
