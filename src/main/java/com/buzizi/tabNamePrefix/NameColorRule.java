package com.buzizi.tabNamePrefix;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 名称颜色规则:读取一个数值型变量,根据其值在「最小值颜色」与「最大值颜色」之间
 * 线性插值出玩家名字的颜色。
 *
 * <pre>
 * settings:
 *   name-color:
 *     enabled: true
 *     placeholder: "%player_health%"
 *     min-value: 0
 *     max-value: 20
 *     min-color: "&c"
 *     max-color: "&a"
 * </pre>
 */
final class NameColorRule {

    static final NameColorRule DISABLED =
            new NameColorRule(false, "", 0.0D, 20.0D, 0xFF5555, 0x55FF55);

    private final boolean enabled;
    private final String placeholder;
    private final double minValue;
    private final double maxValue;
    private final int minColor;
    private final int maxColor;

    private NameColorRule(boolean enabled, String placeholder, double minValue, double maxValue,
                          int minColor, int maxColor) {
        this.enabled = enabled;
        this.placeholder = placeholder;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.minColor = minColor;
        this.maxColor = maxColor;
    }

    static NameColorRule parse(FileConfiguration config) {
        boolean enabled = config.getBoolean("settings.name-color.enabled", false);
        String placeholder = config.getString("settings.name-color.placeholder", "%player_health%");
        double minValue = config.getDouble("settings.name-color.min-value", 0.0D);
        double maxValue = config.getDouble("settings.name-color.max-value", 20.0D);
        int minColor = TextUtil.parseColor(config.getString("settings.name-color.min-color", "&c"));
        int maxColor = TextUtil.parseColor(config.getString("settings.name-color.max-color", "&a"));
        if (minColor < 0) {
            minColor = 0xFF5555;
        }
        if (maxColor < 0) {
            maxColor = 0x55FF55;
        }
        return new NameColorRule(enabled, placeholder == null ? "" : placeholder,
                minValue, maxValue, minColor, maxColor);
    }

    boolean isEnabled() {
        return enabled;
    }

    String placeholder() {
        return placeholder;
    }

    /**
     * 解析变量值并计算插值位置。
     *
     * @param rawValue PlaceholderAPI 解析后的原始文本
     * @return 0.0 ~ 1.0 之间的位置;无法解析为数字时返回 -1
     */
    double ratioOf(String rawValue) {
        if (rawValue == null) {
            return -1.0D;
        }
        String cleaned = TextUtil.stripColors(rawValue).trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return -1.0D;
        }
        double value;
        try {
            value = Double.parseDouble(cleaned);
        } catch (NumberFormatException ignored) {
            return -1.0D;
        }
        if (maxValue == minValue) {
            return value >= maxValue ? 1.0D : 0.0D;
        }
        double ratio = (value - minValue) / (maxValue - minValue);
        if (ratio < 0.0D) {
            return 0.0D;
        }
        if (ratio > 1.0D) {
            return 1.0D;
        }
        return ratio;
    }

    /** 按插值位置计算 RGB 颜色 */
    int colorAt(double ratio) {
        int red = interpolate((minColor >> 16) & 0xFF, (maxColor >> 16) & 0xFF, ratio);
        int green = interpolate((minColor >> 8) & 0xFF, (maxColor >> 8) & 0xFF, ratio);
        int blue = interpolate(minColor & 0xFF, maxColor & 0xFF, ratio);
        return (red << 16) | (green << 8) | blue;
    }

    private static int interpolate(int from, int to, double ratio) {
        int value = (int) Math.round(from + (to - from) * ratio);
        if (value < 0) {
            return 0;
        }
        return value > 255 ? 255 : value;
    }

    String describe() {
        if (!enabled) {
            return "关闭";
        }
        return placeholder + " (" + number(minValue) + "~" + number(maxValue)
                + ", " + hex(minColor) + "→" + hex(maxColor) + ")";
    }

    private static String number(double value) {
        if (!Double.isInfinite(value) && !Double.isNaN(value) && value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
