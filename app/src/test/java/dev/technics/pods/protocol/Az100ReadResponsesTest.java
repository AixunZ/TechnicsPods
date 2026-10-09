package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Az100ReadResponsesTest {
    @Test
    public void parsesCapturedFirmwareIndications() {
        Az100ReadResponses.FirmwareVersion left = Az100ReadResponses.firmwareVersion(
                RaceFrame.decode(ProtocolTestSupport.hex("055D1100071C00000C4A4E5A315A4520312E303430"))
        );
        Az100ReadResponses.FirmwareVersion right = Az100ReadResponses.firmwareVersion(
                RaceFrame.decode(ProtocolTestSupport.hex("055D1100071C00010C4A4E5A315A4520312E303430"))
        );

        assertEquals(Az100BatteryUpdate.LEFT, left.side());
        assertEquals("JNZ1ZE 1.040", left.version());
        assertEquals(Az100BatteryUpdate.RIGHT, right.side());
        assertEquals("JNZ1ZE 1.040", right.version());
    }

    @Test
    public void parsesIndividualCradleBatteryResponse() {
        assertEquals(100, Az100ReadResponses.cradleBattery(
                RaceFrame.decode(ProtocolTestSupport.hex("055B040040000064"))
        ));
    }
}
