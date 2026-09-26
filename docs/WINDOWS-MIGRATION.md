# Windows migration

## Decision

The canonical Windows host is the Kotlin/JVM Compose host in `windowsApp`. It
continues to use the shared `core`, `brushes`, `renderer`, and `ui` modules. The
audited `windows-parity-foundation` lineage was the strongest proven source: its
head, `65120e86cf8a9f97f6873e597781938f30d31394`, passed Windows workflow run
`36193129797` before consolidation.

All five public Windows source branches remain available in both `Canvas-Mac`
and the canonical repository:

| Source branch | Audited head | Treatment |
|---|---|---|
| `windows-parity-foundation` | `65120e86cf8a9f97f6873e597781938f30d31394` | Selected capability baseline |
| `windows-export-parity` | `cea53ea034e0a844d1dbd22e5631c13211165962` | Unique export work integrated by original commits |
| `windows-host-parity` | `e05bc6bf13fef9ec62c77dda3082b86d948393a2` | Preserved for host comparison |
| `windows-parity-phase1` | `eb4253756153a1c318238577e7f86c285cab80eb` | Preserved for early input/host history |
| `windows-release-contract` | `21c55ae6d65245420951818e3029594d9dd03444` | Release checks integrated by original commits |

The wider private `christianrobertson36/neoworks` suite remains preserved at
`archive/pre-consolidation-legacy-windows`, peeled to
`803fc4ba359042a0843761286982b80e839395ae`. Its complete branch objects were
audited locally, but they are not published into the public canonical repository:
doing so would expose unrelated private suite history. No private legacy code was
required to complete the dedicated NeoCanvas Windows host.

## Integrated capabilities

- PNG, JPEG, flattened PDF, layered PSD, and TIFF export through shared renderers.
- PSD and image import through the native Windows chooser.
- Persistent local documents, Gallery stacks, recovery, versions, Workbench,
  Deep Layers, palettes, preferences, custom brushes, and imported fonts.
- HTTPS-only release lookup through the Windows update service.
- `.neocanvas` file association and opening an associated document at launch.
- A packaged executable self-test before UI startup.
- Pen-pressure normalization at the Windows input boundary.

Current iPad/shared behavior won every shared-code conflict. Platform-specific
filesystem, package, update, and desktop APIs remain in `windowsApp`.

## Test evidence

TDD red runs on canonical `windows-consolidation` established the missing
behavior before production commits were integrated:

- Run `36235188036`: expected failure because `WindowsUpdateService` was absent.
- Run `36235422525`: expected failure because shared `PdfExporter` was absent.

Canonical run `36236333249` passed at
`f533a3dcaf0dc2cc7b3ed10cae4583501067fadb`. Its single authoritative job ran
the shared and Windows regression suites, packaged the NeoCanvas EXE, and
uploaded the Windows package artifact successfully. Local Gradle execution is
unavailable on the managed Windows host because its loopback channel is blocked,
so GitHub Actions is the native build authority.

## Retained work and remaining parity

The private NeoWorks suite, old Windows experiments, and unrelated Calendar,
Writer, Sheets, PDF-suite, launcher, and photo-product code remain in their
source repositories. They are intentionally not copied into NeoCanvas.

Windows remains parity-ready only for capabilities backed by the canonical
workflow. Store signing, installer distribution, and hardware stylus validation
remain release tasks rather than claims made by repository presence alone.
