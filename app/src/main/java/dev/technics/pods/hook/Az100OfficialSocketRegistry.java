package dev.technics.pods.hook;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.ArrayList;

/** Pure identity-keyed lifecycle bookkeeping, including close/connect races. */
final class Az100OfficialSocketRegistry<T> {
    private final IdentityHashMap<Object, Session<T>> sessions = new IdentityHashMap<>();
    record Session<T>(Object socket, T token, boolean connected) {}

    synchronized Session<T> get(Object socket) { return sessions.get(socket); }

    synchronized Session<T> begin(Object socket, T token) {
        Session<T> existing = sessions.get(socket);
        if (existing != null) return existing;
        Session<T> created = new Session<>(socket, token, false);
        sessions.put(socket, created);
        return created;
    }

    synchronized Session<T> connected(Object socket, T token) {
        Session<T> current = sessions.get(socket);
        if (current == null || current.token() != token) return null;
        Session<T> connected = new Session<>(socket, token, true);
        sessions.put(socket, connected);
        return connected;
    }

    synchronized Session<T> remove(Object socket, T token) {
        Session<T> current = sessions.get(socket);
        if (current == null || (token != null && current.token() != token)) return null;
        sessions.remove(socket);
        return current;
    }

    synchronized List<Session<T>> snapshot() { return new ArrayList<>(sessions.values()); }

    synchronized List<Session<T>> removeDisconnected(java.util.function.Predicate<Object> disconnected) {
        List<Session<T>> removed = new ArrayList<>();
        java.util.Iterator<Session<T>> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            Session<T> session = iterator.next();
            if (session.connected() && disconnected.test(session.socket())) {
                removed.add(session);
                iterator.remove();
            }
        }
        return removed;
    }
}
