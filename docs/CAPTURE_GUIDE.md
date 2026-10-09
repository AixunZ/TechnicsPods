# EAH-AZ100 controlled capture guide

## Prerequisites

- A rooted Xiaomi or Redmi phone with a libxposed API 102 compatible framework.
- Technics Audio Connect installed as `com.panasonic.technicsaudioconnect`.
- EAH-AZ100 paired and updated to a known firmware version.
- Bluetooth HCI snoop logging is optional but strongly recommended for the first session.

Before sharing a capture, record the phone model, Android version, HyperOS build, Audio Connect version and earbud firmware in a separate text note. Do not include the Bluetooth address.

## Installation

1. Install the debug APK.
2. Enable TechnicsPods in LSPosed.
3. Select only the Technics Audio Connect scope.
4. Force-stop Technics Audio Connect or restart its scope.
5. Open TechnicsPods and start a new capture session before opening the official app.

Every valid v2 export must contain a `META/application-on-create` record with note `capture-hook-ready`. If it is absent, the official app process was not injected and transport records are not trustworthy.

## Session A: discovery and idle state

1. Open Technics Audio Connect.
2. Wait until the home screen is fully populated.
3. Do not touch any setting for 15 seconds.
4. Return to TechnicsPods and stop the capture.

This session identifies connection setup, MTU, services, subscriptions, initial queries and unsolicited state notifications.

## Session B: external sound controls

Start from a known `Off` state. Wait five seconds between each operation:

1. Off to Noise Cancelling.
2. Noise Cancelling to Ambient Sound / Transparent.
3. Transparent to Ambient Sound / Attention.
4. Attention to Off.
5. Enable Adaptive / Auto-optimization.
6. Disable Adaptive / Auto-optimization.

Do not adjust a level in this session.

## Session C: levels

Use a fresh session for each control family:

- Noise cancelling: 0, 25, 50, 75, 100.
- EAH-AZ100 ambient sound has no strength control in Audio Connect 4.4.0, so skip this family.

Return to the same baseline before every value. This distinguishes absolute values from increments.

## Session D: battery and charging case

1. Capture with both earbuds out of the case.
2. Place only the left earbud in the case and wait ten seconds.
3. Place both earbuds in the case, keep the lid open and wait ten seconds.
4. Close the lid, wait ten seconds, then reopen it.

The case may expose its level only while open or relay it through an earbud. Treat stale case values as cached state.

## Avoid during protocol discovery

- Firmware update
- Factory reset
- LE Audio priority changes that erase pairing data
- Voice prompt firmware/language transfer
- Repeated raw writes

## Sharing

Use the module's export button. Hook metadata excludes Bluetooth addresses and names, but vendor payloads may themselves contain paired-device names, addresses or other identifiers. Treat every raw JSONL export as sensitive: share it privately with the developer, and do not publish it without protocol-aware redaction.

Only derived, minimal frames that have been inspected for identifiers belong under `protocol-fixtures/`. Never copy a complete capture into the repository.
