# TechnicsPods

TechnicsPods is an experimental LSPosed module that integrates Technics EAH-AZ100 earbuds with Xiaomi HyperOS system surfaces. The project has two separate layers:

- A verified Panasonic RACE/RFCOMM transport layer for reading state and performing guarded noise-control writes.
- Protective HyperOS/MiLink hooks that make AZ100 visible to Xiaomi system pages and route Fusion Device Center cards to the stock headset detail page.

The current hardware baseline is Technics Audio Connect 4.4.0, EAH-AZ100 firmware `JNZ1ZE 1.040`, Xiaomi `nezha`, Android 17, HyperOS `OS4.0.0.17.XPACNXM`. Obfuscated MiLink class names are ROM-specific and must be rediscovered after a major ROM update.

## Current Status

The planned beta is `0.10.0-beta.1` (`versionCode 30`). The recorded hardware baseline remains Xiaomi `nezha`, Android 17, HyperOS `OS4.0.0.17.XPACNXM`, Technics Audio Connect 4.4.0 and AZ100 firmware `JNZ1ZE 1.040`. The current source adds protective status-bar ownership, capability reporting, asynchronous Provider reads and guarded system-page mode requests. The beta has not had a fresh physical-device acceptance run or an adb-backed log review.

1. AZ100 is recognized as an eligible headset service.
2. The stock `SOUND_BLUETOOTH_CONTROL` display configuration is selected instead of `BluetoothCardView`.
3. The hook waits for the asynchronously created `HeadsetContentController`.
4. It invokes `fc.t.b0()` when ready, opening the same stock headset control/detail page used by the direct earphone entry.

The outer Fusion card's blue phone-host marker was unresolved in the previous evidence and needs rechecking. The app provides device state, selected-device settings, opt-in management, custom artwork and a separate advanced diagnostic surface. Stock mode callbacks submit typed requests through the module process; a successful installation report does not establish that a view rendered or a write completed on hardware.

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

## Not Completed

- Pending/error presentation in the stock detail page still needs physical beta testing. Historical mode switching is user-confirmed; the beta does not claim native Settings ANC-strength support.
- External mode changes are discovered by opt-in automatic monitoring at 30-second intervals after each sample completes.
- Reproducing the Fusion Device Center outer blue phone-host indicator.
- Full charging-state semantics for all six HyperOS battery slots.
- Compatibility validation on other HyperOS/Android releases and other AZ100 firmware versions.
- Maintainer release signing, physical acceptance and public upload.
- Physical acceptance of the beta status icon, artwork, capability records and asynchronous detail refresh.

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
- `app/src/main/java/dev/technics/pods/hook/Az100ArtHook.java`: identity-bound MiLink artwork; Settings image IDs are unverified.

All hooks are protective: non-AZ100 devices and unknown signatures continue through the original implementation. Cross-process state is exposed through the restricted `dev.technics.pods.state` Provider; raw captures remain sensitive.

Diagnostic class-loading and Bluetooth battery inventories are opt-in. Fusion's obfuscated `wc.a`, `wc.b` and `fc.t` route is enabled only for the recorded Android 17 / OS4.0.0.17 profile; other ROMs retain generic recognition and report the route unavailable.

## Testing the Current Build

1. Install the debug APK and enable the module in LSPosed for the packages listed in `META-INF/xposed/scope.list`.
2. Reboot after changing LSPosed scopes.
3. Pair AZ100 and run the exclusive read/control baseline with Technics Audio Connect force-stopped. Test official-app handover separately as described in the beta acceptance checklist.
4. Open the Fusion Device Center and select AZ100. The inner page should be the stock headset control/detail page.
5. For a route regression, look for `Found named system Headset panel`, `Routed AZ100 MainCardView to Headset display config`, and `Routed AZ100 MainCardView request to HeadsetContentController` in the LSPosed log.

## Build

Use JDK 21 with Android SDK Platform 37 and Build Tools 36.1.0. Set `JAVA_HOME` and `ANDROID_HOME` to your installed toolchains (or configure the SDK through untracked `local.properties`). The project's Java source/target level remains 17. Local SDKs, toolchains and signing keys are excluded from the source archive.

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $PWD '.android-user-home'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-daemon
```

The debug artifact is `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs use the local debug/test key and are for controlled LSPosed testing. Without a configured release key, `assembleRelease` produces `app-release-unsigned.apk`, which cannot be installed as a signed distribution package. A minified test-signed candidate is provided separately. The maintainer must sign and upload the public artifact independently; see [beta delivery](docs/BETA_DELIVERY.md) for candidate hashes and acceptance limitations.

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

## License and Provenance

TechnicsPods is licensed under GPL-3.0. The HyperOS integration approach is informed by HuaweiPods, SonyPods and their OppoPods lineage. No proprietary Technics Audio Connect assets or code are included.
