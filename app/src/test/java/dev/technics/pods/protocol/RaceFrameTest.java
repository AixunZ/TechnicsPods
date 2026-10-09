package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RaceFrameTest {
    @Test
    public void decodesLittleEndianCommandAndPayload() {
        RaceFrame frame = RaceFrame.decode(ProtocolTestSupport.hex("055A0300D60C00"));

        assertEquals(RaceFrame.TYPE_COMMAND, frame.type());
        assertEquals(PanasonicCommand.TWS_GET_BATTERY, frame.commandId());
        assertArrayEquals(new byte[]{0}, frame.payload());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLengthMismatch() {
        RaceFrame.decode(ProtocolTestSupport.hex("055A0300D60C"));
    }
}
