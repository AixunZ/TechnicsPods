package dev.technics.pods.hook;

import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import dev.technics.pods.ModulePreferences;
import dev.technics.pods.capture.CaptureContract;
import dev.technics.pods.diagnostic.Az100DeviceMatcher;
import io.github.libxposed.api.XposedModule;

/** Cached selected-device guard. Hook getter paths never read remote preferences. */
final class Az100HookDeviceIdentity {
    private record Selection(String address, long revision) {}
    private volatile Selection selection = new Selection("", 0L);
    private final SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;

    Az100HookDeviceIdentity(XposedModule module, Runnable changed) {
        SharedPreferences loaded = null;
        try { loaded = module.getRemotePreferences(CaptureContract.PREFS_NAME); }
        catch (Throwable ignored) {}
        preferences = loaded;
        listener = (values, key) -> {
            if (ModulePreferences.SELECTED_ADDRESS.equals(key)) refresh(values, changed);
        };
        if (preferences != null) {
            refresh(preferences, null);
            try { preferences.registerOnSharedPreferenceChangeListener(listener); }
            catch (Throwable ignored) {}
        }
    }

    Az100HookDeviceIdentity(String selectedAddress) {
        preferences = null;
        listener = null;
        selection = new Selection(selectedAddress == null ? "" : selectedAddress, 0L);
    }

    private void refresh(SharedPreferences values, Runnable changed) {
        try {
            String address = values.getString(ModulePreferences.SELECTED_ADDRESS, "");
            String raw = address == null ? "" : address.trim();
            synchronized (this) {
                if (raw.equalsIgnoreCase(selection.address())) return;
                selection = new Selection(raw, selection.revision() + 1L);
            }
            if (changed != null) changed.run();
        } catch (Throwable ignored) {}
    }

    long revision() { return selection.revision(); }
    boolean hasSelection() { return !selection.address().isEmpty(); }

    boolean matches(BluetoothDevice device) {
        if (device == null) return false;
        try {
            String address = device.getAddress();
            // A saved address remains authoritative when a user renames the headset.
            return hasSelection() ? matches(address, null) : matches(address, device.getName());
        } catch (SecurityException denied) { return false; }
        catch (RuntimeException ignored) { return false; }
    }

    boolean matches(String address, String name) {
        return matches(selection.address(), address, name);
    }

    boolean permitsKnownAddress(String address) {
        String normalized = normalize(address);
        return normalized != null && (!hasSelection() || matches(address, null));
    }

    static boolean matches(String selected, String address, String name) {
        if (selected != null && !selected.trim().isEmpty()) {
            String selectedMac = normalize(selected);
            return selectedMac != null && selectedMac.equals(normalize(address));
        }
        return Az100DeviceMatcher.matchesName(name);
    }

    static String normalize(String address) {
        if (address == null) return null;
        String value = address.trim();
        return value.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
                ? value.toUpperCase(Locale.ROOT) : null;
    }

    /** Conflicting MACs are rejected, even if one belongs to the selected headset. */
    boolean matchesOwner(Object owner) {
        return targetAddress(owner) != null;
    }

    boolean matchesOwnerOrName(Object owner, String fallbackName) {
        Map<String, Boolean> addresses = new HashMap<>();
        collect(owner, 0, Collections.newSetFromMap(new IdentityHashMap<>()), addresses);
        if (addresses.size() == 1) return addresses.values().iterator().next();
        return addresses.isEmpty() && !hasSelection() && Az100DeviceMatcher.matchesName(fallbackName);
    }

    String targetAddress(Object owner) {
        Map<String, Boolean> addresses = new HashMap<>();
        collect(owner, 0, Collections.newSetFromMap(new IdentityHashMap<>()), addresses);
        if (addresses.size() != 1) return null;
        Map.Entry<String, Boolean> only = addresses.entrySet().iterator().next();
        return only.getValue() ? only.getKey() : null;
    }

    private void collect(Object owner, int depth, Set<Object> seen, Map<String, Boolean> addresses) {
        if (owner == null || depth > 2 || !seen.add(owner)) return;
        if (owner instanceof BluetoothDevice device) {
            try { add(addresses, device.getAddress(), matches(device)); }
            catch (RuntimeException ignored) {}
            return;
        }
        String name = property(owner, new String[]{"getName", "getDeviceName", "getDevicesName", "getDeviceModel", "getServiceId"},
                new String[]{"name", "deviceName", "devicesName", "serviceId"});
        for (String value : new String[]{
                property(owner, new String[]{"getAddress"}, new String[]{"address"}),
                property(owner, new String[]{"getBtMac", "getDeviceMac", "getDevicesMac"}, new String[]{"btMac", "deviceMac", "devicesMac"}),
                property(owner, new String[]{"getMac", "getDeviceAddress"}, new String[]{"mac", "deviceAddress"})}) {
            add(addresses, value, matches(value, name));
        }
        for (Class<?> type = owner.getClass(); type != null && type != Object.class
                && !"android.view.View".equals(type.getName()); type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String fieldName = field.getName().toLowerCase(Locale.ROOT);
                if (!BluetoothDevice.class.isAssignableFrom(field.getType())
                        && !fieldName.contains("device") && !fieldName.contains("headsetinfo")) continue;
                try { field.setAccessible(true); collect(field.get(owner), depth + 1, seen, addresses); }
                catch (Throwable ignored) {}
            }
        }
    }

    private static void add(Map<String, Boolean> values, String address, boolean target) {
        String mac = normalize(address);
        if (mac != null) values.merge(mac, target, (first, second) -> first && second);
    }

    private static String property(Object owner, String[] getters, String[] fields) {
        for (String getter : getters) {
            try {
                Method method = owner.getClass().getMethod(getter);
                Object value = method.invoke(owner);
                if (value instanceof String text && !text.isBlank()) return text;
            } catch (Throwable ignored) {}
        }
        for (String name : fields) for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name); field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof String text && !text.isBlank()) return text;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
