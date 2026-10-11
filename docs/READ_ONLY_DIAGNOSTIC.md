# EAH-AZ100 read-only diagnostic

The independent RFCOMM client and shared module-process runtime introduced in `0.10.0-beta.1` are retained in `0.10.0-beta.2`. Manual diagnostics and the opt-in background monitor submit read-only requests through that owner; control requests use the same exclusive queue.

As of the user's 2026-10-11 retest, both system entry points control and update state normally, and Settings artwork replacement succeeds. The module device-page **选择 AZ100**, **耳机颜色** and **设备图片** entries remain unresponsive, so the picker/import instructions are not a confirmed working path in this build. The user has paused further repair; this document records the current behavior rather than a completed UI fix.

## Before running

1. Pair the EAH-AZ100 in Android Bluetooth settings.
2. Force-stop Technics Audio Connect so that two apps do not compete for the proprietary RFCOMM service.
3. Take at least one earbud out of the case and wait until it is powered and connected.
4. Open TechnicsPods, enter **诊断与协议日志**, and tap **读取 AZ100 状态**.
5. Grant the Nearby devices permission when Android asks.
6. When the run finishes, tap **复制诊断结果** and share that text for analysis.

The copied result omits the Bluetooth device name and address. It can contain decoded battery, operating-mode and firmware values, but not raw vendor payloads.

## Safety boundary

The diagnostic transport can emit only the query frames constructed by `Az100ReadFrames`. The current whitelist is:

| Command | ID | Purpose |
| --- | ---: | --- |
| Model ID | `0x0000` | Confirm that the endpoint speaks the expected protocol |
| Device color | `0x0002` | Read color only after an AZ100 family model ID is confirmed |
| Relay destinations | `0x0D00` | Determine whether the partner earbud is reachable |
| Outside control | `0x000A` | Read off/ANC/ambient state and remembered levels |
| Ambient mode | `0x0021` | Read transparent/attention submode |
| Cradle battery | `0x0040` | Read the charging-case value when available |
| Adaptive ANC | `0x0067` | Read adaptive-ANC state |
| Earbud battery | `0x0CD6` | Read one side's battery indication |
| Firmware version | `0x1C07` | Read one side's firmware string |

Control IDs, firmware-transfer IDs, reset commands and pairing commands are not exposed by this client. Individual `GET_STATUS` requests were removed after two physical-device runs consistently timed out; the same field remains supported only when parsing a captured `GET_ALL_DATA` bundle. A model-handshake failure stops the run; later query failures are reported as missing fields. The socket is closed after success, failure, cancellation or timeout.

The optional background monitor repeats this proven one-shot read every 30 seconds from an Android connected-device foreground service. See [BACKGROUND_STATE.md](BACKGROUND_STATE.md).

## Interpreting the first test

- A complete result confirms that Android permits an independent app to use the vendor RFCOMM endpoint and that the captured read frames can initialize the session.
- A handshake failure means the expected response was not confirmed; control refuses to write unless the model response and current ANC state both succeed. Additional official initialization is one possible cause, not a conclusion from a failure alone. The general handshake checks response type, command and success status; the official APK now establishes AZ100 family IDs `0x00A0..0x00A3`, which are checked before the optional color query. This does not yet mean all control transactions strictly enforce those IDs.
- Missing optional fields can be caused by one earbud being in the case, a response variation, or an unsupported individual query. They are evidence for refining the parser, not permission to broaden the command set.
- Color parsing, persistence and automatic asset selection are implemented from APK evidence, including black, silver, blue, gold and violet. Unknown values remain unknown and do not overwrite a confirmed color. Automatic identification has not been separately accepted on-device; see [OFFICIAL_COLOR_FINDINGS.md](OFFICIAL_COLOR_FINDINGS.md).
