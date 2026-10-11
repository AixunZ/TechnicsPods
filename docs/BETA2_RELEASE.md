# 0.10.0-beta.2 candidate

Version code: 31. Upgrade uses the existing local Android debug/test identity. Maintainer signing and upload remain separate.

## Changes

- Genuine Miuix Compose Home / Device / About tabs, with system/light/dark appearance. Home contains module and connection summaries, Device contains AZ100 state and artwork, and About contains module settings, permissions, diagnostics and credits.
- One lifecycle-aware UI state source, retained tab/selection state, per-field freshness and confirmed noise-level presentation. No daily mode controls were added to the module UI.
- Reviewed artwork manifest, main/left/right/case slots, structured import results and restricted read-only Provider access. Installed official resources are identified from resource mappings rather than filenames or file size. The package includes black, blue, gold, silver and violet AZ100 finish sets.
- AZ100 model/color protocol reads implement automatic selection of the corresponding bundled finish. Automatic color recognition has not been separately accepted on hardware. Manual override and import actions exist in code, but the Device page entry rows currently do not respond to taps, so these user paths remain unavailable. Provider notifications refresh the MiLink artwork hook.
- Settings click feedback is triggered only by an accepted real request, once per request ID, on the current attached view. It respects global haptic settings.
- MiLink artwork replacement uses the verified resource IDs. Settings artwork replacement uses a conservative AZ100 identity-bound header candidate, with original-image fallback when the candidate is ambiguous. The user confirmed both system entry images are replaced.

## Recorded device feedback — 2026-10-11

The user's latest test confirms Settings Bluetooth device-detail artwork replacement. Earlier feedback confirmed control and state updates in both system entries, Settings haptic feedback, the status-bar headset icon and MiLink/control-center artwork replacement. These are user-reported, partial device acceptance results; they are not a complete checklist run or an adb/instrumentation execution.

The Device page rows “选择 AZ100”, “耳机颜色” and “设备图片” still do not respond to taps after the attempted click-handler change. The earlier claim that these entries were fixed was not supported by the user's retest. The issue remains unresolved, and further repair is paused at the user's request. Automatic color recognition has been implemented but has not been separately verified by the user; manual color selection, device selection and image-source actions cannot currently be accepted through these rows.

MiLink/control-center artwork appears soft. The official case source is 176×130 pixels, with transparent padding and soft shadows; no sharpening or artificial enlargement was applied. This remains a known visual limitation.

## Compatibility

| Combination | Evidence | Beta.2 status |
| --- | --- | --- |
| Xiaomi nezha / Android 17 / HyperOS OS4.0.0.17.XPACNXM / AZ100 JNZ1ZE 1.040 | User reports two-entry control/state refresh, Settings haptics, status icon and both system artwork replacements working | Partial device acceptance; three Device page action rows remain unresponsive |
| Audio Connect 5.0.1 (versionCode 104) | Supplied APK, signer and resource hash review | Black, blue, gold, silver and violet AZ100 case/left/right bundled |
| Settings product artwork | User confirmed replacement on 2026-10-11; conservative AZ100 identity-bound header selection | Working on the reported ROM; page reuse/ambiguity/restoration cases still require separate acceptance |
| Device selection, manual color and image-source rows | User retest on 2026-10-11 | Unresolved: taps produce no response; further repair paused by request |
| Other ROMs, official app versions or firmware | No verified evidence | Affected hooks fall through/report unavailable |

## Remaining inputs and device checks

The supplied official APK has been indexed and the reviewed five-color resource mappings are bundled. The extraction/index tooling and installed-resource importer remain available; missing mappings are reported, never guessed. Settings artwork uses a single large, visible header candidate only after the fragment exposes the selected AZ100 address; ambiguous or unmeasured layouts keep the host image.

There is no connected adb device. The user has confirmed basic haptics and both system artwork replacements, but this does not cover their complete edge-case checklists. UI instrumentation and screenshots, same-key upgrade installation, boot/background recovery, official-app coexistence, unknown-ROM fallback and the remaining cases in PUBLIC_BETA_ACCEPTANCE.md still require hardware evidence. Build success is not a physical acceptance result.

Build/package values and the partial user report are recorded in `dist/0.10.0-beta.2/delivery-manifest.json` and `SHA256SUMS.txt`. This sync refreshes the working-tree source ZIP and metadata, retaining existing APK bytes. The Debug logging guard and packaged instrumentation freshness differ from current source; see [delivery boundaries](BETA_DELIVERY.md) and [current handoff](CURRENT_SESSION_HANDOFF.md).

Local validation from the existing build: 124 unit tests passed; Debug and minified Release built, and Android instrumentation source compiled. Lint has zero errors and 60 warnings. UI and artwork instrumentation cases, including the added click case, are compiled but unexecuted; the retained test APK predates that click case. They must not be described as passing device click tests. The UI cases generate 320dp/font-scale-2 light/dark PNGs with nonblank pixel checks when run on a device. This status-only sync does not change application code, versionCode or packaged APKs.
