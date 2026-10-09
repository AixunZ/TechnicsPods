package dev.technics.pods.protocol;

/**
 * Keeps last-known battery telemetry separate from the live RFCOMM connection state.
 * A disconnect never fabricates a zero-percent value and never discards the cache.
 */
public final class Az100TelemetryTracker {
    private Integer leftBatteryPercent;
    private Integer rightBatteryPercent;
    private Integer cradleBatteryPercent;
    private boolean connected;
    private boolean partnerAvailable;

    public void onConnected() {
        connected = true;
    }

    public void onBattery(Az100BatteryUpdate update) {
        if (update.side() == Az100BatteryUpdate.LEFT) {
            leftBatteryPercent = update.percent();
        } else {
            rightBatteryPercent = update.percent();
        }
    }

    public void onIdleState(Az100StateSnapshot state) {
        if (state.cradleBatteryPercent() != null) {
            cradleBatteryPercent = state.cradleBatteryPercent();
        }
    }

    public void onRelayState(Az100RelayState state) {
        partnerAvailable = state.partnerAvailable();
    }

    public void onDisconnected() {
        connected = false;
        partnerAvailable = false;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                leftBatteryPercent,
                rightBatteryPercent,
                cradleBatteryPercent,
                connected,
                partnerAvailable
        );
    }

    public record Snapshot(
            Integer leftBatteryPercent,
            Integer rightBatteryPercent,
            Integer cradleBatteryPercent,
            boolean connected,
            boolean partnerAvailable
    ) {}
}
