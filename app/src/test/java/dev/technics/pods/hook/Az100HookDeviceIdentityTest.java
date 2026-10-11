package dev.technics.pods.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class Az100HookDeviceIdentityTest {
    private static final String SELECTED = "AA:BB:CC:DD:EE:01";
    private static final String OTHER = "AA:BB:CC:DD:EE:02";

    @Test public void selectedAddressSurvivesRenaming() {
        assertTrue(Az100HookDeviceIdentity.matches(SELECTED, "aa:bb:cc:dd:ee:01", "Renamed earbuds"));
    }
    @Test public void anotherAz100DoesNotReceiveSelectedState() {
        assertFalse(Az100HookDeviceIdentity.matches(SELECTED, OTHER, "EAH-AZ100"));
    }
    @Test public void selectionRequiresActualMacAndRejectsInvalidSavedAddress() {
        assertFalse(Az100HookDeviceIdentity.matches(SELECTED, null, "EAH-AZ100"));
        assertFalse(Az100HookDeviceIdentity.matches("invalid", SELECTED, "EAH-AZ100"));
    }
    @Test public void namesAreFallbackOnlyWithoutSelection() {
        assertTrue(Az100HookDeviceIdentity.matches("", OTHER, "Technics EAH-AZ100"));
        assertFalse(Az100HookDeviceIdentity.matches("", OTHER, "Other headset"));
    }
    @Test public void conflictingOwnerMacsAreRejectedEvenWhenOneIsSelected() {
        Az100HookDeviceIdentity guard = new Az100HookDeviceIdentity(SELECTED);
        assertNull(guard.targetAddress(new Owner(SELECTED, OTHER)));
        assertEquals(SELECTED, guard.targetAddress(new Owner(SELECTED, SELECTED)));
        assertFalse(new Az100HookDeviceIdentity("").matchesOwnerOrName(new Owner(SELECTED, OTHER), "EAH-AZ100"));
    }
    @Test public void actualSelectedMacAllowsRenamedModelOwner() {
        Az100HookDeviceIdentity guard = new Az100HookDeviceIdentity(SELECTED);
        assertEquals(SELECTED, guard.targetAddress(new Owner(SELECTED, null)));
        assertNull(guard.targetAddress(new Owner(OTHER, null)));
    }

    @Test public void nestedHostModelAddressIsAccepted() {
        Az100HookDeviceIdentity guard = new Az100HookDeviceIdentity(SELECTED);
        assertEquals(SELECTED, guard.targetAddress(new NestedOwner(SELECTED)));
    }

    @Test public void nestedConflictingHostModelIsRejected() {
        Az100HookDeviceIdentity guard = new Az100HookDeviceIdentity(SELECTED);
        assertNull(guard.targetAddress(new NestedOwner(OTHER)));
    }
    public static final class Owner {
        private final String address;
        private final String mac;
        Owner(String address, String mac) { this.address = address; this.mac = mac; }
        public String getAddress() { return address; }
        public String getMac() { return mac; }
        public String getName() { return "Renamed headset"; }
    }

    public static final class NestedOwner {
        private final NestedInfo info;
        NestedOwner(String address) { info = new NestedInfo(address); }
        public NestedInfo getHeadsetInfo() { return info; }
    }

    public static final class NestedInfo {
        private final String address;
        NestedInfo(String address) { this.address = address; }
        public String getAddress() { return address; }
    }
}
