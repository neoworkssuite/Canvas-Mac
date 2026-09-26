# NeoCanvas

NeoCanvas is an offline-first drawing and creative studio for iPad, macOS,
Windows, and Android. The iPad implementation is the product reference while
the other hosts move through evidence-based parity work.

The project is a Kotlin and Compose Multiplatform application. It has no account,
network, telemetry, or cloud-storage requirement; documents are designed to remain
local unless a person explicitly exports or copies them.

## Modules

- `core` — document model, commands, history, and persistence contracts
- `brushes` — brush definitions and stroke sampling
- `renderer` — tiles, compositing, and export
- `ui` — shared Compose editor workspace
- `androidApp` — Android host
- `windowsApp` — Windows desktop host
- `iosApp` — native iPad host for the shared NeoCanvas editor

## Prerequisites

- JDK 17 or newer
- Android SDK with API 36 installed (for the Android host)
- Gradle 8.13 or the project Gradle wrapper
- macOS + Xcode + XcodeGen for the Apple host

## Verify the shell

```powershell
./gradlew :androidApp:testDebugUnitTest
./gradlew :windowsApp:run
```

The Android test task should complete successfully. The Windows task opens a window
titled `NeoCanvas`; close the window to end the task.

## Development policy

Feature work begins on `ipad-dev` and is accepted on iPad first. A feature is
marked **PARITY-READY** only after its shared contract and required assets are
documented for macOS, Windows, and Android. Promotion to `main` requires the
applicable platform matrix or an explicit documented exception; source presence
alone is not completion evidence.

## Project records

- [Migration audit](docs/MIGRATION-AUDIT.md)
- [Platform parity](docs/PLATFORM-PARITY.md)
- [Versioning](docs/VERSIONING.md)
- [Release checklist](docs/release-checklist.md)


## Apple release validation

The `NeoCanvas CI` workflow uses fast shared tests for ordinary commits and a deliberate full Apple lane for `[ipad]` / `[full-ci]` checkpoints. The full lane builds both simulator and ARM64 device apps, performs simulator launch/visual stability checks, and verifies release resources such as the AppIcon asset catalog and privacy manifest.

NeoCanvas does not require an account, network connection, telemetry service or cloud-storage provider for its core workflow.
