package dev.technics.pods.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.technics.pods.state.Az100StateProvider;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import dev.technics.pods.state.Az100StateFreshness;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Protective HyperOS bridge for the already-discovered third-party-headset surface. */
final class Az100HyperOsBridgeHook {
    private static final String TAG = "TechnicsPods-HyperOsBridge";
    private static final String PROFILE_CONTEXT = "com.miui.headset.runtime.ProfileContext";
    private static final String THIRD_PARTY =
            "com.miui.headset.runtime.model.ThirdPartyHeadsetStrategy";
    private static final String ANC_CONTROLLER = "com.miui.headset.runtime.AncBatteryController";
    private static final String HEADSET_INFO = "com.miui.headset.api.HeadsetInfo";
    private static final long STATE_CACHE_MILLIS = 1_000L;
    private static final long STATE_MAX_AGE_MILLIS = 120_000L;


    private final XposedModule module;
    private final String packageName;
    private final ClassLoader classLoader;
    private final Az100HookDeviceIdentity identity;
    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TechnicsPods-AZ100-control");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean controlPending = new AtomicBoolean();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, BluetoothDevice> observedDevices = new ConcurrentHashMap<>();
    private volatile WeakReference<Object> controller = new WeakReference<>(null);
    private final Set<String> knownAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> diagnosticEvents = ConcurrentHashMap.newKeySet();
    private volatile Context appContext;
    private volatile BridgeState cachedState;
    private volatile long cachedAt;
    private long cachedIdentityRevision = -1L;
    private final AtomicBoolean stateRefreshPending = new AtomicBoolean();

    Az100HyperOsBridgeHook(XposedModule module, String packageName, ClassLoader classLoader) {
        this.module = module;
        this.packageName = packageName;
        this.classLoader = classLoader;
        identity = new Az100HookDeviceIdentity(module, this::clearIdentityState);
    }

    private void clearIdentityState() {
        synchronized (identity) {
            knownAddresses.clear();
            observedDevices.clear();
            cachedState = null;
            cachedAt = 0L;
            cachedIdentityRevision = -1L;
        }
    }

    void install() throws ReflectiveOperationException {
        module.log(Log.INFO, TAG, "Installing AZ100 HyperOS bridge in " + packageName);
        emitFromCurrentApplication();
        Method method = Instrumentation.class.getDeclaredMethod(
                "callApplicationOnCreate", Application.class);
        method.setAccessible(true);
        module.hook(method)
                .setId("technicspods.az100-bridge:application-on-create:" + packageName)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (chain.getArg(0) instanceof Application application) {
                        installForApplication(application);
                    }
                    return result;
                });
    }

    private void emitFromCurrentApplication() {
        try {
            Class<?> activityThread = Class.forName(
                    "android.app.ActivityThread", false, classLoader);
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            if (application instanceof Application value) installForApplication(value);
        } catch (Throwable ignored) {
            module.log(Log.INFO, TAG, "Application is not ready in " + packageName);
        }
    }

    private synchronized void installForApplication(Application application) {
        if (appContext != null) return;
        appContext = application.getApplicationContext();
        try {
            appContext.getContentResolver().registerContentObserver(Az100StateProvider.STATE_URI,
                    true, new ContentObserver(mainHandler) {
                        @Override public void onChange(boolean selfChange) { refreshHostState(); }
                    });
        } catch (RuntimeException error) {
            module.log(Log.WARN, TAG, "State observer unavailable", error);
        }
        readState();
        int count = 0;
        count += installMethods(PROFILE_CONTEXT);
        count += installMethods(THIRD_PARTY);
        count += installMethods(ANC_CONTROLLER);
        count += installMethods(HEADSET_INFO);
        module.log(Log.INFO, TAG, "AZ100 bridge ready package=" + packageName
                + ";hooks=" + count);
        HostCapabilityReport.report(appContext, "hyperos_bridge", count,
                "ProfileContext/ThirdPartyHeadsetStrategy/AncBatteryController/HeadsetInfo;rendering pending hardware");
    }

    private int installMethods(String className) {
        int count = 0;
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            if (ANC_CONTROLLER.equals(className)) {
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    module.hook(constructor)
                            .setId("technicspods.az100-controller:" + constructor.toGenericString())
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                controller = new WeakReference<>(chain.getThisObject());
                                return result;
                            });
                    count++;
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (!isBridgeMethod(name)) continue;
                method.setAccessible(true);
                String id = "technicspods.az100-bridge:" + packageName + ":"
                        + className + ":" + signature(method);
                module.hook(method)
                        .setId(id)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> intercept(method, chain));
                count++;
            }
        } catch (ClassNotFoundException ignored) {
            // Optional runtime surfaces differ between HyperOS releases.
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Failed to install " + className, error);
        }
        return count;
    }

    private Object intercept(Method method, XposedInterface.Chain chain) throws Throwable {
        String name = method.getName();
        BluetoothDevice device = deviceArgument(chain.getArgs());
        if (device == null) {
            String address = stringArgument(chain.getArgs());
            if ("getSwitchState".equals(name) && isKnownAddress(address)
                    && method.getReturnType() == int.class) {
                return Az100MiLinkPolicy.SWITCH_SUPPORTED;
            }
            if (HEADSET_INFO.equals(method.getDeclaringClass().getName())
                    && isTargetHeadsetInfo(chain.getThisObject())) {
                BridgeState state = readState(headsetInfoAddress(chain.getThisObject()));
                logOnce("headset-info", "Matched AZ100 HeadsetInfo");
                if (("getDeviceId".equals(name) || "component3".equals(name))
                        && method.getReturnType() == String.class) {
                    return Az100MiLinkPolicy.DEVICE_ID;
                }
                if (("getPowers".equals(name) || "component4".equals(name))
                        && state != null && List.class.isAssignableFrom(method.getReturnType())) {
                    return state.batteryLevels();
                }
                if (("getMode".equals(name) || "component5".equals(name))
                        && method.getReturnType() == int.class) {
                    if (state != null && state.outsideMode != null) return state.outsideMode;
                    return chain.proceed();
                }
                if (("getSwitchState".equals(name) || "component8".equals(name))
                        && method.getReturnType() == int.class) {
                    return Az100MiLinkPolicy.SWITCH_SUPPORTED;
                }
            }
            return chain.proceed();
        }
        if (!isAz100(device)) return chain.proceed();
        String deviceAddress = Az100HookDeviceIdentity.normalize(deviceKey(device));
        if (deviceAddress == null) return chain.proceed();
        knownAddresses.add(deviceAddress);
        observedDevices.put(deviceAddress, device);
        if (ANC_CONTROLLER.equals(method.getDeclaringClass().getName())) {
            controller = new WeakReference<>(chain.getThisObject());
        }
        logOnce("device", "Matched AZ100 BluetoothDevice");
        try {
            if ("isThirdPartDevice".equals(name) && method.getReturnType() == boolean.class) {
                return true;
            }
            if ("getDeviceId".equals(name) && method.getReturnType() == String.class) {
                return Az100MiLinkPolicy.DEVICE_ID;
            }
            BridgeState state = readState(deviceAddress);
            if (name.equals("getBatteryLevel") || name.equals("getBatteryCache")
                    || name.equals("getBatteryLevelCache")) {
                if (state != null && List.class.isAssignableFrom(method.getReturnType())) {
                    return state.batteryLevels();
                }
            }
            if (name.equals("getAncState") && method.getReturnType() == int.class) {
                if (state != null && state.outsideMode != null) return state.outsideMode;
                return chain.proceed();
            }
            if (name.equals("setAncState") || name.equals("setAncStateBlock")) {
                Integer requested = integerArgument(chain.getArgs());
                if (requested == null || requested < 0 || requested > 2) return chain.proceed();
                if (method.getReturnType() != int.class || method.getParameterCount() != 2
                        || method.getParameterTypes()[0] != BluetoothDevice.class
                        || method.getParameterTypes()[1] != int.class) return chain.proceed();
                return setAncState(requested, state, deviceAddress);
            }
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "AZ100 bridge interception failed for " + name, error);
        }
        return chain.proceed();
    }

    private int setAncState(int requested, BridgeState previous, String expectedAddress) {
        if (!controlPending.compareAndSet(false, true)) return -1;
        long selectionRevision = identity.revision();
        try {
            controlExecutor.execute(() -> {
                try {
                    if (selectionRevision != identity.revision()) return;
                    Bundle values = new Bundle();
                    values.putInt("mode", requested);
                    values.putString("expected_address", expectedAddress);
                    Bundle published = appContext.getContentResolver().call(Az100StateProvider.STATE_URI,
                            Az100StateProvider.METHOD_CONTROL_REQUEST, null, values);
                    if (published == null || !published.getBoolean("accepted", false)) {
                        throw new IllegalStateException("Control request rejected");
                    }
                } catch (Exception error) {
                    module.log(Log.WARN, TAG, "AZ100 control failed; no confirmed state", error);
                } finally {
                    controlPending.set(false);
                    mainHandler.post(this::refreshHostState);
                }
            });
        } catch (RuntimeException rejected) {
            controlPending.set(false);
            return -1;
        }
        // This synchronous API cannot report an asynchronous result. Keep only the last
        // confirmed selection; the property notification publishes the eventual result.
        return previous != null && previous.outsideMode != null ? previous.outsideMode : -1;
    }

    private void refreshHostState() {
        synchronized (identity) { cachedAt = 0L; }
        readState();
    }

    private void notifyHostState() {
        Object owner = controller.get();
        if (owner == null) return;
        try {
            Method notify = owner.getClass().getDeclaredMethod("notifyPropertyChanged",
                    BluetoothDevice.class, int.class, long.class);
            if (notify.getReturnType() != void.class) return;
            notify.setAccessible(true);
            for (BluetoothDevice device : observedDevices.values()) {
                if (!identity.matches(device)) continue;
                // Signatures observed on the test ROM; types 4/8 match the reference bridge.
                notify.invoke(owner, device, 4, 0L);
                notify.invoke(owner, device, 8, 0L);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            logOnce("refresh-unavailable", "Host state notification unavailable; getters still read shared state");
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
                    mainHandler.post(this::notifyHostState);
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

    private void logOnce(String key, String message) {
        if (diagnosticEvents.add(key)) module.log(Log.INFO, TAG, message);
    }

    private static boolean isBridgeMethod(String name) {
        return name.equals("getBatteryLevel") || name.equals("getBatteryCache")
                || name.equals("getBatteryLevelCache") || name.equals("getAncState")
                || name.equals("setAncState") || name.equals("setAncStateBlock")
                || name.equals("getHeadsetPropertyBlock")
                || name.equals("getDeviceId") || name.equals("isThirdPartDevice")
                || name.equals("getSwitchState") || name.equals("getPowers")
                || name.equals("getMode") || name.equals("component3")
                || name.equals("component4") || name.equals("component5")
                || name.equals("component8");
    }

    private static BluetoothDevice deviceArgument(List<Object> args) {
        for (Object argument : args) {
            if (argument instanceof BluetoothDevice device) return device;
        }
        return null;
    }

    private static Integer integerArgument(List<Object> args) {
        for (Object argument : args) {
            if (argument instanceof Integer value) return value;
        }
        return null;
    }

    private static String stringArgument(List<Object> args) {
        for (Object argument : args) {
            if (argument instanceof String value) return value;
        }
        return null;
    }

    private boolean isKnownAddress(String address) {
        return identity.permitsKnownAddress(address) && knownAddresses.contains(address.toUpperCase(Locale.ROOT));
    }

    private boolean isTargetHeadsetInfo(Object info) {
        return isKnownAddress(headsetInfoAddress(info));
    }

    private String headsetInfoAddress(Object info) {
        if (info == null) return null;
        String address = null;
        for (String name : new String[]{"getAddress", "component1"}) {
            try {
                Method getter = info.getClass().getDeclaredMethod(name);
                getter.setAccessible(true);
                Object value = getter.invoke(info);
                if (value instanceof String candidate) {
                    String normalized = Az100HookDeviceIdentity.normalize(candidate);
                    if (normalized == null) continue;
                    if (address != null && !address.equals(normalized)) return null;
                    address = normalized;
                }
            } catch (Throwable ignored) {
            }
        }
        return address;
    }

    private boolean isAz100(BluetoothDevice device) {
        return identity.matches(device);
    }

    private static String deviceKey(BluetoothDevice device) {
        try {
            String address = device.getAddress();
            return address == null ? "unknown" : address.toUpperCase(Locale.ROOT);
        } catch (SecurityException error) {
            return "unknown";
        }
    }

    private static String deviceId(BluetoothDevice device) {
        String key = deviceKey(device).replace(":", "");
        return "technics_eah_az100_" + key.toLowerCase(Locale.ROOT);
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

    private static final class BridgeState {
        final String deviceAddress;
        final boolean reachable;
        final Integer left;
        final Integer right;
        final Integer cradle;
        final Integer outsideMode;

        private BridgeState(String deviceAddress, boolean reachable, Integer left, Integer right,
                Integer cradle, Integer outsideMode) {
            this.deviceAddress = deviceAddress;
            this.reachable = reachable;
            this.left = left;
            this.right = right;
            this.cradle = cradle;
            this.outsideMode = outsideMode;
        }

        static BridgeState from(Bundle bundle) {
            if (bundle == null || !bundle.getBoolean("reachable", false)) return null;
            String deviceAddress = Az100HookDeviceIdentity.normalize(bundle.getString("device_address"));
            if (deviceAddress == null) return null;
            long now = System.currentTimeMillis();
            boolean controlsFresh = Az100StateFreshness.isFresh(
                    bundle.getLong("outside_mode_updated_at", 0L), now);
            boolean leftFresh = Az100StateFreshness.isFresh(
                    bundle.getLong("left_battery_updated_at", 0L), now);
            boolean rightFresh = Az100StateFreshness.isFresh(
                    bundle.getLong("right_battery_updated_at", 0L), now);
            boolean cradleFresh = Az100StateFreshness.isFresh(
                    bundle.getLong("cradle_battery_updated_at", 0L), now);
            if (!controlsFresh && !leftFresh && !rightFresh && !cradleFresh) return null;
            return new BridgeState(deviceAddress, true,
                    nullable(bundle, "left_battery", leftFresh),
                    nullable(bundle, "right_battery", rightFresh),
                    nullable(bundle, "cradle_battery", cradleFresh),
                    controlsFresh ? nullable(bundle, "outside_mode") : null);
        }

        List<Integer> batteryLevels() {
            ArrayList<Integer> values = new ArrayList<>(6);
            values.add(clamp(cradle));
            values.add(clamp(left));
            values.add(clamp(right));
            values.add(0);
            values.add(0);
            values.add(0);
            return values;
        }

        private static Integer nullable(Bundle bundle, String key, boolean fresh) {
            return fresh && bundle.containsKey(key) ? bundle.getInt(key) : null;
        }

        private static Integer nullable(Bundle bundle, String key) {
            return bundle.containsKey(key) ? bundle.getInt(key) : null;
        }

        private static int clamp(Integer value) {
            return value == null ? -1 : Math.max(0, Math.min(100, value));
        }
    }
}
