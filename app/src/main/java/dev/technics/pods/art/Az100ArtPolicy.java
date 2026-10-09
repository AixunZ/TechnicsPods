package dev.technics.pods.art;

import java.util.Locale;

/** Pure import and URI rules shared by the artwork store/provider. */
public final class Az100ArtPolicy {
    private Az100ArtPolicy() {}

    public static boolean officialEntry(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT);
        return (normalized.startsWith("assets/") || normalized.startsWith("res/"))
                && !normalized.contains("..") && normalized.matches(".*(?:^|[/_.-])(?:eah[_-]?)?az100(?:[/_.-]).*")
                && (normalized.endsWith(".png") || normalized.endsWith(".jpg")
                || normalized.endsWith(".jpeg") || normalized.endsWith(".webp"));
    }

    public static boolean readableUri(String scheme, String authority, String path,
            String query, String fragment, String mode) {
        return "content".equals(scheme) && "dev.technics.pods.art".equals(authority)
                && "/product".equals(path) && query == null && fragment == null && "r".equals(mode);
    }
}
