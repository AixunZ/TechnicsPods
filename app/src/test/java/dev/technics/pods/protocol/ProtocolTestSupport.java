package dev.technics.pods.protocol;

final class ProtocolTestSupport {
    private ProtocolTestSupport() {}

    static byte[] hex(String value) {
        byte[] output = new byte[value.length() / 2];
        for (int index = 0; index < output.length; index++) {
            output[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return output;
    }
}
