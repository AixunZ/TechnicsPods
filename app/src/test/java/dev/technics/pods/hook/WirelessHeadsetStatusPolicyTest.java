package dev.technics.pods.hook;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;

public class WirelessHeadsetStatusPolicyTest {
    @Test public void verifiedCallbackUsesCurrentNotPreviousState() {
        assertEquals(2, WirelessHeadsetStatusPolicy.a2dpCurrentState(Arrays.asList("device", 0, 2)));
        assertEquals(0, WirelessHeadsetStatusPolicy.a2dpCurrentState(Arrays.asList("device", 2, 0)));
        assertEquals(-1, WirelessHeadsetStatusPolicy.a2dpCurrentState(Arrays.asList("device", 2)));
    }
    @Test public void unrelatedDeviceDoesNotClaimIcon() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("other", 2, 2, false);
        assertNull(policy.visibility(true));
    }
    @Test public void disablingPreferenceRemovesOnlyOwnedTargetIcon() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        assertEquals(Boolean.FALSE, policy.visibility(false));
    }
    @Test public void incompleteStartupSnapshotCannotHideOwnedIcon() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.update("az", 2, 0, true);
        assertNull(policy.visibility(true, false));
        assertEquals(Boolean.FALSE, policy.visibility(true, true));
    }
    @Test public void lastTargetProfileDisconnectHidesOwnedIcon() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        policy.update("az", 1, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.update("az", 2, 0, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.update("az", 1, 0, true);
        assertEquals(Boolean.FALSE, policy.visibility(true));
        assertNull(policy.visibility(true));
    }
    @Test public void leavesOtherHeadsetIconAndClearsOnAdapterOff() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        policy.update("other", 2, 2, false);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.update("az", 2, 0, true);
        assertNull(policy.visibility(true));
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.clear();
        assertEquals(Boolean.FALSE, policy.visibility(true));
    }
    @Test public void liveDisconnectWinsOverEarlierConnectedSnapshot() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.beginReconciliation(2);
        policy.update("az", 2, 0, true);
        policy.clearProfile(2);
        policy.updateSnapshot("az", 2, 2, true);
        policy.completeProfile(2);
        assertEquals(Boolean.FALSE, policy.visibility(true, !policy.isReconciling()));
    }
    @Test public void liveOtherConnectionWinsOverEarlierEmptySnapshot() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.beginReconciliation(2);
        policy.update("other", 2, 2, false);
        policy.clearProfile(2);
        policy.completeProfile(2);
        assertNull(policy.visibility(true, !policy.isReconciling()));
    }
    @Test public void latestLiveEventWinsAndProfilesRemainIndependent() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.beginReconciliation(1, 2);
        policy.update("az", 2, 0, true);
        policy.update("az", 2, 2, true);
        policy.clearProfile(2);
        policy.completeProfile(2);
        assertTrue(policy.isReconciling());
        assertEquals(Boolean.TRUE, policy.visibility(true, false));
        policy.completeProfile(1);
        assertFalse(policy.isReconciling());
    }
    @Test public void unsupportedProfileDoesNotPreventLastTargetDisconnectHiding() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.beginReconciliation(1, 2, 22);
        policy.completeProfile(22);
        policy.clearProfile(1);
        policy.completeProfile(1);
        policy.clearProfile(2);
        policy.completeProfile(2);
        assertEquals(Boolean.FALSE, policy.visibility(true, !policy.isReconciling()));
    }
    @Test public void failedProfilePreservesKnownOtherHeadset() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        policy.update("other", 22, 2, false);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.beginReconciliation(2, 22);
        policy.completeProfile(22);
        policy.clearProfile(2);
        policy.completeProfile(2);
        assertNull(policy.visibility(true, !policy.isReconciling()));
    }
    @Test public void timeoutFinishesWaitWithoutDiscardingLiveConnections() {
        WirelessHeadsetStatusPolicy policy = new WirelessHeadsetStatusPolicy();
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.beginReconciliation(2, 22);
        policy.update("az", 2, 0, true);
        policy.update("other", 22, 2, false);
        policy.completeReconciliation();
        assertFalse(policy.isProfilePending(22));
        assertNull(policy.visibility(true, !policy.isReconciling()));
        policy.update("az", 2, 2, true);
        assertEquals(Boolean.TRUE, policy.visibility(true));
        policy.beginReconciliation(2);
        policy.update("az", 2, 0, true);
        policy.update("other", 22, 0, false);
        policy.completeReconciliation();
        assertEquals(Boolean.FALSE, policy.visibility(true, !policy.isReconciling()));
    }
}
