package com.buzizi.tabNamePrefix;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本工具: 把包含 & 颜色代码(含 &#RRGGBB 十六进制)的字符串转换为 § 格式。
 * <p>
 * 1.21.x 起 Bukkit 的 {@code ChatColor} 已整体标记为废弃,这里不再依赖它,
 * 转换规则与旧版 {@code ChatColor.translateAlternateColorCodes('&', ...)} 完全一致。
 */
public final class TextUtil {

    /** Minecraft 颜色代码前缀字符(§) */
    private static final char COLOR_CHAR = '\u00A7';

    /** &#RRGGBB 形式的十六进制颜色 */
    private static final Pattern HEX_SHORT = Pattern.compile("&#([0-9a-fA-F]{6})");
    /** 传统 &x&R&R&G&G&B&B 形式的十六进制颜色 */
    private static final Pattern HEX_LEGACY = Pattern.compile("&x(&[0-9a-fA-F]){6}");

    private TextUtil() {
    }

    /**
     * 将 & 颜色代码转换为 Minecraft 可识别的 § 格式。
     * 支持: &0-&9 &a-&f &k &l &m &n &o &r, &#RRGGBB, &x&R&R&G&G&B&B
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        text = translateHexShort(text);
        text = translateHexLegacy(text);
        return translateAmpersand(text);
    }

    /** 与旧版 ChatColor#isColorChar 保持一致的合法颜色代码字符判断 */
    private static boolean isColorChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'k' && c <= 'o')
                || (c >= 'A' && c <= 'F')
                || (c >= 'K' && c <= 'O')
                || c == 'r' || c == 'R';
    }

    /** 把 "&a" 这类合法颜色代码中的 & 替换为 §,其余 & 原样保留(代码字符统一转小写,与旧版 ChatColor 行为一致) */
    private static String translateAmpersand(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && isColorChar(chars[i + 1])) {
                chars[i] = COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private static String translateHexShort(String text) {
        Matcher matcher = HEX_SHORT.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuffer buffer = new StringBuffer();
        do {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) {
                replacement.append('§').append(hex.charAt(i));
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        } while (matcher.find());
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String translateHexLegacy(String text) {
        Matcher matcher = HEX_LEGACY.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        StringBuffer buffer = new StringBuffer();
        do {
            String segment = matcher.group().replace('&', COLOR_CHAR);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(segment));
        } while (matcher.find());
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 去掉文本中的颜色代码(同时兼容 & 与 § 写法,含十六进制),返回纯文本。
     * <p>用于排序时比较前缀 / 后缀的实际内容。</p>
     */
    public static String stripColors(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String colored = colorize(text);
        StringBuilder builder = new StringBuilder(colored.length());
        int i = 0;
        while (i < colored.length()) {
            char c = colored.charAt(i);
            if (c == COLOR_CHAR && i + 1 < colored.length()) {
                char code = colored.charAt(i + 1);
                if (code == 'x' || code == 'X') {
                    // §x§R§R§G§G§B§B 共 14 个字符
                    i += 14;
                } else {
                    i += 2;
                }
                continue;
            }
            builder.append(c);
            i++;
        }
        return builder.toString();
    }

    /**
     * 按字符数截断 § 文本,且不会把颜色代码截成一半。
     * <p>Bukkit 的队伍前缀 / 后缀上限为 64 个字符。</p>
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        StringBuilder builder = new StringBuilder(Math.min(text.length(), maxLength));
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int chunk;
            if (c == COLOR_CHAR && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                chunk = (code == 'x' || code == 'X') ? 14 : 2;
            } else {
                chunk = 1;
            }
            if (i + chunk > text.length() || builder.length() + chunk > maxLength) {
                break;
            }
            builder.append(text, i, i + chunk);
            i += chunk;
        }
        return builder.toString();
    }
}
