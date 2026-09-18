# NeoCanvas

NeoCanvas is an offline-first raster drawing studio for Android and Windows.

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

## Prerequisites

- JDK 17 or newer
- Android SDK with API 36 installed (for the Android host)
- Gradle 8.13 or the project Gradle wrapper

## Verify the shell

```powershell
./gradlew :androidApp:testDebugUnitTest
./gradlew :windowsApp:run
```

The Android test task should complete successfully. The Windows task opens a window
titled `NeoCanvas`; close the window to end the task.
