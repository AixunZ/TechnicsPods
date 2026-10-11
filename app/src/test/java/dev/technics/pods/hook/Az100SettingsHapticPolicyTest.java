package dev.technics.pods.hook;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Az100SettingsHapticPolicyTest {
    private static final String ADDRESS = "AA:BB:CC:DD:EE:FF";
    private final Az100SettingsHapticPolicy policy = new Az100SettingsHapticPolicy();
    private final Az100SettingsHapticPolicy.Session session = session(ADDRESS, 2L, 3L, 4L);

    @Test public void acceptsClickOnceAndRetainsDeduplicationAcrossLaterRequests() {
        assertTrue(claim(true, true, "accepted", session, true, true, true));
        assertFalse(claim(true, true, "accepted", session, true, true, true));
        assertTrue(claim(true, true, "next", session, true, true, true));
        assertFalse(claim(true, true, "accepted", session, true, true, true));
    }

    @Test public void rejectsBusyRejectedMissingIdsAndInternalReplay() {
        assertFalse(claim(false, true, "busy", session, true, true, true));
        assertFalse(claim(false, true, "rejected", session, true, true, true));
        assertFalse(claim(true, true, null, session, true, true, true));
        assertFalse(claim(true, true, " ", session, true, true, true));
        assertFalse(claim(true, false, "refresh", session, true, true, true));
    }

    @Test public void rejectsChangedDeviceSelectionViewAndRuntimeSession() {
        assertFalse(claim(true, true, "device", session("11:22:33:44:55:66", 2L, 3L, 4L), true, true, true));
        assertFalse(claim(true, true, "selection", session(ADDRESS, 5L, 3L, 4L), true, true, true));
        assertFalse(claim(true, true, "view", session(ADDRESS, 2L, 5L, 4L), true, true, true));
        assertFalse(claim(true, true, "runtime", session(ADDRESS, 2L, 3L, 5L), true, true, true));
    }

    @Test public void rejectsClosedDetachedMissingViewAndDisabledHaptics() {
        assertFalse(claim(true, true, "closed", session, false, true, true));
        assertFalse(claim(true, true, "detached", session, true, false, true));
        assertFalse(claim(true, true, "disabled", session, true, true, false));
        assertFalse(claim(true, true, "missing", null, true, true, true));
        assertFalse(policy.claim(true, true, "missingExpected", null, session, true, true, true));
    }

    @Test public void rejectsUnknownGenerationAndMissingAddress() {
        Az100SettingsHapticPolicy.Session unknown = session(ADDRESS, 2L, 3L, -1L);
        assertFalse(policy.claim(true, true, "unknown", unknown, unknown, true, true, true));
        assertFalse(claim(true, true, "address", session(null, 2L, 3L, 4L), true, true, true));
    }

    @Test public void doesNotConsumeRequestUntilItIsEligible() {
        assertFalse(claim(true, true, "accepted", session, true, false, true));
        assertTrue(claim(true, true, "accepted", session, true, true, true));
    }

    @Test public void laterTransportFailureDoesNotInvalidateAcknowledgement() {
        // Completion status is intentionally irrelevant while the same runtime session remains.
        assertTrue(claim(true, true, "failedAfterAcceptance", session, true, true, true));
        assertFalse(claim(true, true, "failedAfterAcceptance", session, true, true, true));
    }

    private boolean claim(boolean accepted, boolean userAction, String id,
            Az100SettingsHapticPolicy.Session current, boolean active, boolean attached,
            boolean enabled) {
        return policy.claim(accepted, userAction, id, session, current, active, attached, enabled);
    }

    private static Az100SettingsHapticPolicy.Session session(String address, long selection,
            long view, long runtime) {
        return new Az100SettingsHapticPolicy.Session(address, selection, view, runtime);
    }
}
