package dev.technics.pods.diagnostic;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Az100DeviceMatcherTest {
    @Test
    public void acceptsKnownAz100NameFormatting() {
        assertTrue(Az100DeviceMatcher.matchesName("EAH-AZ100"));
        assertTrue(Az100DeviceMatcher.matchesName("Technics EAH AZ100"));
    }

    @Test
    public void rejectsOtherDevicesAndNull() {
        assertFalse(Az100DeviceMatcher.matchesName("EAH-AZ80"));
        assertFalse(Az100DeviceMatcher.matchesName(null));
    }
}
