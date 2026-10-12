# 0.10.0-beta.3 — First public test release

Version code: **32**. Package: `dev.technics.pods`. Developer: **AixunZ**. This release is published as a GitHub **Pre-release** at [AixunZ/TechnicsPods](https://github.com/AixunZ/TechnicsPods/releases/tag/v0.10.0-beta.3). Beta.1 and beta.2 remain internal candidate history.

## Changes

- The application version is `0.10.0-beta.3` and its developer credit is `AixunZ`.
- The public distribution package uses the maintainer's long-term release certificate `97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`. The release APK SHA-256 is `c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`. Debug, test-signed, unsigned and instrumentation APKs remain local test artifacts.
- Local public packaging completed with 124 unit tests, 0 lint errors/60 warnings and `releaseSignatureVerified=true`. Instrumentation execution and complete cross-ROM physical acceptance remain unexecuted. The README includes installation, signature/checksum verification, known beta limitations and the maintainer's GPT development-credit estimate. The token statement is a maintainer estimate, not an OpenAI-endorsed or independently audited usage report.
- The transport, system control/state hooks, artwork implementation and Device-page actions from beta.2 are retained. The three Device-page actions and their main paths were reported working on beta.3/32 on two devices: Xiaomi 17 Ultra (`nezha`) / HyperOS 4 and Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package. The 2026-10-12 device-identity correction confirms that `nezha` is the Xiaomi 17 Ultra; its latest successful retest supersedes the earlier unresponsive-row result on that same device.

## Download and install

Download `TechnicsPods-0.10.0-beta.3-release.apk` from the GitHub Pre-release together with `SHA256SUMS.txt` and `delivery-manifest.json`. Verify the APK SHA-256 (`c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`) and maintainer certificate (`97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`) against the published manifest before installing. Public attachments also include `TechnicsPods-0.10.0-beta.3-source.zip` and `TechnicsPods-0.10.0-beta.3-mapping.txt` for corresponding source and crash retracing.

Enable TechnicsPods in LSPosed for the scopes listed in `META-INF/xposed/scope.list`, then reboot after changing scopes. Pair the EAH-AZ100 through Android Bluetooth settings. For an exclusive read/control baseline, force-stop Technics Audio Connect; official-app coexistence is a separate acceptance scenario.

Internal beta.1/beta.2 candidates used a local debug/test certificate. Android cannot upgrade one of those installations with a different maintainer certificate. Preserve any needed local diagnostics, uninstall the old candidate, and install this maintainer-signed package. Subsequent public releases must retain the same maintainer key for normal updates.

## Known limitations and evidence

- **Device-page results are device-specific.** The three actions and their main paths were reported working on beta.3/32 on Xiaomi 17 Ultra (`nezha`) / HyperOS 4 and Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package. The earlier failure on the same Xiaomi 17 Ultra is superseded by the latest successful retest, rather than being a separate currently failing device.
- No adb device was available for this release workflow. Instrumentation execution and complete cross-ROM physical acceptance are unexecuted, not passing. The two successful device reports cover the stated Device-page paths only; lifecycle, theme, rotation, multi-device and other firmware cases remain open.
- Earlier reports confirmed mode control/state updates in both system entries, Settings haptics, the status-bar headset icon and MiLink/Settings artwork replacement. The historical captured baseline is Xiaomi 17 Ultra (`nezha`), Android 17, HyperOS `OS4.0.0.17.XPACNXM`, with AZ100 firmware `JNZ1ZE 1.040`. These exact system/firmware versions were not re-collected in the latest beta.3 report.
- Native Settings ANC-strength encoding is unverified and unsupported. MiLink artwork can look soft because its official case source is 176×130 pixels. The Fusion card's outer blue phone-host marker, multi-device behavior, lifecycle/recovery, official-app ownership and other ROMs remain acceptance targets.

Complete criteria are in [public beta acceptance](PUBLIC_BETA_ACCEPTANCE.md). Generated local check results, hashes, certificate identity and source boundaries belong in [beta delivery](BETA_DELIVERY.md) and the release's `delivery-manifest.json`.
