#!/usr/bin/env python3
"""Validate NeoCanvas product-version and documentation consistency."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


SEMVER_RE = re.compile(
    r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)"
    r"(?:[-+][0-9A-Za-z.-]+)?$"
)


def verify_version_contract(root: Path) -> list[str]:
    errors: list[str] = []
    version_path = root / "VERSION"
    versioning_path = root / "docs" / "VERSIONING.md"
    readme_path = root / "README.md"
    for path in (version_path, versioning_path, readme_path):
        if not path.is_file():
            errors.append(f"missing required version file: {path.relative_to(root)}")
    if errors:
        return errors

    version = version_path.read_text(encoding="utf-8").strip()
    if not SEMVER_RE.fullmatch(version):
        errors.append(f"VERSION must contain valid SemVer, got {version!r}")

    versioning = versioning_path.read_text(encoding="utf-8")
    if f"Product version: `{version}`" not in versioning:
        errors.append("documented product version does not match VERSION")
    for platform in ("Apple", "Android", "Windows"):
        match = re.search(rf"{platform} build:\s*`([^`]+)`", versioning)
        if not match or not match.group(1).isdigit() or int(match.group(1)) < 1:
            errors.append(f"{platform} build must be a positive numeric value")

    readme = readme_path.read_text(encoding="utf-8")
    required_readme = (
        "iPad",
        "macOS",
        "Windows",
        "Android",
        "PARITY-READY",
        "docs/MIGRATION-AUDIT.md",
        "docs/PLATFORM-PARITY.md",
        "docs/VERSIONING.md",
    )
    missing = [value for value in required_readme if value not in readme]
    if missing:
        errors.append(f"README is missing platform/policy links: {', '.join(missing)}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path, default=Path(__file__).parents[1])
    args = parser.parse_args()
    errors = verify_version_contract(args.root.resolve())
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print("OK: NeoCanvas version contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
