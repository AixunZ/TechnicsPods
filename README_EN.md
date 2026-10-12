# TechnicsPods

> **English · [简体中文](README.md)**

TechnicsPods is an independent LSPosed module for Xiaomi HyperOS. It lets Technics EAH-AZ100 earbuds appear in the system Bluetooth details, Fusion Device Center and related HyperOS pages, with state reads and guarded noise-control mode requests.

The project is informed by the public research and ideas of [HuaweiPods](https://github.com/Nshpiter/HuaweiPods), SonyPods and the related OppoPods lineage. TechnicsPods is an independent implementation and does not contain HuaweiPods or Technics Audio Connect code.

## Current release

- **Version:** `0.10.0-beta.3` (`versionCode 32`)
- **Release type:** GitHub **Pre-release**, the first public test release
- **Package:** `dev.technics.pods`
- **Supported earbud model:** Technics EAH-AZ100
- **Public download:** [GitHub Releases](https://github.com/AixunZ/TechnicsPods/releases/tag/v0.10.0-beta.3)
- **Developer shown in the About page:** **AixunZ**

Beta.3 is a public test build. It does not claim that every ROM, firmware version or system page has completed physical acceptance. Install it only where LSPosed rollback and log collection are available.

## Features and evidence levels

| Capability | Current state | Evidence level |
| --- | --- | --- |
| Recognize AZ100 and show its system-earbud identity | Implemented | Verified |
| Read left/right earbud, case battery and noise-control state | Implemented | Verified |
| Switch modes from system Bluetooth details | Read-before-write, ACK and read-back guarded | Verified; retest per device |
| Open the stock headset detail page from Fusion Device Center | Delayed route to the stock page | Verified on the recorded ROM profile |
| Own the wireless-headset status-bar icon | Multi-profile state reconciliation | Verified for the known callback signature |
| Replace AZ100 artwork in MiLink and Settings | Conservative resource matching | Rendering confirmed on test devices |
| Manual Device-page device, colour and image selection | Main workflows were reported working on the two additional hosts; the `nezha` baseline's entry rows do not respond | Verified per device |
| Automatic colour recognition and artwork selection | Implemented; no separate physical acceptance | Physical verification pending |
| Other ROMs, firmware and complete lifecycle scenarios | Not covered | Unverified |

All hooks fall through for non-AZ100 devices and unknown signatures. Obfuscated MiLink class names can change after a ROM update and may need to be probed again.

## Compatibility records

These records describe combinations for which evidence was supplied; they do not guarantee compatibility elsewhere.

| Device / system | beta.3 (`32`) result |
| --- | --- |
| Xiaomi 17 Ultra / HyperOS 4 | The three Device-page entry workflows and their main actions were reported as passing |
| Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 port package | The three Device-page entry workflows and their main actions were reported as passing |
| Xiaomi `nezha` / Android 17 / HyperOS `OS4.0.0.17.XPACNXM` | Historical record: **选择 AZ100**, **耳机颜色** and **设备图片** remain unresponsive; other bridge behavior was verified only within the documented scope |
| Other HyperOS/Android versions, AZ100 firmware or unlisted devices | Unverified |

“Complete main workflow” means the listed Device-page entries and their primary actions on that setup. Themes, rotation, font scaling, background recovery, multi-device behavior and all error paths still need separate acceptance. The recorded AZ100 firmware baseline is `JNZ1ZE 1.040`.

## Download, verify and install

Download the [v0.10.0-beta.3 GitHub Pre-release](https://github.com/AixunZ/TechnicsPods/releases/tag/v0.10.0-beta.3):

- `TechnicsPods-0.10.0-beta.3-release.apk` — the public installation package.
- `TechnicsPods-0.10.0-beta.3-source.zip` — the source archive.
- `TechnicsPods-0.10.0-beta.3-mapping.txt` — the R8 mapping file.
- `SHA256SUMS.txt` and `delivery-manifest.json` — checksums and delivery metadata.

GitHub may also show automatically generated source ZIP/TAR files. Debug, test-signed, unsigned and instrumentation APKs are local test artifacts, not the default public download.

Release APK SHA-256:

```text
c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986
```

Maintainer signing certificate SHA-256:

```text
97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d
```

On Windows, verify the downloaded APK:

```powershell
Get-FileHash -Algorithm SHA256 .\TechnicsPods-0.10.0-beta.3-release.apk
```

With Android SDK Build Tools, also verify the signer:

```powershell
apksigner verify --verbose --print-certs .\TechnicsPods-0.10.0-beta.3-release.apk
```

Compare the hash and certificate with `SHA256SUMS.txt` and `delivery-manifest.json`. Do not install a file whose package, version, hash or signer does not match.

1. Install the verified APK and enable TechnicsPods in LSPosed.
2. Enable the module for these declared scopes: `com.panasonic.technicsaudioconnect`, `com.android.bluetooth`, `com.android.systemui`, `com.android.settings`, `com.miui.home`, `com.milink.service` and `com.xiaomi.bluetooth`.
3. Reboot after changing scopes, then pair or reconnect EAH-AZ100 through Android Bluetooth settings.
4. Use Bluetooth details or Fusion Device Center for daily mode control. Test coexistence with Technics Audio Connect separately using the [public beta acceptance checklist](docs/PUBLIC_BETA_ACCEPTANCE.md); for an exclusive transport baseline, force-stop the official app first.

The scope source of truth is `META-INF/xposed/scope.list` inside the APK. Beta.1 and beta.2 used a local debug/test certificate and cannot be upgraded in place to the maintainer-signed beta.3. Export any diagnostics, uninstall the old candidate and install beta.3. Future public releases must keep the maintainer key for normal updates.

## Known limitations

- On the recorded Xiaomi `nezha` / Android 17 / `OS4.0.0.17.XPACNXM` setup, the Device-page rows **选择 AZ100**, **耳机颜色** and **设备图片** do not respond to taps. Device selection, manual colour changes and image import are unavailable through those rows there; repair is paused for this beta. See the [current handoff](docs/CURRENT_SESSION_HANDOFF.md).
- Native Settings ANC-strength encoding is unverified and is not advertised or written.
- The Fusion outer blue phone-host marker and complete charging semantics for all six HyperOS battery slots remain unverified.
- Automatic colour, official-app ownership handover, background recovery, multi-device artwork and unknown ROMs have not completed full physical acceptance.
- No adb device was available for this release workflow. Instrumentation execution and the complete physical acceptance checklist were not run; public documents do not describe them as passing.

## Documentation

- [Project status and evidence (English)](docs/PROJECT_STATUS.md) / [中文](docs/PROJECT_STATUS.zh-CN.md)
- [Beta.3 release notes (English)](docs/BETA3_RELEASE.md) / [中文](docs/BETA3_RELEASE.zh-CN.md)
- [Public beta acceptance (English)](docs/PUBLIC_BETA_ACCEPTANCE.md) / [中文](docs/PUBLIC_BETA_ACCEPTANCE.zh-CN.md)
- [Beta delivery and signing](docs/BETA_DELIVERY.md)
- [Safe control test](docs/CONTROL_TEST.md)
- [Read-only diagnostic](docs/READ_ONLY_DIAGNOSTIC.md)
- [Background state monitor](docs/BACKGROUND_STATE.md)
- [Protocol schema](docs/PROTOCOL_SCHEMA.md) · [System bridge probe](docs/SYSTEM_BRIDGE_PROBE.md)
- [Official colour and artwork evidence](docs/OFFICIAL_COLOR_FINDINGS.md) · [Current handoff and unresolved issues](docs/CURRENT_SESSION_HANDOFF.md)
- [Release history](CHANGELOG.md)

Historical beta.1 and beta.2 documents are retained for the implementation timeline. They do not describe the public beta.3 artifact.

## Build from source

Use JDK 21, Android SDK Platform 37 and Build Tools 36.1.0. AGP 9.3.1 uses Kotlin 2.4.0; Java/Kotlin bytecode targets remain 17. Configure `JAVA_HOME` and `ANDROID_HOME` (or an untracked `local.properties`), then run. SDKs, toolchains, keystores and passwords are excluded from the repository.

```powershell
$env:GRADLE_USER_HOME = Join-Path $PWD '.gradle-user-home'
$env:ANDROID_USER_HOME = Join-Path $PWD '.android-user-home'
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-daemon
```

Release signing uses `TECHNICSPODS_KEYSTORE`, `TECHNICSPODS_STORE_PASSWORD`, `TECHNICSPODS_KEY_ALIAS` and `TECHNICSPODS_KEY_PASSWORD`. Keep the keystore and passwords outside the repository. Without a release key, `assembleRelease` produces an unsigned APK that cannot be used as a public installation package. See [beta delivery](docs/BETA_DELIVERY.md) for packaging boundaries.

## Development credit

As of 2026-10-11, the maintainer estimates that more than 500 million GPT tokens were used in the development of this project. Models involved include GPT-5.6-sol, GPT-6.0-Astra, and GPT-6.1-sol. This is a maintainer-provided estimate, not an OpenAI-endorsed or independently audited usage report.

## License and provenance

TechnicsPods source code is licensed under [GPL-3.0](LICENSE). The reviewed five AZ100 artwork finishes come from Technics Audio Connect resources; their source, signer and resource hashes are recorded in `NOTICE` and the artwork manifests. These third-party assets are not relabeled as GPL code. No Technics Audio Connect code is bundled.
