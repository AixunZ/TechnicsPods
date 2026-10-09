package dev.technics.pods;

import android.app.Application;
import android.util.Log;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class TechnicsPodsApp extends Application implements XposedServiceHelper.OnServiceListener {
    private static final String TAG = "TechnicsPods-App";

    private static volatile XposedService xposedService;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        xposedService = service;
        Log.i(TAG, "LSPosed service connected, API=" + service.getApiVersion());
        ModulePreferences.mirrorToFramework(this);
        getContentResolver().notifyChange(dev.technics.pods.state.Az100StateProvider.STATE_URI, null);
    }

    @Override
    public void onServiceDied(XposedService service) {
        if (xposedService == service) {
            xposedService = null;
            Log.w(TAG, "LSPosed service disconnected");
            getContentResolver().notifyChange(dev.technics.pods.state.Az100StateProvider.STATE_URI, null);
        }
    }

    public static XposedService getXposedService() {
        return xposedService;
    }
}
