package dev.technics.pods.protocol;

import java.nio.charset.StandardCharsets;

/** Parses command 0x00F0, whose payload is a counted list of command-id/length/value entries. */
public final class Az100AllDataParser {
    private Az100AllDataParser() {}

    public static Az100StateSnapshot parse(RaceFrame frame) {
        if (frame.type() != RaceFrame.TYPE_RESPONSE
                || frame.commandId() != PanasonicCommand.GET_ALL_DATA) {
            throw new IllegalArgumentException("Not an AZ100 GET_ALL_DATA response");
        }

        byte[] payload = frame.payload();
        if (payload.length < 2 || RaceFrame.unsigned(payload[0]) != 0) {
            throw new IllegalArgumentException("GET_ALL_DATA failed or is truncated");
        }

        Integer outsideMode = null;
        Integer noiseLevel = null;
        Integer ambientLevel = null;
        Integer noiseAdjustment = null;
        Integer cradleBattery = null;
        Integer leAudio = null;
        Integer currentStatus = null;
        String cradleVersion = null;

        int itemCount = RaceFrame.unsigned(payload[1]);
        int cursor = 2;
        for (int item = 0; item < itemCount; item++) {
            if (cursor + 3 > payload.length) {
                throw new IllegalArgumentException("GET_ALL_DATA item header is truncated");
            }
            int commandId = RaceFrame.unsigned(payload[cursor])
                    | (RaceFrame.unsigned(payload[cursor + 1]) << 8);
            int length = RaceFrame.unsigned(payload[cursor + 2]);
            cursor += 3;
            if (cursor + length > payload.length) {
                throw new IllegalArgumentException("GET_ALL_DATA item value is truncated");
            }

            boolean success = length > 0 && RaceFrame.unsigned(payload[cursor]) == 0;
            if (success) {
                switch (commandId) {
                    case PanasonicCommand.GET_OUTSIDE_CONTROL -> {
                        requireLength(length, 4, commandId);
                        outsideMode = RaceFrame.unsigned(payload[cursor + 1]);
                        noiseLevel = RaceFrame.unsigned(payload[cursor + 2]);
                        ambientLevel = RaceFrame.unsigned(payload[cursor + 3]);
                    }
                    case PanasonicCommand.GET_NOISE_CANCELLING_ADJUSTMENT -> {
                        requireLength(length, 2, commandId);
                        noiseAdjustment = RaceFrame.unsigned(payload[cursor + 1]);
                    }
                    case PanasonicCommand.GET_CRADLE_BATTERY -> {
                        requireLength(length, 2, commandId);
                        cradleBattery = RaceFrame.unsigned(payload[cursor + 1]);
                    }
                    case PanasonicCommand.GET_LE_AUDIO -> {
                        requireLength(length, 2, commandId);
                        leAudio = RaceFrame.unsigned(payload[cursor + 1]);
                    }
                    case PanasonicCommand.GET_STATUS -> {
                        requireLength(length, 2, commandId);
                        currentStatus = RaceFrame.unsigned(payload[cursor + 1]);
                    }
                    case PanasonicCommand.GET_CRADLE_VERSION -> {
                        cradleVersion = new String(
                                payload,
                                cursor + 1,
                                length - 1,
                                StandardCharsets.US_ASCII
                        ).replace("\u0000", "").trim();
                    }
                    default -> {
                        // Unknown and currently unused items remain available in the raw fixture.
                    }
                }
            }
            cursor += length;
        }

        return new Az100StateSnapshot(
                outsideMode,
                noiseLevel,
                ambientLevel,
                noiseAdjustment,
                cradleBattery,
                leAudio,
                currentStatus,
                cradleVersion
        );
    }

    private static void requireLength(int actual, int expected, int commandId) {
        if (actual < expected) {
            throw new IllegalArgumentException(
                    "GET_ALL_DATA item 0x" + Integer.toHexString(commandId) + " is truncated"
            );
        }
    }
}
