package dev.technics.pods.protocol;

/** The subset of the AZ100 idle-state bundle needed by system device-center surfaces. */
public record Az100StateSnapshot(
        Integer outsideControlMode,
        Integer noiseCancellingLevel,
        Integer ambientSoundLevel,
        Integer noiseCancellingAdjustment,
        Integer cradleBatteryPercent,
        Integer leAudioEnabled,
        Integer currentStatus,
        String cradleFirmwareVersion
) {
    public static final int OUTSIDE_CONTROL_OFF = 0;
    public static final int OUTSIDE_CONTROL_NOISE_CANCELLING = 1;
    public static final int OUTSIDE_CONTROL_AMBIENT = 2;
}
