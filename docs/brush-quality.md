# Brush quality pass

- Continuous distance-based stamp placement carries spacing across pointer samples instead of restarting at every event. Collinear input with different sampling density produces identical raster output in regression tests.
- Configured round brushes have antialiased edges. Graphite uses a finer deterministic paper grain; airbrush uses a smooth cubic falloff and lower default flow; Dry Paint adds fixed bristle/grain texture.
- Six presets: Graphite Pencil, Studio Ink, Airbrush, Dry Paint, Flat Marker and Eraser.
- Brush panel exposes size, flow, spacing, pressure-to-size, pressure-to-opacity, stabilisation and Reset preset.
- Brush thumbnails and the larger settings preview use the real raster engine with a synthetic pressure-varying stroke. Large preview sizes are capped at 40 pixels to fit the preview; eraser previews show the footprint in pigment, not a live erase demonstration.

Pressure response requires pen pressure input. A mouse reports full pressure; automatic mouse endpoint taper is not implemented. Brush edits are session-only and selecting a preset resets its values. Saved custom brushes, directional bristles, tilt, smudge, wet mixing and imported brush textures remain future work.

Automated tests cover spacing consistency, edge coverage, pressure controls, deterministic texture, existing brush behavior and editor regressions. Drawing feel and device performance still require hands-on Windows/tablet testing.
