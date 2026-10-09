package dev.technics.pods.diagnostic;

import java.io.IOException;

import dev.technics.pods.protocol.Az100ControlFrames;
import dev.technics.pods.protocol.Az100ControlResponses.OutsideControl;

/** Transport-independent read/write/ACK/read-back transaction. */
public final class Az100OutsideControlTransaction {
    public interface Transport {
        OutsideControl read() throws Exception;
        /** Must validate the device ACK before returning. */
        void write(int mode, int noise, int ambient) throws Exception;
    }

    private Az100OutsideControlTransaction() { }

    public static Az100OutsideControlResult run(Transport transport, ControlDeadline deadline,
            int mode, Integer noise) throws Exception {
        deadline.check();
        OutsideControl original = transport.read();
        int targetNoise = noise == null ? original.noiseLevel() : noise;
        Az100ControlFrames.setOutsideControl(mode, targetNoise, original.ambientLevel());
        deadline.check();
        if (original.mode() == mode && original.noiseLevel() == targetNoise) {
            return new Az100OutsideControlResult(0L, mode, targetNoise, original.ambientLevel(),
                    original.mode(), original.noiseLevel(), false);
        }
        try {
            // A missing ACK does not prove that the command was not applied.
            transport.write(mode, targetNoise, original.ambientLevel());
            deadline.check();
            OutsideControl actual = transport.read();
            deadline.check();
            requireState(actual, mode, targetNoise, original.ambientLevel());
            return new Az100OutsideControlResult(0L, mode, targetNoise, actual.ambientLevel(),
                    actual.mode(), actual.noiseLevel(), true);
        } catch (Exception failure) {
            // Cancellation/deadline means stop sending, including recovery writes.
            deadline.check();
            try {
                transport.write(original.mode(), original.noiseLevel(), original.ambientLevel());
                deadline.check();
                OutsideControl restored = transport.read();
                deadline.check();
                requireState(restored, original.mode(), original.noiseLevel(), original.ambientLevel());
            } catch (Exception recovery) {
                failure.addSuppressed(recovery);
            }
            throw failure;
        }
    }

    private static void requireState(OutsideControl actual, int mode, int noise, int ambient)
            throws IOException {
        if (actual.mode() != mode || actual.noiseLevel() != noise || actual.ambientLevel() != ambient) {
            throw new IOException("Control read-back mismatch");
        }
    }
}
