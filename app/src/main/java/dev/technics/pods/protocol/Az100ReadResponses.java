package dev.technics.pods.protocol;

import java.nio.charset.StandardCharsets;

/** Parsers for individual read-only diagnostic responses. */
public final class Az100ReadResponses {
    private Az100ReadResponses() {}

    public record FirmwareVersion(int side, String version) {}

    public static void requireSuccessfulModelResponse(RaceFrame frame) {
        successfulResponse(frame, PanasonicCommand.GET_MODEL_ID, 1);
    }

    /** The official model callback reads two little-endian bytes after the response status. */
    public static int modelId(RaceFrame frame) {
        byte[] payload = successfulResponse(frame, PanasonicCommand.GET_MODEL_ID, 3);
        return RaceFrame.unsigned(payload[1]) | (RaceFrame.unsigned(payload[2]) << 8);
    }

    public static boolean isAz100ModelId(int modelId) {
        // Audio Connect DeviceMmiConstants: AZ100, AZ100E, AZ100G and AZ100P.
        return modelId >= 0x00A0 && modelId <= 0x00A3;
    }

    public static Az100DeviceColor deviceColor(RaceFrame frame) {
        byte[] payload = successfulResponse(frame, PanasonicCommand.GET_DEVICE_COLOR, 2);
        return Az100DeviceColor.fromCode(RaceFrame.unsigned(payload[1]));
    }

    public static int cradleBattery(RaceFrame frame) {
        byte[] payload = successfulResponse(frame, PanasonicCommand.GET_CRADLE_BATTERY, 2);
        int percent = RaceFrame.unsigned(payload[1]);
        if (percent > 100) throw new IllegalArgumentException("Invalid cradle battery value");
        return percent;
    }

    public static FirmwareVersion firmwareVersion(RaceFrame frame) {
        if (frame.type() != RaceFrame.TYPE_INDICATION
                || frame.commandId() != PanasonicCommand.FOTA_GET_VERSION) {
            throw new IllegalArgumentException("Not an AZ100 firmware indication");
        }
        byte[] payload = frame.payload();
        if (payload.length < 3 || RaceFrame.unsigned(payload[0]) != 0) {
            throw new IllegalArgumentException("Firmware indication failed or is truncated");
        }
        int side = RaceFrame.unsigned(payload[1]);
        int length = RaceFrame.unsigned(payload[2]);
        if ((side != Az100BatteryUpdate.LEFT && side != Az100BatteryUpdate.RIGHT)
                || payload.length < length + 3) {
            throw new IllegalArgumentException("Firmware indication contains an invalid value");
        }
        String version = new String(payload, 3, length, StandardCharsets.US_ASCII)
                .replace("\u0000", "")
                .trim();
        return new FirmwareVersion(side, version);
    }

    private static byte[] successfulResponse(RaceFrame frame, int commandId, int minimumLength) {
        if (frame.type() != RaceFrame.TYPE_RESPONSE || frame.commandId() != commandId) {
            throw new IllegalArgumentException("Unexpected AZ100 read response");
        }
        byte[] payload = frame.payload();
        if (payload.length < minimumLength || RaceFrame.unsigned(payload[0]) != 0) {
            throw new IllegalArgumentException("AZ100 read response failed or is truncated");
        }
        return payload;
    }
}
