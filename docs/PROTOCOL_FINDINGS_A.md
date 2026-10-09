# EAH-AZ100 idle-session findings

## Evidence set

- Capture: `technics-az100-capture-20260829-212115.jsonl`
- Official app: Technics Audio Connect 4.4.0 (`versionCode 103`)
- App SHA-256: `E662480EEFE7444132E82A4835CE22595AFC9201D626F92C2906C8701FB9767E`
- Earbud firmware: `JNZ1ZE 1.040`
- Cradle firmware: `JNZ2ZA 01.00`

The APK's v2 signature verifies. Static inspection was used only to confirm protocol constants and field semantics; no proprietary source or asset is included in TechnicsPods.

## Transport and framing

The official app communicates through Bluetooth Classic RFCOMM, not BLE GATT. It opens UUID `00000000-0000-0000-0099-aabbccddeeff` and carries Airoha RACE packets.

| Offset | Size | Meaning |
| --- | ---: | --- |
| 0 | 1 | Channel/start byte, `0x05` |
| 1 | 1 | Type: `0x5A` command, `0x5B` response, `0x5C` no-response command, `0x5D` indication |
| 2 | 2 | Little-endian body length, including the command id |
| 4 | 2 | Little-endian command id |
| 6 | variable | Payload |

The RFCOMM stream can split or combine packets. Consumers must reassemble by the length field rather than assuming one socket read equals one packet.

## Read-only state commands

| Command | Meaning | Relevant payload |
| --- | --- | --- |
| `0x00F0` | Get all data | Counted list of command id, one-byte length and response bytes |
| `0x000A` | Get outside control | Status, mode, noise-cancelling level, ambient level |
| `0x0038` | Get noise-cancelling adjustment | Status, value |
| `0x0040` | Get cradle battery | Status, percent |
| `0x0059` | Get LE Audio state | Status, enabled flag |
| `0x005B` | Get current status | Status, state value |
| `0x0061` | Get cradle version | Status, ASCII version |
| `0x0CD6` | Get TWS battery | Indication contains status, side and percent |
| `0x1C07` | Get earbud firmware | Indication contains status, side, length and ASCII version |

Outside-control mode values are `0` off, `1` noise cancelling and `2` ambient. Battery side `0` is left and `1` is right.

## Session result

This A session contained 84 records: 31 socket writes, 48 socket reads and 5 metadata records over about 25.3 seconds. It showed both earbuds at 100%, cradle at 100%, outside control off, remembered noise/ambient levels at 100, noise-cancelling adjustment at 40, and LE Audio disabled.

The `0x00F0` response has a declared fixed size and zero padding after its counted entries. Parsing must stop after the advertised item count. The derived fixtures under `protocol-fixtures/` contain only the state packets used by tests; the original JSONL is intentionally excluded.

## Safety boundary

This milestone decodes captured traffic only. It does not open an RFCOMM socket or send commands. Control writes remain blocked until B/C captures establish mode and level transitions with repeatable before/after evidence.
