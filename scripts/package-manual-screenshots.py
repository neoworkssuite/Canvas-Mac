#!/usr/bin/env python3
"""Validate and index the deterministic NeoCanvas manual screenshot pack."""

from __future__ import annotations

import argparse
import html
import json
import shutil
import struct
from pathlib import Path


REQUIRED_SCREENSHOTS = (
    "01-gallery-light.png",
    "02-gallery-dark.png",
    "03-new-canvas.png",
    "04-editor.png",
    "05-brush-library.png",
    "06-colour-studio.png",
    "07-layers.png",
    "08-fx-adjustments.png",
    "09-settings.png",
    "10-kids-activities.png",
)


def collect_exported_attachments(attachments: Path, directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for name in REQUIRED_SCREENSHOTS:
        matches = [path for path in attachments.rglob(name) if path.is_file()]
        if len(matches) > 1:
            raise ValueError(f"Found duplicate exported attachments named {name}")
        if matches:
            shutil.copy2(matches[0], directory / name)


def png_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()[:24]
    if len(data) != 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError(f"{path.name} is not a valid PNG")
    return struct.unpack(">II", data[16:24])


def build_pack(directory: Path, commit: str, device: str) -> dict:
    missing = [name for name in REQUIRED_SCREENSHOTS if not (directory / name).is_file()]
    if missing:
        raise ValueError("Missing required screenshots: " + ", ".join(missing))

    screenshots = []
    for name in REQUIRED_SCREENSHOTS:
        width, height = png_dimensions(directory / name)
        screenshots.append({"file": name, "width": width, "height": height})

    manifest = {
        "product": "NeoCanvas",
        "purpose": "Website and user manual source screenshots",
        "commit": commit,
        "device": device,
        "screenshots": screenshots,
    }
    (directory / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )

    cards = "\n".join(
        f'<figure><img src="{html.escape(item["file"])}" alt="{html.escape(item["file"])}">'
        f'<figcaption>{html.escape(item["file"])}</figcaption></figure>'
        for item in screenshots
    )
    contact_sheet = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><title>NeoCanvas screenshot pack</title>
<style>body{{font:16px system-ui;background:#171b22;color:#fff;margin:24px}}main{{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}}figure{{margin:0;background:#252b35;padding:12px;border-radius:12px}}img{{display:block;width:100%;height:auto}}figcaption{{padding-top:8px;color:#c7ced8}}</style>
</head><body><h1>NeoCanvas manual screenshot pack</h1><p>Commit {html.escape(commit)} · {html.escape(device)}</p><main>{cards}</main></body></html>
"""
    (directory / "contact-sheet.html").write_text(contact_sheet, encoding="utf-8")
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--attachments-directory", type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--device", required=True)
    args = parser.parse_args()
    if args.attachments_directory:
        collect_exported_attachments(args.attachments_directory, args.directory)
    build_pack(args.directory, args.commit, args.device)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
