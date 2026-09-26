import tempfile
import unittest
from pathlib import Path

from scripts.verify_macos_host import verify_macos_host


class MacOSHostContractTest(unittest.TestCase):
    def test_complete_host_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "settings.gradle.kts").write_text('include(":macosApp")')
            (root / "macosApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas").mkdir(parents=True)
            (root / "macosApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/Main.kt").write_text(
                "fun main() = application { Window { NeoCanvasApp() }; MenuBar {} }"
            )
            (root / "macosApp/build.gradle.kts").write_text(
                'implementation(project(":ui")); targetFormats(TargetFormat.Dmg); bundleID = "com.neoworksuite.neocanvas"'
            )
            (root / ".github/workflows").mkdir(parents=True)
            (root / ".github/workflows/macos-build.yml").write_text(
                "runs-on: macos-latest\nrun: ./gradlew :macosApp:packageDmg\n"
            )
            self.assertEqual([], verify_macos_host(root))

    def test_missing_host_boundaries_are_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = verify_macos_host(Path(directory))
            self.assertTrue(any("module" in error.lower() for error in errors))
            self.assertTrue(any("workflow" in error.lower() for error in errors))
            self.assertTrue(any("menu" in error.lower() for error in errors))


if __name__ == "__main__":
    unittest.main()
