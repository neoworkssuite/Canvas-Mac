# NeoCanvas versioning

Product version: `1.0.0`

The root `VERSION` is the user-visible SemVer shared by all NeoCanvas hosts.
`1.0.0` is based on the existing iPad release-candidate metadata, release
checklist, and successful simulator/device/smoke/visual validation; it was not
chosen merely because repository consolidation began. Platform parity remains
separately evidence-gated.

Apple build: `1`
Android build: `1`
Windows build: `1`

Each store or installer build value is a positive integer that increases for
every uploaded or distributed binary on that platform. It is independent of
SemVer and must never decrease. Native configuration displays the root product
version and retains its platform build sequence.

The next product version is changed deliberately in `VERSION`, native packaging
metadata, and this document in one reviewed commit. A platform build-only retry
increments only that platform's native build number.
