package dev.technics.pods.hook;

import java.util.Locale;

import dev.technics.pods.diagnostic.Az100DeviceMatcher;

/** Pure guard for the AZ100 fusion-device headset service fallback. */
final class Az100FusionRoutePolicy {
    static final int HEADSET_PROTOCOL = 393216;

    private Az100FusionRoutePolicy() {}

    static boolean shouldPatch(int protocol, String deviceName,
            boolean lookupEmpty, boolean cachedServiceAvailable) {
        return protocol == HEADSET_PROTOCOL
                && Az100DeviceMatcher.matchesName(deviceName)
                && lookupEmpty
                && cachedServiceAvailable;
    }

    static boolean isHeadsetPanelResource(String resourceName) {
        if (resourceName == null) return false;
        String normalized = resourceName.toLowerCase(Locale.ROOT);
        return normalized.contains("headset")
                || normalized.contains("earphone")
                || normalized.contains("earbud")
                || normalized.contains("ear_bud")
                || normalized.contains("sound_bluetooth_control");
    }
}
