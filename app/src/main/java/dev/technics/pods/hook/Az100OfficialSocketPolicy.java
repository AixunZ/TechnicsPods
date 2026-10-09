package dev.technics.pods.hook;

import java.util.UUID;
import dev.technics.pods.diagnostic.Az100DeviceMatcher;

/** Only the observed Panasonic RFCOMM service is eligible for handover. */
final class Az100OfficialSocketPolicy {
    static final UUID SERVICE = UUID.fromString("00000000-0000-0000-0099-aabbccddeeff");
    private Az100OfficialSocketPolicy() {}

    static boolean verified(String deviceName, int socketType, UUID service) {
        return Az100DeviceMatcher.matchesName(deviceName) && socketType == 1 && SERVICE.equals(service);
    }

    static boolean verified(String selectedAddress, String address, String deviceName,
            int socketType, UUID service) {
        if (socketType != 1 || !SERVICE.equals(service)) return false;
        if (selectedAddress != null && !selectedAddress.isBlank()) {
            return selectedAddress.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
                    && selectedAddress.equalsIgnoreCase(address);
        }
        return Az100DeviceMatcher.matchesName(deviceName);
    }

    static UUID serviceFromFactory(String method, Object argument) {
        if (("createRfcommSocketToServiceRecord".equals(method)
                || "createInsecureRfcommSocketToServiceRecord".equals(method))
                && argument instanceof UUID uuid) return uuid;
        // Direct channel factories do not prove the service UUID. No channel number was
        // verified in captures, so even a valid RFCOMM channel cannot claim ownership.
        return null;
    }
}
