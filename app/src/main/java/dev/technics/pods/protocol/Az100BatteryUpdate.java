package dev.technics.pods.protocol;

/** A left or right earbud battery indication. Side 0 is left and side 1 is right. */
public record Az100BatteryUpdate(int side, int percent) {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;

    public static Az100BatteryUpdate from(RaceFrame frame) {
        if (frame.type() != RaceFrame.TYPE_INDICATION
                || frame.commandId() != PanasonicCommand.TWS_GET_BATTERY) {
            throw new IllegalArgumentException("Not an AZ100 battery indication");
        }
        byte[] payload = frame.payload();
        if (payload.length < 3 || RaceFrame.unsigned(payload[0]) != 0) {
            throw new IllegalArgumentException("Battery indication failed or is truncated");
        }

        int side = RaceFrame.unsigned(payload[1]);
        int percent = RaceFrame.unsigned(payload[2]);
        if ((side != LEFT && side != RIGHT) || percent > 100) {
            throw new IllegalArgumentException("Battery indication contains an invalid value");
        }
        return new Az100BatteryUpdate(side, percent);
    }
}
