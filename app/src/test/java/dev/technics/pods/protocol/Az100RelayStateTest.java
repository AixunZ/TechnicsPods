package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Az100RelayStateTest {
    @Test
    public void parsesPartnerPresentDuringIdleConnection() {
        Az100RelayState state = Az100RelayState.from(
                RaceFrame.decode(ProtocolTestSupport.hex("055B0400000D0506"))
        );

        assertTrue(state.partnerAvailable());
        assertEquals(Integer.valueOf(6), state.partnerId());
    }

    @Test
    public void parsesPartnerUnavailableWhenLeftEarbudEntersCase() {
        Az100RelayState state = Az100RelayState.from(
                RaceFrame.decode(ProtocolTestSupport.hex("055D0400000D0000"))
        );

        assertFalse(state.partnerAvailable());
        assertNull(state.partnerId());
    }
}
