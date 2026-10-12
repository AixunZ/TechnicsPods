# TechnicsPods Project Status

Last updated: 2026-10-12 (device identity and latest acceptance records corrected)

## Beta.3 public test release

Candidate: `0.10.0-beta.3` / `versionCode 32`. This is the first public test release and is published as a GitHub **Pre-release**. Developer: **AixunZ**. The public package set was generated with the maintainer-signed release APK, matching source archive and mapping, `SHA256SUMS.txt`, and `delivery-manifest.json`; the five fixed attachments are published at the release page. The verified release certificate SHA-256 is `97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`, and the release APK SHA-256 is `c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`. Private signing material stays outside the repository.

The three Device-page rows **选择 AZ100**, **耳机颜色** and **设备图片**, including their main operation paths, were reported working on beta.3/32 on two devices: Xiaomi 17 Ultra (`nezha`) / HyperOS 4 and Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package. On 2026-10-12 the user clarified that `nezha` is the Xiaomi 17 Ultra, not a separate device. The latest successful retest supersedes the earlier unresponsive-row result on that same device. Android 17 and HyperOS `OS4.0.0.17.XPACNXM` remain historical captured baseline values; the latest report did not independently recheck those exact versions. No adb device was available for this release workflow, so instrumentation execution and complete cross-ROM hardware acceptance are not claimed.

For the public project page, the maintainer estimates that more than 500 million GPT tokens were used in development as of 2026-10-11. Models named by the maintainer include GPT-5.6-sol, GPT-6.0-Astra and GPT-6.1-sol. This is a maintainer-provided estimate and is not an OpenAI-endorsed or independently audited usage report. See [beta.3 release notes](BETA3_RELEASE.md) for release-specific limits and [beta delivery](BETA_DELIVERY.md) for artifact boundaries.

## Beta.2 update

Candidate: `0.10.0-beta.2` / versionCode 31. On 2026-10-11 the user confirmed Settings Bluetooth-detail artwork replacement on the phone. Earlier reports confirmed control/state updates in both system entries, Settings haptics, the status-bar icon and MiLink artwork replacement. Beta.2 includes genuine Miuix Compose Home / Device / About tabs, layout alignment changes, accepted-request haptics and five reviewed official artwork finishes. Java transport and system-control semantics remain the regression baseline.

The Device page rows **选择 AZ100**, **耳机颜色** and **设备图片** still do not respond to taps. The previous explicit click handling did not pass the user's retest. Device selection, manual colour changes and image import remain inaccessible through those rows. Automatic colour detection is implemented and unit-tested but not separately accepted on the phone. The user requested that further repair stop for now; this session synchronizes records and source snapshots only.

The supplied Audio Connect 5.0.1 (104) APK has been indexed and black, blue, gold, silver and violet artwork is bundled. Settings uses an identity-bound single large-header candidate with safe fallback; successful rendering does not establish exact resource IDs or every page reuse/write-back/restore case. MiLink replacement is user-confirmed but looks soft when the 176 x 130 official case image is enlarged; the source and scaling were retained.

Local evidence remains 124 passing unit tests and Lint 0 errors / 60 warnings. Compose/integration tests were compiled, not executed through adb. The latest phone report is partial user acceptance; no new ROM/package versions, tested APK variant or hash were supplied. See [current handoff](CURRENT_SESSION_HANDOFF.md), [beta.2 release notes](BETA2_RELEASE.md) and [acceptance results](PUBLIC_BETA_ACCEPTANCE.md).

## Phase 1 update

The user confirmed working Fusion Device Center and Bluetooth-detail visibility and off / ANC / ambient switching on 2026-09-27. The older statements below about unproven stock-button writes describe the August investigation, not the current baseline.

Historical beta.1: `0.10.0-beta.1` / versionCode 30. This stage centralized system control requests in the Provider runtime, capability reports and asynchronous snapshot reads. Later user reports confirmed the two system control/state paths and status-bar icon; official-app ownership and background recovery still need dedicated acceptance. See [public beta acceptance](PUBLIC_BETA_ACCEPTANCE.md).

The device/ROM/firmware listed below remain the recorded hardware baseline. Historical user confirmation covers the inner Fusion route and off / ANC / ambient mode switching. The beta source now implements connection ownership, official-app session announcements and a device/settings UI; their hardware acceptance remains pending. Settings native ANC-strength levels are deliberately unavailable because their host encoding is unverified.

## Recorded baseline and historical evidence

- Current candidate: `0.10.0-beta.3` / `versionCode 32`; historical beta.2 code 31 and beta.1 code 30
- Historical captured device baseline: Xiaomi 17 Ultra (`nezha`), Android 17, HyperOS `OS4.0.0.17.XPACNXM`; exact versions were not re-collected in the latest beta.3 report
- Earbuds: Technics EAH-AZ100, firmware `JNZ1ZE 1.040`
- Official app used for captures: Technics Audio Connect 4.4.0
- Historical route log: `LSPosed_20260831_015717.zip`; latest rendering feedback is the user's 2026-10-11 report

## Compatibility evidence

| Host | Evidence | Scope |
|---|---|---|
| Xiaomi 17 Ultra (`nezha`) / HyperOS 4 | beta.3/32 user report; historical LSPosed route logs and hardware confirmation on the same device | Three Device-page actions and their main paths working; earlier unresponsive-row result superseded. Historical evidence also covers Fusion inner route, Settings mode path and AZ100 identity/battery/state; exact current build and full lifecycle coverage not recorded |
| Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package | beta.3/32 user report | Three Device-page actions and their main paths working; exact build and full lifecycle coverage not recorded |
| `com.milink.service` `18.1.2.2.2608192125` / code `180010202` | Historical host inventory; later user control/state and artwork reports | Recorded host version; latest package version not re-collected |
| `com.android.settings` `17` / code `37` | Historical host inventory; user control/haptics reports and 2026-10-11 image success | Recorded host version; latest package version not re-collected |
| `com.xiaomi.bluetooth` `17` / code `37` | `success_half_modules.log`, Surface process ready | Recorded runtime host version; beta untested |
| `com.android.systemui` `17.03.260226.r` / code `202602260` | Historical inventory; user confirmed status-bar icon | Basic rendering confirmed; multi-headset/startup cases not fully accepted |
| `com.android.bluetooth`, `com.miui.home` | Exact package versions not established in the evidence quoted here | No package-version compatibility claim |
| Other Android/HyperOS builds | No evidence in this repository | Unknown; protective no-op/fall-through expected |

## What Is Working

### Transport and protocol

- Independent RFCOMM session uses UUID `00000000-0000-0000-0099-aabbccddeeff`.
- RACE stream reassembly, response matching, timeout, cancellation and socket cleanup are implemented.
- Confirmed reads: model ID, left/right earbud battery, cradle battery, outside-control mode and remembered levels, ambient sub-mode, adaptive ANC, firmware and relay availability.
- Outside-control encoding is confirmed: mode `0=off`, `1=noise cancelling`, `2=ambient`; ANC level is a direct `1..100` byte.
- Adaptive ANC and ambient sub-mode builders/parsers are typed and fixture-tested.
- Disconnect handling preserves last-known values and marks freshness separately; it never fabricates zero battery.

### Safe control and state

- The control client performs read current state -> typed write -> ACK -> forced read-back.
- Best-effort restoration runs after a post-write error.
- Background monitoring is explicit, foreground-service based and rate limited.
- `Az100StateCache`, `Az100StateRepository` and the restricted Provider carry partial state across processes.
- The diagnostic page exposes read-only sampling and guarded mode/level transactions; it reuses the production transaction and removes the separate fixed-75 round-trip client.

### HyperOS system integration

- `com.android.bluetooth` already receives and stores the AZ100 battery value; battery injection is unnecessary.
- `Az100HyperOsBridgeHook` maps AZ100 identity and cached battery/ANC state into the verified MiLink/HyperOS surfaces.
- `Az100SettingsBridgeHook` patches the legacy Settings headset page for AZ100 identity, connection checks, battery slots and read-only ANC/state rendering.
- Fusion Device Center routing is verified on hardware. `Az100FusionDeviceRouteHook`:
  - patches the empty headset service lookup (`protocol 393216`) for AZ100;
  - recognizes the ROM's `wc.a.q()` display config labeled `SOUND_BLUETOOTH_CONTROL`;
  - replaces the ordinary Bluetooth display config in `MainCardView.k()`;
  - waits for the asynchronous `fc.t` controller and retries `fc.t.b0()` after card creation;
  - reaches `com.miui.circulateplus.world.headset.HeadsetDetailFragment`.
- Status-bar callback discovery is exact for the recorded A2DP signature and basic rendering is user-confirmed. Profile startup reconciliation and multi-headset behavior remain pending dedicated acceptance.
- MiLink uses verified image IDs; Settings uses the identity-bound conservative large-header selector. Both image replacements are user-confirmed on the recorded phone, with further lifecycle/device-switch cases pending.
- The protocol reads the AZ100 color after model confirmation and retains automatic finish selection. Manual override and image-source paths were reported working on the two beta.3/32 devices above. Automatic colour is not separately accepted.

The final route log contains:

```text
Found named system Headset panel;factory=q;label=SOUND_BLUETOOTH_CONTROL
Routed AZ100 MainCardView to Headset display config
AZ100 Headset controller call=fc.t:b0()
Routed AZ100 MainCardView request to HeadsetContentController
```

## What Is Not Working Yet

- The Fusion Device Center outer AZ100 tile does not show the blue phone-host marker. This is a separate device-host/status model from the inner panel route.
- Both current system entry control/state paths are user-confirmed. Pending/error presentation, official ownership and cancellation/lifecycle boundary cases still need dedicated tests.
- MiLink artwork softness remains; the official case asset is only 176 x 130. No sharpening or alternate source was introduced.
- Charging flags and offline/stale presentation require more ROM-specific validation.
- Compatibility with other HyperOS builds, Android versions and AZ100 firmware versions is unknown.
- Native Settings ANC-strength level encoding is unverified; the beta exposes no invented conversion or write path. This restriction supersedes v29 level-forwarding behavior and prevents a host slider value being treated as a verified AZ100 percentage.
- Maintainer release signing, local public packaging and the GitHub Pre-release upload are complete. Optional signing environment variables and unsigned/minified test artifacts remain available for reproducibility.
- Official APK inspection establishes AZ100 model IDs `0x00A0..0x00A3` for the colour-read gate. This evidence does not imply that every general control-handshake path performs strict model-ID validation; see `OFFICIAL_COLOR_FINDINGS.md` and `CONTROL_TEST.md`.

## Fusion Route Investigation Summary

The problem was narrowed through staged LSPosed logs rather than blind method guessing:

1. Earlier builds proved the AZ100 service lookup and `fc.t` controller capture, while Fusion still inflated `BluetoothCardView`.
2. v24/v25 showed `MainCardView.k()` was hooked but `findHeadsetDisplay()` returned `null`.
3. The ROM's display factory labels were logged. The headset configuration was `SOUND_BLUETOOTH_CONTROL`, which the initial `headset/earphone/earbud` resource matcher did not recognize.
4. v26 added that exact label and successfully routed `MainCardView` to the stock headset display config.
5. The next log showed a timing gap: card creation happened before `fc.t` was captured. v27 added a bounded delayed retry for `fc.t.b0()`.
6. The final log confirmed `fc.t.b0()` and `HeadsetDetailFragment.onCreate()`. The inner Fusion entry now reaches the control page.

This fix intentionally leaves the original behavior for non-AZ100 devices and for unknown ROM signatures. Do not remove the delayed retry without rechecking controller initialization timing.

## Next Acceptance Order

The public beta is released. Preserve the current user-confirmed system paths and use these outstanding checks for future maintenance:

1. Execute the Compose click cases on hardware and extend Device-page lifecycle coverage before generalizing the two successful beta.3/32 reports.
2. Separately verify automatic colour, manual override/import boundary cases, and Settings/MiLink device switching, asynchronous host writes and original-image restoration.
3. Confirm automatic management, official-app ownership, cancel/reconnect and status-bar ownership with simultaneous headsets.
4. Complete remaining theme/font-scale/rotation/background/unknown-ROM cases from `PUBLIC_BETA_ACCEPTANCE.md`.
5. Establish native Settings level encoding before enabling its writes.

## Safety Rules

- Never add arbitrary vendor-HEX input, firmware update, reset or pairing commands.
- Keep official-app ownership and exclusive-session checks around every live write.
- Treat raw capture ZIPs as sensitive; derived fixtures must omit names, MAC addresses and unrelated payloads.
- Unknown return types, non-AZ100 devices and stale state must fall through to the system implementation.

## Build Verification

The preceding beta.2 session compiled/ran these local checks (timestamps in the delivery manifest are UTC; the final package was created on 2026-10-11 in Asia/Shanghai):

```text
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:compileDebugAndroidTestKotlin
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

124 unit tests passed; Lint reports 0 errors and 60 warnings. Debug and minified Release built and local test signatures were checked. The instrumentation cases, including the added Device-row click case, were compiled but not run; the packaged test APK predates the new click case. The user reports device-specific physical acceptance of the listed Device-page paths on the two beta.3/32 devices; the earlier click failure on Xiaomi 17 Ultra (`nezha`) was superseded by its latest successful retest. No full cross-ROM checklist or adb-backed acceptance is claimed. This documentation correction does not rebuild or replace APKs. See `BETA_DELIVERY.md` for current artifact/source boundaries.
