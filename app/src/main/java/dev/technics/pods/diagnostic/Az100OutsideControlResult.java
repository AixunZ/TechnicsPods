package dev.technics.pods.diagnostic;

/** Read-back result for one user-selected outside-control operation. */
public record Az100OutsideControlResult(
        long elapsedMillis,
        int requestedMode,
        int requestedNoiseLevel,
        int ambientLevel,
        int readBackMode,
        int readBackNoiseLevel,
        boolean changed
) {
    public Az100OutsideControlResult {
        if (requestedMode < 0 || requestedMode > 2 || requestedMode != readBackMode
                || requestedNoiseLevel < 1 || requestedNoiseLevel > 100
                || requestedNoiseLevel != readBackNoiseLevel || ambientLevel < 1 || ambientLevel > 100) {
            throw new IllegalArgumentException("Outside-control result is not confirmed or is out of range");
        }
    }

    public String toDisplayText() {
        String prefix = changed ? "外音控制成功" : "目标状态已是当前值，未发送写入";
        return prefix + "\n"
                + "耗时: " + elapsedMillis + " ms\n"
                + "回读模式: " + mode(readBackMode) + "\n"
                + "回读降噪: " + readBackNoiseLevel + "\n"
                + "环境声值: " + ambientLevel;
    }

    private static String mode(int value) {
        return switch (value) {
            case 0 -> "关闭";
            case 1 -> "降噪";
            case 2 -> "环境声";
            default -> "未知(" + value + ")";
        };
    }
}
