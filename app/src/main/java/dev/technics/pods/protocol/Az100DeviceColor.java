package dev.technics.pods.protocol;

/** AZ100 finishes with dedicated artwork in Technics Audio Connect 5.0.1. */
public enum Az100DeviceColor {
    UNKNOWN(-1, ""),
    BLUE(1, "blue"),
    BLACK(11, "black"),
    GOLD(14, "gold"),
    SILVER(19, "silver"),
    VIOLET(22, "violet");

    private final int code;
    private final String artKey;

    Az100DeviceColor(int code, String artKey) {
        this.code = code;
        this.artKey = artKey;
    }

    public int code() {
        return code;
    }

    /** Empty for unknown values: callers must retain the last verified color or manual override. */
    public String artKey() {
        return artKey;
    }

    public static Az100DeviceColor fromCode(int code) {
        for (Az100DeviceColor color : values()) {
            if (color != UNKNOWN && color.code == code) return color;
        }
        return UNKNOWN;
    }
}
