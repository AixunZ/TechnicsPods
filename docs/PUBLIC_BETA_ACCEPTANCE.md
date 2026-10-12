# Public beta acceptance

This checklist describes the published `0.10.0-beta.3` / versionCode 32 first public test release. The latest beta.3/32 device reports cover the explicitly listed Device-page paths; they do not establish cross-ROM, lifecycle or complete instrumentation acceptance.

## Recorded acceptance status — 2026-10-11

| Area | Evidence and current status |
| --- | --- |
| Settings and MiLink controls / state updates | User reports normal operation in both system entries |
| Settings haptic feedback | User reports normal feedback; rejection, duplicate, disabled-system-haptics and lifecycle cases were not separately reported |
| Status-bar headset icon | User reports normal display; Bluetooth restart and multiple-headset cases were not separately reported |
| MiLink / control-center artwork | User confirms replacement; displayed image appears soft and has not been sharpened |
| Settings Bluetooth detail artwork | User confirms replacement in the latest test |
| Device page “选择 AZ100”, “耳机颜色”, “设备图片” | User reports all three paths working on Xiaomi 17 Ultra / HyperOS 4 and Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package; the earlier `nezha` record remains unresponsive |
| Automatic AZ100 color recognition | Implemented; the complete Device-page reports cover the listed manual paths, while automatic selection remains separately unaccepted |
| Manual device/color/image selection | Reported working on the two beta.3/32 devices above; not accepted on the earlier `nezha` record |
| Local automated validation | Existing build: 124 unit tests passed; Lint 0 errors / 60 warnings; Debug and minified Release built |
| Compose / artwork instrumentation | Current cases compiled only; retained test APK predates the newest Device-row click case; no adb execution result |

These are device-specific user-reported results, not a complete cross-ROM acceptance pass. The two beta.3/32 reports cover the three Device-page paths listed above; the earlier `nezha` record remains a known failure. Do not treat compiled Compose click tests as evidence for a physical device. No adb device was available for this release workflow: instrumentation execution and complete physical acceptance remain outstanding and must be recorded as unexecuted rather than passing.

## Recorded compatibility baseline

- Xiaomi `nezha`
- Android 17
- HyperOS `OS4.0.0.17.XPACNXM`
- Technics Audio Connect 4.4.0
- EAH-AZ100 firmware `JNZ1ZE 1.040`

Other hosts and firmware versions are unknown. Protective hooks should fall through or report unavailable when their exact signature is absent.

## Additional beta.3 device results

| Host | beta.3/32 result | Boundary |
| --- | --- | --- |
| Xiaomi 17 Ultra / HyperOS 4 | The three Device-page actions were reported working | Device-specific report; exact build and all lifecycle cases were not recorded |
| Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package | The three Device-page actions were reported working | Device-specific report; exact build and all lifecycle cases were not recorded |
| Xiaomi `nezha` / Android 17 / HyperOS `OS4.0.0.17.XPACNXM` | The three Device-page actions remain unresponsive in the recorded result | Retained as a known limitation for that host |

## Before installing

1. Download the maintainer-signed `TechnicsPods-0.10.0-beta.3-release.apk` and `SHA256SUMS.txt` from the GitHub Pre-release. Verify its checksum and maintainer certificate before installing on a test device.
2. Enable the module for the packages in `META-INF/xposed/scope.list`: `com.android.bluetooth`, SystemUI, Settings, `com.miui.home`, `com.milink.service`, `com.xiaomi.bluetooth`, and Technics Audio Connect.
3. Reboot after changing LSPosed scopes. If a beta.1/beta.2 debug or test-signed candidate is installed, Android cannot upgrade it with a different maintainer certificate; export any needed diagnostics, then uninstall the old candidate before installing beta.3. Debug/test builds remain local test artifacts.

An unsigned release APK is not a signed installation package. If a release build is produced without a configured key, the maintainer must sign it, verify the certificate/hash, and upload that artifact independently. A debug/test key is not proof of a maintainer release signature.

## Acceptance checks

1. Pair and connect an EAH-AZ100. Confirm the status-bar hook reports the exact A2DP callback or an explicit unavailable capability.
2. Confirm an AZ100 connection can show the `wireless_headset` icon when enabled, while an unrelated headset does not claim or hide the module-owned icon.
3. Restart Bluetooth and SystemUI. Confirm startup profile reconciliation does not hide an icon while profile snapshots are incomplete.
4. Open Fusion Device Center and Bluetooth details. Confirm AZ100 identity, battery and mode rendering continue to fall through safely on non-AZ100 devices.
5. Exercise Settings mode actions. Confirm requests are accepted by the central Provider and any confirmed result refreshes asynchronously.
6. Treat Settings ANC-strength level changes as unsupported until the host encoding is independently verified.
7. Confirm capability records report feature, installed/unavailable status and exact hook count without claiming hardware rendering success.
8. Confirm artwork replacement is limited to verified MiLink resource IDs or the single conservative Settings header candidate and the selected AZ100. Basic replacement in both entries is user-confirmed; the beta bundles reviewed black, blue, gold, silver and violet mappings from Audio Connect 5.0.1. Source/color/slots, automatic protocol selection, notifications, host asynchronous overwrites and original restoration still need explicit acceptance. The two beta.3/32 Device-page reports include image-source access, while the earlier `nezha` record does not. If the Settings layout is ambiguous or unmeasured, the host image must remain unchanged.
9. Enable automatic management, then disconnect/reconnect and turn Bluetooth off/on. Confirm a live transaction cancels, cached values remain, and sampling recovers without a concurrent RFCOMM session.
10. Open Technics Audio Connect while the module is reading or controlling. Confirm official ownership cancels/yields the module, rejects new writes and releases only after the official socket/session ends.
11. During uncertain official-session recovery, confirm the module keeps yielding. Restart the official app to reannounce sessions, or turn Bluetooth fully off/on; confirmed adapter-off may release the recovery lock and reconnect must read fresh state before writing.
12. Stop automatic management from the app and notification separately, then close the official app. Confirm there are no autonomous module reads; a new explicit request still performs a fresh read before control.
13. Test genuine Miuix Home / Device / About navigation, system/light/dark themes, narrow screens and font scale 2. The three Device-page actions are reported working on the two beta.3/32 hosts in the compatibility table, but remain unresponsive in the earlier `nezha` record. Rotate during permissions, diagnostics and image selection; selected tab, results and selected images must survive. About settings must remain reachable by scrolling and no page may create an additional connection or observer. A slow document provider must not freeze the UI during image import or log export.
14. Switch selected AZ100 devices while an old system detail page is open. Its requests must be rejected, and battery/mode/artwork from one device must not appear as another device's current data.
15. Test lock-screen connection, reboot, package update, process reclaim and revoked Bluetooth permission. Confirm recovery or an actionable permission/background-restriction state without a host crash.
16. Install the maintainer-signed beta.3 Release on the test device and verify future same-maintainer-key upgrades. Keep Debug and test-signed variants as local checks only. Exercise an unknown ROM/host signature to confirm affected capabilities fall through safely; publishing a signed package does not establish hardware acceptance.
17. In Settings, click off / ANC / ambient: each accepted real request should cause one system haptic before hardware completion. Basic feedback is user-confirmed. Rejected/busy/invalid requests, internal replay, old views, disconnect and selected-device changes must not cause feedback; these edge cases remain unreported. Close system haptics and confirm controls still work without vibration. MiLink retains its original feedback behavior.

## Evidence to retain

Record the device/build strings, module APK SHA-256, signing identity, LSPosed logs and Provider capability records. Do not publish Bluetooth addresses, raw vendor payloads or unredacted capture archives.
