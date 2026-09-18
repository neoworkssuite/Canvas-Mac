# NeoCanvas Apple Port

NeoCanvas is being extended from the existing Kotlin Multiplatform codebase.

## Shared code

- core
- brushes
- renderer
- ui

## iPad targets

- iosArm64
- iosSimulatorArm64

The UI module creates:

NeoCanvasKit.framework

This framework will be hosted by a small Xcode/iPad application.

## macOS

The Mac edition will reuse the existing Compose Desktop implementation
rather than rewriting NeoCanvas in Swift.

Platform-specific Mac services will provide:

- native open/save dialogs
- macOS menus
- keyboard shortcuts
- clipboard
- drag and drop
- application packaging

## Development

Primary development remains on Windows.

Apple compilation, signing and TestFlight deployment will run on macOS.