package dev.technics.pods.hook;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.app.Activity;
import android.app.Instrumentation;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.Binder;
import android.os.SystemClock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;

import dev.technics.pods.ModulePreferences;
import dev.technics.pods.capture.CaptureContract;
import dev.technics.pods.state.Az100StateProvider;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Announces the official Technics app's AZ100 socket ownership to the module
 * process. This hook never writes to the socket and never blocks connect().
 */
@SuppressLint("MissingPermission")
final class Az100OfficialHandoverHook {
    private static final String TAG = "TechnicsPods-Handover";
    static final String ACTION_QUERY_OFFICIAL_SESSIONS =
            "dev.technics.pods.action.QUERY_OFFICIAL_SESSIONS";
    private static final Uri STATE_URI = Az100StateProvider.STATE_URI;

    private final XposedModule module;
    private final ClassLoader classLoader;
    private final Map<Object, FactorySocket> factorySockets = Collections.synchronizedMap(new WeakHashMap<>());
    private final Az100OfficialSocketRegistry<Binder> sessions = new Az100OfficialSocketRegistry<>();
    private final Map<Binder, Object> pendingReleases = new java.util.HashMap<>();
    private final Object lifecycleLock = new Object();
    private volatile boolean observing;
    private volatile boolean recoveryReceiverRegistered;
    private volatile boolean reannouncing;
    private volatile long lastRecoveryAt;
    private final Handler recoveryHandler = new Handler(Looper.getMainLooper());
    private boolean recoveryScheduled;

    Az100OfficialHandoverHook(XposedModule module, ClassLoader classLoader) {
        this.module = module;
        this.classLoader = classLoader;
    }

    void install() {
        try {
            registerRecoveryReceiver();
            Method applicationReady = Instrumentation.class.getDeclaredMethod(
                    "callApplicationOnCreate", Application.class);
            module.hook(applicationReady).setId("technicspods.official-handover:application-ready")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        registerRecoveryReceiver();
                        return result;
                    });
            installFactories();
            Class<?> socketType = Class.forName(
                    BluetoothSocket.class.getName(), false, classLoader
            );
            for (Method method : socketType.getDeclaredMethods()) {
                method.setAccessible(true);
                if (method.getName().equals("connect") && method.getParameterCount() == 0) {
                    module.hook(method)
                            .setId("technicspods.official-handover:connect")
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object socket = chain.getThisObject();
                                if (!isAz100Socket(socket)) return chain.proceed();
                                Binder token;
                                boolean newSession;
                                synchronized (lifecycleLock) {
                                    Binder candidate = new Binder();
                                    token = sessions.begin(socket, candidate).token();
                                    newSession = token == candidate;
                                    if (newSession) announce(socket, Az100StateProvider.METHOD_OFFICIAL_BEGIN, token);
                                }
                                if (!newSession) return chain.proceed();
                                try {
                                    Object result = chain.proceed();
                                    synchronized (lifecycleLock) {
                                        if (sessions.connected(socket, token) != null) {
                                            announce(socket, Az100StateProvider.METHOD_OFFICIAL_CONNECTED, token);
                                        }
                                    }
                                    return result;
                                } catch (Throwable error) {
                                    synchronized (lifecycleLock) {
                                        if (sessions.remove(socket, token) != null) {
                                            release(socket, token, Az100StateProvider.METHOD_OFFICIAL_FAILED);
                                        }
                                    }
                                    throw error;
                                }
                            });
                } else if (method.getName().equals("close") && method.getParameterCount() == 0) {
                    module.hook(method)
                            .setId("technicspods.official-handover:close")
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object socket = chain.getThisObject();
                                boolean closed = false;
                                try {
                                    Object result = chain.proceed();
                                    closed = true;
                                    return result;
                                } finally {
                                    boolean confirmedClosed = closed;
                                    if (!confirmedClosed && socket instanceof BluetoothSocket value) {
                                        try { confirmedClosed = !value.isConnected(); } catch (Throwable ignored) { }
                                    }
                                    if (confirmedClosed) synchronized (lifecycleLock) {
                                        Az100OfficialSocketRegistry.Session<Binder> session = sessions.remove(socket, null);
                                        if (session != null) {
                                            release(socket, session.token(), Az100StateProvider.METHOD_OFFICIAL_RELEASED);
                                        }
                                        factorySockets.remove(socket);
                                    }
                                }
                            });
                }
            }
            module.log(Log.INFO, TAG, "Installed AZ100 official socket handover hooks");
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Official socket handover unavailable; leaving ownership unchanged", error);
        }
    }

    private synchronized void registerRecoveryReceiver() {
        Context context = currentApplication();
        if (context == null || recoveryReceiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter(ACTION_QUERY_OFFICIAL_SESSIONS);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context receiverContext, Intent intent) {
                    if (intent == null || !ACTION_QUERY_OFFICIAL_SESSIONS.equals(intent.getAction())) return;
                    boolean accepted = true;
                    synchronized (lifecycleLock) {
                        releaseDisconnectedSessions();
                        for (Az100OfficialSocketRegistry.Session<Binder> session : sessions.snapshot()) {
                            accepted &= announce(session.socket(), session.connected()
                                    ? Az100StateProvider.METHOD_OFFICIAL_CONNECTED
                                    : Az100StateProvider.METHOD_OFFICIAL_BEGIN,
                                    session.token());
                        }
                    }
                    setResultCode(accepted ? Activity.RESULT_OK : -2);
                }
            }, filter, Manifest.permission.BLUETOOTH_CONNECT, new Handler(Looper.getMainLooper()),
                    Context.RECEIVER_EXPORTED);
            recoveryReceiverRegistered = true;
            module.log(Log.INFO, TAG, "Registered official session recovery receiver");
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "Official session recovery receiver unavailable", error);
        }
    }

    private boolean isAz100Socket(Object socket) {
        if (!(socket instanceof BluetoothSocket value)) return false;
        try {
            BluetoothDevice device = value.getRemoteDevice();
            if (device == null) return false;
            String selectedAddress = selectedAddress();
            FactorySocket factory = factorySockets.get(socket);
            if (factory != null) {
                return factory.device().equals(device) && Az100OfficialSocketPolicy.verified(
                        selectedAddress, device.getAddress(), device.getName(), value.getConnectionType(), factory.uuid());
            }
            Object uuid = fieldValue(socket, "mUuid");
            if (uuid instanceof UUID candidate) return Az100OfficialSocketPolicy.verified(
                    selectedAddress, device.getAddress(), device.getName(), value.getConnectionType(), candidate);
            if (uuid instanceof android.os.ParcelUuid candidate) {
                return Az100OfficialSocketPolicy.verified(selectedAddress, device.getAddress(), device.getName(),
                        value.getConnectionType(), candidate.getUuid());
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String selectedAddress() {
        try {
            return module.getRemotePreferences(CaptureContract.PREFS_NAME)
                    .getString(ModulePreferences.SELECTED_ADDRESS, "");
        } catch (Throwable ignored) { return ""; }
    }

    private boolean announce(Object socket, String method, android.os.Binder token) {
        Context context = currentApplication();
        if (context == null) return false;
        registerRecoveryReceiver();
        observe(context);
        Bundle extras = new Bundle();
        extras.putString("socket", Integer.toHexString(System.identityHashCode(socket)));
        extras.putBinder("token", token);
        try {
            Bundle result = context.getContentResolver().call(STATE_URI, method, null, extras);
            if (result == null || !result.getBoolean("accepted", false)) {
                module.log(Log.INFO, TAG, "Handover announcement not accepted: " + method);
                return false;
            }
            return true;
        } catch (Throwable error) {
            module.log(Log.INFO, TAG, "Handover announcement unavailable: " + method);
            return false;
        }
    }

    private void release(Object socket, Binder token, String method) {
        pendingReleases.put(token, socket);
        if (announce(socket, method, token)) pendingReleases.remove(token);
    }

    private void retryReleases() {
        synchronized (lifecycleLock) {
            for (Map.Entry<Binder, Object> pending : new java.util.HashMap<>(pendingReleases).entrySet()) {
                if (announce(pending.getValue(), Az100StateProvider.METHOD_OFFICIAL_RELEASED, pending.getKey())) {
                    pendingReleases.remove(pending.getKey());
                }
            }
        }
    }

    private void releaseDisconnectedSessions() {
        synchronized (lifecycleLock) {
            for (Az100OfficialSocketRegistry.Session<Binder> session : sessions.removeDisconnected(socket -> {
                if (!(socket instanceof BluetoothSocket value)) return false;
                try { return !value.isConnected(); } catch (Throwable unknown) { return false; }
            })) {
                factorySockets.remove(session.socket());
                release(session.socket(), session.token(), Az100StateProvider.METHOD_OFFICIAL_RELEASED);
            }
        }
    }

    private void installFactories() {
        for (Method method : BluetoothDevice.class.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.equals("createRfcommSocketToServiceRecord")
                    && !name.equals("createInsecureRfcommSocketToServiceRecord")
                    && !name.equals("createRfcommSocket") && !name.equals("createInsecureRfcommSocket")) continue;
            if (method.getParameterCount() != 1) continue;
            module.hook(method).setId("technicspods.official-handover:factory:" + method.toGenericString())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        if (result instanceof BluetoothSocket && chain.getThisObject() instanceof BluetoothDevice device) {
                            UUID service = Az100OfficialSocketPolicy.serviceFromFactory(name, chain.getArg(0));
                            Integer channel = chain.getArg(0) instanceof Integer value ? value : null;
                            boolean target = Az100OfficialSocketPolicy.verified(selectedAddress(),
                                    device.getAddress(), device.getName(), 1, service);
                            if (target) factorySockets.put(result, new FactorySocket(device, service, channel));
                        }
                        return result;
                    });
        }
    }

    private synchronized void observe(Context context) {
        if (!recoveryScheduled) {
            recoveryScheduled = true;
            recoveryHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    releaseDisconnectedSessions();
                    retryReleases();
                    reannounceIfOwnerForgot();
                    recoveryHandler.postDelayed(this, 10_000L);
                }
            }, 10_000L);
        }
        if (observing) return;
        try {
            context.getContentResolver().registerContentObserver(STATE_URI, false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override public void onChange(boolean selfChange) { reannounceIfOwnerForgot(); }
                    });
            observing = true;
        } catch (RuntimeException ignored) { }
    }

    /** A restarted module provider may have lost tokens while official sockets remain open. */
    private void reannounceIfOwnerForgot() {
        releaseDisconnectedSessions();
        if (reannouncing || sessions.snapshot().isEmpty()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastRecoveryAt < 5_000L) return;
        lastRecoveryAt = now;
        Context context = currentApplication();
        if (context == null) return;
        reannouncing = true;
        try {
            Bundle state = context.getContentResolver().call(STATE_URI,
                    Az100StateProvider.METHOD_STATE, null, null);
            // Official callers cannot read module telemetry on some builds. Re-announcing the
            // same token is idempotent and also handles provider process recovery.
            if (state != null && state.getBoolean("official_busy", false)) return;
            synchronized (lifecycleLock) {
                for (Az100OfficialSocketRegistry.Session<Binder> session : sessions.snapshot()) {
                    announce(session.socket(), session.connected() ? Az100StateProvider.METHOD_OFFICIAL_CONNECTED
                            : Az100StateProvider.METHOD_OFFICIAL_BEGIN, session.token());
                }
            }
        } catch (Throwable ignored) { }
        finally { reannouncing = false; }
    }

    private record FactorySocket(BluetoothDevice device, UUID uuid, Integer channel) {}

    private Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName(
                    "android.app.ActivityThread", false, classLoader
            );
            Method method = activityThread.getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            Object value = method.invoke(null);
            return value instanceof Application app ? app.getApplicationContext() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object fieldValue(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
