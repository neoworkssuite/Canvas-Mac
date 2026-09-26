from pathlib import Path


def verify_macos_host(root: Path) -> list[str]:
    errors: list[str] = []
    settings = (root / "settings.gradle.kts")
    build = root / "macosApp/build.gradle.kts"
    main = root / "macosApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/Main.kt"
    workflow = root / ".github/workflows/macos-build.yml"

    if not settings.is_file() or '":macosApp"' not in settings.read_text(encoding="utf-8"):
        errors.append("macOS module is not registered")
    build_text = build.read_text(encoding="utf-8") if build.is_file() else ""
    if 'project(":ui")' not in build_text:
        errors.append("macOS module is not linked to shared UI")
    if "TargetFormat.Dmg" not in build_text or "bundleID" not in build_text:
        errors.append("macOS bundle metadata or DMG packaging is missing")
    main_text = main.read_text(encoding="utf-8") if main.is_file() else ""
    if "MenuBar" not in main_text:
        errors.append("macOS host menu is missing")
    if "NeoCanvasApp" not in main_text:
        errors.append("macOS host does not launch shared UI")
    workflow_text = workflow.read_text(encoding="utf-8") if workflow.is_file() else ""
    if "macos-latest" not in workflow_text or ":macosApp:packageDmg" not in workflow_text:
        errors.append("macOS workflow does not package the native DMG")
    return errors


if __name__ == "__main__":
    import sys

    findings = verify_macos_host(Path.cwd())
    for finding in findings:
        print(f"ERROR: {finding}")
    raise SystemExit(1 if findings else 0)
