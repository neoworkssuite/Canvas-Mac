#!/usr/bin/env python3
"""Read and verify NeoCanvas preservation refs without mutating GitHub."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path
from typing import NamedTuple


class PreservationRef(NamedTuple):
    repository: str
    name: str
    target_sha: str


ROW_RE = re.compile(
    r"^\|\s*`(?P<repository>[^`]+)`\s*\|\s*"
    r"`(?P<name>archive/pre-consolidation-[^`]+)`\s*\|\s*"
    r"`(?P<sha>[0-9a-f]{40})`\s*\|",
    re.IGNORECASE | re.MULTILINE,
)


def expected_preservation_refs(audit_text: str) -> list[PreservationRef]:
    """Parse the audit's preservation table and reject ambiguous mappings."""
    refs: list[PreservationRef] = []
    seen: dict[tuple[str, str], str] = {}
    for match in ROW_RE.finditer(audit_text):
        ref = PreservationRef(
            match.group("repository"),
            match.group("name"),
            match.group("sha").lower(),
        )
        key = (ref.repository, ref.name)
        previous = seen.get(key)
        if previous is not None and previous != ref.target_sha:
            raise ValueError(
                f"preservation ref {ref.repository}:{ref.name} has different targets "
                f"{previous} and {ref.target_sha}"
            )
        if previous is None:
            refs.append(ref)
            seen[key] = ref.target_sha
    if not refs:
        raise ValueError("no preservation references found in audit")
    return refs


def creation_candidates(
    expected: list[PreservationRef], remote: dict[str, str]
) -> list[PreservationRef]:
    """Return missing refs; refuse any operation that would move a tag."""
    missing: list[PreservationRef] = []
    for ref in expected:
        actual = remote.get(ref.name)
        if actual is None:
            missing.append(ref)
        elif actual.lower() != ref.target_sha:
            raise ValueError(
                f"refusing to move {ref.name}: remote targets {actual}, "
                f"audit expects {ref.target_sha}"
            )
    return missing


def _gh_api(endpoint: str) -> object:
    result = subprocess.run(
        ["gh", "api", endpoint],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return json.loads(result.stdout)


def _peel(repository: str, object_type: str, sha: str) -> str:
    current_type = object_type
    current_sha = sha
    while current_type == "tag":
        payload = _gh_api(f"repos/{repository}/git/tags/{current_sha}")
        assert isinstance(payload, dict)
        target = payload["object"]
        current_type = str(target["type"])
        current_sha = str(target["sha"])
    return current_sha.lower()


def remote_preservation_refs(repository: str) -> dict[str, str]:
    payload = _gh_api(
        f"repos/{repository}/git/matching-refs/tags/archive/pre-consolidation"
    )
    assert isinstance(payload, list)
    refs: dict[str, str] = {}
    prefix = "refs/tags/"
    for item in payload:
        name = str(item["ref"])
        if name.startswith(prefix):
            name = name[len(prefix) :]
        obj = item["object"]
        refs[name] = _peel(repository, str(obj["type"]), str(obj["sha"]))
    return refs


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audit", type=Path)
    parser.add_argument(
        "--repository", help="verify only one repository under the active identity"
    )
    args = parser.parse_args()

    refs = expected_preservation_refs(args.audit.read_text(encoding="utf-8"))
    repositories = sorted({ref.repository for ref in refs})
    if args.repository:
        repositories = [args.repository]

    failures = 0
    for repository in repositories:
        expected = [ref for ref in refs if ref.repository == repository]
        if not expected:
            print(f"ERROR {repository}: no expected preservation refs")
            failures += 1
            continue
        remote = remote_preservation_refs(repository)
        for ref in expected:
            actual = remote.get(ref.name)
            if actual is None:
                print(f"missing {repository} {ref.name} {ref.target_sha}")
                failures += 1
            elif actual == ref.target_sha:
                print(f"matching {repository} {ref.name} {actual}")
            else:
                print(
                    f"conflict {repository} {ref.name} expected={ref.target_sha} "
                    f"actual={actual}"
                )
                failures += 1
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
