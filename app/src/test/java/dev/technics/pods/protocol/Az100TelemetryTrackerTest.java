package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Az100TelemetryTrackerTest {
    @Test
    public void disconnectPreservesLastKnownBatteryWithoutPretendingItIsLive() {
        Az100TelemetryTracker tracker = new Az100TelemetryTracker();
        tracker.onConnected();
        tracker.onBattery(new Az100BatteryUpdate(Az100BatteryUpdate.LEFT, 81));
        tracker.onBattery(new Az100BatteryUpdate(Az100BatteryUpdate.RIGHT, 79));
        tracker.onRelayState(new Az100RelayState(true, 6));

        tracker.onRelayState(new Az100RelayState(false, null));
        assertFalse(tracker.snapshot().partnerAvailable());
        assertTrue(tracker.snapshot().connected());

        tracker.onDisconnected();
        Az100TelemetryTracker.Snapshot snapshot = tracker.snapshot();
        assertFalse(snapshot.connected());
        assertEquals(Integer.valueOf(81), snapshot.leftBatteryPercent());
        assertEquals(Integer.valueOf(79), snapshot.rightBatteryPercent());
    }
}
