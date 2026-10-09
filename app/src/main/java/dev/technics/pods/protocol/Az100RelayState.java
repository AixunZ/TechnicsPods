package dev.technics.pods.protocol;

/** Availability of the peer earbud in the Airoha TWS relay topology. */
public record Az100RelayState(boolean partnerAvailable, Integer partnerId) {
    private static final int PARTNER_DESTINATION_TYPE = 5;

    public static Az100RelayState from(RaceFrame frame) {
        if (frame.commandId() != PanasonicCommand.RELAY_GET_AVAILABLE_DESTINATION
                || (frame.type() != RaceFrame.TYPE_RESPONSE
                && frame.type() != RaceFrame.TYPE_INDICATION)) {
            throw new IllegalArgumentException("Not an AZ100 relay-destination frame");
        }

        byte[] payload = frame.payload();
        if ((payload.length & 1) != 0) {
            throw new IllegalArgumentException("Relay-destination payload is truncated");
        }
        for (int cursor = 0; cursor < payload.length; cursor += 2) {
            if (RaceFrame.unsigned(payload[cursor]) == PARTNER_DESTINATION_TYPE) {
                return new Az100RelayState(true, RaceFrame.unsigned(payload[cursor + 1]));
            }
        }
        return new Az100RelayState(false, null);
    }
}
