package dev.technics.pods.capture;

public final class HexCodec {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private HexCodec() {}

    public static String encode(byte[] value, int limit) {
        if (value == null || value.length == 0) return "";
        int count = Math.min(value.length, Math.max(0, limit));
        char[] output = new char[count * 2];
        for (int index = 0; index < count; index++) {
            int current = value[index] & 0xFF;
            output[index * 2] = HEX[current >>> 4];
            output[index * 2 + 1] = HEX[current & 0x0F];
        }
        return new String(output);
    }
}
