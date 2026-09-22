# Local recovery

NeoCanvas checks for a local recovery copy on startup. The user can recover it as an unsaved canvas or start fresh. A snapshot is not automatically substituted for a manually saved document.

While the shared editor is running, a 30-second coroutine checks for unsaved changes. Changed revisions are copied into an independent snapshot and written in a background dispatcher. Unchanged revisions are skipped; failed writes are reported and retried at the next interval. The file is written through SafeDocumentStore, using a completed temporary package before replacement.

Locations:

- Windows: `%LOCALAPPDATA%/NeoCanvas/recovery/last-session.neocanvas` (user-home fallback if LOCALAPPDATA is unavailable).
- Android: `recovery/last-session.neocanvas` beneath the app's local documents directory.
- iPad: `Documents/NeoCanvas/Recovery/last-session.neocanvas` inside the app's Files-visible document container.

The snapshot is separate from manual Save and export. Autosaving does not mark artwork as manually saved. A successful manual Save or Save As retires the recovery snapshot so the next launch does not offer an older copy of artwork that was already saved. Choosing Start fresh also retires the offered snapshot. If recovery cleanup fails, the manual save remains valid and NeoCanvas reports the cleanup problem; a later autosave can replace the recovery copy. An unreadable snapshot is not overwritten before the user chooses whether to start fresh.

Limits: one recovery slot per host storage directory; no version browser; edits since the last completed snapshot may be lost; forced termination and Android background suspension cannot guarantee a final save. Full tile snapshots consume memory and initial pixel copying occurs on the UI thread. Background package/file writing avoids doing that work on the UI thread. Multiple concurrent app instances sharing this recovery path are not supported.

Automated coverage checks autosave retry, unchanged revisions, retained dirty state, startup recovery consent, unreadable-snapshot protection, manual-save cleanup, failed-save preservation, and Start fresh cleanup. Windows compilation and an Android debug build were verified; forced-termination and physical-device lifecycle tests remain manual acceptance work.
