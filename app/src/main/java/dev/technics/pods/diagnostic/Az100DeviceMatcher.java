package dev.technics.pods.diagnostic;

import java.util.Locale;

public final class Az100DeviceMatcher {
    private Az100DeviceMatcher() {}

    public static boolean matchesName(String name) {
        if (name == null) return false;
        String normalized = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.contains("EAHAZ100");
    }
}
