# TechnicsPods

TechnicsPods is an experimental LSPosed module that integrates Technics EAH-AZ100 earbuds with Xiaomi HyperOS system surfaces. The project has two separate layers:

- A verified Panasonic RACE/RFCOMM transport layer for reading state and performing guarded noise-control writes.
- Protective HyperOS/MiLink hooks that make AZ100 visible to Xiaomi system pages and route Fusion Device Center cards to the stock headset detail page.

The current hardware baseline is Technics Audio Connect 4.4.0, EAH-AZ100 firmware `JNZ1ZE 1.040`, Xiaomi `nezha`, Android 17, HyperOS `OS4.0.0.17.XPACNXM`. Obfuscated MiLink class names are ROM-specific and must be rediscovered after a major ROM update.

## Current Status

The current candidate is `0.10.0-beta.3` (`versionCode 32`). It is the first public test release and is intended to be distributed as a GitHub **Pre-release**. The maintainer-signed APK has been built and verified locally; the GitHub upload remains an operational publishing step. The recorded hardware baseline remains Xiaomi `nezha`, Android 17, HyperOS `OS4.0.0.17.XPACNXM` and AZ100 firmware `JNZ1ZE 1.040`; historical captures used Audio Connect 4.4.0 and bundled artwork/colour mappings were reviewed from 5.0.1 (104). On 2026-10-11 the user confirmed Settings product-image replacement. Earlier reports confirmed both system entries' mode control/state updates, Settings haptics, the status-bar headset icon and MiLink image replacement.

The Device page rows **选择 AZ100**, **耳机颜色** and **设备图片** still do not respond to taps on the phone. The previous click changes and compiled Compose tests did not establish a working fix. Automatic colour detection is implemented but has not been separately accepted; manual colour, device selection and artwork actions are currently inaccessible through those rows. Work on these failures is paused at the user's request. See [current handoff](docs/CURRENT_SESSION_HANDOFF.md) for the saved implementation and verification boundaries.

The beta is a public test build rather than a complete hardware-acceptance claim. No adb device was available for this release workflow, so instrumentation execution and full physical acceptance remain outstanding. Install only on a device where LSPosed rollback and log collection are available, and verify the APK SHA-256 and maintainer signing certificate before use. The verified local release APK SHA-256 is `c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`; the maintainer certificate SHA-256 is `97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`. See [beta.3 release notes](docs/BETA3_RELEASE.md) and [public beta acceptance](docs/PUBLIC_BETA_ACCEPTANCE.md).

1. AZ100 is recognized as an eligible headset service.
2. The stock `SOUND_BLUETOOTH_CONTROL` display configuration is selected instead of `BluetoothCardView`.
3. The hook waits for the asynchronously created `HeadsetContentController`.
4. It invokes `fc.t.b0()` when ready, opening the same stock headset control/detail page used by the direct earphone entry.

The outer Fusion card's blue phone-host marker was unresolved in the previous evidence and needs rechecking. The app provides device state, selected-device settings, opt-in management, custom artwork and a separate advanced diagnostic surface. Stock mode callbacks submit typed requests through the module process; a successful installation report does not establish that a view rendered or a write completed on hardware.

## Download and install

Use the [GitHub Releases page](https://github.com/AixunZ/TechnicsPods/releases) and choose the `v0.10.0-beta.3` **Pre-release**. The public installation file is `TechnicsPods-0.10.0-beta.3-release.apk`; download `SHA256SUMS.txt` and `delivery-manifest.json` alongside it. The manifest records the APK package, version, certificate SHA-256 and attachment hashes. The source archive and mapping file are also included. Debug, test-signed, unsigned and instrumentation APKs are local test artifacts.

On Windows, verify the downloaded APK hash with `Get-FileHash -Algorithm SHA256 .\TechnicsPods-0.10.0-beta.3-release.apk` and compare it with `SHA256SUMS.txt`. With Android SDK Build Tools, run `apksigner verify --verbose --print-certs TechnicsPods-0.10.0-beta.3-release.apk` and compare the signer certificate SHA-256 with the delivery manifest. A signature error or mismatch is a failed verification.

1. Install the verified APK and enable TechnicsPods in LSPosed.
2. Enable its declared scopes: `com.panasonic.technicsaudioconnect`, `com.android.bluetooth`, `com.android.systemui`, `com.android.settings`, `com.miui.home`, `com.milink.service` and `com.xiaomi.bluetooth`. The source of truth is `META-INF/xposed/scope.list` in the APK.
3. Reboot after changing LSPosed scopes, then pair/connect the EAH-AZ100 through Android Bluetooth settings.
4. Use Bluetooth device details or Fusion Device Center for daily mode control. Test official-app coexistence separately using the [acceptance checklist](docs/PUBLIC_BETA_ACCEPTANCE.md).

Internal beta.1/beta.2 APKs used a local debug/test certificate. Android rejects an in-place update from that certificate to a different maintainer key. Preserve any needed diagnostics, uninstall the internal candidate and install beta.3. Future public updates must use the same maintainer certificate. GitHub Releases is the current public distribution channel; no third-party module index is published in this beta.

## Development credit

As of 2026-10-11, the maintainer estimates that more than 500 million GPT tokens were used in the development of this project. Models involved include GPT-5.6-sol, GPT-6.0-Astra, and GPT-6.1-sol. This is a maintainer-provided estimate, not an OpenAI-endorsed or independently audited usage report.

## Completed

- Read-only RFCOMM/RACE session with stream-safe frame decoding, response matching, timeout and cancellation handling.
- Verified reads for model, left/right battery, cradle battery, outside-control mode and levels, ambient sub-mode, adaptive ANC, firmware and TWS partner availability.
- Typed builders and parsers for the confirmed AZ100 commands; no arbitrary HEX sender.
- Guarded noise-control writes with read-before-write, ACK checking, forced read-back and best-effort restoration.
- Background state repository and connected-device foreground monitor with partial-read and disconnect cache semantics.
- HyperOS battery inventory confirming that `com.android.bluetooth` already stores AZ100 battery values.
- Settings and MiLink bridges for AZ100 identity, cached battery and read-only ANC/state rendering.
- Fusion Device Center service fallback, headset panel selection and delayed controller click routing.
- Unit tests for protocol parsing, state merging, control policy and Fusion routing policy.
- Phase 1: asynchronous system mode writes, confirmed-result publication to the restricted state Provider, state-change observers, bounded cancellation and stale-sample protection.
- Settings Bluetooth-detail control: forwards verified mode actions through the central Provider runtime and refreshes asynchronously. Native ANC-strength level encoding remains unavailable and is not advertised or written.
- Status-bar wireless-headset ownership: exact `A2dpService.handleConnectionStateChanged(BluetoothDevice,int,int)` discovery, current-state argument 2, multi-profile address tracking and startup reconciliation. Unknown ROM signatures are safe no-ops; unrelated headsets do not claim or hide the module-owned icon.
- Capability IPC reports exact installed hook counts and distinguishes installed from unavailable signatures.
- Verified MiLink artwork replacement is installed only for the known product-image resource IDs and AZ100 identity.
- Genuine Miuix Home / Device / About navigation, system/light/dark appearance, a shared lifecycle state owner and secondary advanced diagnostics. Daily noise control remains in system pages.
- Slot-aware restricted artwork Provider and versioned official resource mappings. Five reviewed AZ100 finishes (black, blue, gold, silver, violet) are bundled from Technics Audio Connect 5.0.1 with provenance. Manual import/override code exists, but the Device page entry rows are currently unresponsive.
- AZ100 model validation and automatic colour selection follow the official app's model/colour protocol; their evidence and remaining physical checks are in `docs/OFFICIAL_COLOR_FINDINGS.md`.
- Settings product-image replacement uses the identified AZ100 fragment and a conservative single large-header candidate, with asynchronous Provider decoding, host write-back protection and original restoration. The user confirmed rendering on the tested phone; all fallback/lifecycle cases have not been accepted.
- Settings user mode requests acknowledge acceptance with a single system-respecting haptic, guarded by request ID, device and view/session generation.

## Not Completed

- Pending/error presentation in the stock detail page still needs physical beta testing. Historical mode switching is user-confirmed; the beta does not claim native Settings ANC-strength support.
- External mode changes are discovered by opt-in automatic monitoring at 30-second intervals after each sample completes.
- Reproducing the Fusion Device Center outer blue phone-host indicator.
- Full charging-state semantics for all six HyperOS battery slots.
- Compatibility validation on other HyperOS/Android releases and other AZ100 firmware versions.
- Device page selection, colour and artwork rows remain unresponsive; further repair is paused at the user's request.
- MiLink artwork appears soft when enlarged from the official 176 x 130 case asset; no sharpening or replacement source was applied.
- Full physical acceptance for themes/rotation/font scale, automatic colour, official-app ownership, background recovery, multi-device artwork and unknown ROMs.
- GitHub Pre-release upload is still a publishing step; the maintainer-signed artifact and local delivery manifest are ready.

## Architecture

- `app/src/main/java/dev/technics/pods/protocol/`: typed RACE frames, command IDs and response/state parsers.
- `app/src/main/java/dev/technics/pods/diagnostic/`: safe read-only client, guarded control clients and test results.
- `app/src/main/java/dev/technics/pods/state/`: state cache, persistent repository, Provider and foreground monitor.
- `app/src/main/java/dev/technics/pods/state/Az100ConnectionRuntime.java`: central module-process transport ownership and official-session handover.
- `app/src/main/java/dev/technics/pods/hook/Az100HyperOsBridgeHook.java`: generic MiLink/HyperOS identity, battery and ANC bridge.
- `app/src/main/java/dev/technics/pods/hook/Az100SettingsBridgeHook.java`: legacy Settings headset-page bridge.
- `app/src/main/java/dev/technics/pods/hook/Az100FusionDeviceRouteHook.java`: Fusion service lookup, panel/config selection and headset-detail click routing.
- `app/src/main/java/dev/technics/pods/hook/HyperOsSurfaceProbeHook.java`: read-only surface inventory for ROM changes.
- `app/src/main/java/dev/technics/pods/hook/WirelessHeadsetStatusBarHook.java`: exact A2DP status callback and multi-profile reconciliation.
- `app/src/main/java/dev/technics/pods/hook/Az100ArtHook.java`: identity-bound MiLink artwork and a conservative Settings header selector; Settings resource IDs are not yet locked to a verified per-ROM list.
- `app/src/main/java/dev/technics/pods/ui/`: immutable UI state, lifecycle state owner and Miuix three-tab pages.

All hooks are protective: non-AZ100 devices and unknown signatures continue through the original implementation. Cross-process state is exposed through the restricted `dev.technics.pods.state` Provider; raw captures remain sensitive.

Diagnostic class-loading and Bluetooth battery inventories are opt-in. Fusion's obfuscated `wc.a`, `wc.b` and `fc.t` route is enabled only for the recorded Android 17 / OS4.0.0.17 profile; other ROMs retain generic recognition and report the route unavailable.

## Testing the Current Build

1. Install the verified maintainer-signed Release APK for the public beta. Local Debug variants use a separate test identity. Enable the module in LSPosed for the packages listed in `META-INF/xposed/scope.list`.
2. Reboot after changing LSPosed scopes.
3. Pair AZ100 and run the exclusive read/control baseline with Technics Audio Connect force-stopped. Test official-app handover separately as described in the beta acceptance checklist.
4. Open the Fusion Device Center and select AZ100. The inner page should be the stock headset control/detail page.
5. For a route regression, look for `Found named system Headset panel`, `Routed AZ100 MainCardView to Headset display config`, and `Routed AZ100 MainCardView request to HeadsetContentController` in the LSPosed log.

## Build

Use JDK 21 with Android SDK Platform 37 and Build Tools 36.1.0. Set `JAVA_HOME` and `ANDROID_HOME` to your installed toolchains (or configure the SDK through untracked `local.properties`). AGP 9.3.1 owns the Kotlin 2.4.0 compiler; Java and Kotlin bytecode targets remain 17. The UI uses Compose BOM 2026.05.01 and Miuix 0.9.2. Local SDKs, toolchains and signing keys are excluded from the source archive.

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $PWD '.android-user-home'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-daemon
```

The debug artifact is `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs use the local debug/test key and are for controlled LSPosed testing. Without a configured release key, `assembleRelease` produces `app-release-unsigned.apk`, which cannot be installed as a signed distribution package. With the maintainer signing variables, the signed output is `app/build/outputs/apk/release/app-release.apk`. Only the maintainer-signed Release is copied to the public `TechnicsPods-0.10.0-beta.3-release.apk` filename; see [beta delivery](docs/BETA_DELIVERY.md) for packaging and acceptance boundaries.

Release signing can be configured through `TECHNICSPODS_KEYSTORE`, `TECHNICSPODS_STORE_PASSWORD`, `TECHNICSPODS_KEY_ALIAS` and `TECHNICSPODS_KEY_PASSWORD`. Secrets and keystores are excluded from source control. Current build results and artifact hashes are recorded in [beta delivery](docs/BETA_DELIVERY.md).

## Documentation

- [Project status and evidence](docs/PROJECT_STATUS.md)
- [Release history](CHANGELOG.md)
- [Read-only diagnostic](docs/READ_ONLY_DIAGNOSTIC.md)
- [Safe control test](docs/CONTROL_TEST.md)
- [Background monitor](docs/BACKGROUND_STATE.md)
- [Protocol schema](docs/PROTOCOL_SCHEMA.md)
- [System and HyperOS bridges](docs/SYSTEM_BRIDGE_PROBE.md)
- [Capture guide](docs/CAPTURE_GUIDE.md)
- [Public beta acceptance](docs/PUBLIC_BETA_ACCEPTANCE.md)
- [Beta delivery](docs/BETA_DELIVERY.md)
- [Beta.3 release notes](docs/BETA3_RELEASE.md)
- [Beta.2 release notes](docs/BETA2_RELEASE.md)
- [Artwork resource mapping](docs/artwork-beta2.md)
- [Current handoff and unresolved issues](docs/CURRENT_SESSION_HANDOFF.md)

## License and Provenance

TechnicsPods code is licensed under GPL-3.0. The HyperOS integration approach is informed by HuaweiPods, SonyPods and their OppoPods lineage. No Technics Audio Connect code is bundled. Reviewed five-colour official AZ100 artwork is included as third-party assets; its source, signer and resource hashes are recorded in NOTICE and the artwork manifests, and it is not relabeled as GPL code.
