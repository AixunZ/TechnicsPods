package dev.technics.pods.hook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Address/profile tracking, independent of framework classes and callback ordering. */
final class WirelessHeadsetStatusPolicy {
    static final int STATE_DISCONNECTED = 0;
    static final int STATE_CONNECTED = 2;
    private final Map<String, Set<Integer>> profiles = new HashMap<>();
    private final Set<String> targets = new HashSet<>();
    private final Set<Integer> pendingProfiles = new HashSet<>();
    private final Map<Integer, Map<String, LiveState>> liveStates = new HashMap<>();
    private boolean ownsIcon;
    private record LiveState(int state, boolean target) {}

    static int a2dpCurrentState(java.util.List<?> args) {
        return args != null && args.size() == 3 && args.get(2) instanceof Integer value
                && value >= 0 && value <= 3 ? value : -1;
    }

    void update(String address, int profile, int state, boolean target) {
        updateSnapshot(address, profile, state, target);
        if (address != null && !address.isEmpty() && pendingProfiles.contains(profile)) {
            liveStates.computeIfAbsent(profile, key -> new HashMap<>())
                    .put(address, new LiveState(state, target));
        }
    }

    void updateSnapshot(String address, int profile, int state, boolean target) {
        if (address == null || address.isEmpty()) return;
        if (target) targets.add(address);
        else if (state == STATE_CONNECTED) targets.remove(address);
        if (state == STATE_CONNECTED) profiles.computeIfAbsent(address, key -> new HashSet<>()).add(profile);
        else if (state == STATE_DISCONNECTED) {
            Set<Integer> active = profiles.get(address);
            if (active != null) {
                active.remove(profile);
                if (active.isEmpty()) profiles.remove(address);
            }
        }
    }

    void beginReconciliation(int... requestedProfiles) {
        pendingProfiles.clear();
        liveStates.clear();
        for (int profile : requestedProfiles) pendingProfiles.add(profile);
    }

    boolean isProfilePending(int profile) { return pendingProfiles.contains(profile); }

    void completeProfile(int profile) {
        // A proxy snapshot can predate callbacks received while its Binder read was running.
        Map<String, LiveState> changes = liveStates.remove(profile);
        if (changes != null) changes.forEach((address, value) ->
                updateSnapshot(address, profile, value.state(), value.target()));
        pendingProfiles.remove(profile);
    }

    void completeReconciliation() {
        pendingProfiles.clear();
        liveStates.clear();
    }

    boolean isReconciling() { return !pendingProfiles.isEmpty(); }

    void clearProfile(int profile) {
        for (Set<Integer> active : profiles.values()) active.remove(profile);
        profiles.values().removeIf(Set::isEmpty);
    }

    void clear() { profiles.clear(); completeReconciliation(); }

    /** Null means leave stock visibility untouched. */
    Boolean visibility(boolean enabled) {
        return visibility(enabled, true);
    }

    Boolean visibility(boolean enabled, boolean mayHide) {
        boolean connectedTarget = profiles.keySet().stream().anyMatch(targets::contains);
        if (enabled && connectedTarget) { ownsIcon = true; return true; }
        if (!ownsIcon) return null;
        boolean connectedOther = profiles.keySet().stream().anyMatch(address -> !targets.contains(address));
        if (!mayHide && !connectedOther) return null;
        ownsIcon = false;
        return connectedOther ? null : Boolean.FALSE;
    }
}
