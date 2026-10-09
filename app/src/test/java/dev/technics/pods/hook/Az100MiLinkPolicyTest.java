package dev.technics.pods.hook;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dev.technics.pods.state.Az100StateCache;

public class Az100MiLinkPolicyTest {
    @Test
    public void supportIdentityUsesTwsCarrier() {
        assertEquals("01010607,000000000000000010000000", Az100MiLinkPolicy.supportString());
    }

    @Test
    public void offlineStateDoesNotProjectCachedBatteryAsLive() {
        Az100StateCache.State state = state(false, 80, 70, 90, 1, 55, 0);
        assertArrayEquals(new int[]{255, 255, 255}, Az100MiLinkPolicy.batteryLevels(state));
        assertFalse(Az100MiLinkPolicy.hasFreshState(state));
        assertEquals(null, Az100MiLinkPolicy.noiseControlMode(state));
        assertEquals(null, Az100MiLinkPolicy.noiseControlLevelCode(state));
    }

    @Test
    public void reachableStateMapsLeftRightAndCase() {
        Az100StateCache.State state = state(true, 101, -4, 90, 1, 55, 0);
        assertArrayEquals(new int[]{100, 0, 90}, Az100MiLinkPolicy.batteryLevels(state));
        assertEquals(Integer.valueOf(1), Az100MiLinkPolicy.noiseControlMode(state));
        assertEquals("0100", Az100MiLinkPolicy.noiseControlLevelCode(state));
        assertTrue(Az100MiLinkPolicy.hasFreshState(state));
    }

    @Test
    public void ambientAttentionUsesMiLinkVoiceCode() {
        Az100StateCache.State state = state(true, 80, 70, 90, 2, 55, 1);
        assertEquals(Integer.valueOf(2), Az100MiLinkPolicy.noiseControlMode(state));
        assertEquals("0201", Az100MiLinkPolicy.noiseControlLevelCode(state));
    }

    private static Az100StateCache.State state(boolean reachable, int left, int right, int cradle,
            int mode, int ambient, int ambientMode) {
        long now = 100L;
        return new Az100StateCache.State(
                new Az100StateCache.Earbud(left, now, null, 0L),
                new Az100StateCache.Earbud(right, now, null, 0L),
                cradle, now,
                new Az100StateCache.Controls(mode, 50, ambient, ambientMode, false, now),
                true, now, true, false, reachable, 0, now,
                reachable ? now : 0L, null, null);
    }
}
