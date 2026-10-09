package dev.technics.pods.hook;

import java.util.List;

/** Parses the user-action arguments of the legacy Settings ANC callbacks. */
final class Az100SettingsControlPolicy {
    record Request(int mode, Integer noiseLevel) { }

    private Az100SettingsControlPolicy() { }

    static Request request(String methodName, List<Object> args) {
        if (methodName == null || args == null) return null;
        boolean markerPresent = false;
        boolean userAction = false;
        Integer value = null;
        for (Object argument : args) {
            if (argument instanceof Boolean flag) {
                markerPresent = true;
                userAction |= flag;
            }
            if (value == null && argument instanceof Integer integer) value = integer;
        }
        // The observed ROM passes true for the click path. Do not intercept internal UI replay.
        if (!markerPresent || !userAction) return null;
        if (value == null) return null;
        if (methodName.equals("updateAncMode")) {
            return value >= 0 && value <= 2 ? new Request(value, null) : null;
        }
        if (methodName.equals("updateAncLevel")) {
            return value >= 1 && value <= 100 ? new Request(-1, value) : null;
        }
        return null;
    }
}
