package dev.technics.pods.state;

public final class Az100StateFreshness {
    private Az100StateFreshness() { }

    public static boolean isFresh(long updatedAt, long now) {
        return updatedAt > 0 && now >= updatedAt && now - updatedAt <= 120_000L;
    }
}
