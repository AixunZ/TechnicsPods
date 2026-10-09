# Capture and protocol fixture schema

Each capture record is one JSON object per line.

```json
{
  "schema": "technics-transport-capture-v2",
  "wallTimeMillis": 0,
  "elapsedNanos": 0,
  "session": "az100-YYYYMMDD-HHMMSS",
  "process": "com.panasonic.technicsaudioconnect",
  "thread": "main",
  "direction": "TX",
  "operation": "writeCharacteristic",
  "serviceUuid": "",
  "characteristicUuid": "",
  "descriptorUuid": "",
  "status": -1,
  "writeType": 2,
  "payloadLength": 0,
  "payloadHex": "",
  "note": ""
}
```

`direction` is one of:

- `TX`: an outgoing GATT call or bytes written to a Bluetooth socket.
- `RX`: a GATT callback or bytes read from a Bluetooth socket.
- `META`: process, connection or user session markers.

The raw capture must remain immutable and private. Hook metadata is reduced, but opaque vendor payloads may contain paired-device names or identifiers. Derived, inspected protocol fixtures are stored separately under `protocol-fixtures/` with this shape:

```json
{
  "name": "anc_off_to_on",
  "firmware": "JNZ1ZE 1.040",
  "precondition": "external sound control is off",
  "tx": ["HEX"],
  "rx": ["HEX"],
  "expectedState": {
    "noiseControlMode": "noise_cancelling"
  }
}
```

Protocol code must be developed against these fixtures before it is allowed to send data to a real device. Release builds will not contain a raw-HEX sender.

Do not copy whole JSONL captures into the repository. Include only the minimum complete frames needed to reproduce a parser test, and verify every retained payload for device names and identifiers.

Transition fixtures may replace the top-level `tx`, `rx` and `expectedState` fields with a chronological `sequence`. Every sequence entry contains one semantic operation, its complete write/read-back exchange and the expected state, for example:

```json
{"operation":"off_to_noise_cancelling","tx":["HEX"],"rx":["HEX"],"expectedState":{"noiseControlMode":"noise_cancelling"}}
```

The array order is significant and must match the capture timeline.

Lifecycle fixtures may omit `tx` or `rx` and include inspected `meta` events such as `socket-close`. Absence of traffic is evidence only when the fixture records a bounded observation window and states that interpretation explicitly.
