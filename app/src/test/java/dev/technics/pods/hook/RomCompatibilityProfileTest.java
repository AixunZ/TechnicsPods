package dev.technics.pods.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class RomCompatibilityProfileTest {
    @Test public void permitsOnlyRecordedRomAndHostPair() {
        assertTrue(allowed(37, "OS4.0.0.17.XPACNXM", 180010202L, "18.1.2.2.2608192125"));
        assertFalse(allowed(36, "OS4.0.0.17.XPACNXM", 180010202L, "18.1.2.2.2608192125"));
        assertFalse(allowed(37, "OS4.0.0.18.XPACNXM", 180010202L, "18.1.2.2.2608192125"));
        assertFalse(allowed(37, "OS4.0.0.17", 180010202L, "18.1.2.2.2608192125"));
        assertFalse(allowed(37, "prefix-OS4.0.0.17.XPACNXM", 180010202L, "18.1.2.2.2608192125"));
        assertFalse(allowed(37, "OS4.0.0.17.XPACNXM", 180010203L, "18.1.2.2.2608192125"));
        assertFalse(allowed(37, "OS4.0.0.17.XPACNXM", 180010202L, "18.1.2.3.2608192125"));
        assertFalse(allowed(37, null, 180010202L, "18.1.2.2.2608192125"));
        assertFalse(RomCompatibilityProfile.allowsObfuscatedFusion(37, "OS4.0.0.17.XPACNXM",
                "other.host", 180010202L, "18.1.2.2.2608192125"));
    }
    private static boolean allowed(int sdk, String rom, long code, String name) {
        return RomCompatibilityProfile.allowsObfuscatedFusion(sdk, rom,
                "com.milink.service", code, name);
    }
}
