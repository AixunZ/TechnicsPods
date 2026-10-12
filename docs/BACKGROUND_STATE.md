# AZ100 background state monitor

The `0.10.0-beta.2` candidate retains beta.1's opt-in connected-device foreground management. It samples the read-only AZ100 endpoint every 30 seconds through the central runtime used by manual and system control requests. System hooks consume Provider snapshots asynchronously; they do not perform RFCOMM I/O in host callbacks. Official-app handover and disconnect recovery remain pending dedicated physical acceptance.

Status synchronized 2026-10-11: user reports confirm current system control/state updates, Settings haptics, status-bar rendering and both system image replacements. The three Device-page configuration rows were reported working on beta.3/32 on Xiaomi 17 Ultra / HyperOS 4 and Xiaomi 11 Ultra / HyperOS 2 base with a HyperOS 3 upper-layer port package; the earlier `nezha` record remains unresponsive. No broad boot/background/ownership acceptance follows from these device-specific results; see [current handoff](CURRENT_SESSION_HANDOFF.md).

## Physical-device test

1. Force-stop Technics Audio Connect.
2. Take at least one earbud out of the cradle and wait for Bluetooth audio to connect.
3. Open TechnicsPods, grant Bluetooth permission and enable automatic connection management.
4. Confirm that the persistent notification and the homepage show a successful sample.
5. Leave the app and wait for at least two more samples.
6. Put one earbud in the cradle. The unavailable side may remain visible as a cached value while partner availability changes.
7. Put both earbuds in the cradle. The service should pause active sampling while disconnected without replacing cached battery values with zero.
8. Take an earbud out again. A later sample should recover automatically.
9. Disable automatic management or stop the monitor from its notification. Both actions disable automatic sampling; releasing a later official session must not restart a read until management is reenabled or a manual request is made.

Keep Technics Audio Connect stopped during the baseline test. In a separate handover test, opening the official app should cancel/yield the module transaction, mark `official_busy`, and suppress module polling/writes until the official session releases. This coexistence path has unit coverage but is not physically accepted in the beta.

## State semantics

- A successful sample marks the device reachable and timestamps each value that was actually returned.
- A partial sample updates only returned fields. Missing right-ear data cannot erase an older right-ear value.
- A failed sample marks the endpoint unreachable and increments the failure counter.
- Stopping the monitor marks live reachability false but retains all last-known values.
- The app labels values as cached whenever the latest sample did not establish reachability.

The repository belongs to the TechnicsPods app process and is exposed through the restricted state Provider. The HyperOS/MiLink bridge, Fusion route and Settings detail-page write callbacks consume this state; broader stale/offline presentation coverage remains separate work.

## Safety boundary

The service submits read-only requests to `Az100ConnectionRuntime`, which owns the socket/client and rejects overlapping or official-owned transactions. The read client uses the manual diagnostic whitelist. Each poll connects, reads and closes; the monitor does not submit control requests.

## Beta limitations

Basic status-bar rendering is user-confirmed; profile reconciliation, multi-headset ownership and capability-record accuracy still need dedicated acceptance. A cached state is returned while a background Provider read is pending. Settings ANC-strength levels remain unavailable until the host encoding is verified. Device-page selection is host-specific: reported working on the two beta.3/32 hosts above and inaccessible in the earlier `nezha` record.

If official ownership cannot be confirmed after process recovery, the module keeps yielding. Restart Technics Audio Connect to reannounce its sessions, or turn Bluetooth fully off and on. A confirmed adapter-off state releases the recovery lock because no live Bluetooth socket can remain; subsequent connection/state reads must establish freshness again.
