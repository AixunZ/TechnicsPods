# Public beta acceptance

This checklist describes what the planned `0.10.0-beta.1` / versionCode 30 beta can claim. The repository has no fresh adb-backed or physical-device acceptance result for this beta.

## Recorded compatibility baseline

- Xiaomi `nezha`
- Android 17
- HyperOS `OS4.0.0.17.XPACNXM`
- Technics Audio Connect 4.4.0
- EAH-AZ100 firmware `JNZ1ZE 1.040`

Other hosts and firmware versions are unknown. Protective hooks should fall through or report unavailable when their exact signature is absent.

## Before installing

1. Build and install the debug APK only on a test device. It is signed with the local debug/test key.
2. Enable the module for `com.android.bluetooth`, SystemUI, Settings, `com.milink.service`, `com.xiaomi.bluetooth`, and the official app as needed.
3. Keep the maintainer's release signing and upload process separate from debug testing.

An unsigned release APK is not a signed installation package. If a release build is produced without a configured key, the maintainer must sign it, verify the certificate/hash, and upload that artifact independently. A debug/test key is not proof of a maintainer release signature.

## Acceptance checks

1. Pair and connect an EAH-AZ100. Confirm the status-bar hook reports the exact A2DP callback or an explicit unavailable capability.
2. Confirm an AZ100 connection can show the `wireless_headset` icon when enabled, while an unrelated headset does not claim or hide the module-owned icon.
3. Restart Bluetooth and SystemUI. Confirm startup profile reconciliation does not hide an icon while profile snapshots are incomplete.
4. Open Fusion Device Center and Bluetooth details. Confirm AZ100 identity, battery and mode rendering continue to fall through safely on non-AZ100 devices.
5. Exercise Settings mode actions. Confirm requests are accepted by the central Provider and any confirmed result refreshes asynchronously.
6. Treat Settings ANC-strength level changes as unsupported until the host encoding is independently verified.
7. Confirm capability records report feature, installed/unavailable status and exact hook count without claiming hardware rendering success.
8. Confirm artwork replacement is limited to the verified MiLink resource IDs; Settings product images remain unsupported.
9. Enable automatic management, then disconnect/reconnect and turn Bluetooth off/on. Confirm a live transaction cancels, cached values remain, and sampling recovers without a concurrent RFCOMM session.
10. Open Technics Audio Connect while the module is reading or controlling. Confirm official ownership cancels/yields the module, rejects new writes and releases only after the official socket/session ends.
11. During uncertain official-session recovery, confirm the module keeps yielding. Restart the official app to reannounce sessions, or turn Bluetooth fully off/on; confirmed adapter-off may release the recovery lock and reconnect must read fresh state before writing.
12. Stop automatic management from the app and notification separately, then close the official app. Confirm there are no autonomous module reads; a new explicit request still performs a fresh read before control.
13. Test light/dark themes, narrow screens and font scale 2. Rotate during permissions, diagnostics and image selection; results and selected images must survive, and a slow document provider must not freeze the UI during log export.
14. Switch selected AZ100 devices while an old system detail page is open. Its requests must be rejected, and battery/mode/artwork from one device must not appear as another device's current data.
15. Test lock-screen connection, reboot, package update, process reclaim and revoked Bluetooth permission. Confirm recovery or an actionable permission/background-restriction state without a host crash.
16. Install both Debug and minified test-signed Release candidates on the test device, verify same-key upgrades, and exercise an unknown ROM/host signature to confirm affected capabilities fall through safely. Maintainer-signed Release installation and public upload are separate checks.

## Evidence to retain

Record the device/build strings, module APK SHA-256, signing identity, LSPosed logs and Provider capability records. Do not publish Bluetooth addresses, raw vendor payloads or unredacted capture archives.
