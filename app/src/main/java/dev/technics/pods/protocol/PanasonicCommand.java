package dev.technics.pods.protocol;

/** Command identifiers observed on EAH-AZ100 firmware JNZ1ZE 1.040. */
public final class PanasonicCommand {
    public static final int GET_MODEL_ID = 0x0000;
    public static final int GET_OUTSIDE_CONTROL = 0x000A;
    public static final int SET_OUTSIDE_CONTROL = 0x000B;
    public static final int GET_AMBIENT_MODE = 0x0021;
    public static final int SET_AMBIENT_MODE = 0x0022;
    public static final int GET_NOISE_CANCELLING_ADJUSTMENT = 0x0038;
    public static final int GET_CRADLE_BATTERY = 0x0040;
    public static final int GET_LE_AUDIO = 0x0059;
    public static final int GET_STATUS = 0x005B;
    public static final int GET_CRADLE_VERSION = 0x0061;
    public static final int GET_ADAPTIVE_ANC = 0x0067;
    public static final int SET_ADAPTIVE_ANC = 0x0068;
    public static final int GET_ALL_DATA = 0x00F0;
    public static final int TWS_GET_BATTERY = 0x0CD6;
    public static final int RELAY_GET_AVAILABLE_DESTINATION = 0x0D00;
    public static final int FOTA_GET_VERSION = 0x1C07;

    private PanasonicCommand() {}
}
