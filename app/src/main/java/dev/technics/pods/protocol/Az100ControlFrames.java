package dev.technics.pods.protocol;

/** Typed AZ100 control frames. This class builds bytes only and never opens a Bluetooth transport. */
public final class Az100ControlFrames {
    public static final int AMBIENT_TRANSPARENT = 0;
    public static final int AMBIENT_ATTENTION = 1;

    private Az100ControlFrames() {}

    public static byte[] setOutsideControl(int mode, int noiseLevel, int ambientLevel) {
        if (mode < Az100StateSnapshot.OUTSIDE_CONTROL_OFF
                || mode > Az100StateSnapshot.OUTSIDE_CONTROL_AMBIENT) {
            throw new IllegalArgumentException("Outside-control mode must be 0, 1 or 2");
        }
        requireLevel(noiseLevel, "Noise-cancelling level");
        requireLevel(ambientLevel, "Ambient level");
        return command(
                PanasonicCommand.SET_OUTSIDE_CONTROL,
                new byte[]{(byte) mode, (byte) noiseLevel, (byte) ambientLevel}
        );
    }

    public static byte[] setAmbientMode(int mode) {
        if (mode == AMBIENT_TRANSPARENT) {
            return command(PanasonicCommand.SET_AMBIENT_MODE, new byte[]{0, 0});
        }
        if (mode == AMBIENT_ATTENTION) {
            return command(PanasonicCommand.SET_AMBIENT_MODE, new byte[]{1, 1});
        }
        throw new IllegalArgumentException("Ambient mode must be transparent or attention");
    }

    public static byte[] setAdaptiveAnc(boolean enabled) {
        return command(PanasonicCommand.SET_ADAPTIVE_ANC, new byte[]{(byte) (enabled ? 1 : 0)});
    }

    private static byte[] command(int commandId, byte[] payload) {
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

    private static void requireLevel(int value, String name) {
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException(name + " must be between 1 and 100");
        }
    }
}
