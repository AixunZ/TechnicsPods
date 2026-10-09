package dev.technics.pods.hook;

import android.util.Log;

import dev.technics.pods.capture.CaptureContract;
import dev.technics.pods.ModulePreferences;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class HookEntry extends XposedModule {
    private static final String TAG = "TechnicsPods-Hook";
    private final Set<String> installedPackages = ConcurrentHashMap.newKeySet();

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in process=" + param.getProcessName() + " API=" + getApiVersion());
    }

    @Override
    public synchronized void onPackageReady(PackageReadyParam param) {
        if (!installedPackages.add(param.getPackageName())) return;
        boolean officialApp = CaptureContract.OFFICIAL_APP_PACKAGE.equals(param.getPackageName());
        boolean systemBluetooth = CaptureContract.SYSTEM_BLUETOOTH_PACKAGE.equals(param.getPackageName());
        boolean hyperOsSurface = CaptureContract.SYSTEM_UI_PACKAGE.equals(param.getPackageName())
                || CaptureContract.SETTINGS_PACKAGE.equals(param.getPackageName())
                || CaptureContract.MIUI_HOME_PACKAGE.equals(param.getPackageName())
                || CaptureContract.MILINK_SERVICE_PACKAGE.equals(param.getPackageName())
                || CaptureContract.XIAOMI_BLUETOOTH_PACKAGE.equals(param.getPackageName());
        if (!officialApp && !systemBluetooth && !hyperOsSurface) return;
        if (getApiVersion() < XposedInterface.API_102) {
            log(Log.ERROR, TAG, "libxposed API 102 is required");
            return;
        }

        try {
            CaptureConfig config = new CaptureConfig(
                    () -> getRemotePreferences(CaptureContract.PREFS_NAME)
            );
            if (officialApp) {
                new Az100OfficialHandoverHook(this, param.getClassLoader()).install();
                new GattCaptureHook(this, config, param.getClassLoader()).install();
            } else if (systemBluetooth) {
                new WirelessHeadsetStatusBarHook(this, param.getClassLoader(),
                        () -> getRemotePreferences(CaptureContract.PREFS_NAME)).install();
                if (getRemotePreferences(CaptureContract.PREFS_NAME).getBoolean(ModulePreferences.DIAGNOSTICS, false)) {
                    new BluetoothEngineProbeHook(this, config, param.getClassLoader()).install();
                }
            } else {
                if (CaptureContract.SYSTEM_UI_PACKAGE.equals(param.getPackageName())) {
                    new WirelessHeadsetStatusBarHook(this, param.getClassLoader(),
                            () -> getRemotePreferences(CaptureContract.PREFS_NAME)).install();
                }
                new Az100HyperOsBridgeHook(this, param.getPackageName(), param.getClassLoader()).install();
                new Az100ArtHook(this, param.getPackageName(), param.getClassLoader()).install();
                if (CaptureContract.MILINK_SERVICE_PACKAGE.equals(param.getPackageName())) {
                    new Az100FusionDeviceRouteHook(this, param.getClassLoader(),
                            getRemotePreferences(CaptureContract.PREFS_NAME).getBoolean(ModulePreferences.DIAGNOSTICS, false)).install();
                }
                if (CaptureContract.SETTINGS_PACKAGE.equals(param.getPackageName())) {
                    new Az100SettingsBridgeHook(this, param.getClassLoader()).install();
                }
                if (getRemotePreferences(CaptureContract.PREFS_NAME).getBoolean(ModulePreferences.DIAGNOSTICS, false)) {
                    new HyperOsSurfaceProbeHook(this, param.getPackageName(), param.getClassLoader()).install();
                }
            }
            log(Log.INFO, TAG, "Hooks installed in " + param.getPackageName());
        } catch (Throwable error) {
            installedPackages.remove(param.getPackageName());
            log(Log.ERROR, TAG, "Failed to install hooks in " + param.getPackageName(), error);
        }
    }
}
