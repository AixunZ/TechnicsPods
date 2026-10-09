package dev.technics.pods.hook;

import dev.technics.pods.state.Az100StateCache;

/** Pure mapping from verified AZ100 state to HyperOS MiLink values. */
public final class Az100MiLinkPolicy {
    public static final String DEVICE_ID = "01010607";
    public static final String SUPPORT_FLAGS = "000000000000000010000000";
    public static final int BATTERY_DISCONNECTED = 255;
    public static final int SWITCH_SUPPORTED = 1;

    private Az100MiLinkPolicy() {}

    public static String supportString() {
        return DEVICE_ID + "," + SUPPORT_FLAGS;
    }

    public static int[] batteryLevels(Az100StateCache.State state) {
        if (state == null || !state.reachable()) {
            return new int[]{BATTERY_DISCONNECTED, BATTERY_DISCONNECTED, BATTERY_DISCONNECTED};
        }
        return new int[]{
                battery(state.left().batteryPercent()),
                battery(state.right().batteryPercent()),
                battery(state.cradleBatteryPercent())
        };
    }

    public static Integer noiseControlMode(Az100StateCache.State state) {
        if (state == null || !state.reachable()) return null;
        Integer mode = state.controls().outsideMode();
        return mode == null || mode < 0 || mode > 2 ? null : mode;
    }

    public static String noiseControlLevelCode(Az100StateCache.State state) {
        Integer mode = noiseControlMode(state);
        if (mode == null) return null;
        if (mode == 1) return "0100";
        if (mode == 2) {
            Integer ambientMode = state.controls().ambientMode();
            return ambientMode != null && ambientMode == 1 ? "0201" : "0200";
        }
        return "0000";
    }

    public static boolean hasFreshState(Az100StateCache.State state) {
        return state != null && state.reachable() && state.lastSuccessAtMillis() > 0L;
    }

    private static int battery(Integer value) {
        return value == null ? BATTERY_DISCONNECTED : Math.max(0, Math.min(100, value));
    }
}
