package dev.technics.pods.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RaceStreamDecoderTest {
    @Test
    public void reassemblesSplitFramesAndSeparatesCombinedFrames() {
        RaceStreamDecoder decoder = new RaceStreamDecoder();
        assertTrue(decoder.feed(ProtocolTestSupport.hex("0001055D05")).isEmpty());

        List<RaceFrame> first = decoder.feed(ProtocolTestSupport.hex("00D60C000064055D"));
        assertEquals(1, first.size());
        assertEquals(PanasonicCommand.TWS_GET_BATTERY, first.get(0).commandId());

        List<RaceFrame> second = decoder.feed(ProtocolTestSupport.hex("0500D60C000164"));
        assertEquals(1, second.size());
        assertEquals(0, decoder.pendingByteCount());
    }
}
