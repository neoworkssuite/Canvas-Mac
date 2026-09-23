# NeoCanvas Brush Pack Engine and Neo Nature Studio Design

Date: 23 September 2026  
Status: Proposed for implementation  
Target branch: `ipad-gestures-phase1`

## Purpose

NeoCanvas already supports versioned data-only `.neobrush` definitions, local custom brushes, and a broad built-in catalogue. The present procedural presets vary through a small set of shared tip algorithms and normalized dynamics, so brushes in the same family can feel too similar. This work will add genuinely distinct brush marks, a safe distributable pack format, native iPad import and export, and one production-quality test pack named **Neo Nature Studio**.

Success means an artist can download a pack in Safari or receive it through Files, AirDrop, or the Share Sheet; inspect it; install it as a named collection; and immediately see clearly different, responsive brushes. Official and third-party packs must remain data-only, bounded, deterministic, and unable to execute code.

## Scope

The first release includes:

- Custom grayscale shape and grain assets.
- Direction-following and randomized stamp orientation.
- Independent shape scale, stamp density, scatter, flow, and colour variation.
- Pressure control for size, opacity, scatter, and density.
- A versioned `.neobrushpack` container with validation and conflict handling.
- iPad Files/Downloads, Open In, AirDrop, and Share Sheet integration.
- Pack installation, removal, export, and local persistence.
- Official-pack verification metadata.
- The original 18-brush Neo Nature Studio test pack.
- Automated codec, security, renderer, persistence, UI-state, and iPad validation.

The first release does not include:

- Importing proprietary Procreate `.brush`/`.brushset` formats.
- Importing Adobe `.abr` files.
- A public creator marketplace, accounts, ratings, or user uploads.
- Paid packs or StoreKit purchasing.
- Executable plug-ins, shaders, scripts, or downloaded Kotlin/native code.
- Cloud synchronization.

## User Experience

### Import from NeoCanvas

The Brush Library gains a compact `+` menu containing:

- Create Brush
- Import Brush or Pack
- Create Pack from Custom Brushes
- Export Selected Brush

`Import Brush or Pack` opens the iPad document picker for `.neobrush` and `.neobrushpack`. A validated pack opens an installation sheet showing its cover, name, author, version, brush count, minimum NeoCanvas version, licence, official/imported status, and brush previews.

The sheet offers `Install`, `Replace`, or `Cancel` as appropriate. A new pack installs as its own category. Reimporting the same pack and version is a no-op with a clear message. A newer version offers Replace while preserving favourites and recent references by stable brush ID. A pack with the same ID but a different author/signature is treated as a conflict and offered as a separately named import rather than silently replacing content.

### Import from Files, Downloads, AirDrop, and Share Sheet

NeoCanvas declares exported Uniform Type Identifiers for its formats:

- `com.neoworksuite.neocanvas.brush` → `.neobrush`
- `com.neoworksuite.neocanvas.brushpack` → `.neobrushpack`

Both conform to `public.data` and `public.content`. Tapping either file routes it to the same validation and installation sheet used by in-app import. Failed validation never changes the brush library and presents a concise, actionable error.

### Pack management

Installed packs appear alongside built-in categories. A pack context menu provides:

- Pack information
- Export/Share Pack
- Remove Pack
- Reinstall or Replace when importing an update

Removal deletes only that pack's brushes and assets. Built-in brushes cannot be removed. Removing a selected brush falls back to the built-in Graphite Pencil. Favourites and recent entries referring to removed brushes are cleaned automatically.

### Trust presentation

Official packs signed by NeoWorks display `Official NeoWorks`. Unsigned packs display `Imported Pack`. The badge describes provenance, not safety: every pack receives the same strict parser and resource limits.

## Brush Engine V2

### Compatibility model

`BrushDefinition` remains readable at version 1. Version 2 adds an optional `stamp` block. A V1 brush renders exactly as it does now. Missing V2 values use conservative defaults, so older custom brushes and artwork remain valid.

### Shape and grain assets

A V2 brush may reference:

- One grayscale shape PNG defining stamp coverage.
- One optional grayscale grain PNG modulating stamp coverage.

Assets are addressed by pack-relative IDs, not arbitrary paths or URLs. The decoder accepts only non-interlaced 8-bit PNG data after platform-neutral decoding. Images are converted to bounded grayscale buffers before entering the renderer.

Limits per asset:

- Minimum: 8 × 8 pixels.
- Maximum: 512 × 512 pixels.
- Maximum decoded pixels: 262,144.
- Maximum compressed file size: 512 KiB.
- Maximum two raster assets per brush.

The runtime caches decoded, normalized stamp masks by pack ID, pack version, and asset checksum. Cache eviction is least-recently-used with a fixed memory ceiling. A failed or missing asset yields a visible import error, never a silent fallback that changes the advertised brush.

### Stamp dynamics

The V2 stamp block contains bounded values for:

- `angleMode`: fixed, stroke direction, randomized, or direction plus jitter.
- `angleDegrees` and `angleJitter`.
- Independent horizontal and vertical scale.
- Spacing as a proportion of current diameter.
- Scatter along and across the stroke.
- Stamp count from one to eight per sample.
- Stamp-count jitter.
- Grain scale and grain movement mode.
- Hue, saturation, and brightness jitter within restrained limits.
- Pressure response for scatter and stamp count.
- Start and end taper.

All random variation derives from stable stroke and stamp coordinates so preview, commit, undo/replay, symmetry, and editable-stroke replay are deterministic.

### Rendering and performance

The renderer introduces a shared stamp-mask sampler rather than adding per-pack rendering code. It computes a transformed bounding rectangle, rejects transparent mask pixels early, and uses the existing sparse tile system and blend path.

Work is bounded by:

- Maximum eight sub-stamps per interpolated sample.
- Adaptive sample spacing for large or complex textured brushes.
- A per-segment stamp budget that preserves the final endpoint.
- Nearest/bilinear mask sampling selected by brush size.
- Cached decoded masks and precomputed non-empty row bounds.
- Precision-category exclusion so pencils and pens retain configured spacing.

If an imported definition exceeds a limit, installation fails. The renderer does not silently reduce imported values because that would make previews and shared artwork inconsistent.

## Pack Format

`.neobrushpack` is a ZIP container with a fixed internal layout:

```text
manifest.json
brushes/<brush-id>.neobrush
assets/<asset-id>.png
previews/<brush-id>.png
artwork/cover.png
artwork/example.png
signature/manifest.sha256
signature/neoworks.sig        # official packs only
```

### Manifest

The UTF-8 JSON manifest contains:

- Schema version.
- Stable pack ID.
- Pack version using semantic versioning.
- Display name, summary, author, and website.
- Licence identifier and human-readable licence text.
- Minimum NeoCanvas version.
- Ordered brush IDs and category metadata.
- Asset and preview paths with SHA-256 hashes.
- Optional official signature metadata.

Unknown required fields or unsupported schema versions fail closed. Unknown optional metadata may be ignored.

### Archive security limits

- Maximum archive size: 25 MiB.
- Maximum unpacked size: 75 MiB.
- Maximum 100 brushes.
- Maximum 250 archive entries.
- No absolute paths, parent traversal, links, nested archives, encrypted entries, or duplicate normalized paths.
- Only declared manifest, brush, PNG, licence, checksum, and signature entries.
- All hashes are checked before installation.
- Installation parses into a temporary in-memory model and commits atomically only after every item validates.

Official signatures use a public verification key embedded in NeoCanvas. Signature failure removes official status and fails installation when the manifest claims to be official. Community packs do not require a signature.

## Persistence Model

The current custom-brush snapshot evolves into a versioned brush-library store containing:

- User-created loose brushes.
- Installed pack records.
- Pack-owned brush definitions.
- Asset files stored inside NeoCanvas's application-support container.
- Favourites and recent stable IDs.

Pack installation uses staging and atomic replacement. The original snapshot codec remains readable and migrates existing custom brushes into a `Custom` collection on first save. Migration is idempotent.

Artwork does not embed entire packs in this release. Editable strokes continue storing their resolved brush definition. If a pack is later removed, existing raster artwork remains unchanged and editable strokes retain enough definition to replay when their referenced asset is still present. Asset removal is therefore reference-aware: assets used by editable project data remain retained until no project references them.

## Neo Nature Studio Pack

The pack contains 18 original brushes with intentionally different silhouettes, grains, dynamics, and intended scale ranges.

### Trees

1. Oak Canopy — multi-stamp rounded leaf masses with edge gaps.
2. Distant Tree Line — direction-following grouped silhouettes for horizons.
3. Pine Tree Builder — tapered stacked bough shapes controlled by pressure.
4. Pine Bough — directional needle fan for close detail.
5. Branch and Twig — tapered directional fork texture.
6. Rough Bark — elongated grain with broken vertical ridges.

### Foliage

7. Dense Leaf Cluster — several overlapping leaf stamps per sample.
8. Fine Leaves — sparse small pointed leaves aligned to motion.
9. Broad Tropical Leaves — large curved leaf silhouettes with low density.
10. Hedge Builder — compact irregular mass with restrained colour variation.
11. Fern — repeated alternating frond shape aligned to the stroke.
12. Moss and Ground Cover — fine clustered grain with edge breakup.

### Landscape

13. Wild Grass — varied directional blades with pressure density.
14. Meadow Grass — softer short blades for broad coverage.
15. Rock and Gravel — angular scattered stamps across multiple sizes.
16. Cloud Builder — soft irregular mass with controlled overlap.
17. Mountain Texture — directional broken plane grain.
18. Water Reflection — horizontal broken marks with flow variation.

The pack includes a 1600 × 1200 cover, one 2048 × 1536 example landscape, and a generated preview for every brush. Artwork and assets must be original NeoWorks material and recorded in the pack licence.

## Website Distribution

Version 1 distribution is free:

- `neoworkssuite.com/neocanvas/brushes` presents the pack cover, example artwork, compatibility, version, size, licence, and a direct `.neobrushpack` download.
- Safari downloads to the standard Downloads location; the page explains `Download → Open in Files → tap the pack → Open in NeoCanvas`.
- The application may open the public catalogue page but does not contain external checkout or paid-unlock behaviour.

Paid packs and StoreKit are explicitly deferred. If later introduced, digital packs offered inside the iPad app use non-consumable In-App Purchases with Restore Purchases and App Store review metadata.

## Colour Studio Upgrade

The existing Disc, Classic, Harmony, Value, and Palettes modes remain, but the primary picker becomes a compact artist-focused control:

- A larger centred disc uses an outer hue ring and inner saturation/brightness field.
- Pinching expands the inner field for fine control; leaving the panel restores the normal scale.
- Double-tap snaps to white, black, mid-grey, full saturation, or half saturation according to the nearest snap target.
- The active reticle compares the hovered colour with the previous colour while dragging.
- Harmony draws its related reticles directly on the wheel and lets any reticle become primary.
- The last Colour Studio mode is persisted locally.
- Existing RGB, HSB, hexadecimal, palette, recent-colour, primary/secondary, and eyedropper workflows remain available.
- Panel spacing and height are reduced without shrinking touch targets below 44 points.

The toolbar trigger becomes an original NeoCanvas colour-ring glyph with the active colour in its centre and a smaller overlapping secondary-colour swatch. It must remain recognisable in grayscale/high-contrast states and must not resemble a telephone handset or copy another application's artwork.

## Error Handling

User-visible failures distinguish:

- Unsupported pack or brush version.
- NeoCanvas update required.
- Damaged archive or checksum mismatch.
- Invalid image asset.
- Unsafe archive path or unsupported entry.
- Pack too large or too many brushes.
- Stable-ID/signature conflict.
- Insufficient local storage.

Parsing errors are sanitized and do not expose device paths. A failed import leaves the existing library byte-for-byte unchanged.

## Testing and Release Gates

### Unit and property tests

- V1 brush decode and rendering compatibility.
- V2 brush round-trip and default handling.
- Pack manifest round-trip.
- Archive traversal, duplicate path, size, count, hash, image, and version rejection.
- Atomic install, replace, conflict, remove, and migration behavior.
- Stable favourites and recent IDs after updates.
- Deterministic shape/grain rendering across preview, commit, replay, and symmetry.
- Distinct output for all 18 pack brushes using image-distance thresholds.
- Pressure, direction, taper, scatter, density, and colour-jitter behavior.
- Work-budget tests for large multi-stamp brushes.

### Integration and visual tests

- iPad document picker import.
- Open In/file-association routing.
- Install sheet and official/imported badges.
- Pack appears as a category, survives restart, exports, replaces, and removes.
- Colour disc pinch expansion, double-tap snapping, harmony reticles, mode persistence, and primary/secondary toolbar glyph.
- Example canvas exercises all 18 brushes.
- Simulator screenshots for library, install sheet, installed pack, and example artwork.
- Physical-device unsigned build and full existing iPad smoke suite.

### Acceptance criteria

- All 18 brushes are visually distinguishable at their documented working sizes.
- A 2,000-pixel continuous stroke with the heaviest pack brush remains within the renderer's work budget and does not omit the endpoint.
- Precision Pencil and Pen output remains unchanged.
- Existing V1 custom brushes load without user action.
- Malformed packs cannot partially install or write outside NeoCanvas storage.
- The full shared test suite, simulator build, physical-iPad build, smoke test, and extended visual validation pass.

## Delivery Sequence

1. V2 definitions, asset decoding, deterministic renderer, and performance tests.
2. Pack codec, security validation, persistence migration, and lifecycle tests.
3. iPad import/export actions, custom UTIs, install sheet, and pack management UI.
4. Original Neo Nature Studio assets, brush tuning, previews, and example artwork.
5. Website download page and documentation.
6. Full iPad validation and downloadable test IPA/artifacts.
