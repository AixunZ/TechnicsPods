package dev.technics.pods.hook;

import java.util.Arrays;

/** Pure AZ100-to-HyperOS Settings status mapping. */
final class Az100SettingsPolicy {
    // Only mode tokens are established. Native ANC-strength levels are unverified.
    private static final String SUPPORTED_LEVELS = "0100;0200;0201";

    private Az100SettingsPolicy() {}

    static String batteryCsv(int[] batteries) {
        requireBatterySlots(batteries);
        return batteries[0] + "," + batteries[1] + "," + batteries[2];
    }

    static String atUiPayload(Integer outsideMode, Integer ambientMode, int[] batteries) {
        return settingsMode(outsideMode) + "|" + SUPPORTED_LEVELS + "|"
                + batteryCsv(batteries) + "|00";
    }

    static String refreshPayload(Integer outsideMode, Integer ambientMode, int[] batteries,
            String firmware) {
        requireBatterySlots(batteries);
        String[] values = new String[16];
        Arrays.fill(values, "");
        values[0] = Integer.toString(batteries[0]);
        values[1] = Integer.toString(batteries[1]);
        values[2] = Integer.toString(batteries[2]);
        if (firmware != null && !firmware.isBlank()) values[3] = "0+" + firmware.trim();
        values[7] = settingsLevel(outsideMode, ambientMode);
        values[8] = "false";
        values[11] = "00";
        values[13] = "00";
        values[14] = "00";
        return String.join(",", Arrays.asList(values));
    }

    static String settingsMode(Integer outsideMode) {
        if (outsideMode == null) return "0";
        if (outsideMode == 1) return "1";
        if (outsideMode == 2) return "2";
        return "0";
    }

    static String settingsLevel(Integer outsideMode, Integer ambientMode) {
        if (outsideMode == null) return "0000";
        if (outsideMode == 1) return "0100";
        if (outsideMode == 2) return ambientMode != null && ambientMode == 1
                ? "0201" : "0200";
        return "0000";
    }

    private static void requireBatterySlots(int[] batteries) {
        if (batteries == null || batteries.length != 3) {
            throw new IllegalArgumentException("Settings requires left/right/case battery slots");
        }
    }
}
