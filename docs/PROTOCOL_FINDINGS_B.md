# EAH-AZ100 noise-control findings

## Evidence set

- Capture: `technics-az100-capture-20260829-221548.jsonl`
- Official app: Technics Audio Connect 4.4.0
- Earbud firmware: `JNZ1ZE 1.040`
- Capture duration: about 49.2 seconds

The capture contains 14 RFCOMM writes and 14 reads. Each write is followed by a success response and a read-back query, giving a complete before/after chain for all six operations.

## Outside control

Command `0x000B` sets outside control with payload `[mode, noiseLevel, ambientLevel]`. Command `0x000A` reads it back and returns `[status, mode, noiseLevel, ambientLevel]`.

| State | Mode byte | Captured set frame | Captured read-back |
| --- | ---: | --- | --- |
| Off | `0` | `055A05000B00006464` | `055B06000A0000006464` |
| Noise cancelling | `1` | `055A05000B00016464` | `055B06000A0000016464` |
| Ambient | `2` | `055A05000B00026464` | `055B06000A0000026464` |

The two level bytes remained `0x64` (100) during this session. Their writable range still needs the C capture; B proves only mode changes at the remembered level.

## Ambient sub-mode

Command `0x0022` sets `[ambientMode, musicBehavior]`; command `0x0021` reads the two values back after a status byte.

- Transparent: ambient mode `0`, keep music playing `0` (confirmed by A idle state and official enum mapping).
- Attention: ambient mode `1`, stop music `1` (directly captured in B as `055A040022000101`).

The official UI deliberately couples these values, so TechnicsPods exposes only the two valid paired combinations rather than arbitrary byte pairs.

## Adaptive ANC

Command `0x0068` sets adaptive ANC with one byte (`0` off, `1` on). Command `0x0067` reads it back:

- On: set `055A0300680001`, read-back `055B040067000001`.
- Off: set `055A0300680000`, read-back `055B040067000000`.

Enabling adaptive ANC was preceded by selecting ordinary noise cancelling. A future system control must preserve that ordering.

## Safety boundary

The typed frame builder added from this capture has no socket or Bluetooth access. It cannot transmit anything by itself. Live control remains disabled until the C level capture is verified and an exclusive-session strategy prevents the official app and TechnicsPods from writing concurrently.
