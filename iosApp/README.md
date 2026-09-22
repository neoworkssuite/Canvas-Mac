# NeoCanvas iPad Host

This directory contains the native Apple host for **NeoCanvas**. The creative engine and most UI remain in Kotlin Multiplatform; SwiftUI provides the Apple application shell and platform integrations.

## Architecture

```text
SwiftUI
  └─ UIViewControllerRepresentable
      └─ NeoCanvasKit
          └─ MainViewController()
              └─ NeoCanvasApp()
```

## Generate the Xcode project

On macOS:

```bash
brew install xcodegen
./scripts/prepare-ios.sh
```

The preparation script:

- builds the iOS asset catalog from the checked-in NeoCanvas logo;
- generates `iosApp/NeoCanvas.xcodeproj`;
- keeps the app bundle ID at `com.neoworksuite.neocanvas`;
- leaves signing under Xcode's automatic signing configuration.

The Xcode build phase invokes `:ui:embedAndSignAppleFrameworkForXcode` and embeds NeoCanvasKit.

## Release packaging

The iPad host includes:

- AppIcon generated from the suite artwork;
- `PrivacyInfo.xcprivacy` declaring the app's local-only/no-tracking data posture;
- Files-visible local NeoCanvas documents;
- Photos import permission text;
- native JPEG, PDF, TIFF and PSD export support;
- portrait and landscape orientations;
- unsigned ARM64 device builds in CI for release validation.

NeoCanvas is designed to work without an account, telemetry, cloud storage or advertising SDKs. Release CI verifies that the privacy manifest and compiled asset catalog are present in the built iPad app.

Before App Store submission, update `Configuration/Config.xcconfig` with the intended marketing/build version and create the signed Archive using the NeoWorksSuite Apple Developer account.
