package dev.technics.pods.diagnostic;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.LongSupplier;

/** One-shot cancellation gate. Closing the transport also interrupts blocking socket I/O. */
public final class ControlDeadline {
    private final LongSupplier clock;
    private final long deadline;
    private Closeable transport;
    private boolean cancelled;
    private boolean completed;

    public ControlDeadline(LongSupplier clock, long timeoutMillis) {
        this.clock = clock;
        this.deadline = clock.getAsLong() + timeoutMillis;
    }

    public synchronized void attach(Closeable value) throws IOException {
        transport = value;
        check();
    }

    public synchronized void check() throws IOException {
        if (!completed && (cancelled || clock.getAsLong() >= deadline || Thread.currentThread().isInterrupted())) {
            cancel();
        }
        if (cancelled) throw new IOException("Control cancelled or timed out; state requires a new read");
    }

    public synchronized void cancel() {
        if (completed) return;
        cancelled = true;
        if (transport != null) {
            try { transport.close(); } catch (IOException ignored) { }
            transport = null;
        }
    }

    public synchronized void complete() throws IOException {
        check();
        completed = true;
    }
}
