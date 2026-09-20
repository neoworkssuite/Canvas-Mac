#!/bin/bash

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="${1:-/Volumes/media/projects/apple apps/NeoCanavs.png}"

if [ -z "$SOURCE" ] || [ ! -f "$SOURCE" ]; then
    echo "Usage: ./scripts/install-app-icon.sh /path/to/NeoCanvas-icon.png"\n    echo "Default: /Volumes/media/projects/apple apps/NeoCanavs.png"
    exit 1
fi

CATALOG="$ROOT/iosApp/NeoCanvas/Assets.xcassets"
ICONSET="$CATALOG/AppIcon.appiconset"
ICON="$ICONSET/NeoCanvas-AppIcon-1024.png"

mkdir -p "$ICONSET"

cat > "$CATALOG/Contents.json" <<'JSON'
{
  "info": {
    "author": "xcode",
    "version": 1
  }
}
JSON

# Xcode 26 supports the single 1024x1024 universal iOS icon format.
# The supplied NeoCanvas artwork is opaque, so it is safe for an app icon.
sips -z 1024 1024 "$SOURCE" --out "$ICON" >/dev/null

cat > "$ICONSET/Contents.json" <<'JSON'
{
  "images": [
    {
      "filename": "NeoCanvas-AppIcon-1024.png",
      "idiom": "universal",
      "platform": "ios",
      "size": "1024x1024"
    }
  ],
  "info": {
    "author": "xcode",
    "version": 1
  }
}
JSON

echo "Installed NeoCanvas app icon:"
echo "$ICON"
sips -g pixelWidth -g pixelHeight "$ICON"
