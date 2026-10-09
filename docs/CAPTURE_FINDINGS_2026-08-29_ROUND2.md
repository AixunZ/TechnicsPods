# Capture findings: 2026-08-29 round 2

## Session mapping

| Label | Session | Start (Asia/Shanghai) | Duration | Records from official app |
|---|---|---:|---:|---:|
| A | az100-20260829-135436 | 13:54:36.762 | 24.845 s | 0 |
| B | az100-20260829-135513 | 13:55:13.128 | 47.771 s | 0 |
| C | az100-20260829-135605 | 13:56:05.285 | 59.187 s | 0 |
| D | az100-20260829-135715 | 13:57:15.416 | 37.686 s | 0 |

The four files are cumulative. The final file contains all four round-1 sessions and all four round-2 sessions. Each new session contains only the local start and stop markers.

## Root cause

Version 0.2.0 sent hook records from the official app process through an explicit cross-UID broadcast. The receiver intentionally validates `BroadcastReceiver.getSentFromUid()` against the Technics Audio Connect package before accepting a record.

On Android 14 and newer, broadcast sender identity is not shared by default. Without `BroadcastOptions.setShareIdentityEnabled(true)`, the receiver cannot access the sender UID and returns `Process.INVALID_UID`. Consequently, every hook-originated record was rejected, including the unconditional `capture-hook-ready` diagnostic. Local session markers were unaffected because they bypass the receiver.

This means round 2 cannot establish whether the hook was injected or which Bluetooth transport AZ100 uses. Absence of the diagnostic was caused by the telemetry path itself.

## Fix

Version 0.2.1 sends capture broadcasts with sender identity sharing enabled while retaining the strict receiver-side package/UID validation. No receiver permission or trust boundary was weakened.
