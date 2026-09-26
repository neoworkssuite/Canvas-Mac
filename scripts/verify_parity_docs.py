from pathlib import Path
import re

REQUIRED_FEATURES = ("Drawing", "Documents", "Layers", "Selections", "Export", "Brush import", "Fonts", "Packaging")

def verify_parity_docs(root: Path) -> list[str]:
    errors: list[str] = []
    matrix = root / "docs/PLATFORM-PARITY.md"
    policy = root / "docs/PARITY-READY.md"
    if not matrix.is_file():
        return ["platform parity document is missing"]
    if not policy.is_file() or "PARITY-READY" not in policy.read_text(encoding="utf-8"):
        errors.append("PARITY-READY policy is missing")
    text = matrix.read_text(encoding="utf-8")
    if "| Feature | iPad | macOS | Windows | Android |" not in text:
        errors.append("parity matrix platform columns are missing")
    for feature in REQUIRED_FEATURES:
        if f"| {feature} " not in text:
            errors.append(f"parity matrix feature row is missing: {feature}")
    allowed = ("✅", "🟡", "🔴", "N/A")
    for line in text.splitlines():
        if not line.startswith("|") or line.startswith("|---") or "Feature" in line:
            continue
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if len(cells) != 5:
            continue
        for cell in cells[1:]:
            if not any(cell.startswith(status) for status in allowed):
                errors.append(f"invalid parity status: {cell}")
            if cell.startswith("✅") and not re.search(r"\[[^]]+\]\(https://", cell):
                errors.append(f"green parity status lacks linked evidence: {cell}")
    return errors

if __name__ == "__main__":
    findings = verify_parity_docs(Path.cwd())
    for finding in findings:
        print(f"ERROR: {finding}")
    raise SystemExit(1 if findings else 0)
