package dev.technics.pods.art;

import java.util.Set;

/** Pure import and URI rules shared by the artwork store/provider. */
public final class Az100ArtPolicy {
    private Az100ArtPolicy() {}

    public static final Set<String> SLOTS = Set.of("main", "left", "right", "case");

    public static boolean validSlot(String slot) { return SLOTS.contains(slot == null ? "" : slot); }

    public static String slot(String query) {
        if (query == null) return "main";
        if (!query.startsWith("slot=")) return null;
        String value = query.substring(5);
        return validSlot(value) ? value : null;
    }

    public static boolean relativeFile(String path) {
        return path != null && path.matches("[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*")
                && !path.contains("..") && !path.startsWith("/");
    }

    public static boolean resourceName(String name) {
        return name != null && name.matches("(?:drawable|mipmap)/[A-Za-z0-9_]+");
    }

    public static boolean sha256(String value) {
        return value != null && value.matches("[a-fA-F0-9]{64}");
    }

    public static boolean readableUri(String scheme, String authority, String path,
            String query, String fragment, String mode) {
        return "content".equals(scheme) && "dev.technics.pods.art".equals(authority)
                && "/product".equals(path) && slot(query) != null && fragment == null && "r".equals(mode);
    }
}
