package dev.technics.pods.hook;

import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.function.Supplier;

import dev.technics.pods.capture.CaptureContract;

final class CaptureConfig {
    private final Supplier<SharedPreferences> preferencesProvider;

    private volatile boolean enabled;
    private volatile String session = "unlabeled";
    private volatile long lastRefreshElapsed;

    CaptureConfig(Supplier<SharedPreferences> preferencesProvider) {
        this.preferencesProvider = preferencesProvider;
        refresh();
    }

    boolean isEnabled() {
        refreshIfStale();
        return enabled;
    }

    String session() {
        refreshIfStale();
        return session;
    }

    private void refreshIfStale() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefreshElapsed >= 1_000L) refresh();
    }

    private synchronized void refresh() {
        try {
            SharedPreferences preferences = preferencesProvider.get();
            enabled = preferences.getBoolean(CaptureContract.PREF_ENABLED, false);
            String value = preferences.getString(CaptureContract.PREF_SESSION, "unlabeled");
            session = value == null || value.isBlank() ? "unlabeled" : value;
        } catch (Throwable ignored) {
            enabled = false;
        } finally {
            lastRefreshElapsed = SystemClock.elapsedRealtime();
        }
    }
}
