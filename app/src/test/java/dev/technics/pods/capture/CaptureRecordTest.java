package dev.technics.pods.capture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaptureRecordTest {
    @Test
    public void jsonLineEscapesUntrustedText() {
        CaptureRecord record = new CaptureRecord(
                1L,
                2L,
                "session\nname",
                "process",
                "thread",
                "TX",
                "write\"Characteristic",
                "service",
                "characteristic",
                "",
                0,
                2,
                2,
                "00FF",
                "note\\value"
        );

        String json = record.toJsonLine();
        assertTrue(json.contains("session\\nname"));
        assertTrue(json.contains("write\\\"Characteristic"));
        assertTrue(json.contains("note\\\\value"));
        assertFalse(json.contains("session\nname"));
        assertTrue(json.contains("\"schema\":\"technics-transport-capture-v2\""));
    }
}
