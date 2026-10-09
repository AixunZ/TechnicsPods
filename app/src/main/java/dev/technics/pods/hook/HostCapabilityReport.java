package dev.technics.pods.hook;

import android.content.Context;
import android.os.Bundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import dev.technics.pods.state.Az100StateProvider;

/** Reports discoveries off host startup threads; absence of IPC leaves capability unknown. */
final class HostCapabilityReport {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TechnicsPods-capability");
        thread.setDaemon(true);
        return thread;
    });
    private HostCapabilityReport() {}
    static void report(Context context, String feature, int hooks, String detail) {
        if (context == null) return;
        WORKER.execute(() -> {
            try {
                Bundle extras = new Bundle();
                extras.putString("feature", feature);
                extras.putString("status", hooks > 0 ? "installed" : "unavailable");
                extras.putString("detail", "hooks=" + hooks + ";" + detail);
                context.getContentResolver().call(Az100StateProvider.STATE_URI,
                        Az100StateProvider.METHOD_CAPABILITY, null, extras);
            } catch (RuntimeException ignored) {}
        });
    }
}
