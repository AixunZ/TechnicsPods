# AZ100 guarded control

The shared module-process write runtime was introduced in `0.10.0-beta.1` and is retained in the current `0.10.0-beta.2`. The homepage has no daily controls. Advanced diagnostics exposes off / ANC / ambient commands and the verified AZ100 `1..100` noise level. The old separate fixed-75 round-trip client has been removed.

## Current device feedback — 2026-10-12

The user has confirmed mode control and state updates in both system entry points, Settings haptic feedback, the status-bar headset icon, and now Settings Bluetooth detail artwork replacement. MiLink/control-center artwork also replaces, with source-resolution softness remaining. The module's device-page **选择 AZ100**, **耳机颜色** and **设备图片** rows were reported working on beta.3/32 on Xiaomi 17 Ultra (codename `nezha`) / HyperOS 4 and Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package. The earlier `nezha` failure report describes the same Xiaomi 17 Ultra and was superseded by its later main-workflow report. This feedback does not complete the broader coexistence, multi-device and lifecycle acceptance matrix.

## Transaction

1. Bind the selected paired device and validate a successful model-handshake response.
2. Read the current outside-control mode, noise level and ambient level.
3. Send the typed `SET_OUTSIDE_CONTROL` (`0x000B`) request, preserving the ambient byte.
4. Require the matching successful ACK.
5. Force a `GET_OUTSIDE_CONTROL` (`0x000A`) read-back and compare all three fields.
6. Close the socket before the runtime releases its worker slot.

A missing ACK, mismatched read-back or failed recovery never publishes success. Best-effort restoration is attempted only within the original transaction deadline. Cancellation, official takeover, disconnection or timeout prohibits further recovery writes. A cancelled session cannot update a replacement session.

The write transaction has a 15-second total deadline. Read-only sampling has a 45-second total deadline. Polling waits 30 seconds after each completed sample; it stops active reads while disconnected or owned by the official app.

## Device test

Stop capture, select the AZ100 and connect Bluetooth audio. For the exclusive baseline, force-stop Technics Audio Connect. Run a diagnostic read before testing mode/level writes. The app requires confirmation and disables controls while another transaction runs.

Test official coexistence separately: open the official app during a module transaction, keep it connected in the background, and then close or crash it. The module must cancel/yield, reject clicks during ownership, and perform a fresh read after release. Do not expect rejected clicks to execute later.

## System surface limits

Settings and MiLink mode requests use the same Provider/runtime transaction. Native Settings ANC-strength encoding is not established, so its level writes are unavailable in this beta. AZ100's protocol percentage is established; that does not establish the host slider encoding. Settings image replacement now uses selected-device identity and a conservative single-header candidate, and has been confirmed on the user's current ROM. A stable Settings resource ID and the full image lifecycle matrix remain unverified; see [artwork-beta2.md](artwork-beta2.md).

The general control model-handshake currently checks response type, command and success status. The official APK now establishes AZ100 family IDs `0x00A0..0x00A3`; the read-only color path checks those IDs before querying color. Strict model-ID enforcement for every control transaction remains an implementation/acceptance gap. Address selection, name discovery and the exact RFCOMM UUID reduce accidental targeting but are not a substitute for that validation. See [OFFICIAL_COLOR_FINDINGS.md](OFFICIAL_COLOR_FINDINGS.md) for the APK evidence.
