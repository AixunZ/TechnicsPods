package dev.technics.pods.capture;

import java.util.Locale;

public record CaptureRecord(
        long wallTimeMillis,
        long elapsedNanos,
        String session,
        String process,
        String thread,
        String direction,
        String operation,
        String serviceUuid,
        String characteristicUuid,
        String descriptorUuid,
        int status,
        int writeType,
        int payloadLength,
        String payloadHex,
        String note
) {
    public String toJsonLine() {
        return "{" +
                field("schema", "technics-transport-capture-v2") + "," +
                number("wallTimeMillis", wallTimeMillis) + "," +
                number("elapsedNanos", elapsedNanos) + "," +
                field("session", session) + "," +
                field("process", process) + "," +
                field("thread", thread) + "," +
                field("direction", direction) + "," +
                field("operation", operation) + "," +
                field("serviceUuid", serviceUuid) + "," +
                field("characteristicUuid", characteristicUuid) + "," +
                field("descriptorUuid", descriptorUuid) + "," +
                number("status", status) + "," +
                number("writeType", writeType) + "," +
                number("payloadLength", payloadLength) + "," +
                field("payloadHex", payloadHex) + "," +
                field("note", note) +
                "}";
    }

    private static String field(String name, String value) {
        return quote(name) + ":" + quote(value == null ? "" : value);
    }

    private static String number(String name, long value) {
        return String.format(Locale.ROOT, "%s:%d", quote(name), value);
    }

    static String quote(String value) {
        StringBuilder output = new StringBuilder(value.length() + 16).append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (current < 0x20) {
                        output.append(String.format(Locale.ROOT, "\\u%04X", (int) current));
                    } else {
                        output.append(current);
                    }
                }
            }
        }
        return output.append('"').toString();
    }
}
