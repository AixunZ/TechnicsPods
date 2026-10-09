package dev.technics.pods.protocol;

/** Parsers for read-back responses emitted after AZ100 noise-control writes. */
public final class Az100ControlResponses {
    private Az100ControlResponses() {}

    public record OutsideControl(int mode, int noiseLevel, int ambientLevel) {}

    public record AmbientMode(int mode, int musicBehavior) {
        public static final int MUSIC_PLAY = 0;
        public static final int MUSIC_STOP = 1;
    }

    public static OutsideControl outsideControl(RaceFrame frame) {
        byte[] payload = successfulResponse(frame, PanasonicCommand.GET_OUTSIDE_CONTROL, 4);
        int mode = RaceFrame.unsigned(payload[1]);
        int noiseLevel = RaceFrame.unsigned(payload[2]);
        int ambientLevel = RaceFrame.unsigned(payload[3]);
        if (mode > Az100StateSnapshot.OUTSIDE_CONTROL_AMBIENT
                || noiseLevel > 100 || ambientLevel > 100) {
            throw new IllegalArgumentException("Outside-control response contains an invalid value");
        }
        return new OutsideControl(mode, noiseLevel, ambientLevel);
    }

    public static AmbientMode ambientMode(RaceFrame frame) {
        byte[] payload = successfulResponse(frame, PanasonicCommand.GET_AMBIENT_MODE, 3);
        int mode = RaceFrame.unsigned(payload[1]);
        int musicBehavior = RaceFrame.unsigned(payload[2]);
        if (mode > Az100ControlFrames.AMBIENT_ATTENTION
                || musicBehavior > AmbientMode.MUSIC_STOP) {
            throw new IllegalArgumentException("Ambient-mode response contains an invalid value");
        }
        return new AmbientMode(mode, musicBehavior);
    }

    public static boolean adaptiveAnc(RaceFrame frame) {
        byte[] payload = successfulResponse(frame, PanasonicCommand.GET_ADAPTIVE_ANC, 2);
        int value = RaceFrame.unsigned(payload[1]);
        if (value > 1) {
            throw new IllegalArgumentException("Adaptive-ANC response contains an invalid value");
        }
        return value == 1;
    }

    public static void requireSuccessfulOutsideControlSet(RaceFrame frame) {
        successfulResponse(frame, PanasonicCommand.SET_OUTSIDE_CONTROL, 1);
    }

    private static byte[] successfulResponse(RaceFrame frame, int commandId, int minimumLength) {
        if (frame.type() != RaceFrame.TYPE_RESPONSE || frame.commandId() != commandId) {
            throw new IllegalArgumentException("Unexpected AZ100 control response");
        }
        byte[] payload = frame.payload();
        if (payload.length < minimumLength || RaceFrame.unsigned(payload[0]) != 0) {
            throw new IllegalArgumentException("AZ100 control response failed or is truncated");
        }
        return payload;
    }
}
