# Beta delivery

Public candidate: `0.10.0-beta.3` / versionCode `32`. This is the first public test release and the GitHub release must be marked **Pre-release**. The About page developer credit is `AixunZ`. Release signing and local packaging are complete; GitHub upload remains a publishing step. The signing key is supplied through environment variables and no private key or password belongs in this repository.

## Beta.3 public release contract

The GitHub release publishes exactly these locally generated and verified files:

- `TechnicsPods-0.10.0-beta.3-release.apk`
- `TechnicsPods-0.10.0-beta.3-source.zip`
- `TechnicsPods-0.10.0-beta.3-mapping.txt`
- `SHA256SUMS.txt`
- `delivery-manifest.json`

Local output directory: `dist/0.10.0-beta.3` contains only these five fixed attachments. The release APK SHA-256 is `c6257b7105a594d5d1814715bc41594089b0938814b8a37d2c93a3a065051986`; its maintainer certificate SHA-256 is `97598098835abf3b86d18b028333a3c06d575fd7a0f5e14e2a4d438b5b13292d`. The mapping SHA-256 is `06304a1a4fcf9bca32a287ff6e76520f3e44ba8a1a014044cacf8acd1a329465`. The generated `SHA256SUMS.txt` is the source of truth for the source archive and manifest hashes. The manifest records the clean tagged source revision, `sourceDirty=false`, `releaseSignatureVerified=true`, 124 passing unit tests, 0 lint errors/60 warnings, `instrumentationExecuted=false` and `physicalAcceptanceExecuted=false`.

The GitHub Pre-release upload is not represented as complete by local packaging; publish these five files only after reviewing the manifest and repository target.

The release APK must report package `dev.technics.pods`, `versionName=0.10.0-beta.3` and `versionCode=32`. Before publishing, verify the maintainer certificate with `apksigner`, compute SHA-256 for every attachment and inspect the source archive for SDKs, build outputs, captures, logs, keys and the temporary `$out` directory. These checks completed for the local package. Debug, test-signed, unsigned and instrumentation APKs stay local and are not public default downloads.

The three Device-page rows **选择 AZ100**, **耳机颜色** and **设备图片** remain unresponsive on the recorded phone. No adb device was available for this release workflow, so instrumentation execution and complete physical acceptance are not claimed. The partial user-reported system controls, haptics, status-bar and artwork results are documented in [public beta acceptance](PUBLIC_BETA_ACCEPTANCE.md). Repair of those rows is paused for this beta.

## Historical beta.2 artifacts

The `dist/0.10.0-beta.2` directory contains:

- `TechnicsPods-0.10.0-beta.2-debug.apk`: installable local test APK.
- `TechnicsPods-0.10.0-beta.2-release-test-signed.apk`: minified/resource-shrunk Release candidate signed with the same local test identity.
- `TechnicsPods-0.10.0-beta.2-release-unsigned.apk`: Release output for separate maintainer signing.
- `TechnicsPods-0.10.0-beta.2-ipc-tests.apk`: existing Provider, artwork-cache and Compose/lifecycle instrumentation package; it predates the added Device-row click case.
- `TechnicsPods-0.10.0-beta.2-mapping.txt`: crash retracing and adapted Xposed entry mapping.
- `TechnicsPods-0.10.0-beta.2-source.zip`: synchronized current working-tree snapshot, excluding local tools, SDK, captures, keys, diagnostics and the temporary `$out` extraction directory. This snapshot is newer than the retained APKs.
- `delivery-manifest.json` and `SHA256SUMS.txt`: sizes, hashes, base source revision, working-tree status and verification results.

The test certificate remains `610bdf09702579a3c9328886e5d514c266e1ef573abd1bd7abea37d487874446`, matching beta.1. The user tested the candidate on the phone, but did not identify the installed APK variant/hash or provide a dedicated upgrade-install result. Maintainer signing and upload are separate; no maintainer private key is included.

## Historical beta.2 verification

Local checks pass: all 124 unit tests, Debug, minified Release and Android instrumentation APK compilation. Lint has no errors; the exact warning count is recorded in the generated delivery manifest. Release packaging preserves manifest components, the artwork manifest and adapted `META-INF/xposed/java_init.list`, `module.prop` and `scope.list`. Debug, minified test-signed Release and instrumentation APK signatures are verified during packaging.

User reports confirm both system entries' control/state updates, Settings haptics, status-bar rendering and MiLink artwork replacement. The 2026-10-11 follow-up confirms Settings artwork replacement succeeds but the Device-page rows **选择 AZ100**, **耳机颜色** and **设备图片** remain unresponsive. Further repair is paused at the user's request. The report is partial physical acceptance, not a complete checklist or an adb instrumentation run. Compose click cases compile but have not been executed; the existing instrumentation APK does not include the latest click case. See [acceptance results](PUBLIC_BETA_ACCEPTANCE.md).

The supplied Technics Audio Connect 5.0.1 APK was reviewed and five AZ100 finish sets are bundled with provenance and SHA-256 hashes. Settings artwork uses an identity-bound conservative header candidate, now user-confirmed for basic rendering. Automatic colour selection, manual override/import, device switching and all lifecycle/fallback cases have not been individually accepted. See [artwork mapping](artwork-beta2.md).

## Historical beta.2 retained artifact boundaries

The status sync keeps the tested APK bytes and hashes. The latest Debug predates the final `diagnostics` guard around detailed Settings image logging; APK bytecode calls `logSettingsImages` directly while the current compiled source checks that flag. This discrepancy is recorded, not repaired by producing a new APK. The minified Release and mapping were generated later; the latest user report does not establish which variant was installed.

The old source ZIP predated the last hook/UI/click-test edits and is replaced by a current source snapshot. Its prior contents remain in ignored `.diagnostics/status-sync-20261011/previous-source.zip`. `sourceRevision` records the base Git commit `2d34e52b6730bebe027410e89c2ac9dc7778fcc1`; `sourceIncludesWorkingTree` and `sourceDirty` are true. These fields describe the source snapshot, not an exact source-to-APK reproduction claim.

| Retained APK | SHA-256 |
| --- | --- |
| Debug | `ea314234ca1257ae814ba600bd576edb26168d6dfb84b4267aa022e632dd5fdf` |
| Test-signed Release | `24142cd432a197446ff05c22563821840cd22f0b56906f1003185ef5b3440004` |

The delivery manifest retains `physicalAcceptanceExecuted=false` for complete-checklist execution and adds the partial user-report status separately. Unit/Lint values describe the previous validation run; this documentation-only sync does not rerun Android builds. The source archive and checksum list are refreshed after documentation synchronization.

## Reproduce public beta.3

Run the full Gradle check from README with the maintainer signing variables configured before packaging. The public package was generated with `tools/package-beta.ps1 -PublicRelease -WorkingTreeSource`; local extraction outputs such as `$out` must be excluded first. The script builds no artifacts and uploads nothing. Public packaging requires the signed `app-release.apk`; verify its certificate/version/package and the source archive before creating the GitHub Pre-release.

Test files and raw diagnostics stay local. Publishing the unsigned APK is not a signed public release.
