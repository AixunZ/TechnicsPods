package dev.technics.pods.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class Az100ControlFramesTest {
    @Test
    public void reproducesCapturedOutsideControlWrites() {
        assertArrayEquals(
                ProtocolTestSupport.hex("055A05000B00016464"),
                Az100ControlFrames.setOutsideControl(
                        Az100StateSnapshot.OUTSIDE_CONTROL_NOISE_CANCELLING,
                        100,
                        100
                )
        );
        assertArrayEquals(
                ProtocolTestSupport.hex("055A05000B00026464"),
                Az100ControlFrames.setOutsideControl(
                        Az100StateSnapshot.OUTSIDE_CONTROL_AMBIENT,
                        100,
                        100
                )
        );
        assertArrayEquals(
                ProtocolTestSupport.hex("055A05000B00006464"),
                Az100ControlFrames.setOutsideControl(Az100StateSnapshot.OUTSIDE_CONTROL_OFF, 100, 100)
        );
    }

    @Test
    public void reproducesCapturedNoiseCancellingLevels() {
        int[] levels = {25, 50, 75, 100};
        String[] expected = {
                "055A05000B00011964",
                "055A05000B00013264",
                "055A05000B00014B64",
                "055A05000B00016464"
        };
        for (int index = 0; index < levels.length; index++) {
            assertArrayEquals(
                    ProtocolTestSupport.hex(expected[index]),
                    Az100ControlFrames.setOutsideControl(
                            Az100StateSnapshot.OUTSIDE_CONTROL_NOISE_CANCELLING,
                            levels[index],
                            100
                    )
            );
        }
    }

    @Test
    public void encodesConfirmedMinimumLevel() {
        assertArrayEquals(
                ProtocolTestSupport.hex("055A05000B00010164"),
                Az100ControlFrames.setOutsideControl(
                        Az100StateSnapshot.OUTSIDE_CONTROL_NOISE_CANCELLING,
                        1,
                        100
                )
        );
    }

    @Test
    public void reproducesAttentionAndAdaptiveAncWrites() {
        assertArrayEquals(
                ProtocolTestSupport.hex("055A040022000101"),
                Az100ControlFrames.setAmbientMode(Az100ControlFrames.AMBIENT_ATTENTION)
        );
        assertArrayEquals(
                ProtocolTestSupport.hex("055A0300680001"),
                Az100ControlFrames.setAdaptiveAnc(true)
        );
        assertArrayEquals(
                ProtocolTestSupport.hex("055A0300680000"),
                Az100ControlFrames.setAdaptiveAnc(false)
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAboveMaximumLevel() {
        Az100ControlFrames.setOutsideControl(Az100StateSnapshot.OUTSIDE_CONTROL_NOISE_CANCELLING, 101, 100);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroLevel() {
        Az100ControlFrames.setOutsideControl(Az100StateSnapshot.OUTSIDE_CONTROL_NOISE_CANCELLING, 0, 100);
    }
}
