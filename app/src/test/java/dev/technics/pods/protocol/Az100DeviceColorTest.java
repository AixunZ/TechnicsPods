package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Az100DeviceColorTest {
    @Test
    public void mapsOnlyFinishesWithVerifiedAz100Artwork() {
        assertEquals("blue", Az100DeviceColor.fromCode(1).artKey());
        assertEquals("black", Az100DeviceColor.fromCode(11).artKey());
        assertEquals("gold", Az100DeviceColor.fromCode(14).artKey());
        assertEquals("silver", Az100DeviceColor.fromCode(19).artKey());
        assertEquals("violet", Az100DeviceColor.fromCode(22).artKey());
        assertEquals(Az100DeviceColor.UNKNOWN, Az100DeviceColor.fromCode(23));
        assertEquals(Az100DeviceColor.UNKNOWN, Az100DeviceColor.fromCode(-1));
        assertEquals(Az100DeviceColor.UNKNOWN, Az100DeviceColor.fromCode(267));
    }
}
