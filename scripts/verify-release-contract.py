#!/usr/bin/env python3
from pathlib import Path
import plistlib
import re
import sys

from verify_ipad_integrity import verify_ipad_integrity

ROOT = Path(__file__).resolve().parents[1]

def fail(message: str) -> None:
    print(f"RELEASE CONTRACT FAILURE: {message}", file=sys.stderr)
    raise SystemExit(1)

def parse_xcconfig(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("//") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values

def kotlin_constant(text: str, name: str) -> str:
    match = re.search(rf'const val {re.escape(name)}: String = "([^"]+)"', text)
    if not match:
        fail(f"ReleaseInfo.kt is missing {name}")
    return match.group(1)

config_path = ROOT / "iosApp/Configuration/Config.xcconfig"
release_info_path = ROOT / "ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/ReleaseInfo.kt"
info_plist_path = ROOT / "iosApp/NeoCanvas/Info.plist"
privacy_path = ROOT / "iosApp/NeoCanvas/PrivacyInfo.xcprivacy"
project_yml_path = ROOT / "iosApp/project.yml"

for required in (config_path, release_info_path, info_plist_path, privacy_path, project_yml_path):
    if not required.is_file():
        fail(f"missing required release file: {required.relative_to(ROOT)}")

config = parse_xcconfig(config_path)
marketing = config.get("MARKETING_VERSION", "")
build = config.get("CURRENT_PROJECT_VERSION", "")
bundle = config.get("PRODUCT_BUNDLE_IDENTIFIER", "")

if not re.fullmatch(r"\d+\.\d+\.\d+", marketing):
    fail(f"MARKETING_VERSION must be semantic x.y.z, got {marketing!r}")
if not build.isdigit() or int(build) < 1:
    fail(f"CURRENT_PROJECT_VERSION must be a positive integer, got {build!r}")
if bundle != "com.neoworksuite.neocanvas":
    fail(f"unexpected bundle identifier {bundle!r}")

target_family = config.get("TARGETED_DEVICE_FAMILY", "")
if target_family != "2":
    fail(f"NeoCanvas 1.0 must remain iPad-only until iPhone has its own acceptance gate; got {target_family!r}")

release_info = release_info_path.read_text(encoding="utf-8")
if kotlin_constant(release_info, "marketingVersion") != marketing:
    fail("in-app marketingVersion does not match Config.xcconfig")
if kotlin_constant(release_info, "buildNumber") != build:
    fail("in-app buildNumber does not match Config.xcconfig")
if kotlin_constant(release_info, "stage").strip().lower() in {"", "development", "prototype"}:
    fail("release stage still identifies a development/prototype build")
for url_name in ("websiteUrl", "communityUrl"):
    url = kotlin_constant(release_info, url_name)
    if not url.startswith("https://"):
        fail(f"{url_name} must use https")

with info_plist_path.open("rb") as handle:
    info = plistlib.load(handle)

if info.get("ITSAppUsesNonExemptEncryption") is not False:
    fail("Info.plist must explicitly declare ITSAppUsesNonExemptEncryption=false")
if info.get("CFBundleShortVersionString") != "$(MARKETING_VERSION)":
    fail("Info.plist CFBundleShortVersionString must use MARKETING_VERSION")
if info.get("CFBundleVersion") != "$(CURRENT_PROJECT_VERSION)":
    fail("Info.plist CFBundleVersion must use CURRENT_PROJECT_VERSION")
if info.get("CFBundleIdentifier") != "$(PRODUCT_BUNDLE_IDENTIFIER)":
    fail("Info.plist CFBundleIdentifier must use PRODUCT_BUNDLE_IDENTIFIER")

ipad_orientations = set(info.get("UISupportedInterfaceOrientations~ipad", []))
expected_orientations = {
    "UIInterfaceOrientationPortrait",
    "UIInterfaceOrientationPortraitUpsideDown",
    "UIInterfaceOrientationLandscapeLeft",
    "UIInterfaceOrientationLandscapeRight",
}
if not expected_orientations.issubset(ipad_orientations):
    fail("Info.plist does not declare all four iPad orientations")

with privacy_path.open("rb") as handle:
    privacy = plistlib.load(handle)

if privacy.get("NSPrivacyTracking") is not False:
    fail("privacy manifest must declare NSPrivacyTracking=false")
if not isinstance(privacy.get("NSPrivacyTrackingDomains"), list):
    fail("privacy manifest must contain NSPrivacyTrackingDomains array")
if not isinstance(privacy.get("NSPrivacyCollectedDataTypes"), list):
    fail("privacy manifest must contain NSPrivacyCollectedDataTypes array")
if not isinstance(privacy.get("NSPrivacyAccessedAPITypes"), list):
    fail("privacy manifest must contain NSPrivacyAccessedAPITypes array")

project_yml = project_yml_path.read_text(encoding="utf-8")
if f"PRODUCT_BUNDLE_IDENTIFIER: {bundle}" not in project_yml:
    fail("project.yml bundle identifier does not match Config.xcconfig")
if 'TARGETED_DEVICE_FAMILY: "2"' not in project_yml:
    fail("project.yml must scope NeoCanvas 1.0 to iPad")
if "ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon" not in project_yml:
    fail("project.yml does not select the AppIcon asset catalog")
if "CODE_SIGN_STYLE: Automatic" not in project_yml:
    fail("project.yml is not configured for automatic signing handoff")

integrity_errors = verify_ipad_integrity(ROOT)
if integrity_errors:
    fail("; ".join(integrity_errors))

print(f"NeoCanvas release contract OK: {marketing} ({build}) · {bundle}")
