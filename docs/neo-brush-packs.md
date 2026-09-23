# NeoCanvas brush packs

NeoCanvas supports individual `.neobrush` files and installable `.neobrushpack` collections. Packs contain declarative brush settings and bounded grayscale stamp images only; they cannot contain scripts, native plug-ins, shaders, or executable code.

## Install a pack on iPad

1. Download the `.neobrushpack` file in Safari and save it to Files.
2. Tap the file and choose **Open in NeoCanvas**, or open NeoCanvas, choose **Brushes → + → Import brush pack**, and select it from Files.
3. Review the pack name, author, version, brush count, compatibility, licence, preview, and provenance badge.
4. Choose **Install**. A newer version of the same pack offers **Replace** and keeps favourites whose stable brush IDs remain present.

Imported packs show an **Imported Pack** badge. Packs distributed by NeoWorks show **Official NeoWorks** after their manifest and checksums validate.

## Use, share, update, or remove

- Installed brushes appear in a category named after their pack.
- Use the pack menu to view information, export/share the original `.neobrushpack`, or remove it.
- Removing a pack never changes existing raster artwork. If its brush is active, NeoCanvas returns to Graphite Pencil.
- Export sends the pack through the standard iPad share sheet; Files, AirDrop, Mail, and other available destinations may appear.

## Supported content and limits

- Up to 100 brushes and 250 archive entries per pack.
- Up to 25 MiB compressed and 75 MiB unpacked.
- Shape and grain assets must be grayscale PNG files from 8 to 512 pixels per side, no more than 512 KiB each.
- NeoCanvas does not claim compatibility with Procreate `.brush`/`.brushset` or Adobe `.abr` files.

## Troubleshooting

If a pack is rejected, download it again from its original source. NeoCanvas rejects damaged checksums, unsafe paths, undeclared files, unsupported compression, oversized content, and packs requiring a newer app version. An author conflict means another installed pack is already using that pack ID; remove the conflicting pack only if you trust the replacement.

The free **Neo Nature Studio** pack contains 18 original landscape brushes and is available from [neoworkssuite.com/neocanvas](https://neoworkssuite.com/neocanvas).
