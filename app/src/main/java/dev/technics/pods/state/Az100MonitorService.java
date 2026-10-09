package dev.technics.pods.state;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.IBinder;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dev.technics.pods.MainActivity;
import dev.technics.pods.R;
import dev.technics.pods.ModulePreferences;
import dev.technics.pods.diagnostic.Az100DeviceIdentity;

/** Low-noise foreground scheduler; the shared runtime owns every transport transaction. */
public final class Az100MonitorService extends Service {
    public static final String ACTION_START = "dev.technics.pods.action.START_MONITOR";
    public static final String ACTION_STOP = "dev.technics.pods.action.STOP_MONITOR";

    private static final String CHANNEL_ID = "az100_state_monitor";
    private static final int NOTIFICATION_ID = 1001;
    private static final long POLL_INTERVAL_SECONDS = 30L;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicLong monitorGeneration = new AtomicLong();

    private Az100StateRepository repository;
    private volatile ScheduledFuture<?> scheduledTask;
    private Az100ConnectionRuntime runtime;
    private android.content.BroadcastReceiver bluetoothReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new Az100StateRepository(this);
        runtime = Az100ConnectionRuntime.get(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, notification(repository.state()));
        bluetoothReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        stopPolling();
                        runtime.bluetoothOff();
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        runtime.reconcileConnection();
                        startPolling();
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (!Az100DeviceIdentity.matches(context, device)) return;
                    runtime.connectionChanged(false);
                    stopPolling();
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (!Az100DeviceIdentity.matches(context, device)) return;
                    runtime.connectionChanged(true);
                    startPolling();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ModulePreferences.get(this).edit().putBoolean(ModulePreferences.AUTO_MANAGE, false).apply();
            ModulePreferences.mirrorToFramework(this);
            monitorGeneration.incrementAndGet();
            stopPolling();
            runtime.cancel();
            repository.monitoringStopped();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!ModulePreferences.get(this).getBoolean(ModulePreferences.AUTO_MANAGE, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        repository.monitoringStarted();
        runtime.reconcileConnection();
        updateNotification();
        startPolling();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        monitorGeneration.incrementAndGet();
        stopPolling();
        runtime.cancel();
        if (bluetoothReceiver != null) {
            try { unregisterReceiver(bluetoothReceiver); } catch (IllegalArgumentException ignored) {}
        }
        executor.shutdownNow();
        repository.monitoringStopped();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sample(long generation) {
        if (generation != monitorGeneration.get()) return;
        if (!runtime.isConnected()) { stopPolling(); return; }
        if (runtime.isOfficialBusy() || runtime.isRunning()) { scheduleNext(generation); return; }
        updateNotification();
        runtime.read().whenComplete((result, error) -> {
            if (generation != monitorGeneration.get()) return;
            updateNotification();
            scheduleNext(generation);
        });
    }

    private synchronized void startPolling() {
        if (!ModulePreferences.get(this).getBoolean(ModulePreferences.AUTO_MANAGE, false)) return;
        if (scheduled.compareAndSet(false, true)) {
            long generation = monitorGeneration.incrementAndGet();
            scheduledTask = executor.schedule(() -> sample(generation), 0L, TimeUnit.SECONDS);
        }
    }

    private synchronized void scheduleNext(long generation) {
        if (generation != monitorGeneration.get() || !scheduled.get() || executor.isShutdown()) return;
        scheduledTask = executor.schedule(() -> sample(generation), POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void stopPolling() {
        scheduled.set(false);
        monitorGeneration.incrementAndGet();
        ScheduledFuture<?> task = scheduledTask;
        scheduledTask = null;
        if (task != null) task.cancel(false);
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AZ100 状态监测",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("定期读取耳机电量和降噪状态");
        manager.createNotificationChannel(channel);
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            manager.notify(NOTIFICATION_ID, notification(repository.state()));
        }
    }

    private Notification notification(Az100StateCache.State state) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Intent stopIntent = new Intent(this, Az100MonitorService.class)
                .setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String content = runtime.isOfficialBusy() ? "Technics Audio Connect 正在使用耳机"
                : !runtime.isConnected() ? "耳机未连接，采样已暂停"
                : state.sampling() ? "正在读取耳机状态" : batterySummary(state);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_headset)
                .setContentTitle("TechnicsPods 后台监测")
                .setContentText(content)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "停止", stop).build())
                .build();
    }

    private static String batterySummary(Az100StateCache.State state) {
        if (state.lastSuccessAtMillis() == 0L) {
            return state.lastError() == null ? "等待首次读取" : state.lastError();
        }
        return "左 " + percent(state.left().batteryPercent())
                + " · 右 " + percent(state.right().batteryPercent())
                + " · 盒 " + percent(state.cradleBatteryPercent());
    }

    private static String percent(Integer value) {
        return value == null ? "--" : value + "%";
    }
}
