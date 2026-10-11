package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Az100ProtocolParserTest {
    private static final String ALL_DATA_RESPONSE =
            "055B2E01F000001706000300000508000200000A000400006464100002001112000900060C030000"
                    + "000000150002000617000200012100030000004D000500010100012E000200013200020001340002"
                    + "000038000200283A00020002400002006455000200005700020001020002000B61000D004A4E5A32"
                    + "5A412030312E303063000300000067000059000200005B0002000200000000000000000000000000"
                    + "00000000000000000000000000000000000000000000000000000000000000000000000000000000"
                    + "00000000000000000000000000000000000000000000000000000000000000000000000000000000"
                    + "00000000000000000000000000000000000000000000000000000000000000000000000000000000"
                    + "0000000000000000000000000000000000000000000000000000";

    @Test
    public void parsesIdleStateAndIgnoresFixedSizePadding() {
        Az100StateSnapshot state = Az100AllDataParser.parse(
                RaceFrame.decode(ProtocolTestSupport.hex(ALL_DATA_RESPONSE))
        );

        assertEquals(Integer.valueOf(Az100StateSnapshot.OUTSIDE_CONTROL_OFF), state.outsideControlMode());
        assertEquals(Integer.valueOf(100), state.noiseCancellingLevel());
        assertEquals(Integer.valueOf(100), state.ambientSoundLevel());
        assertEquals(Integer.valueOf(40), state.noiseCancellingAdjustment());
        assertEquals(Integer.valueOf(100), state.cradleBatteryPercent());
        assertEquals(Integer.valueOf(0), state.leAudioEnabled());
        assertEquals(Integer.valueOf(2), state.currentStatus());
        assertEquals("JNZ2ZA 01.00", state.cradleFirmwareVersion());
        assertEquals(Az100DeviceColor.BLACK, state.deviceColor());
    }

    @Test
    public void absentOrRejectedAllDataColorStaysUnknown() {
        for (String response : new String[]{"055B0400F0000000", "055B0800F000000102000106"}) {
            assertEquals(Az100DeviceColor.UNKNOWN, Az100AllDataParser.parse(
                    RaceFrame.decode(ProtocolTestSupport.hex(response))).deviceColor());
        }
    }

    @Test
    public void parsesLeftAndRightBatteryIndications() {
        Az100BatteryUpdate left = Az100BatteryUpdate.from(
                RaceFrame.decode(ProtocolTestSupport.hex("055D0500D60C000064"))
        );
        Az100BatteryUpdate right = Az100BatteryUpdate.from(
                RaceFrame.decode(ProtocolTestSupport.hex("055D0500D60C000164"))
        );

        assertEquals(Az100BatteryUpdate.LEFT, left.side());
        assertEquals(100, left.percent());
        assertEquals(Az100BatteryUpdate.RIGHT, right.side());
        assertEquals(100, right.percent());
    }
}
