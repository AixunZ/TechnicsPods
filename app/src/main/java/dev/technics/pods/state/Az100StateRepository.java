package dev.technics.pods.state;

import android.content.Context;
import android.content.ContentResolver;
import java.util.function.UnaryOperator;
import android.content.SharedPreferences;

import dev.technics.pods.diagnostic.Az100DiagnosticResult;
import dev.technics.pods.diagnostic.Az100OutsideControlResult;
import dev.technics.pods.ModulePreferences;
import dev.technics.pods.art.Az100ArtStore;
import dev.technics.pods.protocol.Az100DeviceColor;
import dev.technics.pods.protocol.Az100DeviceColor;
import dev.technics.pods.art.Az100ArtProvider;

/** Persists the latest state so UI and future system integrations share one source of truth. */
public final class Az100StateRepository {
    private static final String PREFS = "az100_background_state";

    private static final Object LOCK = new Object();
    private final ContentResolver resolver;
    private final SharedPreferences preferences;
    private final Context context;

    public Az100StateRepository(Context context) {
        this.context = context.getApplicationContext();
        resolver = this.context.getContentResolver();
        preferences = this.context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Az100StateCache.State state() {
        synchronized (LOCK) { return load(); }
    }

    private void update(UnaryOperator<Az100StateCache.State> transition) {
        boolean colorChanged;
        synchronized (LOCK) {
            Az100StateCache.State before = load();
            Az100StateCache.State after = transition.apply(before);
            colorChanged = before.deviceColor() != after.deviceColor()
                    || before.deviceColorUpdatedAtMillis() != after.deviceColorUpdatedAtMillis();
            save(after);
        }
        resolver.notifyChange(Az100StateProvider.STATE_URI, null);
        if (colorChanged) resolver.notifyChange(Az100ArtProvider.ART_URI, null);
    }

    public void monitoringStarted() { update(Az100StateCache::monitoringStarted); }
    public void samplingStarted(long nowMillis) {
        update(state -> Az100StateCache.samplingStarted(state, nowMillis));
    }
    public void sampleSucceeded(Az100DiagnosticResult result, long startedAt, long nowMillis) {
        update(state -> Az100StateCache.sampleSucceeded(state, result, startedAt, nowMillis));
        Az100DeviceColor color = result == null ? Az100DeviceColor.UNKNOWN : result.deviceColor();
        String override = ModulePreferences.get(context).getString(ModulePreferences.ART_COLOR_OVERRIDE, "");
        if (override == null || override.isBlank()) {
            String key = color == null ? "" : color.artKey();
            if (!key.isBlank()) {
                ModulePreferences.get(context).edit().putString(ModulePreferences.ART_COLOR_AUTO, key).apply();
                try { Az100ArtStore.selectBundledColor(context, key); }
                catch (java.io.IOException ignored) { /* Keep the last confirmed artwork set. */ }
            }
        }
    }
    public void sampleFailed(String error, long startedAt, long nowMillis) {
        update(state -> Az100StateCache.sampleFailed(state, error, startedAt, nowMillis));
    }
    public void controlSucceeded(Az100OutsideControlResult result, long nowMillis) {
        update(state -> Az100StateCache.controlSucceeded(state, result, nowMillis));
    }
    public void controlFailed(String error, long nowMillis) {
        update(state -> Az100StateCache.controlFailed(state, error, nowMillis));
    }
    public void monitoringStopped() { update(Az100StateCache::monitoringStopped); }
    public void clearDevice() { update(previous -> Az100StateCache.State.empty()); }
    private Az100StateCache.State load() {
        Az100StateCache.Earbud left = new Az100StateCache.Earbud(
                nullableInt("left_battery"),
                preferences.getLong("left_battery_at", 0L),
                nullableString("left_firmware"),
                preferences.getLong("left_firmware_at", 0L)
        );
        Az100StateCache.Earbud right = new Az100StateCache.Earbud(
                nullableInt("right_battery"),
                preferences.getLong("right_battery_at", 0L),
                nullableString("right_firmware"),
                preferences.getLong("right_firmware_at", 0L)
        );
        Az100StateCache.Controls controls = new Az100StateCache.Controls(
                nullableInt("outside_mode"),
                nullableInt("noise_level"),
                nullableInt("ambient_level"),
                nullableInt("ambient_mode"),
                nullableBoolean("adaptive_anc"),
                preferences.getLong("controls_at", 0L),
                preferences.getLong("outside_mode_at", preferences.getLong("controls_at", 0L)),
                preferences.getLong("noise_level_at", preferences.getLong("controls_at", 0L)),
                preferences.getLong("ambient_level_at", preferences.getLong("controls_at", 0L)),
                preferences.getLong("ambient_mode_at", preferences.getLong("controls_at", 0L)),
                preferences.getLong("adaptive_anc_at", preferences.getLong("controls_at", 0L))
        );
        return new Az100StateCache.State(
                left,
                right,
                nullableInt("cradle_battery"),
                preferences.getLong("cradle_at", 0L),
                controls,
                nullableBoolean("partner_available"),
                preferences.getLong("partner_at", 0L),
                preferences.getBoolean("monitoring", false),
                preferences.getBoolean("sampling", false),
                preferences.getBoolean("reachable", false),
                preferences.getInt("failures", 0),
                preferences.getLong("last_attempt_at", 0L),
                preferences.getLong("last_success_at", 0L),
                nullableString("last_error"),
                nullableString("incomplete_fields"),
                Az100DeviceColor.fromCode(preferences.getInt("device_color_code", -1)),
                preferences.getLong("device_color_at", 0L)
        );
    }

    private void save(Az100StateCache.State state) {
        SharedPreferences.Editor editor = preferences.edit();
        putNullableInt(editor, "left_battery", state.left().batteryPercent());
        editor.putLong("left_battery_at", state.left().batteryUpdatedAtMillis());
        putNullableString(editor, "left_firmware", state.left().firmware());
        editor.putLong("left_firmware_at", state.left().firmwareUpdatedAtMillis());
        putNullableInt(editor, "right_battery", state.right().batteryPercent());
        editor.putLong("right_battery_at", state.right().batteryUpdatedAtMillis());
        putNullableString(editor, "right_firmware", state.right().firmware());
        editor.putLong("right_firmware_at", state.right().firmwareUpdatedAtMillis());
        putNullableInt(editor, "cradle_battery", state.cradleBatteryPercent());
        editor.putLong("cradle_at", state.cradleUpdatedAtMillis());
        putNullableInt(editor, "outside_mode", state.controls().outsideMode());
        putNullableInt(editor, "noise_level", state.controls().noiseCancellingLevel());
        putNullableInt(editor, "ambient_level", state.controls().ambientLevel());
        putNullableInt(editor, "ambient_mode", state.controls().ambientMode());
        putNullableBoolean(editor, "adaptive_anc", state.controls().adaptiveAncEnabled());
        editor.putLong("controls_at", state.controls().updatedAtMillis());
        editor.putLong("outside_mode_at", state.controls().outsideModeUpdatedAtMillis());
        editor.putLong("noise_level_at", state.controls().noiseCancellingLevelUpdatedAtMillis());
        editor.putLong("ambient_level_at", state.controls().ambientLevelUpdatedAtMillis());
        editor.putLong("ambient_mode_at", state.controls().ambientModeUpdatedAtMillis());
        editor.putLong("adaptive_anc_at", state.controls().adaptiveAncUpdatedAtMillis());
        putNullableBoolean(editor, "partner_available", state.partnerAvailable());
        editor.putLong("partner_at", state.partnerUpdatedAtMillis());
        editor.putBoolean("monitoring", state.monitoring());
        editor.putBoolean("sampling", state.sampling());
        editor.putBoolean("reachable", state.reachable());
        editor.putInt("failures", state.consecutiveFailures());
        editor.putLong("last_attempt_at", state.lastAttemptAtMillis());
        editor.putLong("last_success_at", state.lastSuccessAtMillis());
        putNullableString(editor, "last_error", state.lastError());
        putNullableString(editor, "incomplete_fields", state.incompleteFields());
        editor.putInt("device_color_code", state.deviceColor().code());
        editor.putLong("device_color_at", state.deviceColorUpdatedAtMillis());
        editor.apply();
    }

    private Integer nullableInt(String key) {
        return preferences.contains(key) ? preferences.getInt(key, 0) : null;
    }

    private Boolean nullableBoolean(String key) {
        return preferences.contains(key) ? preferences.getBoolean(key, false) : null;
    }

    private String nullableString(String key) {
        return preferences.contains(key) ? preferences.getString(key, null) : null;
    }

    private static void putNullableInt(
            SharedPreferences.Editor editor,
            String key,
            Integer value
    ) {
        if (value == null) editor.remove(key);
        else editor.putInt(key, value);
    }

    private static void putNullableBoolean(
            SharedPreferences.Editor editor,
            String key,
            Boolean value
    ) {
        if (value == null) editor.remove(key);
        else editor.putBoolean(key, value);
    }

    private static void putNullableString(
            SharedPreferences.Editor editor,
            String key,
            String value
    ) {
        if (value == null) editor.remove(key);
        else editor.putString(key, value);
    }
}
