package dev.technics.pods.hook;

/** Only the recorded ROM/package pair permits these obfuscated Fusion symbols. */
final class RomCompatibilityProfile {
    static final int SDK = 37;
    static final String INCREMENTAL = "OS4.0.0.17.XPACNXM";
    static final String MILINK_PACKAGE = "com.milink.service";
    static final long MILINK_VERSION_CODE = 180010202L;
    static final String MILINK_VERSION_NAME = "18.1.2.2.2608192125";
    static final String DISPLAY_CONFIG = "wc.a";
    static final String PANEL_INFO = "wc.b";
    static final String CONTROLLER = "fc.t";
    static final String CARD_CLICK = "b0";

    private RomCompatibilityProfile() {}

    static boolean allowsObfuscatedFusion(int sdk, String incremental, String packageName,
            long versionCode, String versionName) {
        return sdk == SDK && INCREMENTAL.equals(incremental)
                && MILINK_PACKAGE.equals(packageName)
                && versionCode == MILINK_VERSION_CODE && MILINK_VERSION_NAME.equals(versionName);
    }
}
