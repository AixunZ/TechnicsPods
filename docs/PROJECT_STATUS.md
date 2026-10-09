# TechnicsPods Project Status

Last updated: 2026-10-09

## Phase 1 update

The user confirmed working Fusion Device Center and Bluetooth-detail visibility and off / ANC / ambient switching on 2026-09-27. The older statements below about unproven stock-button writes describe the August investigation, not the current baseline.

Planned beta: `0.10.0-beta.1` / versionCode 30. The source now routes system control requests through the central Provider runtime, reports hook capabilities, reads state asynchronously and adds protective status-bar and artwork hooks. No fresh physical-device acceptance run or adb-backed beta log is available yet. See [public beta acceptance](PUBLIC_BETA_ACCEPTANCE.md).

The device/ROM/firmware listed below remain the recorded hardware baseline. Historical user confirmation covers the inner Fusion route and off / ANC / ambient mode switching. The beta source now implements connection ownership, official-app session announcements and a device/settings UI; their hardware acceptance remains pending. Settings native ANC-strength levels are deliberately unavailable because their host encoding is unverified.

## Historical August evidence

## Baseline

- Planned beta version: `0.10.0-beta.1` / `versionCode 30`
- Test device: Xiaomi `nezha`, Android 17, HyperOS `OS4.0.0.17.XPACNXM`
- Earbuds: Technics EAH-AZ100, firmware `JNZ1ZE 1.040`
- Official app used for captures: Technics Audio Connect 4.4.0
- Latest route verification: `LSPosed_20260831_015717.zip`

## Compatibility evidence

| Host | Evidence | Scope |
|---|---|---|
| Xiaomi `nezha`, Android 17, HyperOS `OS4.0.0.17.XPACNXM` | Recorded LSPosed route logs and user hardware confirmation | Fusion inner route, Settings mode path, AZ100 identity/battery/state |
| `com.milink.service` `18.1.2.2.2608192125` / code `180010202` | `success_half_modules.log`, Surface process ready | Recorded Fusion controller/panel surface; beta untested |
| `com.android.settings` `17` / code `37` | `success_half_modules.log`, Surface process ready | Recorded Settings host version; beta untested |
| `com.xiaomi.bluetooth` `17` / code `37` | `success_half_modules.log`, Surface process ready | Recorded runtime host version; beta untested |
| `com.android.systemui` `17.03.260226.r` / code `202602260` | `success_half_modules.log`, Surface process ready | Recorded SystemUI version; new status rendering untested |
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
- Status-bar callback discovery is exact for the recorded A2DP signature. Rendering, profile startup reconciliation and multi-headset behavior remain pending physical beta acceptance.
- Artwork hook is limited to verified MiLink resource IDs and is not claimed for Settings product images.

The final route log contains:

```text
Found named system Headset panel;factory=q;label=SOUND_BLUETOOTH_CONTROL
Routed AZ100 MainCardView to Headset display config
AZ100 Headset controller call=fc.t:b0()
Routed AZ100 MainCardView request to HeadsetContentController
```

## What Is Not Working Yet

- The Fusion Device Center outer AZ100 tile does not show the blue phone-host marker. This is a separate device-host/status model from the inner panel route.
- The supplied 2026-10-09 LSPosed log identified the Settings write gap. The beta forwards mode requests through the central runtime; its revised refresh/ownership path still needs physical acceptance.
- Live state refresh inside every stock control/detail view is incomplete.
- Charging flags and offline/stale presentation require more ROM-specific validation.
- Compatibility with other HyperOS builds, Android versions and AZ100 firmware versions is unknown.
- Native Settings ANC-strength level encoding is unverified; the beta exposes no invented conversion or write path. This restriction supersedes v29 level-forwarding behavior and prevents a host slider value being treated as a verified AZ100 percentage.
- Maintainer release signing and upload have not been performed. Optional signing environment variables and unsigned/minified test artifacts are available.
- Exact AZ100 model-ID bytes are absent from the inspected captures; the current handshake checks success status only.

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

1. Complete the fresh physical checks in `PUBLIC_BETA_ACCEPTANCE.md`, retaining capability records and module/build versions.
2. Confirm automatic management, official-app ownership, cancel/reconnect and asynchronous mode refresh.
3. Verify status-bar ownership with simultaneous AZ100 and unrelated headset profiles.
4. Verify MiLink custom artwork and unchanged unsupported Settings imagery.
5. Independently establish native Settings level encoding before enabling its writes, then sign and verify a maintainer release artifact.

## Safety Rules

- Never add arbitrary vendor-HEX input, firmware update, reset or pairing commands.
- Keep official-app ownership and exclusive-session checks around every live write.
- Treat raw capture ZIPs as sensitive; derived fixtures must omit names, MAC addresses and unrelated payloads.
- Unknown return types, non-AZ100 devices and stale state must fall through to the system implementation.

## Build Verification

The beta source was verified on 2026-10-09 with:

```text
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:assembleDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

97 unit tests passed. Lint reports 0 errors and 36 warnings. Debug, minified unsigned Release and IPC test APKs build successfully; a minified test-signed Release candidate is packaged separately. Debug, test-signed Release and IPC APK signatures verify against the same local test certificate. No adb device was attached, so the 5 instrumentation cases, UI screenshots, hook rendering, background restrictions and upgrade/install acceptance are not executed. See `BETA_DELIVERY.md` for artifacts and remaining requirements.
