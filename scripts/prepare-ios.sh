#!/bin/bash

set -euo pipefail

cd "$(dirname "$0")/.."

echo "Preparing NeoCanvas iOS app icon..."

ICON_SOURCE="ui/src/commonMain/composeResources/drawable/neocanvas_logo.png"
ASSET_CATALOG="iosApp/NeoCanvas/Assets.xcassets"
ICON_SET="$ASSET_CATALOG/AppIcon.appiconset"
ICON_OUTPUT="$ICON_SET/NeoCanvas-AppIcon-1024.png"

if [ ! -f "$ICON_SOURCE" ]; then
    echo "Missing NeoCanvas icon source: $ICON_SOURCE"
    exit 1
fi

mkdir -p "$ICON_SET"

cat > "$ASSET_CATALOG/Contents.json" <<'JSON'
{
  "info": {
    "author": "xcode",
    "version": 1
  }
}
JSON

# Xcode creates the iPad/iPhone icon variants from one 1024x1024 universal source.
# Resize the same artwork NeoCanvas already uses in its Gallery and app chrome.
sips -z 1024 1024 "$ICON_SOURCE" --out "$ICON_OUTPUT" >/dev/null

cat > "$ICON_SET/Contents.json" <<'JSON'
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

echo "App icon ready:"
sips -g pixelWidth -g pixelHeight "$ICON_OUTPUT"

if ! command -v xcodegen >/dev/null 2>&1; then
    echo "XcodeGen is required."
    echo "Install with: brew install xcodegen"
    exit 1
fi

cd iosApp

xcodegen generate

echo
echo "Generated:"
echo "iosApp/NeoCanvas.xcodeproj"
echo

echo "Available Xcode schemes:"
xcodebuild \
    -project NeoCanvas.xcodeproj \
    -list

echo
echo "NeoCanvas iPad host is ready for Xcode build."