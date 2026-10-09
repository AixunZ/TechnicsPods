package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Az100ControlResponsesTest {
    @Test
    public void parsesAllCapturedOutsideControlReadBacks() {
        assertEquals(
                Az100StateSnapshot.OUTSIDE_CONTROL_NOISE_CANCELLING,
                outside("055B06000A0000016464").mode()
        );
        assertEquals(
                Az100StateSnapshot.OUTSIDE_CONTROL_AMBIENT,
                outside("055B06000A0000026464").mode()
        );
        Az100ControlResponses.OutsideControl off = outside("055B06000A0000006464");
        assertEquals(Az100StateSnapshot.OUTSIDE_CONTROL_OFF, off.mode());
        assertEquals(100, off.noiseLevel());
        assertEquals(100, off.ambientLevel());
    }

    @Test
    public void parsesAttentionAndAdaptiveAncReadBacks() {
        Az100ControlResponses.AmbientMode attention = Az100ControlResponses.ambientMode(
                RaceFrame.decode(ProtocolTestSupport.hex("055B05002100000101"))
        );
        assertEquals(Az100ControlFrames.AMBIENT_ATTENTION, attention.mode());
        assertEquals(Az100ControlResponses.AmbientMode.MUSIC_STOP, attention.musicBehavior());

        assertTrue(Az100ControlResponses.adaptiveAnc(
                RaceFrame.decode(ProtocolTestSupport.hex("055B040067000001"))
        ));
        assertFalse(Az100ControlResponses.adaptiveAnc(
                RaceFrame.decode(ProtocolTestSupport.hex("055B040067000000"))
        ));
    }

    @Test
    public void acceptsSuccessfulOutsideControlWriteResponse() {
        Az100ControlResponses.requireSuccessfulOutsideControlSet(
                RaceFrame.decode(ProtocolTestSupport.hex("055B03000B0000"))
        );
    }

    private static Az100ControlResponses.OutsideControl outside(String hex) {
        return Az100ControlResponses.outsideControl(RaceFrame.decode(ProtocolTestSupport.hex(hex)));
    }
}
