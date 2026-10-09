# Beta delivery

Build candidate: `0.10.0-beta.1` / versionCode `30`, built 2026-10-09 on the recorded Windows workspace.

Artifacts:

- `dist/0.10.0-beta.1/TechnicsPods-0.10.0-beta.1-debug.apk`: installable debug APK, signed with the local Android debug/test certificate.
- `dist/0.10.0-beta.1/TechnicsPods-0.10.0-beta.1-release-test-signed.apk`: minified Release output signed with that same test certificate, for testing shrink behavior.
- Its optional `.apk.idsig` sidecar supports incremental installation; its checksum is included when generated.
- `dist/0.10.0-beta.1/TechnicsPods-0.10.0-beta.1-release-unsigned.apk`: minified and resource-shrunk Release output; intentionally unsigned.
- `dist/0.10.0-beta.1/TechnicsPods-0.10.0-beta.1-ipc-tests.apk`: Android instrumentation test APK.
- `dist/0.10.0-beta.1/TechnicsPods-0.10.0-beta.1-mapping.txt`: Release mapping for crash retracing.
- `dist/0.10.0-beta.1/TechnicsPods-0.10.0-beta.1-source.zip`: tracked source snapshot excluding local toolchains, diagnostics, keys and captures.
- `dist/0.10.0-beta.1/SHA256SUMS.txt`: artifact checksums.

The APKs were rebuilt from the current source before delivery. `delivery-manifest.json` records the committed source revision, exact sizes, hashes and check results; `SHA256SUMS.txt` also covers that manifest. The source archive's own size/hash is recorded externally in those two files. The maintainer must sign the unsigned Release output, verify its certificate and hash, and upload that signed artifact separately. No maintainer signing key is stored in this repository.

Run `tools/package-beta.ps1` after the full build and a clean, reviewed source commit. It copies artifacts, signs the optional minified candidate with the existing local debug key, verifies all test certificates, archives committed source and generates the delivery manifest. It neither builds nor uploads. The default SDK path is `.android-sdk`; use `-SdkPath` for another local SDK.

| Artifact suffix | Bytes | SHA-256 |
| --- | ---: | --- |
| `debug.apk` | 15162336 | `b9e13b827a60a0eea48932249ae7edb1d593e39c6f6639aaa4552256432b78ab` |
| `release-test-signed.apk` | 1799123 | `8797e33cb9f830a3e6b3e0f5c91f5a17516821b764ea7848624cbf583673e782` |
| `release-unsigned.apk` | 1740704 | `43969fda4ab4c923522cfb0fea4a19dd87143e7405df7021f5a506dda2bba70e` |
| `ipc-tests.apk` | 845603 | `65fd4f163b96370ee2474b76de40bd3eac094c860ace34db43a52ccfd2d2c44d` |
| `mapping.txt` | 6341209 | `85bd0eccef8dae9fd7f7dc2e6aabf73fddefa2ffdb8bb8f884d17ed20a5cfe91` |

Debug, test-signed Release and instrumentation artifacts share test-certificate SHA-256 `610bdf09702579a3c9328886e5d514c266e1ef573abd1bd7abea37d487874446`. Certificate equality was verified; actual upgrade installation still requires a device. A build signed by a different previous key cannot update the installed package in place.

Verification completed:

- 97 local unit tests passed, 0 failures, 0 errors, 0 skipped.
- `lintDebug`: 0 errors, 36 warnings in the final successful full check.
- `assembleDebug`, `assembleRelease` and `assembleDebugAndroidTest` passed.
- Release mapping retains manifest components and the Xposed entry; the unsigned output contains adapted `META-INF/xposed/java_init.list`, `module.prop` and `scope.list`.
- Debug, minified test-signed Release and IPC APK signature verification passed. The unsigned Release output is expected to fail signature verification until signed.

Not run in this workspace: adb/instrumentation execution, screenshots at narrow width and font scale 2, physical status-bar rendering, system artwork replacement, official-app handover, boot/background restrictions, upgrade install and unknown-ROM behavior. See [PUBLIC_BETA_ACCEPTANCE.md](PUBLIC_BETA_ACCEPTANCE.md).

Remaining evidence prerequisites: exact AZ100 model-ID response bytes; verified Settings product image IDs; native Settings ANC-level encoding; MiLink outer-card artwork view identity and color selection. Installed-APK artwork import accepts explicit AZ100 raster filenames only; unnamed/vector resources may require user import. Test-signed artifacts are candidates, not a maintainer-signed public release.
