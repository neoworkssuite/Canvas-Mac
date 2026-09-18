#!/bin/bash

set -euo pipefail

cd "$(dirname "$0")/.."

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