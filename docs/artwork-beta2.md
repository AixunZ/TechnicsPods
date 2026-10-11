# AZ100 artwork provenance and extraction

The supplied Technics Audio Connect APK is version 5.0.1 (versionCode 104), and its resource
table contains explicit AZ100 color/model names. This beta bundles the black (`blk`), blue
(`a`), gold (`gld`), silver (`slv`) and violet (`v`) case, left and right earbud resources
after visual review. Each color has a separate manifest below `az100-art/{color}/`; the root
`az100-art/manifest.xml` remains the black compatibility set. The manifests record the
package, version, signer, original resource names and SHA-256 digests. The selected color is
still a runtime choice owned by the application. Automatic color identification and manual
color/import paths are implemented, but their current device-page entry points must not be
treated as usable: on 2026-10-11 the user confirmed that **选择 AZ100**, **耳机颜色** and
**设备图片** still do nothing when tapped. The previous click fix did not resolve the
physical-device issue, and further repair is paused at the user's request.

## Current physical-device status — 2026-10-11

- The user confirmed that the Settings Bluetooth detail image now replaces successfully.
- MiLink/control-center artwork replacement was confirmed earlier; its image remains soft.
  The official case resource is only 176 x 130, so no sharpening, upscaling or source replacement
  has been applied.
- The five official color sets are bundled. The APK-derived `GET_DEVICE_COLOR` (`0x0002`)
  parser, AZ100 model IDs `0x00A0..0x00A3`, persistence and automatic artwork selection are
  implemented; automatic identification has not received a separate physical-device acceptance.
  See [OFFICIAL_COLOR_FINDINGS.md](OFFICIAL_COLOR_FINDINGS.md) for the evidence.
- Multi-device switching, host asynchronous write-back, page reuse, restoration on exit and
  all five color choices have implementation coverage, but are not fully accepted on-device.

These confirmations cover the user's current ROM and tested device; they are not a claim that
all Settings layouts or lifecycle cases are supported.

## Index supplied APKs

Run the tool with the base and every split from the same official installation:

```powershell
./tools/index-official-art.ps1 -Apk @('D:/input/base.apk', 'D:/input/split_config.xxhdpi.apk')
```

The ignored `.diagnostics/artwork-index` output contains package versions, verified signer
digests, APK SHA-256, the complete aapt2 resource table, raster/XML candidates with exact
resource IDs and zip paths, aliases and non-resource assets. Obfuscated resource names are
indexed exactly like readable names. An asset or nested archive is listed but is not guessed
to be an AZ100 image.

Review model/color identity visually and against the official app's model lookup. Create an
explicit selection, using the exact configuration and path from `index.xml`:

```xml
<az100-selection model="EAH-AZ100" color="black" reviewed="true">
  <slot name="main" apkIndex="1" resource="drawable/REVIEWED_RESOURCE"
        entry="res/drawable-nodpi/REVIEWED_FILE.png" configuration="" />
</az100-selection>
```

```powershell
./tools/index-official-art.ps1 -Apk @('D:/input/base.apk', 'D:/input/split_config.xxhdpi.apk') -Selection D:/input/selection.xml
```

This produces `official-mapping.xml` and the selected raw files. The import API
`Az100ArtStore.importOfficialManifest(context, uri)` checks the exact installed package,
version code, current signing certificate, selected color and resource digest before rendering.
Android Resources handles installed base/splits, aliases and Vector/XML references. A device
whose selected resource density/configuration differs from the reviewed digest is rejected,
requiring a matching reviewed variant. Runtime imports never guess by filename or size.

The reviewed AZ100 choices are checked in as deterministic selections:
`tools/official-az100-black-selection.xml`, `official-az100-blue-selection.xml`,
`official-az100-gold-selection.xml`, `official-az100-silver-selection.xml` and
`official-az100-violet-selection.xml`. `tools/official-az100-color-index.xml`
is the compact source index for all fifteen selected APK resources and their source digests.

## Cache and IPC

Artwork sets commit together using a new directory and a synchronous active-set switch.
Each slot is normalized to PNG, bounded to 2048 pixels and 8 MiB, hashed and described in a
schema 1 XML manifest. Fields include model, color, source, official version/signature,
original resource/hash, normalized file/hash/dimensions and selected device address. A
failed import retains the previous complete set. Manual imports populate only `main`;
missing left/right/case images stay missing, never copied or stretched from a composite.

`content://dev.technics.pods.art/product` remains the main image. A single `slot` query may
be `main`, `left`, `right` or `case`. Unknown/duplicate queries and write modes are denied.
The existing calling-package/UID whitelist is retained; slot data and Provider caches
invalidate on artwork version or selected device changes.

MiLink replacements remain restricted to the previously verified image IDs and selected
AZ100 identity. Settings replacement binds the selected AZ100 identity to its
`MiuiHeadsetFragment` subtree and chooses one visible, already-sized, large header image.
The current per-ROM resource-ID set is empty: the conservative candidate selector is active,
and pages with no suitable image or similarly sized competing candidates retain their host
image. Original drawable/scale state and Provider invalidation support restoration and
refresh; image setter guards apply only to bound views.

When diagnostic probes are enabled, Settings logs up to 64 ImageView resource IDs and the
host/callback details. The supplied `technics-az100-capture-20261011-003021.jsonl` contains
transport/system probes but no `com.android.settings` or `TechnicsPods-Art` entries; it did
not establish a stable resource ID. Replacement is now locally confirmed by the user's
device test, while an LSPosed art log remains useful for pinning a future ROM-specific ID.

## Bundling reviewed artwork

The current build uses the reviewed raster resources directly as bounded WebP assets. If a
future build changes density or color, render XML/vector resources on Android, inspect all
images, add bounded assets to `app/src/main/assets/az100-art`, and update the verified
`bundled` manifest with `file`, `sha256`, dimensions and preserved official provenance per slot.
Add the package version, signer/resource digests, selected color and third-party provenance
to NOTICE. Official imagery is not covered by the project's GPL code license.

## Alpha bounds review

All five case resources are 176 x 130 and all ten earbud resources are 274 x 284.
The case alpha bounds remain centered at approximately x=16..160 and y=12..118
(threshold 128) for every color; they are not truncated in the official APK. The
earbud files retain the same 274 x 284 canvas, with visible art ending near y=214
and a transparent/shadow tail to y=284. The apparent case height difference in a
three-card layout therefore comes from mixing the case's 176 x 130 aspect ratio
with the earbuds' 274 x 284 ratio; callers must reserve a common image slot and
use `ContentScale.Fit`, never crop or stretch the assets.
