package dev.technics.pods.protocol;

import java.util.Arrays;

/** A single Airoha RACE packet carried by the Technics RFCOMM stream. */
public final class RaceFrame {
    public static final int CHANNEL = 0x05;
    public static final int TYPE_COMMAND = 0x5A;
    public static final int TYPE_RESPONSE = 0x5B;
    public static final int TYPE_COMMAND_WITHOUT_RESPONSE = 0x5C;
    public static final int TYPE_INDICATION = 0x5D;

    private final int type;
    private final int commandId;
    private final byte[] payload;

    private RaceFrame(int type, int commandId, byte[] payload) {
        this.type = type;
        this.commandId = commandId;
        this.payload = payload;
    }

    public static RaceFrame decode(byte[] packet) {
        if (packet == null || packet.length < 6) {
            throw new IllegalArgumentException("RACE packet is shorter than its six-byte header");
        }
        if ((packet[0] & 0xFF) != CHANNEL) {
            throw new IllegalArgumentException("Unexpected RACE channel");
        }

        int bodyLength = unsigned(packet[2]) | (unsigned(packet[3]) << 8);
        if (bodyLength < 2) {
            throw new IllegalArgumentException("RACE body does not contain a command id");
        }
        if (packet.length != bodyLength + 4) {
            throw new IllegalArgumentException("RACE packet length does not match its header");
        }

        int commandId = unsigned(packet[4]) | (unsigned(packet[5]) << 8);
        return new RaceFrame(
                unsigned(packet[1]),
                commandId,
                Arrays.copyOfRange(packet, 6, packet.length)
        );
    }

    public int type() {
        return type;
    }

    public int commandId() {
        return commandId;
    }

    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    public int payloadLength() {
        return payload.length;
    }

    static int unsigned(byte value) {
        return value & 0xFF;
    }
}
