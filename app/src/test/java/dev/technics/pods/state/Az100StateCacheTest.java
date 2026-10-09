package dev.technics.pods.state;

import org.junit.Test;

import java.util.List;

import dev.technics.pods.diagnostic.Az100DiagnosticResult;
import dev.technics.pods.diagnostic.Az100OutsideControlResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Az100StateCacheTest {
    @Test
    public void failedSampleKeepsValuesButMarksThemAsCached() {
        Az100StateCache.State state = Az100StateCache.samplingStarted(
                Az100StateCache.monitoringStarted(Az100StateCache.State.empty()),
                100L
        );
        state = Az100StateCache.sampleSucceeded(state, completeResult(), 200L);
        state = Az100StateCache.samplingStarted(state, 300L);
        state = Az100StateCache.sampleFailed(state, "耳机不可达", 350L);

        assertTrue(state.monitoring());
        assertFalse(state.sampling());
        assertFalse(state.reachable());
        assertEquals(1, state.consecutiveFailures());
        assertEquals(Integer.valueOf(81), state.left().batteryPercent());
        assertEquals(Integer.valueOf(79), state.right().batteryPercent());
        assertEquals(Integer.valueOf(90), state.cradleBatteryPercent());
        assertEquals(200L, state.left().batteryUpdatedAtMillis());
        assertEquals(200L, state.lastSuccessAtMillis());
        assertEquals("耳机不可达", state.lastError());
    }

    @Test
    public void partialSampleDoesNotOverwriteUnavailablePartnerValues() {
        Az100StateCache.State state = Az100StateCache.sampleSucceeded(
                Az100StateCache.State.empty(),
                completeResult(),
                200L
        );
        Az100DiagnosticResult leftOnly = new Az100DiagnosticResult(
                900L,
                80,
                null,
                89,
                2,
                100,
                100,
                0,
                false,
                false,
                "JNZ1ZE 1.041",
                null,
                List.of("右耳电量", "右耳固件")
        );

        state = Az100StateCache.sampleSucceeded(state, leftOnly, 500L);

        assertEquals(Integer.valueOf(80), state.left().batteryPercent());
        assertEquals(500L, state.left().batteryUpdatedAtMillis());
        assertEquals(Integer.valueOf(79), state.right().batteryPercent());
        assertEquals(200L, state.right().batteryUpdatedAtMillis());
        assertEquals("JNZ1ZE 1.040", state.right().firmware());
        assertFalse(state.partnerAvailable());
        assertEquals("右耳电量、右耳固件", state.incompleteFields());
        assertNull(state.lastError());
    }

    @Test
    public void stoppingMonitorPreservesLastKnownState() {
        Az100StateCache.State sampled = Az100StateCache.sampleSucceeded(
                Az100StateCache.State.empty(),
                completeResult(),
                200L
        );

        Az100StateCache.State stopped = Az100StateCache.monitoringStopped(sampled);

        assertFalse(stopped.monitoring());
        assertFalse(stopped.reachable());
        assertEquals(Integer.valueOf(81), stopped.left().batteryPercent());
        assertEquals(200L, stopped.lastSuccessAtMillis());
    }

    @Test
    public void controlReadBackUpdatesOnlyControlFields() {
        Az100StateCache.State sampled = Az100StateCache.sampleSucceeded(
                Az100StateCache.State.empty(),
                completeResult(),
                200L
        );
        Az100OutsideControlResult control = new Az100OutsideControlResult(
                300L,
                0,
                100,
                100,
                0,
                100,
                true
        );

        Az100StateCache.State updated = Az100StateCache.controlSucceeded(sampled, control, 400L);

        assertEquals(Integer.valueOf(0), updated.controls().outsideMode());
        assertEquals(Integer.valueOf(100), updated.controls().noiseCancellingLevel());
        assertEquals(Integer.valueOf(100), updated.controls().ambientLevel());
        assertEquals(400L, updated.controls().updatedAtMillis());
        assertEquals(400L, updated.controls().outsideModeUpdatedAtMillis());
        assertEquals(Integer.valueOf(81), updated.left().batteryPercent());
        assertEquals(200L, updated.lastSuccessAtMillis());
        assertEquals(200L, updated.left().batteryUpdatedAtMillis());
        assertTrue(updated.reachable());
    }

    @Test public void oldSampleCannotOverwriteNewControlReadBack() {
        Az100StateCache.State state = Az100StateCache.controlSucceeded(
                Az100StateCache.State.empty(), confirmedOff(), 400L);
        state = Az100StateCache.sampleSucceeded(state, completeResult(), 300L, 500L);
        assertEquals(Integer.valueOf(0), state.controls().outsideMode());
        assertEquals(400L, state.controls().updatedAtMillis());
        assertEquals(Integer.valueOf(81), state.left().batteryPercent());
    }

    @Test public void newerExternalChangeReplacesModuleSelection() {
        Az100StateCache.State state = Az100StateCache.controlSucceeded(
                Az100StateCache.State.empty(), confirmedOff(), 400L);
        state = Az100StateCache.sampleSucceeded(state, completeResult(), 500L, 600L);
        assertEquals(Integer.valueOf(1), state.controls().outsideMode());
        assertEquals(600L, state.controls().updatedAtMillis());
    }

    @Test public void olderSampleFailureDoesNotHideConfirmedControl() {
        Az100StateCache.State state = Az100StateCache.controlSucceeded(
                Az100StateCache.State.empty(), confirmedOff(), 400L);
        state = Az100StateCache.sampleFailed(state, "old failure", 300L, 500L);
        assertTrue(state.reachable());
        assertNull(state.lastError());
        assertEquals(Integer.valueOf(0), state.controls().outsideMode());
    }

    @Test public void failedControlInvalidatesModeAndRejectsOlderInFlightSample() {
        Az100StateCache.State state = Az100StateCache.sampleSucceeded(
                Az100StateCache.State.empty(), completeResult(), 200L);
        state = Az100StateCache.controlFailed(state, "timeout", 400L);
        state = Az100StateCache.sampleSucceeded(state, completeResult(), 300L, 500L);
        assertNull(state.controls().outsideMode());
        assertEquals(Integer.valueOf(81), state.left().batteryPercent());
    }

    @Test public void batteryOnlyReadCannotRefreshOldModeTimestamp() {
        Az100StateCache.State state = Az100StateCache.controlSucceeded(
                Az100StateCache.State.empty(), confirmedOff(), 100L);
        Az100DiagnosticResult partial = new Az100DiagnosticResult(100L, 90, null, null,
                null, null, null, 1, true, null, null, null, List.of());
        state = Az100StateCache.sampleSucceeded(state, partial, 200000L, 200100L);
        assertEquals(100L, state.controls().outsideModeUpdatedAtMillis());
        assertFalse(Az100StateFreshness.isFresh(
                state.controls().outsideModeUpdatedAtMillis(), 200100L));
    }

    @Test public void partialControlReadKeepsIndependentFieldFreshness() {
        Az100StateCache.State state = Az100StateCache.sampleSucceeded(
                Az100StateCache.State.empty(), completeResult(), 100L);
        Az100DiagnosticResult ambientOnly = new Az100DiagnosticResult(
                0L, null, null, null, null, null, 77, null, null,
                null, null, null, List.of());
        state = Az100StateCache.sampleSucceeded(state, ambientOnly, 500L);

        assertEquals(100L, state.controls().outsideModeUpdatedAtMillis());
        assertEquals(500L, state.controls().ambientLevelUpdatedAtMillis());
        assertEquals(500L, state.controls().updatedAtMillis());
    }

    @Test public void invalidOrMismatchedResultCannotBePublished() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new Az100OutsideControlResult(0, 2, 80, 67, 1, 80, true));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new Az100OutsideControlResult(0, 2, 80, 101, 2, 80, true));
    }

    private static Az100OutsideControlResult confirmedOff() {
        return new Az100OutsideControlResult(20L, 0, 80, 67, 0, 80, true);
    }

    private static Az100DiagnosticResult completeResult() {
        return new Az100DiagnosticResult(
                1_200L,
                81,
                79,
                90,
                1,
                100,
                100,
                1,
                false,
                true,
                "JNZ1ZE 1.040",
                "JNZ1ZE 1.040",
                List.of()
        );
    }
}
