package dev.technics.pods.hook;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import dev.technics.pods.state.Az100StateProvider;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.technics.pods.capture.CaptureContract;
import dev.technics.pods.state.Az100StateFreshness;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Protective bridge for the legacy HyperOS Settings headset activity. */
final class Az100SettingsBridgeHook {
    private static final String TAG = "TechnicsPods-SettingsBridge";
    private static final String ACTIVITY =
            "com.android.settings.bluetooth.MiuiHeadsetActivity";
    private static final String HEADSET_CONSTANTS =
            "com.android.settings.bluetooth.HeadsetIDConstants";
    private static final String HEADSET_FRAGMENT =
            "com.android.settings.bluetooth.MiuiHeadsetFragment";
    private static final String HEADSET_BATTERY =
            "com.android.settings.bluetooth.tws.MiuiHeadsetBattery";
    private static final String SERVICE_PROXY =
            "com.android.bluetooth.ble.app.IMiuiHeadsetService$Stub$Proxy";
    private static final long STATE_CACHE_MILLIS = 1_000L;
    private static final long STATE_MAX_AGE_MILLIS = 120_000L;

    private final XposedModule module;
    private final ClassLoader classLoader;
    private final Az100HookDeviceIdentity identity;
    private final Set<String> knownAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> diagnosticEvents = ConcurrentHashMap.newKeySet();
    private volatile Context appContext;
    private volatile BridgeState cachedState;
    private volatile long cachedAt;
    private long cachedIdentityRevision = -1L;
    private final AtomicBoolean stateRefreshPending = new AtomicBoolean();
    private volatile WeakReference<Object> activeFragment = new WeakReference<>(null);
    private final AtomicBoolean controlPending = new AtomicBoolean();
    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TechnicsPods-Settings-control");
        thread.setDaemon(true);
        return thread;
    });

    Az100SettingsBridgeHook(XposedModule module, ClassLoader classLoader) {
        this.module = module;
        this.classLoader = classLoader;
        identity = new Az100HookDeviceIdentity(module, this::clearIdentityState);
    }

    private void clearIdentityState() {
        synchronized (identity) {
            knownAddresses.clear();
            cachedState = null;
            cachedAt = 0L;
            cachedIdentityRevision = -1L;
            activeFragment = new WeakReference<>(null);
        }
    }

    void install() throws ReflectiveOperationException {
        module.log(Log.INFO, TAG, "Installing AZ100 Settings bridge");
        installFromCurrentApplication();
        Method method = Instrumentation.class.getDeclaredMethod(
                "callApplicationOnCreate", Application.class);
        method.setAccessible(true);
        module.hook(method)
                .setId("technicspods.settings-bridge:application-on-create")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (chain.getArg(0) instanceof Application application) {
                        installForApplication(application);
                    }
                    return result;
                });
    }

    private void installFromCurrentApplication() {
        try {
            Class<?> activityThread = Class.forName(
                    "android.app.ActivityThread", false, classLoader);
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            if (application instanceof Application value) installForApplication(value);
        } catch (Throwable ignored) {
            module.log(Log.INFO, TAG, "Application is not ready");
        }
    }

    private synchronized void installForApplication(Application application) {
        if (appContext != null) return;
        appContext = application.getApplicationContext();
        try {
            appContext.getContentResolver().registerContentObserver(Az100StateProvider.STATE_URI,
                    true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override public void onChange(boolean selfChange) {
                            synchronized (identity) {
                                cachedAt = 0L;
                            }
                            Object fragment = activeFragment.get();
                            if (fragment != null) injectFragmentStatus(fragment, true);
                        }
                    });
        } catch (RuntimeException error) {
            module.log(Log.WARN, TAG, "Settings state observer unavailable", error);
        }
        int count = 0;
        count += installActivityHooks();
        count += installSupportHooks();
        count += installServiceProxyHooks();
        count += installFragmentHooks();
        count += installBatteryHooks();
        module.log(Log.INFO, TAG, "AZ100 Settings bridge ready;hooks=" + count);
        HostCapabilityReport.report(appContext, "settings_bridge", count,
                "MiuiHeadsetFragment.updateAncMode(int,boolean);noise level mapping pending verification");
        HostCapabilityReport.report(appContext, "settings_noise_level", 0,
                "Native updateAncLevel encoding unverified; no conversion or writes enabled");
        readState();
    }

    private int installActivityHooks() {
        try {
            Class<?> type = Class.forName(ACTIVITY, false, classLoader);
            int count = 0;
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("onCreate") && !name.equals("getDeviceID")
                        && !name.equals("getSupport")) continue;
                method.setAccessible(true);
                module.hook(method)
                        .setId(hookId(ACTIVITY, method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object owner = chain.getThisObject();
                            if (name.equals("onCreate")) {
                                patchActivityIntent(owner);
                                return chain.proceed();
                            }
                            Object result = chain.proceed();
                            if (!isTargetOwner(owner) || method.getReturnType() != String.class) {
                                return result;
                            }
                            return name.equals("getDeviceID")
                                    ? Az100MiLinkPolicy.DEVICE_ID
                                    : Az100MiLinkPolicy.supportString();
                        });
                count++;
            }
            return count;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install activity hooks", error);
            return 0;
        }
    }

    private int installSupportHooks() {
        try {
            Class<?> type = Class.forName(HEADSET_CONSTANTS, false, classLoader);
            int count = 0;
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("checkSupport") && !name.equals("isTWS01Headset")
                        && !name.equals("isBleMmaConnect")) continue;
                method.setAccessible(true);
                module.hook(method)
                        .setId(hookId(HEADSET_CONSTANTS, method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            List<Object> args = chain.getArgs();
                            if (name.equals("isBleMmaConnect")) {
                                BluetoothDevice device = deviceArgument(args);
                                if (device != null && isAz100(device)) {
                                    remember(device);
                                    Boolean connected = isDeviceConnected(device);
                                    if (connected == null) return chain.proceed();
                                    logOnce("mma-connect", "Matched AZ100 Settings connection check");
                                    Object result = supportedValue(method.getReturnType(), connected);
                                    return result == UnsupportedResult.INSTANCE
                                            ? chain.proceed() : result;
                                }
                                return chain.proceed();
                            }
                            if (!hasTargetArgument(args)) return chain.proceed();
                            logOnce("support", "Matched AZ100 Settings support identity");
                            Object result = supportedValue(method.getReturnType(), true);
                            return result == UnsupportedResult.INSTANCE
                                    ? chain.proceed() : result;
                        });
                count++;
            }
            return count;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install support hooks", error);
            return 0;
        }
    }

    private int installServiceProxyHooks() {
        try {
            Class<?> type = Class.forName(SERVICE_PROXY, false, classLoader);
            int count = 0;
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("checkSupport") && !name.equals("getDeviceInfo")
                        && !name.equals("isSupportAudioSwitch")) continue;
                method.setAccessible(true);
                module.hook(method)
                        .setId(hookId(SERVICE_PROXY, method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            List<Object> args = chain.getArgs();
                            if (!hasTargetArgument(args)) return chain.proceed();
                            Object result = proxyResult(name, method.getReturnType());
                            if (result == UnsupportedResult.INSTANCE) return chain.proceed();
                            logOnce("proxy-" + name, "Matched AZ100 Settings proxy "
                                    + signature(method));
                            return result;
                        });
                count++;
            }
            return count;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install service proxy hooks", error);
            return 0;
        }
    }

    private int installFragmentHooks() {
        try {
            Class<?> type = Class.forName(HEADSET_FRAGMENT, false, classLoader);
            int count = 0;
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (!name.equals("onCreateView") && !name.equals("onServiceConnected")
                        && !name.equals("onDestroyView") && !name.equals("refreshStatus")
                        && !name.equals("handleConnectMmaFailed")
                        && !name.equals("updateAncMode") && !name.equals("updateAncLevel")) {
                    continue;
                }
                method.setAccessible(true);
                module.hook(method)
                        .setId(hookId(HEADSET_FRAGMENT, method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> interceptFragment(method, chain));
                count++;
            }
            return count;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install fragment hooks", error);
            return 0;
        }
    }

    private Object interceptFragment(Method method, XposedInterface.Chain chain) throws Throwable {
        String name = method.getName();
        Object fragment = chain.getThisObject();
        if (name.equals("onDestroyView")) {
            if (activeFragment.get() == fragment) activeFragment.clear();
            return chain.proceed();
        }
        if (name.equals("onCreateView") || name.equals("onServiceConnected")) {
            Object result = chain.proceed();
            if (isTargetOwner(fragment)) {
                activeFragment = new WeakReference<>(fragment);
                injectFragmentStatus(fragment, true);
            }
            return result;
        }
        if (!isTargetOwner(fragment)) return chain.proceed();
        if (name.equals("refreshStatus")) {
            String key = stringArgument(chain.getArgs());
            if (key != null && key.startsWith("MMA_CONNECTION_FAILED")) {
                logOnce("mma-failure", "Suppressed AZ100 MMA connection failure");
                injectFragmentStatus(fragment, false);
                return defaultValue(method.getReturnType());
            }
        } else if (name.equals("handleConnectMmaFailed")) {
            logOnce("mma-handler", "Suppressed AZ100 MMA failure handler");
            injectFragmentStatus(fragment, false);
            return defaultValue(method.getReturnType());
        } else if (name.equals("updateAncMode") || name.equals("updateAncLevel")) {
            if (name.equals("updateAncLevel")) {
                // The host slider encoding has not been verified against AZ100's 1..100 level.
                boolean userAction = chain.getArgs().stream().anyMatch(Boolean.TRUE::equals);
                if (!userAction) return chain.proceed();
                logOnce("level-pending", "Settings noise level encoding pending verification");
                return defaultValue(method.getReturnType());
            }
            Az100SettingsControlPolicy.Request request =
                    Az100SettingsControlPolicy.request(name, chain.getArgs());
            if (request == null) return chain.proceed();
            if (!controlPending.compareAndSet(false, true)) {
                logOnce("control-busy", "Ignored overlapping AZ100 Settings control request");
                return defaultValue(method.getReturnType());
            }
            startSettingsControl(fragment, request);
            return defaultValue(method.getReturnType());
        }
        return chain.proceed();
    }

    private void startSettingsControl(Object fragment, Az100SettingsControlPolicy.Request request) {
        long selectionRevision = identity.revision();
        BluetoothDevice device = findBluetoothDevice(fragment);
        String expectedAddress = device == null ? null
                : Az100HookDeviceIdentity.normalize(safeAddress(device));
        if (expectedAddress == null) { controlPending.set(false); return; }
        controlExecutor.execute(() -> {
            try {
                BluetoothDevice currentDevice = findBluetoothDevice(fragment);
                String currentAddress = currentDevice == null
                        ? null : Az100HookDeviceIdentity.normalize(safeAddress(currentDevice));
                if (selectionRevision != identity.revision() || !isTargetOwner(fragment)
                        || !expectedAddress.equals(currentAddress)) return;
                int mode = request.mode();
                if (mode < 0) {
                    BridgeState state = readState(expectedAddress);
                    if (state == null || state.outsideMode == null) {
                        throw new IllegalStateException("当前降噪模式未知，无法设置降噪等级");
                    }
                    mode = state.outsideMode;
                }
                Bundle values = new Bundle();
                values.putInt("mode", mode);
                values.putString("expected_address", expectedAddress);
                if (request.noiseLevel() != null) values.putInt("noise", request.noiseLevel());
                Bundle published = appContext.getContentResolver().call(
                        Az100StateProvider.STATE_URI,
                        Az100StateProvider.METHOD_CONTROL_REQUEST,
                        null,
                        values);
                if (published == null || !published.getBoolean("accepted", false)) {
                    throw new IllegalStateException("Control request rejected");
                }
                clearStateCache();
            } catch (Throwable error) {
                module.log(Log.WARN, TAG, "AZ100 Settings control failed", error);
            } finally {
                controlPending.set(false);
                new Handler(Looper.getMainLooper()).post(() -> {
                    clearStateCache();
                    if (activeFragment.get() == fragment) injectFragmentStatus(fragment, true);
                });
            }
        });
    }

    private void clearStateCache() {
        synchronized (identity) { cachedAt = 0L; }
        readState();
    }

    private int installBatteryHooks() {
        try {
            Class<?> type = Class.forName(HEADSET_BATTERY, false, classLoader);
            int count = 0;
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("onBatteryChanged")
                        || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != String.class) continue;
                method.setAccessible(true);
                module.hook(method)
                        .setId(hookId(HEADSET_BATTERY, method))
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object batteryView = chain.getThisObject();
                            if (!isTargetOwner(batteryView)) return chain.proceed();
                            BluetoothDevice device = findBluetoothDevice(batteryView);
                            BridgeState state = readState(device == null ? null : safeAddress(device));
                            if (state == null) return chain.proceed();
                            if (invokeIfPresent(batteryView, "onBatteryChanged",
                                    state.batteries[0], state.batteries[1], state.batteries[2])) {
                                logOnce("battery", "Injected AZ100 Settings battery state");
                                return defaultValue(method.getReturnType());
                            }
                            return chain.proceed();
                        });
                count++;
            }
            return count;
        } catch (ClassNotFoundException ignored) {
            return 0;
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install battery hooks", error);
            return 0;
        }
    }

    private void patchActivityIntent(Object owner) {
        if (!(owner instanceof Activity activity)) return;
        Intent intent = activity.getIntent();
        if (intent == null) return;
        BluetoothDevice device = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (device == null || !isAz100(device)) return;
        remember(device);
        intent.putExtra("MIUI_HEADSET_SUPPORT", Az100MiLinkPolicy.supportString());
        intent.putExtra("DEVICE_ID", Az100MiLinkPolicy.DEVICE_ID);
        if (!intent.hasExtra("COME_FROM")) {
            intent.putExtra("COME_FROM", "MIUI_BLUETOOTH_SETTINGS");
        }
        logOnce("activity", "Patched AZ100 MiuiHeadsetActivity intent");
    }

    private void injectFragmentStatus(Object fragment, boolean includeRefresh) {
        try {
            BluetoothDevice device = findBluetoothDevice(fragment);
            BridgeState state = readState(device == null ? null : safeAddress(device));
            if (state == null || state.outsideMode == null || device == null || !isAz100(device)) return;
            remember(device);
            boolean atUi = invokeIfPresent(fragment, "updateAtUiInfo",
                    Az100SettingsPolicy.atUiPayload(
                    state.outsideMode, state.ambientMode, state.batteries));
            boolean ancUi = invokeIfPresent(fragment, "updateAncUi",
                    Az100SettingsPolicy.settingsLevel(
                    state.outsideMode, state.ambientMode), false);
            boolean refresh = false;
            if (includeRefresh) {
                String address = safeAddress(device);
                if (address != null) {
                    refresh = invokeIfPresent(fragment, "refreshStatus", address,
                            Az100SettingsPolicy.refreshPayload(state.outsideMode,
                                    state.ambientMode, state.batteries, state.firmware));
                }
            }
            View fragmentView = fragmentView(fragment);
            if (fragmentView != null && appContext != null) {
                Az100ImageResource.replaceDetailImages(appContext, fragmentView);
            }
            logOnce("fragment", "AZ100 Settings fragment state;atUi=" + atUi
                    + ";ancUi=" + ancUi + ";refresh=" + refresh);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to inject Settings fragment state", error);
        }
    }

    private static View fragmentView(Object fragment) {
        if (fragment == null) return null;
        try {
            Method method = fragment.getClass().getMethod("getView");
            method.setAccessible(true);
            Object result = method.invoke(fragment);
            return result instanceof View view ? view : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private BridgeState readState() {
        long now = System.currentTimeMillis();
        BridgeState previous;
        synchronized (identity) {
            boolean currentIdentity = cachedIdentityRevision == identity.revision();
            previous = currentIdentity ? cachedState : null;
            if (currentIdentity && now - cachedAt < STATE_CACHE_MILLIS) return previous;
        }
        Context context = appContext;
        if (context == null) return null;
        if (stateRefreshPending.compareAndSet(false, true)) {
            controlExecutor.execute(() -> {
                long identityRevision = identity.revision();
                try {
                    BridgeState updated = BridgeState.from(context.getContentResolver().call(
                            Az100StateProvider.STATE_URI, Az100StateProvider.METHOD_STATE, null, null));
                    synchronized (identity) {
                        if (identityRevision == identity.revision()) {
                            cachedState = updated;
                            cachedIdentityRevision = identityRevision;
                        }
                    }
                } catch (Throwable error) {
                    synchronized (identity) {
                        if (identityRevision == identity.revision()) {
                            cachedState = null;
                            cachedIdentityRevision = identityRevision;
                        }
                    }
                } finally {
                    synchronized (identity) {
                        if (identityRevision == identity.revision()) cachedAt = System.currentTimeMillis();
                    }
                    stateRefreshPending.set(false);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Object fragment = activeFragment.get();
                        if (fragment != null && isTargetOwner(fragment)) injectFragmentStatus(fragment, true);
                    });
                }
            });
        }
        return previous;
    }

    private BridgeState readState(String actualAddress) {
        BridgeState state = readState();
        String address = Az100HookDeviceIdentity.normalize(actualAddress);
        return state != null && address != null && address.equals(state.deviceAddress) ? state : null;
    }

    private boolean isTargetOwner(Object owner) {
        BluetoothDevice device = findBluetoothDevice(owner);
        // A real BluetoothDevice is authoritative. The shared HyperOS model ID
        // is only a display hint and must never claim another headset instance.
        if (device != null) {
            if (!isAz100(device)) return false;
            remember(device);
            return true;
        }
        return false;
    }

    private boolean hasTargetArgument(List<Object> args) {
        BluetoothDevice device = deviceArgument(args);
        if (device != null && isAz100(device)) {
            remember(device);
            return true;
        }
        if (device != null) return false;
        for (Object argument : args) {
            if (argument instanceof String value && isKnownAddress(value)) return true;
        }
        return false;
    }

    private Boolean isDeviceConnected(BluetoothDevice device) {
        try {
            Method method = BluetoothDevice.class.getDeclaredMethod("isConnected");
            method.setAccessible(true);
            Object result = method.invoke(device);
            if (result instanceof Boolean connected) return connected;
        } catch (Throwable ignored) {
        }
        BridgeState state = readState(safeAddress(device));
        return state != null && state.outsideMode != null ? Boolean.TRUE : null;
    }

    private void remember(BluetoothDevice device) {
        if (!identity.matches(device)) return;
        String address = safeAddress(device);
        if (address != null) knownAddresses.add(address.toUpperCase(Locale.ROOT));
    }

    private boolean isKnownAddress(String value) {
        return identity.permitsKnownAddress(value) && knownAddresses.contains(value.toUpperCase(Locale.ROOT));
    }

    private boolean isAz100(BluetoothDevice device) {
        return identity.matches(device);
    }

    private static BluetoothDevice deviceArgument(List<Object> args) {
        for (Object argument : args) {
            if (argument instanceof BluetoothDevice device) return device;
        }
        return null;
    }

    private static String stringArgument(List<Object> args) {
        for (Object argument : args) {
            if (argument instanceof String value) return value;
        }
        return null;
    }

    private static String describeAncArgs(List<Object> args) {
        StringBuilder output = new StringBuilder("[");
        for (int index = 0; index < args.size(); index++) {
            if (index > 0) output.append(',');
            Object argument = args.get(index);
            if (argument == null || argument instanceof Integer || argument instanceof Boolean) {
                output.append(argument);
            } else if (argument instanceof String value
                    && value.matches("[0-9A-Fa-f]{4}")) {
                output.append(value);
            } else {
                output.append(argument.getClass().getSimpleName());
            }
        }
        return output.append(']').toString();
    }

    private static BluetoothDevice findBluetoothDevice(Object owner) {
        if (owner == null) return null;
        Object named = fieldValue(owner, "mDevice");
        if (named instanceof BluetoothDevice device) return device;
        Class<?> type = owner.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !BluetoothDevice.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value instanceof BluetoothDevice device) return device;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static String stringField(Object owner, String name) {
        Object value = fieldValue(owner, name);
        return value instanceof String string ? string : null;
    }

    private static Object fieldValue(Object owner, String name) {
        if (owner == null) return null;
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static boolean invokeIfPresent(Object owner, String name, Object... args) {
        if (owner == null) return false;
        Class<?> type = owner.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length
                        || !accepts(method.getParameterTypes(), args)) continue;
                try {
                    method.setAccessible(true);
                    method.invoke(owner, args);
                    return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            if (args[index] == null) {
                if (parameterTypes[index].isPrimitive()) return false;
                continue;
            }
            if (!boxed(parameterTypes[index]).isInstance(args[index])) return false;
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object proxyResult(String name, Class<?> resultType) {
        if (name.equals("checkSupport") || name.equals("getDeviceInfo")) {
            if (resultType == String.class) return Az100MiLinkPolicy.supportString();
            return UnsupportedResult.INSTANCE;
        }
        if (name.equals("isSupportAudioSwitch")) {
            return supportedValue(resultType, true);
        }
        return UnsupportedResult.INSTANCE;
    }

    private static Object supportedValue(Class<?> type, boolean value) {
        if (type == boolean.class || type == Boolean.class) return value;
        if (type == int.class || type == Integer.class) return value ? 1 : 0;
        if (type == String.class) return value ? "1" : "0";
        return UnsupportedResult.INSTANCE;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == void.class || !type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private static String safeAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException error) {
            return null;
        }
    }

    private void logOnce(String key, String message) {
        if (diagnosticEvents.add(key)) module.log(Log.INFO, TAG, message);
    }

    private static String hookId(String className, Method method) {
        return "technicspods.settings-bridge:" + className + ":" + signature(method);
    }

    private static String signature(Method method) {
        StringBuilder output = new StringBuilder(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) output.append(',');
            output.append(parameters[index].getName());
        }
        return output.append(')').toString();
    }

    private enum UnsupportedResult { INSTANCE }

    private static final class BridgeState {
        final String deviceAddress;
        final int[] batteries;
        final Integer outsideMode;
        final Integer ambientMode;
        final String firmware;

        private BridgeState(String deviceAddress, int[] batteries, Integer outsideMode, Integer ambientMode,
                String firmware) {
            this.deviceAddress = deviceAddress;
            this.batteries = batteries;
            this.outsideMode = outsideMode;
            this.ambientMode = ambientMode;
            this.firmware = firmware;
        }

        static BridgeState from(Bundle bundle) {
            if (bundle == null || !bundle.getBoolean("reachable", false)) return null;
            String deviceAddress = Az100HookDeviceIdentity.normalize(bundle.getString("device_address"));
            if (deviceAddress == null) return null;
            long now = System.currentTimeMillis();
            long updatedAt = bundle.getLong("updated_at", 0L);
            boolean anyFresh = Az100StateFreshness.isFresh(updatedAt, now)
                    || Az100StateFreshness.isFresh(
                    bundle.getLong("outside_mode_updated_at", 0L), now)
                    || Az100StateFreshness.isFresh(
                    bundle.getLong("ambient_mode_updated_at", 0L), now)
                    || Az100StateFreshness.isFresh(
                    bundle.getLong("left_battery_updated_at", 0L), now)
                    || Az100StateFreshness.isFresh(
                    bundle.getLong("right_battery_updated_at", 0L), now)
                    || Az100StateFreshness.isFresh(
                    bundle.getLong("cradle_battery_updated_at", 0L), now);
            if (!anyFresh) return null;
            int[] batteries = new int[]{
                    nullableBattery(bundle, "left_battery", "left_battery_updated_at", now),
                    nullableBattery(bundle, "right_battery", "right_battery_updated_at", now),
                    nullableBattery(bundle, "cradle_battery", "cradle_battery_updated_at", now)
            };
            return new BridgeState(
                    deviceAddress,
                    batteries,
                    Az100StateFreshness.isFresh(bundle.getLong("outside_mode_updated_at", 0L), now)
                            ? nullableInteger(bundle, "outside_mode") : null,
                    Az100StateFreshness.isFresh(bundle.getLong("ambient_mode_updated_at", 0L), now)
                            ? nullableInteger(bundle, "ambient_mode") : null,
                    bundle.getString("firmware")
            );
        }

        private static int nullableBattery(Bundle bundle, String key, String updatedKey, long now) {
            if (!bundle.containsKey(key)
                    || !Az100StateFreshness.isFresh(bundle.getLong(updatedKey, 0L), now)) {
                return Az100MiLinkPolicy.BATTERY_DISCONNECTED;
            }
            return Math.max(0, Math.min(100, bundle.getInt(key)));
        }

        private static Integer nullableInteger(Bundle bundle, String key) {
            return bundle.containsKey(key) ? bundle.getInt(key) : null;
        }
    }
}
