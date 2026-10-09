package dev.technics.pods.state;

import java.util.HashSet;
import java.util.Set;

/** Pure ownership and generation rules shared by transport and IPC. */
public final class Az100SessionCoordinator {
    private final Set<Object> officialSessions = new HashSet<>();
    private long generation;
    private long activeTicket;
    private boolean recoveringOfficial;

    public synchronized long begin() {
        if (isOfficialBusy() || activeTicket != 0) return 0;
        activeTicket = ++generation;
        return activeTicket;
    }

    public synchronized boolean isCurrent(long ticket) {
        return ticket != 0 && activeTicket == ticket && ticket == generation
                && !isOfficialBusy();
    }

    public synchronized void finish(long ticket) {
        if (activeTicket == ticket) activeTicket = 0;
    }

    public synchronized void cancel() {
        generation++;
        // Keep the slot occupied until the old worker has closed its socket.
    }

    public synchronized void officialBegin(Object token) {
        if (token == null) throw new IllegalArgumentException("Missing official session token");
        if (officialSessions.add(token)) generation++;
    }

    public synchronized void officialRelease(Object token) {
        if (officialSessions.remove(token)) generation++;
    }

    public synchronized void recoverOfficial(boolean busy) {
        recoveringOfficial = busy;
        if (busy) generation++;
    }

    public synchronized void recoveryCompleted() {
        recoveringOfficial = false;
    }

    public synchronized boolean recoveryPending() { return recoveringOfficial; }

    public synchronized boolean isOfficialBusy() {
        return recoveringOfficial || !officialSessions.isEmpty();
    }

    public synchronized boolean isRunning() { return activeTicket != 0; }

    public synchronized long generation() { return generation; }
}
