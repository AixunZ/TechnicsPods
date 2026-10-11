package dev.technics.pods.diagnostic;

import java.util.List;

import dev.technics.pods.protocol.Az100DeviceColor;

public record Az100DiagnosticResult(
        long elapsedMillis,
        Integer leftBatteryPercent,
        Integer rightBatteryPercent,
        Integer cradleBatteryPercent,
        Integer outsideControlMode,
        Integer noiseCancellingLevel,
        Integer ambientLevel,
        Integer ambientMode,
        Boolean adaptiveAncEnabled,
        Boolean partnerAvailable,
        String leftFirmware,
        String rightFirmware,
        List<String> warnings,
        Az100DeviceColor deviceColor
) {
    public Az100DiagnosticResult(long elapsedMillis, Integer leftBatteryPercent,
                                  Integer rightBatteryPercent, Integer cradleBatteryPercent,
                                  Integer outsideControlMode, Integer noiseCancellingLevel,
                                  Integer ambientLevel, Integer ambientMode,
                                  Boolean adaptiveAncEnabled, Boolean partnerAvailable,
                                  String leftFirmware, String rightFirmware,
                                  List<String> warnings) {
        this(elapsedMillis, leftBatteryPercent, rightBatteryPercent, cradleBatteryPercent,
                outsideControlMode, noiseCancellingLevel, ambientLevel, ambientMode,
                adaptiveAncEnabled, partnerAvailable, leftFirmware, rightFirmware, warnings,
                Az100DeviceColor.UNKNOWN);
    }

    public Az100DiagnosticResult {
        warnings = List.copyOf(warnings);
        deviceColor = deviceColor == null ? Az100DeviceColor.UNKNOWN : deviceColor;
    }

    public String toDisplayText() {
        StringBuilder text = new StringBuilder("只读诊断成功\n");
        text.append("耗时: ").append(elapsedMillis).append(" ms\n");
        text.append("左耳电量: ").append(value(leftBatteryPercent, "%")).append('\n');
        text.append("右耳电量: ").append(value(rightBatteryPercent, "%")).append('\n');
        text.append("充电盒电量: ").append(value(cradleBatteryPercent, "%")).append('\n');
        text.append("外音模式: ").append(outsideMode(outsideControlMode)).append('\n');
        text.append("降噪强度: ").append(value(noiseCancellingLevel, "")).append('\n');
        text.append("环境声记忆值: ").append(value(ambientLevel, "")).append('\n');
        text.append("环境声子模式: ").append(ambientMode(ambientMode)).append('\n');
        text.append("自适应降噪: ").append(booleanValue(adaptiveAncEnabled)).append('\n');
        text.append("另一只耳机: ").append(booleanValue(partnerAvailable)).append('\n');
        text.append("左耳固件: ").append(textValue(leftFirmware)).append('\n');
        text.append("右耳固件: ").append(textValue(rightFirmware)).append('\n');
        text.append("耳机配色: ").append(deviceColor == Az100DeviceColor.UNKNOWN
                ? "未识别" : deviceColor.artKey());
        if (!warnings.isEmpty()) {
            text.append("\n未完成项目: ").append(String.join("、", warnings));
        }
        return text.toString();
    }

    private static String value(Integer value, String suffix) {
        return value == null ? "未读取" : value + suffix;
    }

    private static String textValue(String value) {
        return value == null || value.isEmpty() ? "未读取" : value;
    }

    private static String booleanValue(Boolean value) {
        if (value == null) return "未读取";
        return value ? "是/开启" : "否/关闭";
    }

    private static String outsideMode(Integer value) {
        if (value == null) return "未读取";
        return switch (value) {
            case 0 -> "关闭";
            case 1 -> "降噪";
            case 2 -> "环境声";
            default -> "未知(" + value + ")";
        };
    }

    private static String ambientMode(Integer value) {
        if (value == null) return "未读取";
        return switch (value) {
            case 0 -> "通透";
            case 1 -> "注意";
            default -> "未知(" + value + ")";
        };
    }
}
