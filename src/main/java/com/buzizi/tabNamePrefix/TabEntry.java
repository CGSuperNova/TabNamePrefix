package com.buzizi.tabNamePrefix;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 单个显示条目: 描述了某类玩家应显示的前缀 / 后缀,以及命中的匹配条件。
 */
public final class TabEntry {

    private final String id;
    private final String prefix;
    private final String suffix;
    private final String permission;
    private final List<String> groups;
    private final int priority;
    private final boolean fallback;

    private TabEntry(String id, String prefix, String suffix, String permission, List<String> groups, int priority) {
        this.id = id;
        this.prefix = prefix == null ? "" : prefix;
        this.suffix = suffix == null ? "" : suffix;
        this.permission = (permission == null || permission.trim().isEmpty()) ? null : permission.trim();
        this.groups = groups == null ? Collections.emptyList() : groups;
        this.priority = priority;
        // 既没有权限节点也没有权限组 -> 作为默认(兜底)条目
        this.fallback = this.permission == null && this.groups.isEmpty();
    }

    public static TabEntry parse(String id, ConfigurationSection section) {
        String prefix = section.getString("prefix", "");
        String suffix = section.getString("suffix", "");
        String permission = section.getString("permission");
        List<String> groups = readGroups(section);
        int priority = section.getInt("priority", 0);
        return new TabEntry(id, prefix, suffix, permission, groups, priority);
    }

    private static List<String> readGroups(ConfigurationSection section) {
        List<String> result = new ArrayList<>();
        // 支持单个 group: 字符串,以及 groups: 列表(也容忍写成单个字符串的情况)
        Object single = section.get("group");
        if (single != null) {
            result.add(String.valueOf(single));
        }
        Object list = section.get("groups");
        if (list instanceof List<?>) {
            for (Object item : (List<?>) list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (list != null) {
            result.add(String.valueOf(list));
        }
        return result;
    }

    /** 该条目是否是默认兜底条目 */
    boolean isFallback() {
        return fallback;
    }

    int priority() {
        return priority;
    }

    public String getId() {
        return id;
    }

    String prefix() {
        return prefix;
    }

    String suffix() {
        return suffix;
    }

    /**
     * 判断该条目是否匹配指定玩家。
     *
     * @param player          在线玩家
     * @param lpGroups        玩家在 LuckPerms 中的组名集合(可能为空)
     * @param groupNodePrefix 权限节点前缀,用于无 LuckPerms 时按节点回退匹配
     */
    boolean matches(Player player, Set<String> lpGroups, String groupNodePrefix) {
        if (fallback) {
            return false;
        }
        if (permission != null && player.hasPermission(permission)) {
            return true;
        }
        for (String group : groups) {
            if (lpGroups.contains(group)) {
                return true;
            }
            if (player.hasPermission(groupNodePrefix + group)) {
                return true;
            }
        }
        return false;
    }
}
