# Changelog

This file records implementation milestones and hardware verification notes. It is intentionally separate from the long-form evidence in `docs/PROJECT_STATUS.md`.

## 0.10.0-beta.1 - Test Candidate (2026-10-09, versionCode 30)

- Added exact A2DP connection-state discovery for the `wireless_headset` status slot, multi-profile/address tracking, startup reconciliation and safe unknown-ROM no-op behavior.
- Added central capability IPC with installed/unavailable status and hook counts.
- Added XML Material DayNight homepage, lifecycle-driven updates, large-font/TalkBack support, device selection, permission/recovery actions, and a separate diagnostic page.
- Added bounded local/installed-APK artwork imports without distributing proprietary artwork.
- Split battery/control freshness, selected-MAC identity and per-session generation checks.
- Added asynchronous Provider snapshot reads and central control-request forwarding from system hooks.
- Added a central module-process connection owner, opt-in foreground management and official-app session handover with cancellation/generation guards. Physical coexistence acceptance is pending.
- Bound host requests and transport clients to the selected device, rejected callbacks from old sessions, and repaired snapshot/connection-event ordering.
- Stopping automatic management suppresses autonomous recovery reads; confirmed-disconnected official sockets are pruned before ownership replay.
- Preserved diagnostic results across Activity recreation, queued image-selector results, and moved document export to a background worker.
- Added verified MiLink artwork replacement for known AZ100 image IDs.
- Restricted Settings ANC-strength level handling until native host encoding is verified; no ratio or token mapping is claimed.
- Debug/test-signed APK and release-signed APK are separate artifacts. Maintainer release signing/upload remains pending.
- This beta has not been connected to adb or physically accepted in this session; prior hardware evidence applies only to the recorded baseline.

## 0.9.15 - Unreleased (2026-10-09)

- Fixed the legacy Settings Bluetooth-detail entry: `updateAncMode(int, boolean)` and `updateAncLevel(int, boolean)` user actions now use the guarded AZ100 RFCOMM transaction.
- Settings actions preserve the current ambient value, require ACK and read-back confirmation, publish the result through the restricted Provider, and refresh the active detail fragment.
- Internal Settings UI replay calls and overlapping requests are ignored safely; a failed or unavailable control does not fabricate a new mode.
- Added policy tests for mode/level argument parsing. The Settings mode/level path was verified on the supplied test phone after the fix.

## 0.9.14 - Unreleased (2026-09-27)

- User-confirmed baseline: Fusion Device Center and Bluetooth detail pages display and switch off / ANC / ambient modes.
- Removed the permanent per-process mode override. Only confirmed read-back results are published to the restricted state Provider.
- System setters now dispatch without waiting for Bluetooth I/O and return the last confirmed mode (or unknown), followed by host property notifications after completion.
- Added cross-process state observers, Settings refresh, shared repository locking, per-control freshness and protection against older in-flight monitor samples.
- Added a one-shot 15-second control deadline, transport cancellation and checks before every protocol exchange. Timed-out/cancelled operations cannot publish success or send recovery writes.
- Added regression tests for lost ACKs, mismatched read-back, cancellation, deadline expiry, state ordering and external mode changes.
- Hardware acceptance of the new notification path is pending; see docs/PHASE1_STATE_SYNC.md. Connection coordination, official-app coexistence and later features remain out of scope.

## 0.9.13 - 2026-08-31

### Fixed

- Fusion Device Center now waits for the asynchronously initialized `fc.t` HeadsetContentController after creating the AZ100 card.
- Added bounded delayed retries for `fc.t.b0()`, which is the native entry used by the direct earphone shortcut.
- Verified that the Fusion route reaches `com.miui.circulateplus.world.headset.HeadsetDetailFragment` on the test phone.

### Verified

- LSPosed log contains `AZ100 Headset controller call=fc.t:b0()` and `Routed AZ100 MainCardView request to HeadsetContentController`.
- Fusion Device Center no longer stops at the ordinary Bluetooth card.

## 0.9.12 - 2026-08-31

### Fixed

- Recognized the ROM-specific `wc.a.q()` display configuration label `SOUND_BLUETOOTH_CONTROL` as the stock headset panel.
- Fusion `MainCardView.k()` now selects the system headset display configuration instead of falling back to `BluetoothCardView`.

## 0.9.10-0.9.11 - 2026-08-31

### Added

- Hooked `MainCardView.k()` and added reflective matching between `wc.a` display configs and `wc.b` panels.
- Added AZ100 service caching, HeadsetContentController discovery and native click-method inspection.

### Diagnosis

- The first display-config matcher relied on `wc.a.s()` labels containing `headset`, `earphone` or `earbud`. The test ROM used `SOUND_BLUETOOTH_CONTROL`, so the matcher returned `null` until 0.9.12.

## 0.9.2 - 2026-08-30

- Added the dedicated Settings headset-page bridge for the legacy `MiuiHeadsetActivity` path.
- Added restricted cross-process state access, AZ100 identity/connection checks, three-slot battery rendering and read-only ANC/state rendering.
- Historical note: Settings write callbacks were signature-observed but not forwarded in 0.9.2; v0.9.15 adds the guarded forwarding path.

## 0.9.1 - 2026-08-30

- Added the protective MiLink/HyperOS bridge for AZ100 identity, battery slots and read-only ANC/state mapping.
- Confirmed the tested ROM already stores the AZ100 battery value in `com.android.bluetooth`.

## 0.7.0 - 2026-08-29

- Added guarded outside-control writes with ACK, forced read-back and best-effort restoration.
- Added the user-facing ANC level and outside-control mode test controls.

## 0.5.0-0.6.0 - 2026-08-29

- Added persistent state cache, background foreground-service monitoring and read-only diagnostic UI.
- Added capture privacy boundaries and partial-read/disconnect semantics.

## 0.4.0 - 2026-08-29

- Added the independent read-only RFCOMM/RACE client and verified protocol fixtures for EAH-AZ100.
