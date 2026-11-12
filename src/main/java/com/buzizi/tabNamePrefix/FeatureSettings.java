package com.buzizi.tabNamePrefix;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 2.1.x 新增功能(悬浮姓名牌 / Tab 排序 / 名称渐变颜色)的配置快照。
 */
final class FeatureSettings {

    private static final FeatureSettings DISABLED =
            new FeatureSettings(false, true, "", "", SortRule.DISABLED, NameColorRule.DISABLED);

    private final boolean nametagEnabled;
    private final boolean useEntryValues;
    private final String nametagPrefix;
    private final String nametagSuffix;
    private final SortRule sortRule;
    private final NameColorRule nameColorRule;

    private FeatureSettings(boolean nametagEnabled, boolean useEntryValues, String nametagPrefix,
                            String nametagSuffix, SortRule sortRule, NameColorRule nameColorRule) {
        this.nametagEnabled = nametagEnabled;
        this.useEntryValues = useEntryValues;
        this.nametagPrefix = nametagPrefix;
        this.nametagSuffix = nametagSuffix;
        this.sortRule = sortRule;
        this.nameColorRule = nameColorRule;
    }

    static FeatureSettings parse(FileConfiguration config) {
        boolean nametag = config.getBoolean("settings.nametag.enabled", false);
        boolean useEntry = config.getBoolean("settings.nametag.use-entry-values", true);
        String prefix = config.getString("settings.nametag.prefix", "");
        String suffix = config.getString("settings.nametag.suffix", "");
        return new FeatureSettings(nametag, useEntry,
                prefix == null ? "" : prefix,
                suffix == null ? "" : suffix,
                SortRule.parse(config),
                NameColorRule.parse(config));
    }

    static FeatureSettings disabled() {
        return DISABLED;
    }

    boolean nametagEnabled() {
        return nametagEnabled;
    }

    boolean useEntryValues() {
        return useEntryValues;
    }

    String nametagPrefix() {
        return nametagPrefix;
    }

    String nametagSuffix() {
        return nametagSuffix;
    }

    SortRule sortRule() {
        return sortRule;
    }

    NameColorRule nameColorRule() {
        return nameColorRule;
    }

    /** 是否需要接管队伍 / 设置玩家列表顺序 */
    boolean sorterActive() {
        return nametagEnabled || sortRule.isEnabled();
    }
}
