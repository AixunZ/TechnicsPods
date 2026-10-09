package dev.technics.pods.state;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import dev.technics.pods.ModulePreferences;
import dev.technics.pods.capture.CaptureContract;

/** Authenticated state, typed control and official-session gateway to the module owner. */
public final class Az100StateProvider extends ContentProvider {
    public static final String METHOD_STATE = "state";
    public static final String METHOD_READ_REQUEST = "read_request";
    public static final String METHOD_CONTROL_REQUEST = "control_request";
    public static final String METHOD_RUNTIME_CANCEL = "runtime_cancel";
    public static final String METHOD_OFFICIAL_BEGIN = "official_begin";
    public static final String METHOD_OFFICIAL_CONNECTED = "official_connected";
    public static final String METHOD_OFFICIAL_FAILED = "official_failed";
    public static final String METHOD_OFFICIAL_RELEASED = "official_released";
    public static final String METHOD_CAPABILITY = "capability";
    public static final Uri STATE_URI = Uri.parse("content://dev.technics.pods.state");
    private static final Set<String> SYSTEM_CALLERS = Set.of(
            "com.milink.service", "com.xiaomi.bluetooth", "com.android.systemui",
            "com.android.settings", "com.miui.home", "com.android.bluetooth");
    private static final Set<String> CONTROL_CALLERS = Set.of(
            "com.android.settings", "com.milink.service", "com.xiaomi.bluetooth", "com.android.systemui");
    private Az100StateRepository repository;

    @Override public boolean onCreate() {
        repository = new Az100StateRepository(requireContext());
        return true;
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        String caller = getCallingPackage();
        boolean local = Binder.getCallingUid() == Process.myUid();
        if (!local && (caller == null || !java.util.Arrays.asList(
                requireContext().getPackageManager().getPackagesForUid(Binder.getCallingUid()) == null
                        ? new String[0] : requireContext().getPackageManager().getPackagesForUid(Binder.getCallingUid()))
                .contains(caller))) return null;
        boolean system = SYSTEM_CALLERS.contains(caller == null ? "" : caller);
        boolean official = CaptureContract.OFFICIAL_APP_PACKAGE.equals(caller);
        if (!local && !system && !official) return null;
        if (METHOD_OFFICIAL_BEGIN.equals(method) || METHOD_OFFICIAL_CONNECTED.equals(method)
                || METHOD_OFFICIAL_FAILED.equals(method) || METHOD_OFFICIAL_RELEASED.equals(method)) {
            if (!official) return null;
            IBinder token = extras == null ? null : extras.getBinder("token");
            if (token == null) return null;
            Bundle result = new Bundle();
            if (METHOD_OFFICIAL_BEGIN.equals(method) || METHOD_OFFICIAL_CONNECTED.equals(method)) {
                result.putBoolean("accepted", runtime().officialBegin(token));
            } else {
                runtime().officialRelease(token);
                result.putBoolean("accepted", true);
            }
            result.putBoolean("official_busy", runtime().isOfficialBusy());
            result.putLong("generation", runtime().generation());
            return result;
        }
        if (official && !local) return null;
        if (METHOD_READ_REQUEST.equals(method)) {
            if (!local && !CONTROL_CALLERS.contains(caller)) return null;
            return accepted(runtime().read());
        }
        if (METHOD_CONTROL_REQUEST.equals(method)) {
            if (!local && !CONTROL_CALLERS.contains(caller)) return null;
            if (extras == null || !extras.containsKey("mode")) return null;
            int mode = extras.getInt("mode");
            Integer noise = extras.containsKey("noise") ? extras.getInt("noise") : null;
            String expectedAddress = extras.getString("expected_address");
            if (!local && (expectedAddress == null
                    || !android.bluetooth.BluetoothAdapter.checkBluetoothAddress(expectedAddress))) {
                Bundle result = new Bundle();
                result.putBoolean("accepted", false);
                result.putString("status", "rejected");
                result.putString("error", "缺少目标设备身份，请重新打开耳机页面");
                return result;
            }
            Az100ConnectionRuntime owner = runtime();
            synchronized (owner) {
                Bundle result = accepted(owner.control(mode, noise, expectedAddress));
                boolean accepted = result.getBoolean("accepted", false);
                result.putString("request_id", accepted ? owner.requestId() : null);
                result.putString("status", accepted ? "pending" : "rejected");
                return result;
            }
        }
        if (METHOD_RUNTIME_CANCEL.equals(method)) {
            if (!local) return null;
            runtime().cancel();
            Bundle result = new Bundle(); result.putBoolean("accepted", true); return result;
        }
        if (METHOD_CAPABILITY.equals(method)) {
            if (!system || extras == null) return null;
            String feature = extras.getString("feature", "");
            String status = extras.getString("status", "unavailable");
            if (!feature.matches("[a-z_]{1,48}") || !Set.of("installed", "unavailable", "degraded").contains(status)) return null;
            SharedPreferences.Editor editor = requireContext().getSharedPreferences("hook_capabilities", 0).edit();
            String detail = extras.getString("detail", "");
            editor.putString(caller + ":" + feature, status + ": "
                    + detail.substring(0, Math.min(detail.length(), 300)));
            editor.apply();
            requireContext().getContentResolver().notifyChange(STATE_URI, null);
            Bundle result = new Bundle(); result.putBoolean("accepted", true); return result;
        }
        if (!METHOD_STATE.equals(method)) return null;
        return stateBundle();
    }

    private Bundle accepted(CompletableFuture<?> future) {
        Bundle result = new Bundle();
        result.putBoolean("accepted", !future.isCompletedExceptionally());
        if (future.isCompletedExceptionally()) {
            future.handle((value, error) -> {
                result.putString("error", error == null ? "操作不可用" : error.getMessage());
                return null;
            });
        }
        return result;
    }

    private Az100ConnectionRuntime runtime() { return Az100ConnectionRuntime.get(requireContext()); }

    private Bundle stateBundle() {
        Az100StateCache.State state = repository.state();
        Bundle result = new Bundle();
        long now = System.currentTimeMillis();
        result.putBoolean("reachable", state.reachable() && runtime().isConnected() && !runtime().isOfficialBusy());
        result.putLong("updated_at", state.lastSuccessAtMillis());
        putField(result, "left_battery", state.left().batteryPercent(), state.left().batteryUpdatedAtMillis());
        putField(result, "right_battery", state.right().batteryPercent(), state.right().batteryUpdatedAtMillis());
        putField(result, "cradle_battery", state.cradleBatteryPercent(), state.cradleUpdatedAtMillis());
        putFreshField(result, "outside_mode", state.controls().outsideMode(), state.controls().outsideModeUpdatedAtMillis(), now);
        putFreshField(result, "noise_level", state.controls().noiseCancellingLevel(), state.controls().noiseCancellingLevelUpdatedAtMillis(), now);
        putFreshField(result, "ambient_level", state.controls().ambientLevel(), state.controls().ambientLevelUpdatedAtMillis(), now);
        putFreshField(result, "ambient_mode", state.controls().ambientMode(), state.controls().ambientModeUpdatedAtMillis(), now);
        result.putLong("controls_updated_at", state.controls().updatedAtMillis());
        result.putLong("left_firmware_updated_at", state.left().firmwareUpdatedAtMillis());
        result.putLong("right_firmware_updated_at", state.right().firmwareUpdatedAtMillis());
        result.putString("firmware", state.left().firmware() != null ? state.left().firmware() : state.right().firmware());
        result.putString("connection_status", runtime().status());
        result.putString("connection_owner", runtime().isOfficialBusy() ? "official"
                : runtime().isRunning() ? "module" : "none");
        result.putBoolean("official_busy", runtime().isOfficialBusy());
        result.putString("control_status", runtime().controlStatus());
        result.putString("control_error", runtime().controlError());
        result.putString("request_id", runtime().requestId());
        result.putLong("generation", runtime().generation());
        result.putString("selected_address", ModulePreferences.get(requireContext())
                .getString(ModulePreferences.SELECTED_ADDRESS, ""));
        result.putString("device_address", runtime().deviceAddress());
        result.putString("start_error", ModulePreferences.get(requireContext())
                .getString(ModulePreferences.START_ERROR, ""));
        Bundle capabilities = new Bundle();
        requireContext().getSharedPreferences("hook_capabilities", 0).getAll().forEach((key, value) -> {
            if (value instanceof String text) capabilities.putString(key, text);
        });
        result.putBundle("capabilities", capabilities);
        return result;
    }

    private static void putField(Bundle values, String key, Integer value, long updatedAt) {
        if (value != null) values.putInt(key, value);
        values.putLong(key + "_updated_at", updatedAt);
    }

    private static void putFreshField(Bundle values, String key, Integer value, long updatedAt, long now) {
        putField(values, key, Az100StateFreshness.isFresh(updatedAt, now) ? value : null, updatedAt);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
