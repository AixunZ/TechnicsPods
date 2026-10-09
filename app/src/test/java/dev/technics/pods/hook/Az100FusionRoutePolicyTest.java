package dev.technics.pods.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Az100FusionRoutePolicyTest {
    @Test
    public void patchesOnlyEmptyAz100HeadsetLookupWithCachedService() {
        assertTrue(Az100FusionRoutePolicy.shouldPatch(
                393216, "Technics EAH-AZ100", true, true));
        assertFalse(Az100FusionRoutePolicy.shouldPatch(
                65536, "Technics EAH-AZ100", true, true));
        assertFalse(Az100FusionRoutePolicy.shouldPatch(
                393216, "Other headset", true, true));
        assertFalse(Az100FusionRoutePolicy.shouldPatch(
                393216, "Technics EAH-AZ100", false, true));
        assertFalse(Az100FusionRoutePolicy.shouldPatch(
                393216, "Technics EAH-AZ100", true, false));
    }

    @Test
    public void recognizesOnlyHeadsetPanelResources() {
        assertTrue(Az100FusionRoutePolicy.isHeadsetPanelResource(
                "com.milink.service:layout/circulate_headset_card"));
        assertTrue(Az100FusionRoutePolicy.isHeadsetPanelResource(
                "com.milink.service:layout/earphone_panel"));
        assertTrue(Az100FusionRoutePolicy.isHeadsetPanelResource(
                "com.milink.service:SOUND_BLUETOOTH_CONTROL"));
        assertFalse(Az100FusionRoutePolicy.isHeadsetPanelResource(
                "com.milink.service:layout/bluetooth_card"));
        assertFalse(Az100FusionRoutePolicy.isHeadsetPanelResource(null));
    }
}
