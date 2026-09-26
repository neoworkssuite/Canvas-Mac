import tempfile
import unittest
from pathlib import Path

from scripts.verify_android_host import verify_android_host


class AndroidHostContractTest(unittest.TestCase):
    def test_reports_missing_host_integration(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = verify_android_host(Path(directory))
            self.assertTrue(any("manifest" in error.lower() for error in errors))
            self.assertTrue(any("workflow" in error.lower() for error in errors))
            self.assertTrue(any("shared" in error.lower() for error in errors))

    def test_complete_fixture_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "androidApp/src/main").mkdir(parents=True)
            (root / "androidApp/src/main/AndroidManifest.xml").write_text(
                '<manifest><application><provider android:name="FileProvider" /></application></manifest>'
            )
            (root / "androidApp/build.gradle.kts").write_text(
                'implementation(project(":core")); implementation(project(":ui")); versionCode = 1'
            )
            (root / ".github/workflows").mkdir(parents=True)
            (root / ".github/workflows/android-build.yml").write_text(
                "runs-on: ubuntu-latest\nrun: ./gradlew :androidApp:testDebugUnitTest :androidApp:assembleDebug\n"
            )
            self.assertEqual([], verify_android_host(root))


if __name__ == "__main__":
    unittest.main()
