package dev.technics.pods.hook;

import java.util.LinkedHashSet;
import java.util.Set;

/** Admission and lifecycle rules for one click acknowledgement per accepted request. */
final class Az100SettingsHapticPolicy {
    private static final int RECENT_REQUEST_LIMIT = 128;
    private final Set<String> recentRequests = new LinkedHashSet<>();

    record Session(String address, long selectionRevision, long viewGeneration,
            long runtimeGeneration) { }

    synchronized boolean claim(boolean accepted, boolean userAction, String requestId,
            Session expected, Session current, boolean activeView, boolean attachedView,
            boolean hapticsEnabled) {
        if (!accepted || !userAction || requestId == null || requestId.isBlank()
                || !activeView || !attachedView || !hapticsEnabled
                || expected == null || current == null
                || expected.address() == null || expected.address().isBlank()
                || !expected.address().equalsIgnoreCase(current.address())
                || expected.selectionRevision() != current.selectionRevision()
                || expected.viewGeneration() != current.viewGeneration()
                || expected.runtimeGeneration() < 0L
                || expected.runtimeGeneration() != current.runtimeGeneration()
                || !recentRequests.add(requestId)) return false;
        if (recentRequests.size() > RECENT_REQUEST_LIMIT) {
            recentRequests.remove(recentRequests.iterator().next());
        }
        return true;
    }
}
