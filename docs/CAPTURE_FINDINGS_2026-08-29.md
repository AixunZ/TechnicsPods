# Capture findings: 2026-08-29 A-D

## Device and software

- Phone OS: Android 17
- HyperOS: 4.0.0.17
- Technics Audio Connect: 4.4.0
- EAH-AZ100 firmware: JNZ1ZE 1.040
- Common firmware component: VG_CMN_01.00

No Bluetooth address or device name is retained.

## Session mapping

| Label | Session | Start (Asia/Shanghai) | Duration | Intended family | Transport records |
|---|---|---:|---:|---|---:|
| A | az100-20260829-024040 | 02:40:40.613 | 30.377 s | discovery and idle | 0 |
| B | az100-20260829-024156 | 02:41:56.070 | 111.238 s | external sound modes | 0 |
| C | az100-20260829-024446 | 02:44:46.992 | 71.777 s | levels | 0 |
| D | az100-20260829-024643 | 02:46:43.960 | 53.129 s | battery and case | 0 |

Audio Connect 4.4.0 exposes no ambient-sound strength control for EAH-AZ100. Session C therefore covers only controls actually present in the app, such as noise-cancelling strength.

## File structure

The four exports are cumulative rather than independent:

- 024146: A, 2 records
- 024352: A+B, 4 records
- 024600: A+B+C, 6 records
- 024740: A+B+C+D, 8 records

Every record is a local user session marker from the TechnicsPods app process. There is no record from `com.panasonic.technicsaudioconnect`, no GATT lifecycle event, and no TX/RX payload.

## Conclusion

These captures verify that remote preferences, session markers, rotation and JSONL export work. They do not contain enough evidence to infer UUIDs, framing, opcodes or state values.

Two causes remain possible:

1. The official app process was not injected or was not restarted after enabling the module.
2. The AZ100 control plane uses Android BluetoothSocket/RFCOMM rather than GATT.

Version 0.2.0 adds an unconditional `capture-hook-ready` marker and read-only BluetoothSocket stream capture to distinguish these cases on the next run.
