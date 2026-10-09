package dev.technics.pods.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Az100SettingsPolicyTest {
    @Test
    public void atUiPayloadUsesLeftRightCaseOrder() {
        assertEquals(
                "1|0100;0200;0201|100,90,80|00",
                Az100SettingsPolicy.atUiPayload(1, 0, new int[]{100, 90, 80})
        );
    }

    @Test
    public void attentionModeUsesVoiceTransparencyLevel() {
        assertEquals("2", Az100SettingsPolicy.settingsMode(2));
        assertEquals("0201", Az100SettingsPolicy.settingsLevel(2, 1));
    }

    @Test
    public void refreshPayloadContainsBatteryFirmwareAndAnc() {
        String[] values = Az100SettingsPolicy.refreshPayload(
                2, 0, new int[]{100, 90, 80}, "JNZ1ZE 1.040"
        ).split(",", -1);
        assertEquals(16, values.length);
        assertEquals("100", values[0]);
        assertEquals("90", values[1]);
        assertEquals("80", values[2]);
        assertEquals("0+JNZ1ZE 1.040", values[3]);
        assertEquals("0200", values[7]);
    }
}
