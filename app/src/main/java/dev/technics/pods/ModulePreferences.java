package dev.technics.pods;

import android.content.Context;
import android.content.SharedPreferences;

import dev.technics.pods.capture.CaptureContract;
import io.github.libxposed.service.XposedService;

/** Module settings stored in the app process and mirrored to framework hooks. */
public final class ModulePreferences {
    public static final String PREFS = "technics_module_settings";
    public static final String AUTO_MANAGE = "auto_manage";
    public static final String STATUS_ICON = "status_icon";
    public static final String DIAGNOSTICS = "diagnostic_probes";
    public static final String SELECTED_ADDRESS = "selected_address";
    public static final String START_ERROR = "start_error";
    /** Manual artwork finish; empty means automatic protocol detection. */
    public static final String ART_COLOR_OVERRIDE = "art_color_override";
    public static final String ART_COLOR_AUTO = "art_color_auto";

    private ModulePreferences() {}

    public static SharedPreferences get(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void mirrorToFramework(Context context) {
        XposedService service = TechnicsPodsApp.getXposedService();
        if (service == null) return;
        SharedPreferences local = get(context);
        try {
            service.getRemotePreferences(CaptureContract.PREFS_NAME).edit()
                    .putBoolean(AUTO_MANAGE, local.getBoolean(AUTO_MANAGE, false))
                    .putBoolean(STATUS_ICON, local.getBoolean(STATUS_ICON, true))
                    .putBoolean(DIAGNOSTICS, local.getBoolean(DIAGNOSTICS, false))
                    .putString(SELECTED_ADDRESS, local.getString(SELECTED_ADDRESS, ""))
                    .putString(ART_COLOR_OVERRIDE, local.getString(ART_COLOR_OVERRIDE, ""))
                    .putString(ART_COLOR_AUTO, local.getString(ART_COLOR_AUTO, ""))
                    .apply();
        } catch (RuntimeException ignored) {
            // Settings remain available locally while the framework is disconnected.
        }
    }
}
