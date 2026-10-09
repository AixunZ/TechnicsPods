package dev.technics.pods.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Read-only discovery of HyperOS system-surface classes used for Bluetooth devices. */
final class HyperOsSurfaceProbeHook {
    private static final String TAG = "TechnicsPods-HyperOsProbe";
    private static final String[] TOKENS = {
            "bluetooth", "battery", "headset", "earbud", "devicecenter", "device_center",
            "mediaoutput", "media_output", "accessory", "nearby", "audio",
            "milink", "mxbluetooth", "circulate", "ancbattery", "profilecontext",
            "headsetinfo", "headsetservicecontroller"
    };
    private static final String[] SIGNATURE_CLASSES = {
            "com.miui.headset.runtime.HeadsetLocalService",
            "com.miui.headset.runtime.HeadsetLocalServiceImpl",
            "com.miui.headset.runtime.HeadsetCirculateSessionService",
            "com.miui.headset.runtime.HeadsetCirculateImpl",
            "com.miui.headset.runtime.HeadsetLocalImpl",
            "com.miui.headset.runtime.ProfileContext",
            "com.miui.headset.runtime.AncBatteryController",
            "com.miui.headset.runtime.model.ThirdPartyHeadsetStrategy",
            "com.miui.headset.runtime.model.AirPodsHeadsetStrategy",
            "com.miui.headset.api.HeadsetInfo",
            "com.miui.headset.runtime.model.HeadsetStateModel",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.miui.circulate.device.api.DeviceControlManager",
            "com.miui.circulate.device.api.DeviceInfo",
            "com.miui.circulate.device.api.BatteryInfo",
            "com.miui.circulate.device.api.a",
            "com.miui.circulate.device.api.b",
            "com.miui.circulate.device.api.c",
            "com.miui.circulate.device.api.d",
            "com.miui.circulate.api.protocol.headset.HeadsetServiceClient",
            "com.miui.circulate.api.protocol.headset.HeadsetDeviceManager",
            "com.miui.circulate.api.service.CirculateDeviceInfo",
            "com.miui.circulate.api.service.CirculateServiceInfo",
            "com.miui.circulate.world.sticker.PanelInfo",
            "com.miui.circulate.world.sticker.MainCardView",
            "wc.b",
            "wc.a",
            "tc.a",
            "com.miui.circulate.world.headset.ui.g",
            "com.android.bluetooth.ble.app.headset.BluetoothHeadsetService",
            "com.android.bluetooth.ble.app.headset.HeadsetProvider",
            "com.android.bluetooth.ble.app.headset.miuibluetoothprovider.MiuiBluetoothContentProvider",
            "com.android.bluetooth.ble.app.headset.aiaction.MiuiBluetoothActionProvider",
            "com.android.bluetooth.ble.app.fastconnect.MiuiFastConnectService",
            "com.android.bluetooth.ble.app.fastconnect.thirdparty.MiuiSonyFastConnectHelper",
            "com.android.bluetooth.ble.app.fastconnect.thirdparty.OpenProtocolHandler",
            "miui.systemui.devicecenter.DeviceCenterController",
            "miui.systemui.devicecenter.devices.DeviceInfoWrapper",
            "miui.systemui.devicecenter.devices.DeviceItem",
            "miui.systemui.devicecenter.view.MLBatteryView",
            "miui.systemui.controlcenter.panel.main.devicecenter.devices.DeviceItemViewHolder",
            "miui.systemui.controlcenter.panel.main.devicecenter.devices.DetailViewHolder"
    };

    private final XposedModule module;
    private final String packageName;
    private final ClassLoader classLoader;
    private final Set<String> seenClasses = ConcurrentHashMap.newKeySet();
    private final Set<String> seenSignatures = ConcurrentHashMap.newKeySet();

    HyperOsSurfaceProbeHook(XposedModule module, String packageName, ClassLoader classLoader) {
        this.module = module;
        this.packageName = packageName;
        this.classLoader = classLoader;
    }

    void install() throws ReflectiveOperationException {
        module.log(Log.INFO, TAG, "Installing read-only HyperOS surface probe in " + packageName);
        hookClassLoader();
        emitFromCurrentApplication();
        Method method = Instrumentation.class.getDeclaredMethod(
                "callApplicationOnCreate",
                Application.class
        );
        method.setAccessible(true);
        module.hook(method)
                .setId("technicspods.hyperos-probe:application-on-create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    emitApplication((Application) chain.getArg(0));
                    return result;
                });
    }

    private void hookClassLoader() {
        try {
            Method method = ClassLoader.class.getDeclaredMethod("loadClass", String.class, boolean.class);
            method.setAccessible(true);
            module.hook(method)
                    .setId("technicspods.hyperos-probe:class-load")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object argument = chain.getArg(0);
                        if (argument instanceof String name && isInteresting(name)
                                && seenClasses.add(name)) {
                            module.log(Log.INFO, TAG, "Loaded surface class: " + name);
                            if (isSignatureClass(name) && result instanceof Class<?> type) {
                                emitTypeSignatures(type);
                            }
                        }
                        return result;
                    });
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to hook class loading in " + packageName, error);
        }
    }

    private void emitFromCurrentApplication() {
        try {
            Class<?> activityThread = Class.forName(
                    "android.app.ActivityThread",
                    false,
                    classLoader
            );
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            if (application instanceof Application value) emitApplication(value);
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "Application is not ready during package hook: " + packageName);
        }
    }

    private void emitApplication(Application application) {
        Context context = application.getApplicationContext();
        String version = "unknown";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            version = info.versionName + "/" + info.getLongVersionCode();
        } catch (Throwable ignored) {
        }
        module.log(Log.INFO, TAG, "Surface process ready package=" + packageName
                + ";version=" + version
                + ";sdk=" + Build.VERSION.SDK_INT
                + ";release=" + Build.VERSION.RELEASE);
        emitTargetMethodSignatures();
    }

    /** Emit the ROM-specific method surface needed to choose protective hooks. */
    private void emitTargetMethodSignatures() {
        for (String className : SIGNATURE_CLASSES) {
            try {
                Class<?> type = Class.forName(className, false, classLoader);
                emitTypeSignatures(type);
            } catch (ClassNotFoundException ignored) {
                // Optional classes differ between HyperOS builds.
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "Failed to inspect " + className, error);
            }
        }
    }

    private void emitTypeSignatures(Class<?> type) {
        List<String> signatures = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            signatures.add("ctor " + constructor.toGenericString());
        }
        for (Method method : type.getDeclaredMethods()) {
            signatures.add("method " + method.toGenericString());
        }
        signatures.sort(Comparator.naturalOrder());
        for (String signature : signatures) {
            String line = type.getName() + " :: " + signature;
            if (seenSignatures.add(line)) {
                module.log(Log.INFO, TAG, line);
            }
        }
    }

    private static boolean isSignatureClass(String name) {
        for (String candidate : SIGNATURE_CLASSES) {
            if (candidate.equals(name)) return true;
        }
        return false;
    }

    private static boolean isInteresting(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String token : TOKENS) {
            if (lower.contains(token)) return true;
        }
        return false;
    }
}
