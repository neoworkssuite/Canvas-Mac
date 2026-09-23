# NeoCanvas Curated Showcase and Detailed Manual Pack

**Date:** 2026-09-23  
**Branch:** `ipad-gestures-phase1`  
**Status:** Approved design

## Purpose

Create a second automated NeoCanvas media pack that makes the detailed manual more useful and gives the website credible, attractive product imagery. The pack will use original NeoWorks-style artwork to demonstrate populated layers, selections, transformations, Liquify, object arrangement, and export dialogs in the real iPad app.

The result must remain honest: instructional images are real app screenshots, generated source components are identified as such, and promotional copy does not claim that imported components were hand-painted entirely inside NeoCanvas.

## Scope

The work adds:

- Four original showcase compositions in the supplied neon-glass NeoWorks visual language.
- Four genuine, editable NeoCanvas showcase documents with meaningful layer structures.
- Deterministic test-only launch scenes for advanced manual screenshots.
- A second iPad UI-test screenshot suite alongside the existing ten-image manual pack.
- Clean manual screenshots, finished artwork images, and branded marketing variants.
- A manifest, captions, alt text, contact sheets, and automated pack validation.

The work does not redesign the application, replace the repository, alter the branch architecture, import competitor artwork, or silently ship test fixtures in customers' galleries.

## Source Material and Rights

The user supplied nine NeoWorks Suite icons as visual references and campaign assets:

- NeoCanvas/canvas
- Calendar
- Dark Room
- Database
- Design
- PDF
- Photo
- Sheets
- Writer

These source files remain untouched. Approved copies will live in a dedicated showcase source directory with a provenance record. The Design icon is the principal creative-style reference; the NeoCanvas icon is the primary product badge; the other icons may appear as a restrained suite-family strip in marketing-only outputs.

No competitor screenshots, branded characters, or third-party artwork will be included.

## Visual Direction

All four compositions share the NeoWorks visual system:

- luminous cyan, electric blue, violet, magenta, and selective warm accents;
- glossy or glass-like highlights used with restraint;
- deep backgrounds that keep editing controls legible;
- high-contrast silhouettes and clear focal points;
- enough tonal separation for visible selections and transform handles;
- no baked-in promotional text where editable NeoCanvas text or object layers can be used.

Generated raster components are treated as source material. Native NeoCanvas edits, layer organization, selections, transformations, Liquify operations, arrangements, and exports remain genuine app behaviour.

## Showcase Documents

### 1. Neon Metropolis

A cinematic futuristic city poster with luminous architecture and a silhouetted creative figure.

Expected layer structure:

1. Sky Gradient
2. Distant Atmosphere
3. Distant City
4. Main Buildings
5. Neon Signs
6. Creative Figure
7. Foreground
8. Cyan Light
9. Magenta Light
10. Title
11. Colour Grade

Primary demonstrations: populated layers and groups, blend modes, freehand and rectangular selection, scale, rotate, and reposition transformations.

Marketing line: **Build ideas layer by layer.**

### 2. Chromaflow Creature

A friendly expressive creature formed from flowing cyan, violet, pink, and orange paint.

Expected layer structure:

1. Background
2. Ground Glow
3. Body Base
4. Face
5. Cyan Ribbon
6. Magenta Ribbon
7. Orange Ribbon
8. Shadows
9. Highlights
10. Paint Splashes
11. Surface Texture

Primary demonstrations: Liquify push, twirl, and pinch; expressive brushwork; colour selection; and an honest before/after state.

Marketing line: **Shape colour without limits.**

### 3. Cosmic Muse

A space-inspired portrait surrounded by rings, stars, and illuminated particles.

Expected layer structure:

1. Deep Space
2. Nebula
3. Portrait Base
4. Portrait Shadows
5. Skin Lighting
6. Hair
7. Orbital Rings
8. Stars
9. Particles
10. Rim Light
11. Colour Grade

Primary demonstrations: controlled opacity, shading, effects, groups, and layered lighting. Include a mask demonstration only if the current production UI exposes mask creation and editing during implementation verification; otherwise omit that supporting capture rather than simulate the capability.

Marketing line: **Turn imagination into atmosphere.**

### 4. NeoWorks Launch Poster

A clean promotional composition featuring NeoCanvas as the hero and the supplied suite icons as supporting products.

Expected layer structure:

1. Background
2. Perspective Grid
3. NeoCanvas Hero Icon
4. Suite Icon Group
5. Product Glow
6. Headline
7. Feature Labels
8. Call-to-Action Area
9. Finishing Grade

Primary demonstrations: multi-object selection, duplication, alignment, stack order, resizing, and export dialogs.

Marketing line: **Create with NeoCanvas. Do more with NeoWorks Suite.**

## Architecture

### Showcase source assets

Original source components and approved icon copies live outside production resources unless a later product decision explicitly promotes them into the shipped app. Transparent components are named by composition and semantic role.

Each composition has a small metadata description containing its title, component order, intended layer names, feature coverage, caption, and provenance. This metadata is the input to fixture construction and packaging; screenshot names are not duplicated across unrelated scripts.

### Fixture builder

A focused fixture builder constructs real `CanvasDocument` content using existing document, layer, renderer, and package APIs. It creates 8–15 meaningful named layers per document, preserves a stable layer order, and configures opacity, visibility, grouping, and blend behaviour supported by the current model.

Fixture generation must be deterministic. The same source revision produces the same document structure and visual result. Large opaque binary fixtures should not become the primary source of truth when they can be rebuilt from versioned components and metadata.

### Test-only showcase launch mode

The iPad application accepts a private launch argument only in the automated test context. The argument selects a showcase document and an optional scene state. Supported states include:

- completed artwork;
- Layers panel open with a known active layer;
- visible selection;
- active transform session;
- Liquify panel and controlled before/after states;
- object arrangement state;
- export dialog.

The launch path uses an isolated test library and cannot add showcase documents to a customer's normal gallery. Ordinary launches behave exactly as before.

### Screenshot automation

The existing `NeoCanvasManualScreenshotTests` coverage remains intact. A separate showcase test class or clearly separated test method captures the new pack. Accessibility identifiers and state readiness checks replace arbitrary waits wherever practical.

Screenshots use stable descriptive filenames prefixed by composition and feature. The test retains attachments with `keepAlways`, allowing the existing workflow to export them from the XCTest result bundle.

### Packaging

The packager exports two collections:

- `manual/`: clean, full-resolution real-app screenshots without advertising overlays;
- `marketing/`: completed artwork and campaign-ready compositions derived from approved captures and source art.

It also produces:

- `manifest.json` with dimensions, source revision, device, artwork, feature, caption, alt text, and provenance;
- an HTML contact sheet;
- a concise usage guide distinguishing manual, website, and promotional assets.

The existing manual pack remains independently downloadable. The new showcase pack may be published as a separate workflow artifact or a clearly named sibling directory, avoiding breaking consumers of the original pack.

## Screenshot Inventory

The target is 20–24 new iPad screenshots:

- Four completed artwork views.
- Four populated Layers views.
- Two selection views.
- Three transformation/arrangement views.
- Four Liquify views covering controls plus before/after results.
- Three export-dialog views covering the supported formats and options chosen during implementation.
- Up to four focused supporting views for masks, blend/effects controls, colour, or gallery presentation where they add instructional value.

The implementation plan may adjust the supporting views to match verified UI capabilities, but it must preserve direct coverage of layers, selections, transformations, Liquify, and export.

## Manual and Marketing Rules

- Manual captures contain no added advertising overlay.
- Marketing variants may include NeoCanvas branding and a restrained NeoWorks Suite strip.
- Product claims must correspond to visible, working app behaviour.
- Suggested source wording is **Original artwork assembled and edited in NeoCanvas.**
- A promotional asset may use **Created in NeoCanvas** only when the depicted editing and final composition genuinely occurred in the application.
- Alt text describes the artwork and the visible NeoCanvas feature rather than repeating marketing copy.
- Screenshots must not expose simulator chrome, test controls, internal file paths, or private launch arguments.

## Error Handling and Isolation

- Missing source components fail fixture generation with the artwork and component name.
- Unsupported layer configuration fails before UI capture rather than silently flattening the document.
- Missing accessibility targets fail the individual UI test with the expected identifier.
- Missing screenshots, wrong dimensions, duplicate filenames, or unreadable PNGs fail packaging.
- Marketing generation failure does not delete or overwrite successful clean manual captures.
- All generated outputs use fresh versioned directories or replace only known automation staging files.
- Supplied originals are never overwritten.

## Testing and Validation

### Unit and integration coverage

- Fixture metadata validation.
- Stable document/layer ordering and names.
- Document package round-trip for all four fixtures.
- Test-only launch argument parsing and production-launch isolation.
- Screenshot inventory, name normalization, and dimension validation.
- Manifest captions, alt text, and provenance completeness.

### UI automation

- Each requested scene reaches a verified ready state.
- Selection and transform affordances are visibly active where expected.
- Liquify before and after captures differ while retaining the intended artwork.
- Export dialogs show the real supported UI and do not fabricate unavailable formats.
- Existing ten-image manual screenshot tests continue to pass.

### Visual quality review

Every generated source image, constructed artwork, manual screenshot, marketing image, and contact sheet receives visual inspection. Review checks:

- no malformed anatomy or unintended pseudo-text;
- no clipped artwork, panels, handles, or dialogs;
- readable controls and useful zoom level;
- consistent NeoWorks styling without overwhelming the NeoCanvas interface;
- meaningful visible layer names;
- genuine differences between feature states;
- correct branding and no accidental third-party marks.

## Deliverables

Completion requires:

- four original source compositions and their separated components;
- four editable NeoCanvas showcase documents;
- approximately 20–24 new full-resolution iPad screenshots;
- four finished-art gallery/website images;
- four promotional layouts;
- contact sheets, manifest, usage guide, captions, and alt text;
- automated tests and workflow support;
- a successful downloadable CI artifact produced from the current live branch.

## Completion Criteria

The work is complete when:

1. All four showcase documents open and remain editable in NeoCanvas.
2. Each document contains meaningful populated layers matching its composition.
3. The required advanced-feature screenshots come from real deterministic app states.
4. Manual and marketing outputs are visibly and structurally separated.
5. Provenance and claims accurately describe how the artwork was made.
6. Existing manual screenshot coverage and normal application launch behaviour remain unchanged.
7. Relevant unit, integration, packaging, and iPad UI tests pass.
8. CI produces the full downloadable pack and every output passes visual review.
