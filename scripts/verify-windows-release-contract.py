#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def fail(message: str) -> None:
    print(f"WINDOWS RELEASE CONTRACT FAILURE: {message}", file=sys.stderr)
    raise SystemExit(1)

release_info_path = ROOT / "ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ReleaseInfo.kt"
windows_build_path = ROOT / "windowsApp/build.gradle.kts"
launch_contract_path = ROOT / "windowsApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/platform/WindowsLaunchContract.kt"
icon_path = ROOT / "assets/branding/neocanvas.ico"

for required in (release_info_path, windows_build_path, launch_contract_path, icon_path):
    if not required.is_file():
        fail(f"missing required Windows release file: {required.relative_to(ROOT)}")

release_info = release_info_path.read_text(encoding="utf-8")
windows_build = windows_build_path.read_text(encoding="utf-8")
launch_contract = launch_contract_path.read_text(encoding="utf-8")

def kotlin_constant(name: str) -> str:
    match = re.search(rf'const val {re.escape(name)}: String = "([^"]+)"', release_info)
    if not match:
        fail(f"ReleaseInfo.kt is missing {name}")
    return match.group(1)

marketing = kotlin_constant("marketingVersion")
build_number = kotlin_constant("buildNumber")

if not re.fullmatch(r"\d+\.\d+\.\d+", marketing):
    fail(f"marketingVersion must be semantic x.y.z, got {marketing!r}")
if not build_number.isdigit() or int(build_number) < 1:
    fail(f"buildNumber must be a positive integer, got {build_number!r}")

package_match = re.search(r'packageVersion\s*=\s*"([^"]+)"', windows_build)
if not package_match:
    fail("windowsApp/build.gradle.kts is missing packageVersion")
package_version = package_match.group(1)
if package_version != marketing:
    fail(f"Windows packageVersion {package_version!r} does not match marketingVersion {marketing!r}")

for required_text in (
    'packageName = "NeoCanvas"',
    'vendor = "NeoWorksSuite"',
    'targetFormats(TargetFormat.Msi, TargetFormat.Exe)',
    'mainClass = "com.neoworksuite.neocanvas.MainKt"',
    'iconFile.set(rootProject.file("assets/branding/neocanvas.ico"))',
):
    if required_text not in windows_build:
        fail(f"Windows packaging contract is missing: {required_text}")

expected_runtime = 'windowsApp/build/compose/binaries/main/app/NeoCanvas/NeoCanvas.exe'
expected_installer = f'windowsApp/build/compose/binaries/main/exe/NeoCanvas-{marketing}.exe'

if f'const val runtimeRelativePath = "{expected_runtime}"' not in launch_contract:
    fail("Windows runtimeRelativePath no longer matches Compose packaging output")
if f'const val installerRelativePath = "{expected_installer}"' not in launch_contract:
    fail("Windows installerRelativePath does not match the marketing version")

if icon_path.stat().st_size <= 0:
    fail("Windows NeoCanvas icon is empty")

print(f"NeoCanvas Windows release contract OK: {marketing} build {build_number}")
