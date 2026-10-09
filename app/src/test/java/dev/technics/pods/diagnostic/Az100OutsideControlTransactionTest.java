package dev.technics.pods.diagnostic;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import dev.technics.pods.protocol.Az100ControlResponses.OutsideControl;
import static org.junit.Assert.*;

public class Az100OutsideControlTransactionTest {
    private final AtomicLong clock = new AtomicLong();
    private final ControlDeadline deadline = new ControlDeadline(clock::get, 100L);
    private final FakeTransport transport = new FakeTransport();

    @Test public void publishesOnlyMatchingReadBackAndPreservesAmbientLevel() throws Exception {
        Az100OutsideControlResult result = run(2);
        assertEquals(List.of("read", "write:2", "read"), transport.events);
        assertEquals(2, result.readBackMode());
        assertEquals(67, result.ambientLevel());
        assertTrue(result.changed());
    }

    @Test public void noOpStillRequiresReadButDoesNotWrite() throws Exception {
        assertFalse(run(1).changed());
        assertEquals(List.of("read"), transport.events);
    }

    @Test public void mismatchedReadBackRestoresAndReportsFailure() {
        transport.ignoreFirstWrite = true;
        assertThrows(IOException.class, () -> run(2));
        assertEquals(List.of("read", "write:2", "read", "write:1", "read"), transport.events);
        assertEquals(1, transport.state.mode());
    }

    @Test public void lostAckAttemptsRecoveryBecauseWriteMayHaveApplied() {
        transport.failFirstAck = true;
        assertThrows(IOException.class, () -> run(0));
        assertEquals(List.of("read", "write:0", "write:1", "read"), transport.events);
    }

    @Test public void recoveryFailureNeverTurnsIntoSuccess() {
        transport.failFirstAck = true;
        transport.failRecovery = true;
        IOException failure = assertThrows(IOException.class, () -> run(0));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of("read", "write:0", "write:1"), transport.events);
    }

    @Test public void cancellationAfterAckStopsReadBackAndRecoveryWrites() {
        transport.afterFirstWrite = deadline::cancel;
        assertThrows(IOException.class, () -> run(0));
        assertEquals(List.of("read", "write:0"), transport.events);
    }

    @Test public void deadlineAfterReadBackRejectsSuccessWithoutRecoveryWrite() {
        transport.afterSecondRead = () -> clock.set(100L);
        assertThrows(IOException.class, () -> run(2));
        assertEquals(List.of("read", "write:2", "read"), transport.events);
    }

    @Test public void cancelledQueuedRequestDoesNotTouchTransport() {
        deadline.cancel();
        assertThrows(IOException.class, () -> run(2));
        assertTrue(transport.events.isEmpty());
    }

    @Test public void invalidTargetNeverWrites() {
        assertThrows(IllegalArgumentException.class, () -> run(3));
        assertEquals(List.of("read"), transport.events);
    }

    private Az100OutsideControlResult run(int mode) throws Exception {
        return Az100OutsideControlTransaction.run(transport, deadline, mode, null);
    }

    private static final class FakeTransport implements Az100OutsideControlTransaction.Transport {
        final List<String> events = new ArrayList<>();
        OutsideControl state = new OutsideControl(1, 80, 67);
        boolean ignoreFirstWrite;
        boolean failFirstAck;
        boolean failRecovery;
        int reads;
        int writes;
        Runnable afterFirstWrite = () -> { };
        Runnable afterSecondRead = () -> { };

        @Override public OutsideControl read() {
            events.add("read");
            if (++reads == 2) afterSecondRead.run();
            return state;
        }
        @Override public void write(int mode, int noise, int ambient) throws IOException {
            events.add("write:" + mode);
            writes++;
            if (writes > 1 && failRecovery) throw new IOException("Recovery unavailable");
            if (!ignoreFirstWrite || writes != 1) state = new OutsideControl(mode, noise, ambient);
            if (writes == 1) {
                afterFirstWrite.run();
                if (failFirstAck) throw new IOException("ACK lost");
            }
        }
    }
}
