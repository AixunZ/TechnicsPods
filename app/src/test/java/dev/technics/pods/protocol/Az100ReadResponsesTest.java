package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

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

    @Test
    public void parsesOfficialDeviceColorResponse() {
        RaceFrame frame = RaceFrame.decode(ProtocolTestSupport.hex("055B04000200000B"));
        assertEquals(Az100DeviceColor.BLACK, Az100ReadResponses.deviceColor(frame));
    }

    @Test
    public void parsesLittleEndianAz100ModelId() {
        RaceFrame frame = RaceFrame.decode(ProtocolTestSupport.hex("055B0500000000A000"));
        assertEquals(0x00A0, Az100ReadResponses.modelId(frame));
        assertTrue(Az100ReadResponses.isAz100ModelId(0x00A3));
        assertFalse(Az100ReadResponses.isAz100ModelId(0x00A4));
        assertFalse(Az100ReadResponses.isAz100ModelId(0xA000));
    }

    @Test
    public void unknownColorNeverBecomesBlack() {
        for (String response : new String[]{"055B040002000000", "055B0400020000FF",
                "055B040002000002"}) {
            Az100DeviceColor color = Az100ReadResponses.deviceColor(
                    RaceFrame.decode(ProtocolTestSupport.hex(response)));
            assertEquals(Az100DeviceColor.UNKNOWN, color);
            assertEquals("", color.artKey());
        }
    }

    @Test
    public void rejectsFailedTruncatedAndWrongColorResponses() {
        for (String response : new String[]{"055B04000200060B", "055B0300020000",
                "055D04000200000B", "055B04000000000B"}) {
            assertThrows(IllegalArgumentException.class, () -> Az100ReadResponses.deviceColor(
                    RaceFrame.decode(ProtocolTestSupport.hex(response))));
        }
    }

    @Test
    public void rejectsModelResponseWithoutBothIdBytes() {
        assertThrows(IllegalArgumentException.class, () -> Az100ReadResponses.modelId(
                RaceFrame.decode(ProtocolTestSupport.hex("055B0400000000A0"))));
    }
}
