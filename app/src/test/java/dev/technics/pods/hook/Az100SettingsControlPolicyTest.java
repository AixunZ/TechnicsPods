package dev.technics.pods.hook;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class Az100SettingsControlPolicyTest {
    @Test public void parsesModeClick() {
        Az100SettingsControlPolicy.Request request =
                Az100SettingsControlPolicy.request("updateAncMode", List.of(2, true));
        assertNotNull(request);
        assertEquals(2, request.mode());
        assertNull(request.noiseLevel());
    }

    @Test public void parsesNoiseLevelClick() {
        Az100SettingsControlPolicy.Request request =
                Az100SettingsControlPolicy.request("updateAncLevel", List.of(75, true));
        assertNotNull(request);
        assertEquals(-1, request.mode());
        assertEquals(Integer.valueOf(75), request.noiseLevel());
    }

    @Test public void ignoresInternalReplayAndInvalidValues() {
        assertNull(Az100SettingsControlPolicy.request("updateAncMode", List.of(1, false)));
        assertNull(Az100SettingsControlPolicy.request("updateAncMode", List.of(1)));
        assertNull(Az100SettingsControlPolicy.request("updateAncMode", List.of(3, true)));
        assertNull(Az100SettingsControlPolicy.request("updateAncLevel", List.of(0, true)));
        assertNull(Az100SettingsControlPolicy.request("updateAncLevel", List.of(101, true)));
    }
}
