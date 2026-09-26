from pathlib import Path


def verify_ipad_integrity(root: Path) -> list[str]:
    errors: list[str] = []
    missing_modules = [name for name in ("core", "brushes", "renderer", "ui") if not (root / name).is_dir()]
    if missing_modules:
        errors.append("missing shared module(s): " + ", ".join(missing_modules))
    host = root / "iosApp/NeoCanvas"
    if not host.is_dir():
        errors.append("iPad host directory is missing")
    workflow = root / ".github/workflows/ipad-build.yml"
    workflow_text = workflow.read_text(encoding="utf-8") if workflow.is_file() else ""
    required_evidence = ("ipad-dev", "Simulator", "physical iPad", "Smoke test", "Extended visual", "Upload")
    missing_workflow = [item for item in required_evidence if item.lower() not in workflow_text.lower()]
    if missing_workflow:
        errors.append("iPad workflow is missing: " + ", ".join(missing_workflow))
    if host.is_dir():
        for path in host.rglob("*"):
            if path.is_file() and any(term in path.name.lower() for term in ("sampleartwork", "showcaseartwork", "manualscreenshot")):
                errors.append("runtime sample asset is bundled in the iPad host: " + str(path.relative_to(root)))
    return errors


if __name__ == "__main__":
    findings = verify_ipad_integrity(Path.cwd())
    for finding in findings:
        print(f"ERROR: {finding}")
    raise SystemExit(1 if findings else 0)
