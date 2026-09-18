# NeoCanvas branding

`neocanvas-source.png` is the user-supplied artwork, copied without modification. The original colours, proportions and black background are retained.

Run `scripts/Generate-AppIcons.ps1` from PowerShell to regenerate:

- Shared Compose toolbar/window PNG (256 px).
- Windows ICO with 16, 24, 32, 48, 64, 128 and 256 px entries.
- Android density-specific launcher PNGs and a padded adaptive foreground so the artwork fits launcher masks.

Windows native packaging uses the ICO for the application, shortcuts and installer branding supported by jpackage. Vendor and Start menu group are NeoWorksSuite. This does not replace the installer wizard's standard page layout with a custom graphic.

Android references the launcher resource for both normal and round icons. No generated reinterpretation of the supplied logo is used.
