#!/usr/bin/env python3
"""Validate that the NeoCanvas migration audit contains required evidence."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


REQUIRED_REPOSITORIES = (
    "neoworkssuite/Canvas-Mac",
    "neoworkssuite/Canvas_android",
    "christianrobertson36/neoworks",
)

REQUIRED_CANVAS_MAC_BRANCHES = (
    "main",
    "apple-platform",
    "ipad-gestures-phase1",
    "windows-export-parity",
    "windows-host-parity",
    "windows-parity-foundation",
    "windows-parity-phase1",
    "windows-release-contract",
)

REQUIRED_SECTIONS = (
    "Repository visibility and default branches",
    "Branch and tag HEADs",
    "Ancestry relationships",
    "Module inventory",
    "Workflow inventory",
    "Build scripts and targets",
    "Platform hosts",
    "Duplicated code and unique features",
    "Stale experiments and evidence quality",
    "Credential boundary",
)

SHA_RE = re.compile(r"(?<![0-9a-f])[0-9a-f]{40}(?![0-9a-f])", re.IGNORECASE)


def _repository_has_sha(text: str, repository: str) -> bool:
    return any(repository in line and SHA_RE.search(line) for line in text.splitlines())


def verify_audit(path: Path) -> list[str]:
    """Return concise contract errors for an audit Markdown document."""
    if not path.is_file():
        return [f"audit file not found: {path}"]

    text = path.read_text(encoding="utf-8")
    errors: list[str] = []

    for repository in REQUIRED_REPOSITORIES:
        if repository not in text:
            errors.append(f"missing required repository: {repository}")
        elif not _repository_has_sha(text, repository):
            errors.append(f"repository {repository} is missing a 40-character SHA ref")

    for branch in REQUIRED_CANVAS_MAC_BRANCHES:
        branch_pattern = re.compile(
            rf"^\|[^\n]*neoworkssuite/Canvas-Mac[^\n]*`{re.escape(branch)}`[^\n]*"
            rf"[0-9a-f]{{40}}[^\n]*\|$",
            re.IGNORECASE | re.MULTILINE,
        )
        if not branch_pattern.search(text):
            errors.append(f"missing Canvas-Mac branch with 40-character SHA: {branch}")

    for section in REQUIRED_SECTIONS:
        if not re.search(rf"^##\s+{re.escape(section)}\s*$", text, re.MULTILINE):
            errors.append(f"missing required section: {section}")

    if "active `neoworkssuite` identity" not in text:
        errors.append("missing active identity restoration note for neoworkssuite")

    if not re.search(r"^Collected:\s+\d{4}-\d{2}-\d{2}T", text, re.MULTILINE):
        errors.append("missing ISO-8601 collection timestamp")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audit", type=Path)
    args = parser.parse_args()
    errors = verify_audit(args.audit)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print(f"OK: {args.audit}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
