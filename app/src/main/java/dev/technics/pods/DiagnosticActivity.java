package dev.technics.pods;

import android.Manifest;
import android.app.Application;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;

import dev.technics.pods.capture.CaptureContract;
import dev.technics.pods.capture.CaptureStore;
import dev.technics.pods.state.Az100ConnectionRuntime;
import dev.technics.pods.state.Az100StateProvider;
import io.github.libxposed.service.XposedService;

/** Explicit advanced tools; the shared runtime arbitrates all Bluetooth operations. */
public final class DiagnosticActivity extends AppCompatActivity {
    private static final int REQUEST_EXPORT = 2001;
    private static final int REQUEST_BLUETOOTH = 2002;
    private TextView statusView, resultView, levelView;
    private Button startButton, stopButton, readButton, copyButton, applyButton, exportButton, clearButton;
    private Button[] modeButtons;
    private SeekBar levelBar;
    private DiagnosticState diagnostic;
    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange, Uri uri) { refreshStatus(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        diagnostic = new ViewModelProvider(this).get(DiagnosticState.class);
        if (state != null && !diagnostic.initialized) {
            diagnostic.latestResult = state.getString("diagnostic_result");
            diagnostic.permissionAction = PendingAction.restore(state);
        }
        diagnostic.initialized = true;
        setContentView(buildContent());
        if (state != null) levelBar.setProgress(state.getInt("noise_progress", 99));
        diagnostic.changes.observe(this, ignored -> {
            resultView.setText(diagnostic.operationPending ? diagnostic.progressMessage
                    : diagnostic.latestResult == null ? getString(R.string.diagnostic_not_run) : diagnostic.latestResult);
            if (diagnostic.exportMessage != null) {
                toast(diagnostic.exportMessage);
                diagnostic.exportMessage = null;
            }
            refreshStatus();
        });
        diagnostic.changed();
        refreshStatus();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("diagnostic_result", diagnostic.latestResult);
        state.putInt("noise_progress", levelBar.getProgress());
        if (diagnostic.permissionAction != null) diagnostic.permissionAction.save(state);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStart() { super.onStart(); getContentResolver().registerContentObserver(Az100StateProvider.STATE_URI, true, observer); refreshStatus(); }
    @Override protected void onStop() { getContentResolver().unregisterContentObserver(observer); super.onStop(); }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            body.setPadding(dp(20) + bars.left, dp(18) + bars.top, dp(20) + bars.right, dp(28) + bars.bottom);
            return insets;
        });
        body.addView(button(getString(R.string.diagnostic_back), v -> finish()), full());
        body.addView(text(getString(R.string.diagnostic_heading), 24, true), section());
        statusView = text("", 13, false);
        statusView.setTypeface(Typeface.MONOSPACE);
        body.addView(statusView, top());
        body.addView(text(getString(R.string.diagnostic_read_section), 18, true), section());
        readButton = button(getString(R.string.diagnostic_read), v -> ensureBluetooth(new PendingAction(-1, null)));
        body.addView(readButton, top());
        copyButton = button(getString(R.string.diagnostic_copy), v -> copyResult());
        body.addView(copyButton, top());
        resultView = text(getString(R.string.diagnostic_not_run), 14, false);
        resultView.setTypeface(Typeface.MONOSPACE);
        resultView.setTextIsSelectable(true);
        body.addView(resultView, top());
        body.addView(text(getString(R.string.diagnostic_control_section), 18, true), section());
        modeButtons = new Button[3];
        String[] labels = {getString(R.string.mode_off), getString(R.string.mode_anc), getString(R.string.mode_ambient)};
        for (int mode = 0; mode < modeButtons.length; mode++) {
            final int target = mode;
            modeButtons[mode] = button(labels[mode], v -> confirmControl(target, null));
            body.addView(modeButtons[mode], top());
        }
        levelView = text(getString(R.string.noise_level_value, 100), 15, true);
        body.addView(levelView, top());
        levelBar = new SeekBar(this);
        levelBar.setMax(99);
        levelBar.setProgress(99);
        levelBar.setContentDescription(getString(R.string.noise_level_range));
        levelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { levelView.setText(getString(R.string.noise_level_value, progress + 1)); }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        body.addView(levelBar, top());
        applyButton = button(getString(R.string.apply_noise_level), v -> confirmControl(1, levelBar.getProgress() + 1));
        body.addView(applyButton, top());
        body.addView(text(getString(R.string.capture_section), 18, true), section());
        startButton = button(getString(R.string.capture_start), v -> startCapture());
        stopButton = button(getString(R.string.capture_stop), v -> stopCapture());
        body.addView(startButton, top());
        body.addView(stopButton, top());
        body.addView(button(getString(R.string.official_app_open), v -> openOfficialApp()), top());
        exportButton = button(getString(R.string.logs_export), v -> requestExport());
        body.addView(exportButton, top());
        clearButton = button(getString(R.string.logs_clear), v -> confirmClear());
        body.addView(clearButton, top());
        scroll.addView(body);
        return scroll;
    }

    private void ensureBluetooth(PendingAction action) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            diagnostic.runAfterConnection(action);
        else {
            diagnostic.permissionAction = action;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        }
        refreshStatus();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_BLUETOOTH) return;
        PendingAction action = diagnostic.permissionAction;
        diagnostic.permissionAction = null;
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            if (action != null) diagnostic.runAfterConnection(action);
            else Az100ConnectionRuntime.get(this).reconcileConnection();
        } else toast(getString(R.string.permission_bluetooth_missing));
        refreshStatus();
    }

    private void confirmControl(int mode, Integer level) {
        new AlertDialog.Builder(this).setTitle(level == null ? getString(R.string.confirm_mode_title) : getString(R.string.confirm_level_title, level))
                .setMessage(R.string.confirm_control_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.apply,
                        (dialog, which) -> ensureBluetooth(new PendingAction(mode, level))).show();
    }

    private void copyResult() {
        if (diagnostic.latestResult == null) return;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostic_clip_label), diagnostic.latestResult)); toast(getString(R.string.diagnostic_copied)); }
    }

    private void startCapture() {
        if (diagnostic.exportPending) return;
        SharedPreferences prefs = remotePreferences();
        if (prefs == null) return;
        String session = "az100-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        if (!prefs.edit()
                .putString(CaptureContract.PREF_SESSION, session)
                .putBoolean(CaptureContract.PREF_ENABLED, true)
                .commit()) {
            toast(getString(R.string.capture_save_failed));
            return;
        }
        ModulePreferences.get(this).edit().putBoolean("capture_paused", true).commit();
        Az100ConnectionRuntime.get(this).cancel();
        getContentResolver().notifyChange(Az100StateProvider.STATE_URI, null);
        try {
            CaptureStore.appendMarker(this, session, "capture-started-by-user");
        } catch (Exception error) {
            toast(getString(R.string.capture_marker_failed));
        }
        toast(getString(R.string.capture_started, session));
        refreshStatus();
    }

    private void stopCapture() {
        if (diagnostic.exportPending) return;
        SharedPreferences prefs = remotePreferences();
        if (prefs == null) return;
        String session = prefs.getString(CaptureContract.PREF_SESSION, "");
        try {
            CaptureStore.appendMarker(this, session, "capture-stopped-by-user");
        } catch (Exception ignored) {
        }
        boolean saved = prefs.edit().putBoolean(CaptureContract.PREF_ENABLED, false).commit();
        if (saved) {
            ModulePreferences.get(this).edit().putBoolean("capture_paused", false).commit();
            Az100ConnectionRuntime.get(this).cancel();
            Az100ConnectionRuntime.get(this).reconcileConnection();
            getContentResolver().notifyChange(Az100StateProvider.STATE_URI, null);
        }
        toast(getString(saved ? R.string.capture_stopped : R.string.capture_stop_failed));
        refreshStatus();
    }

    private void requestExport() {
        if (diagnostic.exportPending) return;
        if (CaptureStore.totalBytes(this) == 0L) {
            toast(getString(R.string.logs_empty));
            return;
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/x-ndjson")
                .putExtra(Intent.EXTRA_TITLE, "technics-az100-capture-" + timestamp + ".jsonl");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK || data == null) return;
        Uri destination = data.getData();
        if (destination == null) return;
        diagnostic.export(destination);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logs_clear_title)
                .setMessage(R.string.logs_clear_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    if (diagnostic.exportPending) return;
                    CaptureStore.clear(this);
                    toast(getString(R.string.logs_cleared));
                    refreshStatus();
                })
                .show();
    }

    private void openOfficialApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(CaptureContract.OFFICIAL_APP_PACKAGE);
        if (launch == null) {
            toast(getString(R.string.official_app_missing));
            return;
        }
        startActivity(launch);
    }


    private void refreshStatus() {
        if (statusView == null || isDestroyed()) return;
        XposedService service = TechnicsPodsApp.getXposedService();
        SharedPreferences prefs = service == null ? null : safeRemotePreferences(service);
        boolean enabled = prefs != null && prefs.getBoolean(CaptureContract.PREF_ENABLED, false);
        // exportAll owns the capture lock while a document provider may be slow.
        if (!diagnostic.exportPending) {
            diagnostic.captureFileCount = CaptureStore.fileCount(this);
            diagnostic.captureBytes = CaptureStore.totalBytes(this);
        }
        statusView.setText(getString(R.string.diagnostic_status_summary, Az100ConnectionRuntime.get(this).status(),
                getString(service == null ? R.string.framework_disconnected : R.string.framework_connected),
                getString(enabled ? R.string.capture_running : R.string.capture_stopped),
                diagnostic.captureFileCount, diagnostic.captureBytes));
        startButton.setEnabled(service != null && !enabled && !diagnostic.operationPending && !diagnostic.exportPending);
        stopButton.setEnabled(service != null && enabled && !diagnostic.exportPending);
        boolean available = !diagnostic.operationPending && diagnostic.permissionAction == null
                && !enabled && !Az100ConnectionRuntime.get(this).isRunning()
                && !Az100ConnectionRuntime.get(this).isOfficialBusy();
        readButton.setEnabled(available);
        for (Button mode : modeButtons) mode.setEnabled(available);
        applyButton.setEnabled(available);
        levelBar.setEnabled(available);
        copyButton.setEnabled(diagnostic.latestResult != null);
        exportButton.setEnabled(!diagnostic.exportPending);
        clearButton.setEnabled(!diagnostic.exportPending);
    }

    private SharedPreferences remotePreferences() {
        XposedService service = TechnicsPodsApp.getXposedService();
        if (service == null) { toast(getString(R.string.framework_disconnected)); return null; }
        SharedPreferences prefs = safeRemotePreferences(service);
        if (prefs == null) toast(getString(R.string.framework_preferences_failed));
        return prefs;
    }

    private static final class PendingAction {
        final int mode;
        final Integer level;
        PendingAction(int mode, Integer level) { this.mode = mode; this.level = level; }
        boolean isRead() { return mode < 0; }
        void save(Bundle state) {
            state.putInt("permission_mode", mode);
            if (level != null) state.putInt("permission_level", level);
        }
        static PendingAction restore(Bundle state) {
            return state.containsKey("permission_mode") ? new PendingAction(state.getInt("permission_mode"),
                    state.containsKey("permission_level") ? state.getInt("permission_level") : null) : null;
        }
    }

    /** Retains operations across recreation without keeping an Activity or its views alive. */
    public static final class DiagnosticState extends AndroidViewModel {
        final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final ExecutorService exportWorker = Executors.newSingleThreadExecutor();
        private int revision;
        private boolean cleared;
        boolean initialized, operationPending, exportPending;
        int captureFileCount;
        long captureBytes;
        PendingAction permissionAction;
        String latestResult, progressMessage, exportMessage;

        public DiagnosticState(Application application) { super(application); }
        void changed() { if (!cleared) changes.setValue(++revision); }

        void runAfterConnection(PendingAction action) {
            if (operationPending || cleared) return;
            operationPending = true;
            progressMessage = getApplication().getString(action.isRead()
                    ? R.string.diagnostic_reading : R.string.diagnostic_controlling);
            changed();
            Az100ConnectionRuntime runtime = Az100ConnectionRuntime.get(getApplication());
            runtime.reconcileConnection();
            awaitConnection(runtime, action, SystemClock.elapsedRealtime() + 3_500L);
        }

        private void awaitConnection(Az100ConnectionRuntime runtime, PendingAction action, long deadline) {
            if (cleared) return;
            if (getApplication().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                complete(getApplication().getString(R.string.permission_bluetooth_missing));
            } else if (runtime.isConnected()) {
                java.util.concurrent.CompletableFuture<String> operation = action.isRead()
                        ? runtime.read().thenApply(result -> result.toDisplayText())
                        : runtime.control(action.mode, action.level).thenApply(result -> result.toDisplayText());
                operation
                        .whenComplete((result, failure) -> handler.post(() ->
                                complete(failure == null ? result : failureMessage(failure))));
            } else if (SystemClock.elapsedRealtime() >= deadline) {
                complete(getApplication().getString(R.string.diagnostic_connection_unavailable));
            } else {
                handler.postDelayed(() -> awaitConnection(runtime, action, deadline), 100L);
            }
        }

        private String failureMessage(Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            return getApplication().getString(R.string.operation_error,
                    cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        }

        private void complete(String result) {
            operationPending = false;
            latestResult = result;
            changed();
        }

        void export(Uri destination) {
            if (exportPending || cleared) return;
            exportPending = true;
            changed();
            Application application = getApplication();
            exportWorker.execute(() -> {
                String message;
                try (OutputStream output = application.getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IllegalStateException("Document provider returned no stream");
                    CaptureStore.exportAll(application, output);
                    message = application.getString(R.string.logs_exported);
                } catch (Exception error) {
                    message = application.getString(R.string.logs_export_failed, error.getClass().getSimpleName());
                }
                String result = message;
                handler.post(() -> { exportPending = false; exportMessage = result; changed(); });
            });
        }

        @Override protected void onCleared() {
            cleared = true;
            handler.removeCallbacksAndMessages(null);
            exportWorker.shutdown();
        }
    }

    private SharedPreferences safeRemotePreferences(XposedService service) { try { return service.getRemotePreferences(CaptureContract.PREFS_NAME); } catch (Throwable error) { return null; } }
    private TextView text(String value, int size, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color(R.color.tp_text_primary)); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private Button button(String title, View.OnClickListener listener) { MaterialButton v = new MaterialButton(this); v.setText(title); v.setAllCaps(false); v.setMinHeight(dp(48)); v.setCornerRadius(dp(8)); v.setOnClickListener(listener); return v; }
    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top() { LinearLayout.LayoutParams p = full(); p.topMargin = dp(8); return p; }
    private LinearLayout.LayoutParams section() { LinearLayout.LayoutParams p = full(); p.topMargin = dp(24); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int color(int id) { return getResources().getColor(id, getTheme()); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
