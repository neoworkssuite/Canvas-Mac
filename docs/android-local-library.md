# Android local artwork library

- File > Open local document displays a scrollable list of saved NeoCanvas packages in app-local storage.
- Save continues writing to the current successfully saved/opened package. New canvases get a separate UUID-named Untitled package on first Save.
- Save As asks for a human-readable name. Existing names, including case-only differences, are rejected rather than overwritten. Successful Save As makes that copy the subsequent Save target.
- New canvas and recovery clear the previous file target. Failed opens do not change it.
- The legacy NeoCanvas.neocanvas file remains listed without migration or deletion.
- Recovery packages remain separate and do not clutter the artwork list.

No cloud account or provider is involved. This is an in-app file list, not yet a thumbnail gallery. Files remain app-owned; backup/export outside app storage, sorting, renaming and deleting library entries remain future work. Android may remove app-owned files when the app is uninstalled.

Verified with actual temporary-directory package round-trip tests, duplicate/path validation tests, editor-flow tests, Windows tests and an Android debug build. Physical-tablet interaction remains to be checked.
