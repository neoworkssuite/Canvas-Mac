# SDD ledger — plan: C:/Users/Windows11/Documents/Codex/2026-09-15/i-x20/docs/superpowers/plans/2026-09-15-neocanvas-v1.md

Pre-flight review:

| Tasks / interface | Producer and consumer | Finding |
| --- | --- | --- |
| 1 → 2–5 | Multiplatform Gradle modules; later tasks create code inside them | Compatible; Task 1 establishes every module. |
| 2 → 3–5 | CanvasDocument, LayerPayload, DocumentCommand, DocumentHistory | Compatible; later task interfaces consume these exact model boundaries. |
| 3 → 4–5 | TileKey, TileStore, RasterPatch, brush samples | Compatible; storage and export depend on tile data. |
| 4 → 5 | DocumentStore, .neocanvas package, SaveResult | Compatible; UI is the sole consumer. |
| 5 | Workspace integrates all earlier modules | Compatible; no later task alters the interfaces. |

Ruling: Use the unversioned current stable Compose Multiplatform release produced by the project wizard alongside Kotlin 2.4.20, rather than pinning an unverified Compose version in the plan — official Kotlin documentation recommends generating the project through the KMP wizard; cost if wrong: an implementation-time compatibility adjustment in Gradle build files.

Task 1: in progress (no Git baseline exists because the supplied workspace is not a repository)
Task 1: fix round 1/5 (Windows desktop source set addressed; static review clean)
Task 1: complete (no commit baseline; review clean)
Task 2: in progress
Task 2: fix round 1/5 (tile-copy plan and caller-collection aliasing addressed)
Task 2: fix round 2/5 (immutable wrapper and validated duplicate plan addressed)
Task 2: complete (no commit baseline; review clean)
