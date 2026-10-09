# EAH-AZ100 noise-cancelling level findings

## Evidence set

- Capture: `technics-az100-capture-20260829-222737.jsonl`
- Official app: Technics Audio Connect 4.4.0
- Earbud firmware: `JNZ1ZE 1.040`
- Capture duration: about 42.9 seconds
- User sequence: `1, 25, 50, 75, 100`

The capture contains eight RFCOMM writes and eight reads. Level 1 was already selected at the start, so the four subsequent changes produced the recorded exchanges.

## Encoding

Noise-cancelling level is not a separate command. It is the second payload byte of `SET_OUTSIDE_CONTROL` (`0x000B`):

```text
[mode, noiseCancellingLevel, ambientLevel]
```

With mode `1` (noise cancelling) and the remembered ambient level at 100:

| UI level | Hex | Set frame | Read-back |
| ---: | ---: | --- | --- |
| 25 | `0x19` | `055A05000B00011964` | `055B06000A0000011964` |
| 50 | `0x32` | `055A05000B00013264` | `055B06000A0000013264` |
| 75 | `0x4B` | `055A05000B00014B64` | `055B06000A0000014B64` |
| 100 | `0x64` | `055A05000B00016464` | `055B06000A0000016464` |

Every value is transmitted directly without scaling or an offset.

## Minimum value

No packet was emitted for level 1 because it was the unchanged starting value. Static inspection of the official UI confirms that a circular-slider value of 0 is converted to 1 before `setOutsideCtrl`, while every non-zero value is passed through unchanged. The valid user-facing and protocol range is therefore 1–100.

## Distinct adjustment field

The level above is separate from command `0x0038` (`GET_NOISE_CANCELLING_ADJUSTMENT`), which returned 40 during A. That setting belongs to a different calibration/optimization feature and must not be shown as the main noise-cancelling strength.

EAH-AZ100 does not expose an ambient-strength slider in Audio Connect 4.4.0. Although the outside-control packet retains an ambient-level byte, TechnicsPods must preserve the current value rather than expose an unverified control.

## Implementation rule

The typed frame builder now accepts only level values 1–100. A future system slider can use that range directly. When changing only the noise-cancelling level, it must preserve the most recently read ambient-level byte.
