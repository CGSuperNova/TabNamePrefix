package com.buzizi.tabNamePrefix;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Tab 列表排序规则。
 *
 * <pre>
 * sorting:
 *   enabled: true
 *   mode: numeric   # none / numeric / list
 *   numeric:
 *     placeholder: "%player_level%"
 *     descending: true
 *   list:
 *     source: placeholder   # prefix / suffix / placeholder
 *     value: "%rank_rankname%"
 *     order: [ Diamond, Gold, Iron, Copper, Coal ]
 *     unmatched: last       # last / first
 * </pre>
 */
final class SortRule {

    enum Mode {
        /** 不排序(使用客户端默认的按玩家名排序,仅用于姓名牌场景下保持原顺序) */
        NONE,
        /** 按数值变量排序 */
        NUMERIC,
        /** 按预设列表排序 */
        LIST
    }

    enum ListSource {
        PREFIX,
        SUFFIX,
        PLACEHOLDER
    }

    static final SortRule DISABLED = new SortRule(false, Mode.NONE, "", false,
            ListSource.PLACEHOLDER, "", Collections.<String>emptyList(), false);

    private final boolean enabled;
    private final Mode mode;
    private final String numericPlaceholder;
    private final boolean descending;
    private final ListSource listSource;
    private final String listValue;
    /** 已归一化(去颜色、去首尾空格、小写)的预设顺序 */
    private final List<String> order;
    private final boolean unmatchedFirst;

    private SortRule(boolean enabled, Mode mode, String numericPlaceholder, boolean descending,
                     ListSource listSource, String listValue, List<String> order, boolean unmatchedFirst) {
        this.enabled = enabled;
        this.mode = mode;
        this.numericPlaceholder = numericPlaceholder;
        this.descending = descending;
        this.listSource = listSource;
        this.listValue = listValue;
        this.order = order;
        this.unmatchedFirst = unmatchedFirst;
    }

    static SortRule parse(FileConfiguration config) {
        boolean enabled = config.getBoolean("sorting.enabled", false);

        String rawMode = config.getString("sorting.mode", "none");
        Mode mode = Mode.NONE;
        if ("numeric".equalsIgnoreCase(rawMode)) {
            mode = Mode.NUMERIC;
        } else if ("list".equalsIgnoreCase(rawMode)) {
            mode = Mode.LIST;
        }

        String numericPlaceholder = config.getString("sorting.numeric.placeholder", "%player_level%");
        boolean descending = config.getBoolean("sorting.numeric.descending", true);

        String rawSource = config.getString("sorting.list.source", "placeholder");
        ListSource source = ListSource.PLACEHOLDER;
        if ("prefix".equalsIgnoreCase(rawSource)) {
            source = ListSource.PREFIX;
        } else if ("suffix".equalsIgnoreCase(rawSource)) {
            source = ListSource.SUFFIX;
        }

        String value = config.getString("sorting.list.value", "");
        List<String> order = new ArrayList<String>();
        for (String item : config.getStringList("sorting.list.order")) {
            if (item != null && !item.trim().isEmpty()) {
                order.add(normalize(item));
            }
        }

        boolean unmatchedFirst = "first".equalsIgnoreCase(config.getString("sorting.list.unmatched", "last"));

        return new SortRule(enabled, mode, numericPlaceholder == null ? "" : numericPlaceholder,
                descending, source, value == null ? "" : value, order, unmatchedFirst);
    }

    /** 去颜色、去首尾空格、转小写,便于大小写不敏感匹配 */
    static String normalize(String value) {
        return TextUtil.stripColors(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    boolean isEnabled() {
        return enabled;
    }

    Mode mode() {
        return mode;
    }

    String numericPlaceholder() {
        return numericPlaceholder;
    }

    boolean descending() {
        return descending;
    }

    ListSource listSource() {
        return listSource;
    }

    String listValue() {
        return listValue;
    }

    boolean unmatchedFirst() {
        return unmatchedFirst;
    }

    int orderSize() {
        return order.size();
    }

    /** 返回匹配到的顺序下标,未匹配返回 -1 */
    int indexOf(String value) {
        return order.indexOf(normalize(value));
    }

    /**
     * 比较两个玩家的排序键。
     *
     * @return 负数表示 a 应排在 b 前面;相同时按玩家名排序
     */
    int compareValues(String numericA, String listA, String nameA,
                      String numericB, String listB, String nameB) {
        int result;
        if (!enabled) {
            result = 0;
        } else if (mode == Mode.NUMERIC) {
            Double first = parseNumber(numericA);
            Double second = parseNumber(numericB);
            if (first == null && second == null) {
                result = 0;
            } else if (first == null) {
                // 无法解析的玩家始终排在最后
                return 1;
            } else if (second == null) {
                return -1;
            } else {
                result = first.compareTo(second);
                if (descending) {
                    result = -result;
                }
            }
        } else if (mode == Mode.LIST) {
            result = Integer.compare(listIndex(listA), listIndex(listB));
        } else {
            result = 0;
        }
        if (result != 0) {
            return result;
        }
        result = nameA.compareToIgnoreCase(nameB);
        if (result != 0) {
            return result;
        }
        return nameA.compareTo(nameB);
    }

    private int listIndex(String value) {
        int index = indexOf(value);
        if (index >= 0) {
            return index;
        }
        return unmatchedFirst ? -1 : order.size();
    }

    private static Double parseNumber(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = TextUtil.stripColors(value).trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(cleaned);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    String describe() {
        if (!enabled) {
            return "关闭";
        }
        if (mode == Mode.NUMERIC) {
            return "数值排序(" + numericPlaceholder + ", " + (descending ? "大在前" : "小在前") + ")";
        }
        if (mode == Mode.LIST) {
            return "预设顺序(" + listSource.name().toLowerCase(Locale.ROOT) + ", " + order.size() + " 项, 未匹配"
                    + (unmatchedFirst ? "在前" : "在后") + ")";
        }
        return "关闭";
    }
}
