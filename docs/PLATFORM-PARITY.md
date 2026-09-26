# NeoCanvas platform parity

Green means the behavior was built or tested in the linked canonical workflow.
Yellow means code exists but native behavior is not fully validated. Red means
missing or blocked. N/A means the interaction does not apply.

| Feature | iPad | macOS | Windows | Android |
|---|---|---|---|---|
| Drawing | ✅ [reference CI](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | ✅ [host CI](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236949475) | ✅ [host CI](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | ✅ [host CI](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36237376981) |
| Documents | ✅ [smoke CI](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | 🔴 | ✅ [Windows tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | ✅ [Android tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36237376981) |
| Layers | ✅ [visual CI](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | ✅ [shared tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236949475) | ✅ [shared tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | ✅ [Android build](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36237376981) |
| Selections | ✅ [visual CI](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | ✅ [shared tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236949475) | ✅ [shared tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | 🟡 |
| Export | ✅ [reference CI](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | 🔴 | ✅ [package CI](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | ✅ [build CI](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36237376981) |
| Brush import | ✅ [reference CI](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | 🔴 | ✅ [Windows tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | ✅ [Android build](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36237376981) |
| Fonts | ✅ [reference CI](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | 🟡 | ✅ [Windows tests](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | 🟡 |
| Packaging | ✅ [device artifact](https://github.com/neoworkssuite/Canvas-Mac/actions/runs/36233934850) | ✅ [DMG artifact](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236949475) | ✅ [EXE artifact](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36236333249) | ✅ [APK artifact](https://github.com/neoworkssuite/NeoCanvas/actions/runs/36237376981) |

See [macOS parity](MACOS-PARITY.md), [Windows migration](WINDOWS-MIGRATION.md),
and [Android parity](ANDROID-PARITY.md) for platform-specific limitations.
