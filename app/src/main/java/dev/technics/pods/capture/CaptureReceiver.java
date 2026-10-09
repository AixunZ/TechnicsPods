package dev.technics.pods.capture;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CaptureReceiver extends BroadcastReceiver {
    private static final String TAG = "TechnicsPods-Receiver";
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TechnicsCaptureWriter");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !CaptureContract.ACTION_RECORD.equals(intent.getAction())) return;
        int senderUid = getSentFromUid();
        if (!isTrustedSender(context.getPackageManager(), senderUid)) {
            Log.w(TAG, "Rejected capture broadcast from uid=" + senderUid);
            return;
        }

        byte[] payload = intent.getByteArrayExtra(CaptureContract.EXTRA_PAYLOAD);
        if (payload != null && payload.length > CaptureContract.MAX_PAYLOAD_BYTES) {
            payload = Arrays.copyOf(payload, CaptureContract.MAX_PAYLOAD_BYTES);
        }
        final byte[] capturedPayload = payload;
        final PendingResult pending = goAsync();
        WRITER.execute(() -> {
            try {
                CaptureStore.append(context.getApplicationContext(), new CaptureRecord(
                        intent.getLongExtra(CaptureContract.EXTRA_WALL_TIME, System.currentTimeMillis()),
                        intent.getLongExtra(CaptureContract.EXTRA_ELAPSED_NANOS, 0L),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_SESSION), 96),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_PROCESS), 128),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_THREAD), 128),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_DIRECTION), 16),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_OPERATION), 256),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_SERVICE_UUID), 64),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_CHARACTERISTIC_UUID), 64),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_DESCRIPTOR_UUID), 64),
                        intent.getIntExtra(CaptureContract.EXTRA_STATUS, -1),
                        intent.getIntExtra(CaptureContract.EXTRA_WRITE_TYPE, -1),
                        capturedPayload == null ? 0 : capturedPayload.length,
                        HexCodec.encode(capturedPayload, CaptureContract.MAX_PAYLOAD_BYTES),
                        safe(intent.getStringExtra(CaptureContract.EXTRA_NOTE), 512)
                ));
            } catch (Throwable error) {
                Log.e(TAG, "Failed to persist capture record", error);
            } finally {
                pending.finish();
            }
        });
    }

    private static boolean isTrustedSender(PackageManager packageManager, int uid) {
        if (uid == Process.INVALID_UID) return false;
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages == null) return false;
        return Arrays.asList(packages).contains(CaptureContract.OFFICIAL_APP_PACKAGE)
                || Arrays.asList(packages).contains(CaptureContract.SYSTEM_BLUETOOTH_PACKAGE);
    }

    private static String safe(String value, int maxLength) {
        if (value == null) return "";
        String sanitized = value.replace('\u0000', ' ').trim();
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
