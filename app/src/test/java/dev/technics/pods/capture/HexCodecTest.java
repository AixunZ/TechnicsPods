package dev.technics.pods.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HexCodecTest {
    @Test
    public void encodesUnsignedBytesAndHonorsLimit() {
        assertEquals("007FFF", HexCodec.encode(new byte[]{0x00, 0x7F, (byte) 0xFF, 0x42}, 3));
    }

    @Test
    public void encodesNullAsEmptyString() {
        assertEquals("", HexCodec.encode(null, 10));
    }
}
