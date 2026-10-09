package dev.technics.pods.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Reassembles RACE packets when RFCOMM reads split or combine packet boundaries. */
public final class RaceStreamDecoder {
    static final int MAX_BODY_LENGTH = 4096;

    private byte[] pending = new byte[0];

    public List<RaceFrame> feed(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return Collections.emptyList();

        byte[] combined = Arrays.copyOf(pending, pending.length + bytes.length);
        System.arraycopy(bytes, 0, combined, pending.length, bytes.length);

        List<RaceFrame> frames = new ArrayList<>();
        int cursor = 0;
        while (combined.length - cursor >= 4) {
            if ((combined[cursor] & 0xFF) != RaceFrame.CHANNEL) {
                cursor++;
                continue;
            }

            int bodyLength = RaceFrame.unsigned(combined[cursor + 2])
                    | (RaceFrame.unsigned(combined[cursor + 3]) << 8);
            if (bodyLength < 2 || bodyLength > MAX_BODY_LENGTH) {
                cursor++;
                continue;
            }

            int packetLength = bodyLength + 4;
            if (combined.length - cursor < packetLength) break;

            frames.add(RaceFrame.decode(Arrays.copyOfRange(combined, cursor, cursor + packetLength)));
            cursor += packetLength;
        }

        pending = Arrays.copyOfRange(combined, cursor, combined.length);
        if (pending.length > MAX_BODY_LENGTH + 4) {
            pending = new byte[0];
            throw new IllegalArgumentException("Unframed RACE data exceeded the safety limit");
        }
        return frames;
    }

    public int pendingByteCount() {
        return pending.length;
    }
}
