package dev.technics.pods.protocol;

/** Whitelisted read-only requests used by the standalone AZ100 diagnostic connection. */
public final class Az100ReadFrames {
    private Az100ReadFrames() {}

    public static byte[] getModelId() {
        return query(PanasonicCommand.GET_MODEL_ID, new byte[0]);
    }

    public static byte[] getDeviceColor() {
        return query(PanasonicCommand.GET_DEVICE_COLOR, new byte[0]);
    }

    public static byte[] getRelayDestinations() {
        return query(PanasonicCommand.RELAY_GET_AVAILABLE_DESTINATION, new byte[0]);
    }

    public static byte[] getOutsideControl() {
        return query(PanasonicCommand.GET_OUTSIDE_CONTROL, new byte[0]);
    }

    public static byte[] getAmbientMode() {
        return query(PanasonicCommand.GET_AMBIENT_MODE, new byte[0]);
    }

    public static byte[] getCradleBattery() {
        return query(PanasonicCommand.GET_CRADLE_BATTERY, new byte[0]);
    }

    public static byte[] getAdaptiveAnc() {
        return query(PanasonicCommand.GET_ADAPTIVE_ANC, new byte[0]);
    }

    public static byte[] getEarbudBattery(int side) {
        requireSide(side);
        return query(PanasonicCommand.TWS_GET_BATTERY, new byte[]{(byte) side});
    }

    public static byte[] getFirmwareVersion(int side) {
        requireSide(side);
        return query(PanasonicCommand.FOTA_GET_VERSION, new byte[]{(byte) side});
    }

    private static byte[] query(int commandId, byte[] payload) {
        int bodyLength = payload.length + 2;
        byte[] packet = new byte[payload.length + 6];
        packet[0] = RaceFrame.CHANNEL;
        packet[1] = RaceFrame.TYPE_COMMAND;
        packet[2] = (byte) bodyLength;
        packet[3] = (byte) (bodyLength >>> 8);
        packet[4] = (byte) commandId;
        packet[5] = (byte) (commandId >>> 8);
        System.arraycopy(payload, 0, packet, 6, payload.length);
        return packet;
    }

    private static void requireSide(int side) {
        if (side != Az100BatteryUpdate.LEFT && side != Az100BatteryUpdate.RIGHT) {
            throw new IllegalArgumentException("Earbud side must be left or right");
        }
    }
}
