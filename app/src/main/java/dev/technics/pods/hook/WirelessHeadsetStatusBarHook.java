package dev.technics.pods.hook;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import dev.technics.pods.ModulePreferences;
import dev.technics.pods.diagnostic.Az100DeviceMatcher;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Exact A2DP callback plus public profile broadcasts; unknown ROM callback is a safe no-op. */
@SuppressLint({"MissingPermission", "WrongConstant", "SoonBlockedPrivateApi"})
final class WirelessHeadsetStatusBarHook {
    static final String ACTION_RECONCILE = "dev.technics.pods.action.RECONCILE_STATUS_ICON";
    private static final String TAG = "TechnicsPods-WirelessHeadset";
    private final XposedModule module;
    private final ClassLoader loader;
    private final Supplier<SharedPreferences> prefs;
    private final WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
    private Context context;
    private boolean installed;
    private boolean failureLogged;
    private int reconciliationEpoch;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;

    WirelessHeadsetStatusBarHook(XposedModule module, ClassLoader loader,
            Supplier<SharedPreferences> prefs) {
        this.module = module; this.loader = loader; this.prefs = prefs;
    }

    void install() throws ReflectiveOperationException {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread", false, loader);
            Object app = thread.getDeclaredMethod("currentApplication").invoke(null);
            if (app instanceof Application value) ready(value);
        } catch (Throwable ignored) {}
        Method create = Instrumentation.class.getDeclaredMethod("callApplicationOnCreate", Application.class);
        module.hook(create).setId("technicspods.wireless-headset:application")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (chain.getArg(0) instanceof Application app) ready(app);
                    return result;
                });
    }

    private synchronized void ready(Application app) {
        if (installed) return;
        installed = true; context = app.getApplicationContext();
        if ("com.android.systemui".equals(context.getPackageName())) {
            context.sendBroadcast(new Intent(ACTION_RECONCILE).setPackage("com.android.bluetooth"),
                    android.Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }
        boolean verified = false;
        try {
            Class<?> service = Class.forName("com.android.bluetooth.a2dp.A2dpService", false, loader);
            Method callback = service.getDeclaredMethod("handleConnectionStateChanged", BluetoothDevice.class, int.class, int.class);
            callback.setAccessible(true);
            module.hook(callback).setId("technicspods.wireless-headset:a2dp")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        int current = WirelessHeadsetStatusPolicy.a2dpCurrentState(chain.getArgs());
                        if (chain.getArg(0) instanceof BluetoothDevice device) update(device, BluetoothProfile.A2DP, current);
                        return result;
                    });
            verified = true;
        } catch (Throwable ignored) {
            module.log(Log.INFO, TAG, "Verified A2DP callback unavailable; unknown ROM signature is a safe no-op");
        }
        HostCapabilityReport.report(context, "status_bar", verified ? 1 : 0,
                "A2dpService.handleConnectionStateChanged(BluetoothDevice,int previous,int current);current=args[2];rendering pending hardware");
        if (!verified) return;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        filter.addAction(ACTION_RECONCILE);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context owner, Intent intent) {
                String action = intent.getAction();
                if (ACTION_RECONCILE.equals(action)) { reconcile(); return; }
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        synchronized (policy) { reconciliationEpoch++; policy.clear(); apply(); }
                    } else if (state == BluetoothAdapter.STATE_ON) reconcile();
                    return;
                }
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                int profile = BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action) ? BluetoothProfile.A2DP
                        : BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action) ? BluetoothProfile.HEADSET : BluetoothProfile.LE_AUDIO;
                update(device, profile, intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
            }
        }, filter, android.Manifest.permission.BLUETOOTH_CONNECT, null, Context.RECEIVER_EXPORTED);
        try {
            preferenceListener = (values, key) -> {
                if (ModulePreferences.STATUS_ICON.equals(key) || ModulePreferences.SELECTED_ADDRESS.equals(key)) reconcile();
            };
            prefs.get().registerOnSharedPreferenceChangeListener(preferenceListener);
        } catch (Throwable ignored) {}
        reconcile();
    }

    private void reconcile() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) return;
        final int epoch;
        synchronized (policy) {
            epoch = ++reconciliationEpoch;
            policy.beginReconciliation(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.LE_AUDIO);
        }
        for (int profile : new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET, BluetoothProfile.LE_AUDIO}) {
            try {
                boolean requested = adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int id, BluetoothProfile proxy) {
                        java.util.List<BluetoothDevice> devices;
                        try {
                            devices = proxy.getConnectedDevices();
                            synchronized (policy) {
                                if (epoch != reconciliationEpoch || !policy.isProfilePending(id)) return;
                                policy.clearProfile(id);
                                for (BluetoothDevice device : devices) record(device, id, 2, true);
                                policy.completeProfile(id);
                                apply();
                            }
                        } catch (RuntimeException error) {
                            completeProfile(epoch, profile);
                        } finally { adapter.closeProfileProxy(id, proxy); }
                    }
                    @Override public void onServiceDisconnected(int id) {}
                }, profile);
                if (!requested) {
                    completeProfile(epoch, profile);
                }
            } catch (RuntimeException ignored) {
                completeProfile(epoch, profile);
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            synchronized (policy) {
                if (epoch != reconciliationEpoch) return;
                reconciliationEpoch++;
                policy.completeReconciliation();
                apply();
            }
        }, 2_000L);
    }

    private void completeProfile(int epoch, int profile) {
        synchronized (policy) {
            if (epoch != reconciliationEpoch) return;
            policy.completeProfile(profile);
            apply();
        }
    }

    private void update(BluetoothDevice device, int profile, int state) {
        if (device == null || (state != 0 && state != 2)) return;
        synchronized (policy) { record(device, profile, state, false); apply(); }
    }

    private void record(BluetoothDevice device, int profile, int state, boolean snapshot) {
        try {
            String address = device.getAddress();
            String selected = prefs.get().getString(ModulePreferences.SELECTED_ADDRESS, "");
            boolean target = selected != null && !selected.isBlank()
                    ? selected.equalsIgnoreCase(address) : Az100DeviceMatcher.matchesName(device.getName());
            if (snapshot) policy.updateSnapshot(address, profile, state, target);
            else policy.update(address, profile, state, target);
        } catch (Throwable ignored) {}
    }

    private void apply() {
        try {
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            boolean adapterOff = adapter != null && !adapter.isEnabled();
            Boolean visible = policy.visibility(prefs.get().getBoolean(ModulePreferences.STATUS_ICON, true),
                    !policy.isReconciling() || adapterOff);
            if (visible == null) return;
            Object status = context.getSystemService("statusbar");
            if (status == null) return;
            Method method;
            try {
                method = status.getClass().getMethod("setIconVisibility", String.class, boolean.class);
            } catch (NoSuchMethodException missingPublic) {
                method = status.getClass().getDeclaredMethod("setIconVisibility", String.class, boolean.class);
                method.setAccessible(true);
            }
            method.invoke(status, "wireless_headset", visible);
        } catch (Throwable ignored) {
            if (!failureLogged) {
                failureLogged = true;
                module.log(Log.INFO, TAG, "StatusBarManager.setIconVisibility unavailable; safe no-op");
                HostCapabilityReport.report(context, "status_bar", 0,
                        "Exact A2DP callback installed, StatusBarManager.setIconVisibility unavailable");
            }
        }
    }
}
