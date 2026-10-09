# AZ100 guarded control

Version `0.10.0-beta.1` routes diagnostic and system writes through the same module-process runtime. The homepage has no daily controls. Advanced diagnostics exposes off / ANC / ambient commands and the verified AZ100 `1..100` noise level. The old separate fixed-75 round-trip client has been removed.

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

Settings and MiLink mode requests use the same Provider/runtime transaction. Native Settings ANC-strength encoding is not established, so its level writes are unavailable in this beta. AZ100's protocol percentage is established; that does not establish the host slider encoding. Settings image replacement also awaits a confirmed view/resource ID.

The model-handshake parser currently checks response type, command and success status. No inspected capture establishes AZ100's exact model-ID bytes, so strict model-ID validation is a remaining acceptance prerequisite. Address selection, name discovery and the exact RFCOMM UUID reduce accidental targeting but are not a substitute for that missing evidence.
