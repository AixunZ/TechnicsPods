package dev.technics.pods.state;

import dev.technics.pods.diagnostic.Az100DiagnosticResult;
import dev.technics.pods.diagnostic.Az100OutsideControlResult;
import dev.technics.pods.protocol.Az100DeviceColor;

/** Pure state transitions for live samples and last-known AZ100 values. */
public final class Az100StateCache {
    private Az100StateCache() {}

    public record Earbud(
            Integer batteryPercent,
            long batteryUpdatedAtMillis,
            String firmware,
            long firmwareUpdatedAtMillis
    ) {}

    public record Controls(
            Integer outsideMode,
            Integer noiseCancellingLevel,
            Integer ambientLevel,
            Integer ambientMode,
            Boolean adaptiveAncEnabled,
            long updatedAtMillis,
            long outsideModeUpdatedAtMillis,
            long noiseCancellingLevelUpdatedAtMillis,
            long ambientLevelUpdatedAtMillis,
            long ambientModeUpdatedAtMillis,
            long adaptiveAncUpdatedAtMillis
    ) {
        /** Compatibility constructor for callers that only have one observation timestamp. */
        public Controls(Integer outsideMode, Integer noiseCancellingLevel, Integer ambientLevel,
                Integer ambientMode, Boolean adaptiveAncEnabled, long updatedAtMillis) {
            this(outsideMode, noiseCancellingLevel, ambientLevel, ambientMode, adaptiveAncEnabled,
                    updatedAtMillis, updatedAtMillis, updatedAtMillis, updatedAtMillis,
                    updatedAtMillis, updatedAtMillis);
        }
    }

    public record State(
            Earbud left,
            Earbud right,
            Integer cradleBatteryPercent,
            long cradleUpdatedAtMillis,
            Controls controls,
            Boolean partnerAvailable,
            long partnerUpdatedAtMillis,
            boolean monitoring,
            boolean sampling,
            boolean reachable,
            int consecutiveFailures,
            long lastAttemptAtMillis,
            long lastSuccessAtMillis,
            String lastError,
            String incompleteFields,
            Az100DeviceColor deviceColor,
            long deviceColorUpdatedAtMillis
    ) {
        /** Source compatibility for callers that predate color telemetry. */
        public State(Earbud left, Earbud right, Integer cradleBatteryPercent,
                long cradleUpdatedAtMillis, Controls controls, Boolean partnerAvailable,
                long partnerUpdatedAtMillis, boolean monitoring, boolean sampling,
                boolean reachable, int consecutiveFailures, long lastAttemptAtMillis,
                long lastSuccessAtMillis, String lastError, String incompleteFields) {
            this(left, right, cradleBatteryPercent, cradleUpdatedAtMillis, controls,
                    partnerAvailable, partnerUpdatedAtMillis, monitoring, sampling, reachable,
                    consecutiveFailures, lastAttemptAtMillis, lastSuccessAtMillis, lastError,
                    incompleteFields, Az100DeviceColor.UNKNOWN, 0L);
        }

        public State {
            deviceColor = deviceColor == null ? Az100DeviceColor.UNKNOWN : deviceColor;
        }

        public static State empty() {
            Earbud emptyEarbud = new Earbud(null, 0L, null, 0L);
            Controls emptyControls = new Controls(null, null, null, null, null, 0L);
            return new State(
                    emptyEarbud,
                    emptyEarbud,
                    null,
                    0L,
                    emptyControls,
                    null,
                    0L,
                    false,
                    false,
                    false,
                    0,
                    0L,
                    0L,
                    null,
                    null,
                    Az100DeviceColor.UNKNOWN,
                    0L
            );
        }
    }

    public static State monitoringStarted(State previous) {
        return copyRuntime(previous, true, false, previous.reachable(),
                previous.consecutiveFailures(), previous.lastAttemptAtMillis(),
                previous.lastSuccessAtMillis(), previous.lastError(), previous.incompleteFields());
    }

    public static State samplingStarted(State previous, long nowMillis) {
        return copyRuntime(previous, true, true, previous.reachable(),
                previous.consecutiveFailures(), nowMillis, previous.lastSuccessAtMillis(),
                previous.lastError(), previous.incompleteFields());
    }

    public static State sampleSucceeded(
            State previous,
            Az100DiagnosticResult result,
            long nowMillis
    ) {
        return sampleSucceeded(previous, result, nowMillis, nowMillis);
    }

    public static State sampleSucceeded(State previous, Az100DiagnosticResult result,
            long startedAtMillis, long nowMillis) {
        Earbud left = mergeEarbud(
                previous.left(),
                result.leftBatteryPercent(),
                result.leftFirmware(),
                nowMillis
        );
        Earbud right = mergeEarbud(
                previous.right(),
                result.rightBatteryPercent(),
                result.rightFirmware(),
                nowMillis
        );
        Integer cradle = result.cradleBatteryPercent() == null
                ? previous.cradleBatteryPercent()
                : result.cradleBatteryPercent();
        long cradleAt = result.cradleBatteryPercent() == null
                ? previous.cradleUpdatedAtMillis()
                : nowMillis;
        boolean controlsNewer = previous.controls().updatedAtMillis() > startedAtMillis;
        Controls controls = controlsNewer
                ? previous.controls() : mergeControls(previous.controls(), result, nowMillis);
        Boolean partner = result.partnerAvailable() == null
                ? previous.partnerAvailable()
                : result.partnerAvailable();
        long partnerAt = result.partnerAvailable() == null
                ? previous.partnerUpdatedAtMillis()
                : nowMillis;
        Az100DeviceColor color = result.deviceColor() == null
                || result.deviceColor() == Az100DeviceColor.UNKNOWN
                ? previous.deviceColor() : result.deviceColor();
        long colorAt = result.deviceColor() == null
                || result.deviceColor() == Az100DeviceColor.UNKNOWN
                ? previous.deviceColorUpdatedAtMillis() : nowMillis;
        String incomplete = result.warnings().isEmpty()
                ? null
                : String.join("、", result.warnings());

        return new State(
                left,
                right,
                cradle,
                cradleAt,
                controls,
                partner,
                partnerAt,
                previous.monitoring(),
                false,
                true,
                0,
                previous.lastAttemptAtMillis(),
                nowMillis,
                controlsNewer ? previous.lastError() : null,
                incomplete,
                color,
                colorAt
        );
    }

    public static State sampleFailed(State previous, String error, long nowMillis) {
        return sampleFailed(previous, error, nowMillis, nowMillis);
    }

    public static State sampleFailed(State previous, String error, long startedAt, long nowMillis) {
        if (previous.controls().updatedAtMillis() > startedAt) {
            return copyRuntime(previous, previous.monitoring(), false, previous.reachable(),
                    previous.consecutiveFailures(), previous.lastAttemptAtMillis(),
                    previous.lastSuccessAtMillis(), previous.lastError(), previous.incompleteFields());
        }
        return copyRuntime(
                previous,
                previous.monitoring(),
                false,
                false,
                previous.consecutiveFailures() + 1,
                nowMillis,
                previous.lastSuccessAtMillis(),
                error,
                previous.incompleteFields()
        );
    }

    public static State controlSucceeded(
            State previous,
            Az100OutsideControlResult result,
            long nowMillis
    ) {
        Controls controls = new Controls(
                result.readBackMode(),
                result.readBackNoiseLevel(),
                result.ambientLevel(),
                previous.controls().ambientMode(),
                previous.controls().adaptiveAncEnabled(),
                nowMillis,
                nowMillis,
                nowMillis,
                nowMillis,
                previous.controls().ambientModeUpdatedAtMillis(),
                previous.controls().adaptiveAncUpdatedAtMillis()
        );
        return new State(
                previous.left(),
                previous.right(),
                previous.cradleBatteryPercent(),
                previous.cradleUpdatedAtMillis(),
                controls,
                previous.partnerAvailable(),
                previous.partnerUpdatedAtMillis(),
                previous.monitoring(),
                previous.sampling(),
                true,
                0,
                previous.lastAttemptAtMillis(),
                previous.lastSuccessAtMillis(),
                null,
                null,
                previous.deviceColor(),
                previous.deviceColorUpdatedAtMillis()
        );
    }

    public static State controlFailed(State previous, String error, long nowMillis) {
        // Keep battery telemetry; the actual control state after a failed write is unknown.
        return new State(previous.left(), previous.right(), previous.cradleBatteryPercent(),
                previous.cradleUpdatedAtMillis(), new Controls(null, null, null, null, null,
                        nowMillis, nowMillis, nowMillis, nowMillis, nowMillis, nowMillis),
                previous.partnerAvailable(), previous.partnerUpdatedAtMillis(),
                previous.monitoring(), previous.sampling(), previous.reachable(),
                previous.consecutiveFailures(), previous.lastAttemptAtMillis(),
                previous.lastSuccessAtMillis(), error, previous.incompleteFields(),
                previous.deviceColor(), previous.deviceColorUpdatedAtMillis());
    }

    public static State monitoringStopped(State previous) {
        return copyRuntime(
                previous,
                false,
                false,
                false,
                previous.consecutiveFailures(),
                previous.lastAttemptAtMillis(),
                previous.lastSuccessAtMillis(),
                previous.lastError(),
                previous.incompleteFields()
        );
    }

    private static Earbud mergeEarbud(
            Earbud previous,
            Integer battery,
            String firmware,
            long nowMillis
    ) {
        return new Earbud(
                battery == null ? previous.batteryPercent() : battery,
                battery == null ? previous.batteryUpdatedAtMillis() : nowMillis,
                firmware == null || firmware.isEmpty() ? previous.firmware() : firmware,
                firmware == null || firmware.isEmpty()
                        ? previous.firmwareUpdatedAtMillis()
                        : nowMillis
        );
    }

    private static Controls mergeControls(
            Controls previous,
            Az100DiagnosticResult result,
            long nowMillis
    ) {
        // Each control field gets the timestamp of the observation that supplied it.
        long outsideAt = result.outsideControlMode() == null
                ? previous.outsideModeUpdatedAtMillis() : nowMillis;
        long noiseAt = result.noiseCancellingLevel() == null
                ? previous.noiseCancellingLevelUpdatedAtMillis() : nowMillis;
        long ambientAt = result.ambientLevel() == null
                ? previous.ambientLevelUpdatedAtMillis() : nowMillis;
        long ambientModeAt = result.ambientMode() == null
                ? previous.ambientModeUpdatedAtMillis() : nowMillis;
        long adaptiveAt = result.adaptiveAncEnabled() == null
                ? previous.adaptiveAncUpdatedAtMillis() : nowMillis;
        long updatedAt = Math.max(Math.max(outsideAt, noiseAt),
                Math.max(Math.max(ambientAt, ambientModeAt), adaptiveAt));
        return new Controls(
                coalesce(result.outsideControlMode(), previous.outsideMode()),
                coalesce(result.noiseCancellingLevel(), previous.noiseCancellingLevel()),
                coalesce(result.ambientLevel(), previous.ambientLevel()),
                coalesce(result.ambientMode(), previous.ambientMode()),
                coalesce(result.adaptiveAncEnabled(), previous.adaptiveAncEnabled()),
                updatedAt,
                outsideAt,
                noiseAt,
                ambientAt,
                ambientModeAt,
                adaptiveAt
        );
    }

    private static <T> T coalesce(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static State copyRuntime(
            State previous,
            boolean monitoring,
            boolean sampling,
            boolean reachable,
            int failures,
            long attemptAt,
            long successAt,
            String error,
            String incomplete
    ) {
        return new State(
                previous.left(),
                previous.right(),
                previous.cradleBatteryPercent(),
                previous.cradleUpdatedAtMillis(),
                previous.controls(),
                previous.partnerAvailable(),
                previous.partnerUpdatedAtMillis(),
                monitoring,
                sampling,
                reachable,
                failures,
                attemptAt,
                successAt,
                error,
                incomplete,
                previous.deviceColor(),
                previous.deviceColorUpdatedAtMillis()
        );
    }
}
