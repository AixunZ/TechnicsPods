package dev.technics.pods.capture;

public final class CaptureContract {
    public static final String MODULE_PACKAGE = "dev.technics.pods";
    public static final String OFFICIAL_APP_PACKAGE = "com.panasonic.technicsaudioconnect";
    public static final String SYSTEM_BLUETOOTH_PACKAGE = "com.android.bluetooth";
    public static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    public static final String SETTINGS_PACKAGE = "com.android.settings";
    public static final String MIUI_HOME_PACKAGE = "com.miui.home";
    /** HyperOS Fusion device-center service process. */
    public static final String MILINK_SERVICE_PACKAGE = "com.milink.service";
    /** HyperOS/Xiaomi Bluetooth headset runtime process. */
    public static final String XIAOMI_BLUETOOTH_PACKAGE = "com.xiaomi.bluetooth";
    public static final String RECEIVER_CLASS = "dev.technics.pods.capture.CaptureReceiver";
    public static final String STATE_PROVIDER_AUTHORITY = "dev.technics.pods.state";
    public static final String ACTION_RECORD = "dev.technics.pods.action.CAPTURE_RECORD";
    public static final String PREFS_NAME = "technics_capture_settings";
    public static final String PREF_ENABLED = "capture_enabled";
    public static final String PREF_SESSION = "capture_session";

    public static final String EXTRA_WALL_TIME = "wall_time";
    public static final String EXTRA_ELAPSED_NANOS = "elapsed_nanos";
    public static final String EXTRA_SESSION = "session";
    public static final String EXTRA_PROCESS = "process";
    public static final String EXTRA_THREAD = "thread";
    public static final String EXTRA_DIRECTION = "direction";
    public static final String EXTRA_OPERATION = "operation";
    public static final String EXTRA_SERVICE_UUID = "service_uuid";
    public static final String EXTRA_CHARACTERISTIC_UUID = "characteristic_uuid";
    public static final String EXTRA_DESCRIPTOR_UUID = "descriptor_uuid";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_WRITE_TYPE = "write_type";
    public static final String EXTRA_PAYLOAD = "payload";
    public static final String EXTRA_NOTE = "note";

    public static final int MAX_PAYLOAD_BYTES = 1024;

    private CaptureContract() {}
}
