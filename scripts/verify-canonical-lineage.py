#!/usr/bin/env python3
"""Verify canonical NeoCanvas refs and ancestry without changing the remote."""

from __future__ import annotations

import argparse
import re
import subprocess
import tempfile
from pathlib import Path


SOURCE_ROW_RE = re.compile(
    r"^\|\s*`neoworkssuite/Canvas-Mac`\s*\|\s*`(?P<name>[^`]+)`\s*\|\s*"
    r"`(?P<sha>[0-9a-f]{40})`\s*\|$",
    re.IGNORECASE | re.MULTILINE,
)

REQUIRED_TAGS = (
    "archive/pre-consolidation-ipad",
    "archive/pre-consolidation-macos",
    "archive/pre-consolidation-windows",
)


def source_refs(audit_text: str) -> dict[str, str]:
    return {
        match.group("name"): match.group("sha").lower()
        for match in SOURCE_ROW_RE.finditer(audit_text)
    }


def verify_lineage(
    refs: dict[str, str],
    audit_text: str,
    contained_commits: set[str] | None = None,
) -> list[str]:
    source = source_refs(audit_text)
    errors: list[str] = []
    required_source_branches = {
        name: sha
        for name, sha in source.items()
        if name == "main" or name == "ipad-gestures-phase1" or name.startswith("windows-")
    }
    for name, sha in required_source_branches.items():
        ref = f"refs/heads/{name}"
        if ref not in refs:
            errors.append(f"missing required canonical ref: {ref}")
        elif refs[ref] != sha:
            errors.append(f"canonical {name} differs from recorded source SHA {sha}")

    ipad_sha = source.get("ipad-gestures-phase1")
    ipad_dev = refs.get("refs/heads/ipad-dev")
    if ipad_dev is None:
        errors.append("missing required canonical ref: refs/heads/ipad-dev")
    elif ipad_sha and ipad_dev != ipad_sha and (
        contained_commits is None or ipad_sha not in contained_commits
    ):
        errors.append(f"ipad-dev does not contain recorded iPad SHA {ipad_sha}")

    for tag in REQUIRED_TAGS:
        ref = f"refs/tags/{tag}"
        if ref not in refs:
            errors.append(f"missing required canonical ref: {ref}")
    return errors


def _run(*args: str, cwd: Path | None = None, check: bool = True) -> str:
    result = subprocess.run(
        args,
        cwd=cwd,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.stdout.strip()


def inspect_remote(repository: str, audit_text: str) -> tuple[dict[str, str], set[str]]:
    with tempfile.TemporaryDirectory(prefix="neocanvas-lineage-") as directory:
        mirror = Path(directory) / "canonical.git"
        _run("git", "clone", "--mirror", f"https://github.com/{repository}.git", str(mirror))
        rows = _run(
            "git",
            "for-each-ref",
            "--format=%(refname) %(objectname) %(*objectname)",
            "refs/heads",
            "refs/tags",
            cwd=mirror,
        )
        refs: dict[str, str] = {}
        for row in rows.splitlines():
            parts = row.split()
            if len(parts) >= 2:
                refs[parts[0]] = parts[2] if len(parts) >= 3 else parts[1]

        contained: set[str] = set()
        ipad_sha = source_refs(audit_text).get("ipad-gestures-phase1")
        if ipad_sha and "refs/heads/ipad-dev" in refs:
            result = subprocess.run(
                ["git", "merge-base", "--is-ancestor", ipad_sha, "refs/heads/ipad-dev"],
                cwd=mirror,
                capture_output=True,
            )
            if result.returncode == 0:
                contained.add(ipad_sha)
        return refs, contained


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audit", type=Path)
    parser.add_argument("--repository", default="neoworkssuite/NeoCanvas")
    args = parser.parse_args()
    audit_text = args.audit.read_text(encoding="utf-8")
    refs, contained = inspect_remote(args.repository, audit_text)
    errors = verify_lineage(refs, audit_text, contained)
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print(f"OK: canonical lineage verified for {args.repository}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
