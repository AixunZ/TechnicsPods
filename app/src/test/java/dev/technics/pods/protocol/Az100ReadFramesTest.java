package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class Az100ReadFramesTest {
    @Test
    public void reproducesCapturedReadRequests() {
        assertArrayEquals(ProtocolTestSupport.hex("055A02000000"), Az100ReadFrames.getModelId());
        assertArrayEquals(ProtocolTestSupport.hex("055A0200000D"), Az100ReadFrames.getRelayDestinations());
        assertArrayEquals(ProtocolTestSupport.hex("055A02000A00"), Az100ReadFrames.getOutsideControl());
        assertArrayEquals(ProtocolTestSupport.hex("055A02002100"), Az100ReadFrames.getAmbientMode());
        assertArrayEquals(ProtocolTestSupport.hex("055A02006700"), Az100ReadFrames.getAdaptiveAnc());
        assertArrayEquals(ProtocolTestSupport.hex("055A0300D60C00"),
                Az100ReadFrames.getEarbudBattery(Az100BatteryUpdate.LEFT));
        assertArrayEquals(ProtocolTestSupport.hex("055A0300071C01"),
                Az100ReadFrames.getFirmwareVersion(Az100BatteryUpdate.RIGHT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownEarbudSide() {
        Az100ReadFrames.getEarbudBattery(2);
    }
}
