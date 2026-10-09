# EAH-AZ100 cradle and disconnect findings

## Evidence set

- Capture: `technics-az100-capture-20260829-223743.jsonl`
- Official app: Technics Audio Connect 4.4.0
- Earbud firmware: `JNZ1ZE 1.040`
- Capture duration: about 51.1 seconds

The user completed all requested stages. The small record count is itself the relevant result: EAH-AZ100 stops exposing its RFCOMM session once both earbuds enter the cradle, even while the lid remains open.

## Timeline

| Elapsed | User stage | Transport evidence | Interpretation |
| ---: | --- | --- | --- |
| 0–10 s | Both earbuds out | Existing socket remains open | Normal connected state |
| 13.468 s | Left earbud in open cradle | `055D0400000D0000` | TWS partner destination disappears |
| 25.748 s | Both earbuds in open cradle | Three observed `BluetoothSocket.close()` calls | One logical RFCOMM disconnect |
| 25.751–51.109 s | Lid closed, then reopened | No reads, writes or reconnect | Both in-cradle earbuds remain unavailable to the phone |

The three close records are repeated calls through the official stack, not three independent device connections.

## Relay meaning

RACE command `0x0D00` is `RELAY_GET_AVAILABLE_DESTINATION`. Its payload is a list of `[destinationType, destinationId]` pairs. Type `5` represents the partner earbud:

- A idle response `05 06`: partner available with relay id 6.
- D indication `00 00`: no type-5 destination, so the partner is unavailable.

The A session also reported that the connected agent was the right earbud. The D sequence therefore fits the physical actions: placing the left earbud in the cradle removes the partner; placing the right/agent earbud in the cradle ends the socket.

## Battery implications

D contains no left, right or cradle battery update. It is not valid to infer a new percentage or charging flag from the disconnect alone.

The system integration must keep two separate concepts:

1. Last-known left/right/cradle percentages, retained as a cache.
2. Live connection/freshness state, marked disconnected after socket close.

Cached values must never be rewritten to zero on disconnect and must not be presented as freshly sampled. The cradle level can be refreshed only while at least one earbud still provides a transport and returns command `0x0040`/the `0x00F0` state bundle.

## Architectural consequence

An always-on module cannot poll both earbuds after they are placed in the cradle. HyperOS surfaces should show disconnected state and, if appropriate for the target surface, a clearly stale last-known battery value. Reopening the lid with both earbuds still seated does not guarantee a reconnect; an earbud must become available again before live telemetry can resume.
