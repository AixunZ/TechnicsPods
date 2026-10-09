package dev.technics.pods.state;

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.technics.pods.ModulePreferences;
import dev.technics.pods.TechnicsPodsApp;
import dev.technics.pods.diagnostic.Az100DeviceIdentity;
import dev.technics.pods.capture.CaptureContract;
import dev.technics.pods.diagnostic.Az100DiagnosticResult;
import dev.technics.pods.diagnostic.Az100OutsideControlClient;
import dev.technics.pods.diagnostic.Az100OutsideControlResult;
import dev.technics.pods.diagnostic.Az100ReadOnlyClient;

/** The module process owns every active RFCOMM transaction. Hooks submit typed IPC only. */
public final class Az100ConnectionRuntime {
    private static Az100ConnectionRuntime instance;
    private final Context context;
    private final Az100StateRepository repository;
    private final Az100SessionCoordinator sessions = new Az100SessionCoordinator();
    private final Map<IBinder, IBinder.DeathRecipient> officialTokens = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TechnicsPods-connection-owner");
        thread.setDaemon(true);
        return thread;
    });
    private Runnable cancelActive = () -> {};
    private String status = "idle";
    private String controlStatus = "idle";
    private String controlError;
    private String requestId;
    private String lastStartError;
    private boolean connected;
    private boolean needsRecoveryRead;
    private boolean resumeAfterWorker;
    private boolean resumeForUserRequest;
    private boolean recoveryQueryPending;
    private int profileEpoch;
    private String deviceAddress = "";
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener;
    public static final String ACTION_QUERY_OFFICIAL_SESSIONS =
            "dev.technics.pods.action.QUERY_OFFICIAL_SESSIONS";

    private Az100ConnectionRuntime(Context context) {
        this.context = context.getApplicationContext();
        repository = new Az100StateRepository(this.context);
        repository.monitoringStopped();
        boolean recovering = ModulePreferences.get(this.context)
                .getBoolean("official_session_pending", false);
        sessions.recoverOfficial(recovering);
        preferencesListener = (preferences, key) -> {
            if (ModulePreferences.SELECTED_ADDRESS.equals(key)) {
                cancel();
                synchronized (Az100ConnectionRuntime.this) {
                    connected = false;
                    deviceAddress = "";
                    needsRecoveryRead = true;
                    resumeAfterWorker = false;
                    resumeForUserRequest = false;
                    profileEpoch++;
                }
                repository.clearDevice();
                reconcileConnection();
            }
        };
        ModulePreferences.get(this.context).registerOnSharedPreferenceChangeListener(preferencesListener);
        if (recovering) new Handler(Looper.getMainLooper()).post(this::recoverOfficialSessions);
        new Handler(Looper.getMainLooper()).post(this::reconcileConnection);
    }

    public static synchronized Az100ConnectionRuntime get(Context context) {
        if (!CaptureContract.MODULE_PACKAGE.equals(context.getPackageName())) {
            throw new IllegalArgumentException("Transport owner must be the module process");
        }
        if (instance == null) instance = new Az100ConnectionRuntime(context);
        return instance;
    }

    public CompletableFuture<Az100DiagnosticResult> read() {
        CompletableFuture<Az100DiagnosticResult> future = new CompletableFuture<>();
        final long ticket;
        final Az100ReadOnlyClient client;
        synchronized (this) {
            if (captureActive()) return failed(future, "采集期间已暂停模块连接");
            if (!connected) return failed(future, "目标耳机未连接，请检查蓝牙和设备选择");
            BluetoothDevice target;
            try { target = requireSelectedDevice(null); }
            catch (RuntimeException unavailable) { return failed(future, safeMessage(unavailable)); }
            ticket = sessions.begin();
            if (ticket == 0) return failed(future, busyMessage());
            client = new Az100ReadOnlyClient(context, target);
            cancelActive = client::cancel;
            status = "reading";
        }
        changed();
        worker.execute(() -> {
            long started = System.currentTimeMillis();
            try {
                synchronized (this) {
                    requireCurrent(ticket);
                    repository.samplingStarted(started);
                }
                Az100DiagnosticResult result = client.run();
                synchronized (this) {
                    requireCurrent(ticket);
                    repository.sampleSucceeded(result, started, System.currentTimeMillis());
                    needsRecoveryRead = false;
                    future.complete(result);
                }
            } catch (Exception error) {
                synchronized (this) {
                    if (sessions.isCurrent(ticket)) {
                        repository.sampleFailed(safeMessage(error), started, System.currentTimeMillis());
                    }
                    future.completeExceptionally(error);
                }
            } finally {
                finish(ticket);
            }
        });
        return future;
    }

    public CompletableFuture<Az100OutsideControlResult> control(int mode, Integer noise) {
        return control(mode, noise, null);
    }

    public CompletableFuture<Az100OutsideControlResult> control(int mode, Integer noise, String expectedAddress) {
        CompletableFuture<Az100OutsideControlResult> future = new CompletableFuture<>();
        if (mode < -1 || mode > 2 || (mode == -1 && noise == null)
                || (noise != null && (noise < 1 || noise > 100))) {
            return failed(future, "无效的控制参数");
        }
        final long ticket;
        final Az100OutsideControlClient client;
        synchronized (this) {
            if (captureActive()) return failed(future, "采集期间已暂停模块连接");
            if (!connected) return failed(future, "目标耳机未连接");
            BluetoothDevice target;
            try { target = requireSelectedDevice(expectedAddress); }
            catch (RuntimeException unavailable) { return failed(future, safeMessage(unavailable)); }
            if (needsRecoveryRead) {
                scheduleRecoveryRead(true);
                return failed(future, "正在恢复耳机状态，请稍后重试");
            }
            ticket = sessions.begin();
            if (ticket == 0) return failed(future, busyMessage());
            client = new Az100OutsideControlClient(context, target);
            cancelActive = client::cancel;
            requestId = UUID.randomUUID().toString();
            status = "controlling";
            controlStatus = "pending";
            controlError = null;
        }
        changed();
        worker.execute(() -> {
            try {
                synchronized (this) { requireCurrent(ticket); }
                Az100OutsideControlResult result = client.run(mode, noise);
                synchronized (this) {
                    requireCurrent(ticket);
                    repository.controlSucceeded(result, System.currentTimeMillis());
                    controlStatus = "confirmed";
                    future.complete(result);
                }
            } catch (Exception error) {
                synchronized (this) {
                    if (sessions.isCurrent(ticket)) {
                        controlStatus = "failed";
                        controlError = safeMessage(error);
                        repository.controlFailed(controlError, System.currentTimeMillis());
                    }
                    future.completeExceptionally(error);
                }
            } finally {
                finish(ticket);
            }
        });
        return future;
    }

    public synchronized boolean officialBegin(IBinder token) {
        if (token == null) return false;
        if (officialTokens.containsKey(token)) return true;
        IBinder.DeathRecipient death = () -> officialRelease(token);
        try {
            token.linkToDeath(death, 0);
        } catch (RemoteException dead) {
            return false;
        }
        officialTokens.put(token, death);
        sessions.officialBegin(token);
        if (sessions.recoveryPending()) new Handler(Looper.getMainLooper()).post(this::recoverOfficialSessions);
        needsRecoveryRead = true;
        ModulePreferences.get(context).edit().putBoolean("official_session_pending", true).commit();
        cancelActive.run();
        status = "official";
        if ("pending".equals(controlStatus)) {
            controlStatus = "cancelled";
            controlError = "控制已让出给 Technics Audio Connect";
        }
        repository.monitoringStopped();
        changed();
        return true;
    }

    public synchronized void officialRelease(IBinder token) {
        IBinder.DeathRecipient death = officialTokens.remove(token);
        if (death == null) return;
        if (death != null) token.unlinkToDeath(death, 0);
        sessions.officialRelease(token);
        ModulePreferences.get(context).edit()
                .putBoolean("official_session_pending", sessions.isOfficialBusy()).commit();
        status = sessions.isOfficialBusy() ? "official" : "idle";
        changed();
        if (!sessions.isOfficialBusy()) scheduleRecoveryRead();
    }

    public synchronized void cancel() {
        sessions.cancel();
        needsRecoveryRead = true;
        resumeAfterWorker = false;
        resumeForUserRequest = false;
        cancelActive.run();
        if ("pending".equals(controlStatus)) {
            controlStatus = "cancelled";
            controlError = "控制已取消，等待重新读取";
        }
        status = sessions.isOfficialBusy() ? "official" : "idle";
        repository.monitoringStopped();
        changed();
    }

    public synchronized boolean isOfficialBusy() { return sessions.isOfficialBusy(); }
    public synchronized boolean isRunning() { return sessions.isRunning(); }
    public synchronized String status() {
        return sessions.isOfficialBusy() ? "official" : !connected ? "disconnected"
                : captureActive() ? "capture" : status;
    }
    public synchronized boolean isConnected() { return connected; }
    public synchronized String deviceAddress() { return deviceAddress; }
    public synchronized String controlStatus() { return controlStatus; }
    public synchronized String controlError() { return controlError; }
    public synchronized String requestId() { return requestId; }
    public synchronized long generation() { return sessions.generation(); }

    public synchronized void startError(String message) {
        lastStartError = message;
        ModulePreferences.get(context).edit().putString(ModulePreferences.START_ERROR, message).apply();
        changed();
    }

    public synchronized String startError() { return lastStartError; }

    private synchronized void finish(long ticket) {
        sessions.finish(ticket);
        cancelActive = () -> {};
        status = sessions.isOfficialBusy() ? "official" : "idle";
        changed();
        if (resumeAfterWorker && !sessions.isOfficialBusy()) {
            boolean userRequested = resumeForUserRequest;
            resumeAfterWorker = false;
            resumeForUserRequest = false;
            scheduleRecoveryRead(userRequested);
        }
    }

    private boolean captureActive() {
        if (ModulePreferences.get(context).getBoolean("capture_paused", false)) return true;
        try {
            return TechnicsPodsApp.getXposedService() != null
                    && TechnicsPodsApp.getXposedService().getRemotePreferences(CaptureContract.PREFS_NAME)
                    .getBoolean(CaptureContract.PREF_ENABLED, false);
        } catch (RuntimeException unavailable) { return false; }
    }

    private void scheduleRecoveryRead() {
        scheduleRecoveryRead(false);
    }

    private void scheduleRecoveryRead(boolean userRequested) {
        final long generation;
        synchronized (this) { generation = sessions.generation(); }
        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (Az100ConnectionRuntime.this) {
                if (generation != sessions.generation()) return;
                if (!userRequested && !ModulePreferences.get(context)
                        .getBoolean(ModulePreferences.AUTO_MANAGE, false)) return;
                if (!connected || sessions.isOfficialBusy() || captureActive()) return;
                if (sessions.isRunning()) {
                    resumeAfterWorker = true;
                    resumeForUserRequest |= userRequested;
                    return;
                }
                // Keep the generation check and slot acquisition atomic with cancellation.
                read();
            }
        });
    }

    private void recoverOfficialSessions() {
        synchronized (this) {
            if (!sessions.recoveryPending() || recoveryQueryPending) return;
            recoveryQueryPending = true;
        }
        try {
            context.sendOrderedBroadcast(new Intent(ACTION_QUERY_OFFICIAL_SESSIONS)
                            .setPackage(CaptureContract.OFFICIAL_APP_PACKAGE),
                    android.Manifest.permission.BLUETOOTH_CONNECT, new BroadcastReceiver() {
                        @Override public void onReceive(Context ignored, Intent intent) {
                            synchronized (Az100ConnectionRuntime.this) {
                                recoveryQueryPending = false;
                                if (getResultCode() != android.app.Activity.RESULT_OK) {
                                    startError("官方会话同步失败，模块保持让出；请重启官方 App");
                                    return;
                                }
                                sessions.recoveryCompleted();
                                ModulePreferences.get(context).edit()
                                        .putBoolean("official_session_pending", sessions.isOfficialBusy()).commit();
                                changed();
                                if (needsRecoveryRead && !sessions.isOfficialBusy()) scheduleRecoveryRead();
                            }
                        }
                    }, new Handler(Looper.getMainLooper()), 0, null, null);
        } catch (RuntimeException unavailable) {
            synchronized (this) { recoveryQueryPending = false; }
            startError("官方连接状态无法核对，请重启官方 App 后重新尝试");
        }
    }

    public synchronized void connectionChanged(boolean available) {
        profileEpoch++;
        if (connected == available) return;
        connected = available;
        if (!available) cancel();
        else {
            needsRecoveryRead = true;
            if (ModulePreferences.get(context).getBoolean(ModulePreferences.AUTO_MANAGE, false)) scheduleRecoveryRead();
            Az100AutoStartReceiver.startIfEnabled(context);
        }
        changed();
    }

    public synchronized void bluetoothOff() {
        for (Map.Entry<IBinder, IBinder.DeathRecipient> item : officialTokens.entrySet()) {
            item.getKey().unlinkToDeath(item.getValue(), 0);
            sessions.officialRelease(item.getKey());
        }
        officialTokens.clear();
        sessions.recoveryCompleted();
        ModulePreferences.get(context).edit().putBoolean("official_session_pending", false).commit();
        connectionChanged(false);
        cancel();
    }

    @android.annotation.SuppressLint("MissingPermission")
    public void reconcileConnection() {
        final BluetoothDevice device;
        final BluetoothAdapter adapter;
        try {
            device = Az100DeviceIdentity.selectedDevice(context);
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            adapter = manager == null ? null : manager.getAdapter();
        } catch (RuntimeException denied) {
            connectionChanged(false);
            startError(denied.getMessage() == null ? "请检查蓝牙权限和已选设备" : denied.getMessage());
            return;
        }
        if (device == null || adapter == null) { connectionChanged(false); return; }
        final int epoch;
        synchronized (this) {
            String address = device.getAddress();
            if (!deviceAddress.equals(address)) {
                cancel();
                connected = false;
                repository.clearDevice();
                deviceAddress = address;
            }
            epoch = ++profileEpoch;
        }
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(2);
        java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
        for (int profile : new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET}) {
            try {
                boolean requested = adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int id, BluetoothProfile proxy) {
                        try {
                            if (proxy.getConnectedDevices().contains(device)) found.set(true);
                        } catch (RuntimeException denied) { failed.set(true); }
                        finally {
                            adapter.closeProfileProxy(id, proxy);
                            int remaining = pending.decrementAndGet();
                            if (found.get() || remaining == 0) applyConnectionSnapshot(epoch, found.get(), failed.get());
                        }
                    }
                    @Override public void onServiceDisconnected(int id) { }
                }, profile);
                if (!requested) {
                    failed.set(true);
                    if (pending.decrementAndGet() == 0) applyConnectionSnapshot(epoch, found.get(), true);
                }
            } catch (RuntimeException denied) {
                failed.set(true);
                if (pending.decrementAndGet() == 0) applyConnectionSnapshot(epoch, found.get(), true);
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                applyConnectionSnapshot(epoch, found.get(), failed.get() || pending.get() != 0), 3_000L);
    }

    private synchronized void applyConnectionSnapshot(int epoch, boolean found, boolean failed) {
        if (epoch != profileEpoch || (failed && !found)) return;
        connectionChanged(found);
    }

    private void requireCurrent(long ticket) {
        if (!sessions.isCurrent(ticket)) throw new IllegalStateException("操作已取消或连接已让出");
    }

    @android.annotation.SuppressLint("MissingPermission")
    private BluetoothDevice requireSelectedDevice(String expectedAddress) {
        BluetoothDevice target = Az100DeviceIdentity.selectedDevice(context);
        if (target == null || !target.getAddress().equalsIgnoreCase(deviceAddress)
                || (expectedAddress != null && !target.getAddress().equalsIgnoreCase(expectedAddress))) {
            throw new IllegalStateException("设备选择已变化，请重新打开耳机页面");
        }
        return target;
    }

    private String busyMessage() {
        return sessions.isOfficialBusy() ? "Technics Audio Connect 正在使用耳机"
                : "耳机正在处理另一项操作，请稍后重试";
    }

    private void changed() { context.getContentResolver().notifyChange(Az100StateProvider.STATE_URI, null); }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null ? "连接操作失败，请重新读取状态" : error.getMessage();
    }

    private static <T> CompletableFuture<T> failed(CompletableFuture<T> future, String message) {
        future.completeExceptionally(new IllegalStateException(message));
        return future;
    }
}
