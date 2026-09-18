# NeoCanvas Custom Brushes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver editable, persistent, importable and exportable custom brushes.

**Architecture:** Put the portable brush codec in `brushes`, state/snapshot behaviour in `ui`, and filesystem/chooser operations in platform hosts. Built-ins are immutable; all edits create local custom copies.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, platform-local JVM/Android files, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-17-neocanvas-custom-brushes-design.md`

## Global Constraints

- Local-only with no network dependency.
- Preserve existing built-in IDs and document compatibility.
- Import never silently overwrites an existing brush.
- Windows and Android tablet targets remain supported.

---

### Task 1: Portable brush codec

**Files:**
- Create: `brushes/src/commonMain/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushCodec.kt`
- Create: `brushes/src/commonTest/kotlin/com/neoworksuite/neocanvas/brushes/NeoBrushCodecTest.kt`

- [ ] Write failing round-trip, malformed-input and unsupported-version tests.
- [ ] Run `:brushes:desktopTest` and confirm RED.
- [ ] Implement strict versioned UTF-8 encoding/decoding.
- [ ] Run `:brushes:desktopTest` and confirm GREEN.

### Task 2: Persistent library state

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushLibraryState.kt`
- Modify: `ui/src/commonTest/kotlin/com/neoworksuite/neocanvas/ui/BrushLibraryStateTest.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorFileActions.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/EditorState.kt`

- [ ] Write failing custom-save, collision-import and settings round-trip tests.
- [ ] Implement custom merging and snapshot persistence callbacks.
- [ ] Verify UI tests.

### Task 3: Brush Studio and platform storage

**Files:**
- Create: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushStudioDialog.kt`
- Modify: `ui/src/commonMain/kotlin/com/neoworksuite/neocanvas/ui/BrushPanel.kt`
- Modify: `windowsApp/src/jvmMain/kotlin/com/neoworksuite/neocanvas/platform/WindowsEditorFileActions.kt`
- Modify: `androidApp/src/main/kotlin/com/neoworksuite/neocanvas/platform/AndroidEditorFileActions.kt`

- [ ] Add Studio UI and import/export controls.
- [ ] Add atomic local persistence and platform file operations.
- [ ] Run all tests, compile both platforms, and package Windows plus Android outputs.

