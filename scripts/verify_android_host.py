from pathlib import Path


def verify_android_host(root: Path) -> list[str]:
    errors: list[str] = []
    manifest = root / "androidApp/src/main/AndroidManifest.xml"
    build = root / "androidApp/build.gradle.kts"
    workflow = root / ".github/workflows/android-build.yml"
    manifest_text = manifest.read_text(encoding="utf-8") if manifest.is_file() else ""
    build_text = build.read_text(encoding="utf-8") if build.is_file() else ""
    workflow_text = workflow.read_text(encoding="utf-8") if workflow.is_file() else ""
    if "FileProvider" not in manifest_text:
        errors.append("Android manifest file-provider integration is missing")
    if 'project(":core")' not in build_text or 'project(":ui")' not in build_text:
        errors.append("Android host is not linked to shared modules")
    if "versionCode" not in build_text:
        errors.append("Android package version is missing")
    if "ubuntu-latest" not in workflow_text or ":androidApp:assembleDebug" not in workflow_text:
        errors.append("Android workflow does not build an APK")
    return errors


if __name__ == "__main__":
    findings = verify_android_host(Path.cwd())
    for finding in findings:
        print(f"ERROR: {finding}")
    raise SystemExit(1 if findings else 0)
