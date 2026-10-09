package dev.technics.pods;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.materialswitch.MaterialSwitch;

import dev.technics.pods.art.Az100ArtStore;
import dev.technics.pods.diagnostic.Az100DeviceMatcher;
import dev.technics.pods.state.Az100ConnectionRuntime;
import dev.technics.pods.state.Az100MonitorService;
import dev.technics.pods.state.Az100StateCache;
import dev.technics.pods.state.Az100StateFreshness;
import dev.technics.pods.state.Az100StateProvider;
import dev.technics.pods.state.Az100StateRepository;

/** Device state and module settings; advanced operations have their own screen. */
public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE = 4101;
    private static final int REQUEST_AUTOMATION = 4102;
    private static final int REQUEST_DEVICE = 4103;
    private static final int REQUEST_PERMISSIONS = 4104;
    private TextView statusView, frameworkView, modeView, firmwareView, deviceView, artView;
    private TextView permissionView, capabilityView;
    private TextView[] batteryValues, batteryStates;
    private LinearLayout[] batteryCards;
    private ImageView productImage;
    private MaterialSwitch autoSwitch, iconSwitch, diagnosticsSwitch;
    private boolean renderingSettings;
    private long artworkVersion = Long.MIN_VALUE;
    private boolean visible;
    private boolean artLoading;
    private int artworkActions;
    private boolean artworkRefreshPending;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService artworkWorker = Executors.newSingleThreadExecutor();
    private final Runnable freshnessExpiry = this::refreshState;
    private final ContentObserver stateObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange, Uri uri) { refreshState(); refreshArt(); }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener =
            (prefs, key) -> runOnUiThread(() -> { if (!isDestroyed()) refreshSettings(); });
    private final SharedPreferences.OnSharedPreferenceChangeListener capabilityListener =
            (prefs, key) -> runOnUiThread(() -> { if (!isDestroyed()) refreshCapabilities(); });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        bindViews();
        refreshSettings();
        refreshState();
        refreshArt();
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        getContentResolver().registerContentObserver(Az100StateProvider.STATE_URI, true, stateObserver);
        ModulePreferences.get(this).registerOnSharedPreferenceChangeListener(preferencesListener);
        getSharedPreferences("hook_capabilities", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(capabilityListener);
        ModulePreferences.mirrorToFramework(this);
        refreshSettings();
        Az100ConnectionRuntime.get(this).reconcileConnection();
        refreshState();
        refreshArt();
        refreshCapabilities();
    }

    @Override protected void onStop() {
        visible = false;
        handler.removeCallbacks(freshnessExpiry);
        getContentResolver().unregisterContentObserver(stateObserver);
        ModulePreferences.get(this).unregisterOnSharedPreferenceChangeListener(preferencesListener);
        getSharedPreferences("hook_capabilities", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(capabilityListener);
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        Az100ConnectionRuntime.get(this).reconcileConnection();
        refreshSettings();
    }

    @Override protected void onDestroy() {
        artworkWorker.shutdown();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void bindViews() {
        productImage = findViewById(R.id.product_image);
        statusView = findViewById(R.id.connection_status);
        frameworkView = findViewById(R.id.framework_status);
        modeView = findViewById(R.id.outside_mode);
        firmwareView = findViewById(R.id.firmware);
        deviceView = findViewById(R.id.selected_device);
        artView = findViewById(R.id.art_status);
        permissionView = findViewById(R.id.permission_status);
        capabilityView = findViewById(R.id.capability_report);
        batteryValues = new TextView[]{findViewById(R.id.battery_left_value), findViewById(R.id.battery_right_value), findViewById(R.id.battery_case_value)};
        batteryStates = new TextView[]{findViewById(R.id.battery_left_state), findViewById(R.id.battery_right_state), findViewById(R.id.battery_case_state)};
        batteryCards = new LinearLayout[]{findViewById(R.id.battery_left_card), findViewById(R.id.battery_right_card), findViewById(R.id.battery_case_card)};
        LinearLayout batteryRow = findViewById(R.id.battery_row);
        batteryRow.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int cellWidth = (right - left - dp(16)) / 3;
            float needed = batteryValues[0].getPaint().measureText("100%") + dp(24);
            boolean stacked = cellWidth < needed || getResources().getConfiguration().fontScale > 1.2f;
            int orientation = stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
            if (batteryRow.getOrientation() == orientation) return;
            batteryRow.setOrientation(orientation);
            for (int i = 0; i < batteryCards.length; i++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1);
                params.setMarginEnd(stacked || i == 2 ? 0 : dp(8));
                params.topMargin = stacked && i > 0 ? dp(8) : 0;
                batteryCards[i].setLayoutParams(params);
            }
        });
        autoSwitch = findViewById(R.id.auto_switch);
        iconSwitch = findViewById(R.id.icon_switch);
        diagnosticsSwitch = findViewById(R.id.diagnostics_switch);
        bindToggle(autoSwitch, ModulePreferences.AUTO_MANAGE);
        bindToggle(iconSwitch, ModulePreferences.STATUS_ICON);
        bindToggle(diagnosticsSwitch, ModulePreferences.DIAGNOSTICS);
        findViewById(R.id.choose_device).setOnClickListener(v -> chooseDeviceWithPermission());
        findViewById(R.id.import_art).setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQUEST_IMAGE));
        findViewById(R.id.official_art).setOnClickListener(v -> importOfficialArt());
        findViewById(R.id.reset_art).setOnClickListener(v -> artworkTask(() -> { Az100ArtStore.clear(this); return R.string.art_builtin; }));
        findViewById(R.id.open_diagnostics).setOnClickListener(v -> startActivity(new Intent(this, DiagnosticActivity.class)));
        findViewById(R.id.request_permissions).setOnClickListener(v -> requestRequiredPermissions());
        findViewById(R.id.open_app_settings).setOnClickListener(v -> openSettings(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        findViewById(R.id.open_battery_settings).setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        findViewById(R.id.recover_background).setOnClickListener(v -> enableAutomaticManagement());
        LinearLayout body = findViewById(R.id.main_body);
        findViewById(R.id.main_scroll).setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            body.setPadding(dp(20) + bars.left, dp(18) + bars.top, dp(20) + bars.right, dp(28) + bars.bottom);
            return insets;
        });
        productImage.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = Math.min(dp(230), Math.max(dp(140), Math.round(width * 0.5f)));
            if (width > 0 && productImage.getLayoutParams().height != height) {
                productImage.getLayoutParams().height = height;
                productImage.requestLayout();
            }
        });
    }

    private void bindToggle(MaterialSwitch toggle, String key) {
        toggle.setOnCheckedChangeListener((v, checked) -> {
            if (renderingSettings) return;
            if (ModulePreferences.AUTO_MANAGE.equals(key) && checked) { enableAutomaticManagement(); return; }
            ModulePreferences.get(this).edit().putBoolean(key, checked).apply();
            ModulePreferences.mirrorToFramework(this);
            if (ModulePreferences.AUTO_MANAGE.equals(key)) {
                Az100ConnectionRuntime.get(this).cancel();
                stopService(new Intent(this, Az100MonitorService.class));
            }
            refreshSettings();
        });
    }

    private void enableAutomaticManagement() {
        List<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!permissions.isEmpty()) {
            renderingSettings = true;
            autoSwitch.setChecked(false);
            renderingSettings = false;
            requestPermissions(permissions.toArray(new String[0]), REQUEST_AUTOMATION);
            return;
        }
        try {
            ModulePreferences.get(this).edit().putBoolean(ModulePreferences.AUTO_MANAGE, true)
                    .remove(ModulePreferences.START_ERROR).apply();
            ModulePreferences.mirrorToFramework(this);
            startForegroundService(new Intent(this, Az100MonitorService.class).setAction(Az100MonitorService.ACTION_START));
        } catch (RuntimeException error) {
            ModulePreferences.get(this).edit().putBoolean(ModulePreferences.AUTO_MANAGE, false)
                    .putString(ModulePreferences.START_ERROR, error.getMessage()).apply();
            ModulePreferences.mirrorToFramework(this);
            toast(getString(R.string.automation_start_failed));
        }
        refreshSettings();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
            Az100ConnectionRuntime.get(this).reconcileConnection();
        if (requestCode == REQUEST_AUTOMATION) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                enableAutomaticManagement();
            else { toast(getString(R.string.permission_automation_missing)); refreshSettings(); }
        } else if (requestCode == REQUEST_DEVICE) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                chooseDevice();
            else toast(getString(R.string.permission_bluetooth_missing));
        } else if (requestCode == REQUEST_PERMISSIONS) {
            refreshSettings();
        }
    }

    private void chooseDeviceWithPermission() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_DEVICE);
        else chooseDevice();
    }

    private void chooseDevice() {
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        if (manager == null || manager.getAdapter() == null) { toast(getString(R.string.bluetooth_unavailable)); return; }
        List<BluetoothDevice> devices = new ArrayList<>();
        try {
            for (BluetoothDevice device : manager.getAdapter().getBondedDevices())
                if (Az100DeviceMatcher.matchesName(device.getName())) devices.add(device);
            devices.sort(Comparator.comparing(BluetoothDevice::getAddress));
            String[] labels = new String[devices.size() + 1];
            labels[0] = getString(R.string.device_automatic_unique);
            for (int i = 0; i < devices.size(); i++) labels[i + 1] = getString(R.string.device_choice_summary,
                    devices.get(i).getName(), devices.get(i).getAddress());
            new AlertDialog.Builder(this).setTitle(R.string.device_chooser_title).setItems(labels, (dialog, index) -> {
                String address = index == 0 ? "" : devices.get(index - 1).getAddress();
                ModulePreferences.get(this).edit().putString(ModulePreferences.SELECTED_ADDRESS, address).apply();
                ModulePreferences.mirrorToFramework(this);
                refreshSettings();
            }).setNegativeButton(R.string.cancel, null).show();
        } catch (SecurityException error) { toast(getString(R.string.paired_read_failed)); }
    }

    private void refreshSettings() {
        if (autoSwitch == null || isDestroyed()) return;
        SharedPreferences prefs = ModulePreferences.get(this);
        renderingSettings = true;
        autoSwitch.setChecked(prefs.getBoolean(ModulePreferences.AUTO_MANAGE, false));
        iconSwitch.setChecked(prefs.getBoolean(ModulePreferences.STATUS_ICON, true));
        diagnosticsSwitch.setChecked(prefs.getBoolean(ModulePreferences.DIAGNOSTICS, false));
        renderingSettings = false;
        String address = prefs.getString(ModulePreferences.SELECTED_ADDRESS, "");
        deviceView.setText(address == null || address.isEmpty() ? getString(R.string.device_automatic) : address);
        String framework = TechnicsPodsApp.getXposedService() == null ? getString(R.string.framework_disconnected) : getString(R.string.framework_connected);
        String error = prefs.getString(ModulePreferences.START_ERROR, "");
        frameworkView.setText(error == null || error.isEmpty() ? framework
                : getString(R.string.framework_error_summary, framework, error));
        refreshPermissions();
    }

    private void requestRequiredPermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if (missing.isEmpty()) toast(getString(R.string.permissions_ready));
        else requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    private void refreshPermissions() {
        boolean bluetooth = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        boolean notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        PowerManager power = getSystemService(PowerManager.class);
        boolean unrestricted = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        permissionView.setText(getString(R.string.permissions_summary,
                getString(bluetooth ? R.string.permission_granted : R.string.permission_missing),
                getString(notifications ? R.string.permission_granted : R.string.permission_missing),
                getString(unrestricted ? R.string.battery_unrestricted : R.string.battery_optimized)));
        findViewById(R.id.request_permissions).setEnabled(!bluetooth || !notifications);
    }

    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException unavailable) { toast(getString(R.string.settings_unavailable)); }
    }

    private void refreshCapabilities() {
        if (capabilityView == null || isDestroyed()) return;
        java.util.Map<String, ?> reports = getSharedPreferences("hook_capabilities", MODE_PRIVATE).getAll();
        String[] features = {"settings_bridge", "hyperos_bridge", "fusion_route", "status_bar", "artwork", "settings_noise_level"};
        int[] labels = {R.string.capability_settings, R.string.capability_hyperos,
                R.string.capability_fusion, R.string.capability_status_bar,
                R.string.capability_artwork, R.string.capability_noise_level};
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < features.length; i++) {
            List<String> rows = new ArrayList<>();
            for (java.util.Map.Entry<String, ?> item : reports.entrySet()) {
                if (!item.getKey().endsWith(":" + features[i]) || !(item.getValue() instanceof String report)) continue;
                String host = item.getKey().substring(0, item.getKey().indexOf(':'));
                String state = report.substring(0, report.indexOf(':') < 0 ? report.length() : report.indexOf(':'));
                int status = switch (state) {
                    case "installed" -> R.string.capability_installed;
                    case "degraded" -> R.string.capability_degraded;
                    default -> R.string.capability_unavailable;
                };
                rows.add(getString(R.string.capability_host_summary, host, getString(status)));
            }
            rows.sort(String::compareTo);
            if (output.length() > 0) output.append("\n\n");
            output.append(getString(R.string.capability_format, getString(labels[i]),
                    rows.isEmpty() ? getString(R.string.capability_unknown) : String.join("\n", rows)));
        }
        capabilityView.setText(output);
    }

    private void refreshState() {
        if (statusView == null || isDestroyed()) return;
        Az100StateCache.State state = new Az100StateRepository(this).state();
        String runtime = Az100ConnectionRuntime.get(this).status();
        statusView.setText(switch (runtime) {
            case "reading" -> getString(R.string.state_reading);
            case "controlling" -> getString(R.string.state_controlling);
            case "official" -> getString(R.string.state_official);
            case "disconnected" -> getString(R.string.state_disconnected);
            case "capture" -> getString(R.string.state_capture);
            case "idle" -> getString(state.reachable() ? R.string.state_ready : R.string.state_connected_pending);
            default -> getString(state.monitoring() ? R.string.state_auto_on : R.string.state_auto_off);
        });
        Integer[] values = {state.left().batteryPercent(), state.right().batteryPercent(), state.cradleBatteryPercent()};
        long[] times = {state.left().batteryUpdatedAtMillis(), state.right().batteryUpdatedAtMillis(), state.cradleUpdatedAtMillis()};
        int[] labels = {R.string.battery_left, R.string.battery_right, R.string.battery_case};
        for (int i = 0; i < values.length; i++) {
            batteryValues[i].setText(values[i] == null ? getString(R.string.value_unknown) : getString(R.string.battery_percent, values[i]));
            batteryStates[i].setText(freshness(times[i], state, values[i] != null));
            batteryCards[i].setContentDescription(getString(R.string.battery_accessibility,
                    getString(labels[i]), values[i] == null ? getString(R.string.state_unknown) : batteryValues[i].getText(),
                    batteryStates[i].getText()));
        }
        modeView.setText(getString(R.string.outside_mode_summary, outsideMode(state.controls().outsideMode()),
                freshness(state.controls().outsideModeUpdatedAtMillis(), state, state.controls().outsideMode() != null)));
        String firmware = getString(R.string.firmware_summary, value(state.left().firmware()), value(state.right().firmware()),
                state.lastSuccessAtMillis() == 0 ? getString(R.string.value_unknown) : android.text.format.DateFormat.format("MM-dd HH:mm:ss", state.lastSuccessAtMillis()));
        firmwareView.setText(state.lastError() == null ? firmware
                : getString(R.string.firmware_error_summary, firmware, state.lastError()));
        handler.removeCallbacks(freshnessExpiry);
        if (visible && state.reachable()) {
            long next = Long.MAX_VALUE;
            long now = System.currentTimeMillis();
            long[] expiryTimes = {times[0], times[1], times[2], state.controls().outsideModeUpdatedAtMillis()};
            for (long time : expiryTimes) {
                long expiry = time + 120_001L;
                if (time > 0 && expiry > now) next = Math.min(next, expiry);
            }
            if (next != Long.MAX_VALUE) handler.postDelayed(freshnessExpiry, next - now);
        }
        refreshSettings();
    }

    private String freshness(long time, Az100StateCache.State state, boolean known) {
        if (!known || time == 0) return getString(R.string.state_unknown);
        return getString(state.reachable() && Az100StateFreshness.isFresh(time, System.currentTimeMillis())
                ? R.string.state_recent : R.string.state_cached);
    }

    private interface ArtworkAction { int run() throws Exception; }

    private void importOfficialArt() {
        artworkTask(() -> Az100ArtStore.importOfficial(this) ? R.string.art_official_success : R.string.art_official_missing);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        artworkTask(() -> { Az100ArtStore.importImage(this, uri); return R.string.art_import_success; });
    }

    private void artworkTask(ArtworkAction action) {
        if (isDestroyed() || isFinishing()) return;
        artworkActions++;
        artLoading = true;
        artView.setText(R.string.art_working);
        setArtworkButtonsEnabled(false);
        artworkWorker.execute(() -> {
            int message;
            Bitmap bitmap = null;
            long version = Az100ArtStore.version(this);
            try { message = action.run(); }
            catch (Exception error) { message = R.string.art_import_failed; }
            try {
                bitmap = Az100ArtStore.load(this);
                version = Az100ArtStore.version(this);
            } catch (RuntimeException ignored) {
                if (message == R.string.art_import_success) message = R.string.art_import_failed;
            }
            int result = message;
            Bitmap loadedBitmap = bitmap;
            long loadedVersion = version;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                artworkActions--;
                artLoading = artworkActions > 0;
                applyArtwork(loadedBitmap, loadedVersion);
                toast(getString(result));
                finishArtworkRefresh();
            });
        });
    }

    private void refreshArt() {
        if (isDestroyed()) return;
        if (artLoading) { artworkRefreshPending = true; return; }
        long version = Az100ArtStore.version(this);
        if (artworkVersion == version) return;
        artLoading = true;
        setArtworkButtonsEnabled(false);
        artworkWorker.execute(() -> {
            Bitmap bitmap = null;
            try { bitmap = Az100ArtStore.load(this); } catch (RuntimeException ignored) { }
            Bitmap loadedBitmap = bitmap;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                artLoading = artworkActions > 0;
                applyArtwork(loadedBitmap, version);
                finishArtworkRefresh();
            });
        });
    }

    private void finishArtworkRefresh() {
        setArtworkButtonsEnabled(!artLoading);
        if (!artLoading && (artworkRefreshPending || artworkVersion != Az100ArtStore.version(this))) {
            artworkRefreshPending = false;
            refreshArt();
        }
    }

    private void applyArtwork(Bitmap bitmap, long version) {
        if (bitmap == null) productImage.setImageResource(R.drawable.ic_az100_placeholder);
        else productImage.setImageBitmap(bitmap);
        artworkVersion = version;
        artView.setText(bitmap == null ? R.string.art_builtin : R.string.art_imported);
    }

    private void setArtworkButtonsEnabled(boolean enabled) {
        findViewById(R.id.import_art).setEnabled(enabled);
        findViewById(R.id.official_art).setEnabled(enabled);
        findViewById(R.id.reset_art).setEnabled(enabled);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String value(String value) { return value == null || value.isEmpty() ? getString(R.string.value_unknown) : value; }
    private String outsideMode(Integer value) { if (value == null) return getString(R.string.value_unknown); return getString(value == 0 ? R.string.mode_off : value == 1 ? R.string.mode_anc : value == 2 ? R.string.mode_ambient : R.string.mode_unknown); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
