package dev.technics.pods.diagnostic;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class ControlDeadlineTest {
    @Test public void cancellationBeforeSocketCreationClosesLateSocket() {
        ControlDeadline deadline = new ControlDeadline(() -> 0L, 100L);
        AtomicInteger closed = new AtomicInteger();
        deadline.cancel();
        assertThrows(IOException.class, () -> deadline.attach(closed::incrementAndGet));
        assertEquals(1, closed.get());
        assertThrows(IOException.class, deadline::complete);
    }

    @Test public void deadlineClosesTransportAndRejectsLateSuccess() throws Exception {
        AtomicLong clock = new AtomicLong(50L);
        ControlDeadline deadline = new ControlDeadline(clock::get, 100L);
        AtomicInteger closed = new AtomicInteger();
        deadline.attach(closed::incrementAndGet);
        clock.set(150L);
        assertThrows(IOException.class, deadline::complete);
        deadline.cancel();
        assertEquals(1, closed.get());
    }

    @Test public void successfulCompletionWinsAgainstLateTimer() throws Exception {
        ControlDeadline deadline = new ControlDeadline(() -> 0L, 100L);
        AtomicInteger closed = new AtomicInteger();
        deadline.attach(closed::incrementAndGet);
        deadline.complete();
        deadline.cancel();
        assertEquals(0, closed.get());
    }

    @Test public void interruptedWorkerCannotWrite() {
        ControlDeadline deadline = new ControlDeadline(() -> 0L, 100L);
        Thread.currentThread().interrupt();
        try { assertThrows(IOException.class, deadline::check); }
        finally { Thread.interrupted(); }
    }
}
