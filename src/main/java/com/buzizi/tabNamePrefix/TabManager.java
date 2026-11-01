package com.buzizi.tabNamePrefix;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 核心管理器: 负责加载配置、解析玩家命中的条目并写入 Tab 名称。
 */
public final class TabManager {

    /** LuckPerms 组缓存有效期(毫秒),避免高频刷新时频繁查询组数据 */
    private static final long GROUP_CACHE_TTL_MS = 5000L;

    private final TabNamePrefix plugin;
    private final List<TabEntry> entries = new ArrayList<>();
    private final Map<UUID, Component> applied = new HashMap<>();
    private final Map<UUID, CachedGroups> groupCache = new HashMap<>();

    private String groupNodePrefix = "tabnameprefix.group.";
    private boolean enabled = true;
    private boolean papiAvailable;
    private boolean luckPermsAvailable;

    public TabManager(TabNamePrefix plugin) {
        this.plugin = plugin;
    }

    /** 重新读取配置文件并重建内部状态 */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        entries.clear();
        applied.clear();
        groupCache.clear();

        enabled = config.getBoolean("enabled", true);
        groupNodePrefix = config.getString("settings.group-node-prefix", "tabnameprefix.group.");

        ConfigurationSection section = config.getConfigurationSection("entries");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entrySection = section.getConfigurationSection(key);
                if (entrySection == null) {
                    plugin.getLogger().warning("条目 '" + key + "' 配置无效,已跳过。");
                    continue;
                }
                entries.add(TabEntry.parse(key, entrySection));
            }
        }

        papiAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        luckPermsAvailable = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");

        plugin.getLogger().info("已加载 " + entries.size() + " 个显示条目"
                + " | PlaceholderAPI: " + (papiAvailable ? "已安装" : "未安装")
                + " | LuckPerms: " + (luckPermsAvailable ? "已安装" : "未安装"));
    }

    public int entryCount() {
        return entries.size();
    }

    /** 为所有在线玩家刷新 Tab 名称 */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    /** 为单个玩家应用 Tab 名称(prefix + 玩家名 + suffix) */
    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!enabled) {
            restore(player);
            return;
        }

        TabEntry entry = resolve(player);
        if (entry == null) {
            // 没有命中任何条目 -> 还原默认名称
            restore(player);
            return;
        }

        String prefix = entry.prefix();
        String suffix = entry.suffix();

        if (papiAvailable) {
            try {
                prefix = PlaceholderAPI.setPlaceholders(player, prefix);
                suffix = PlaceholderAPI.setPlaceholders(player, suffix);
            } catch (Throwable ignored) {
                // PlaceholderAPI 版本不兼容等异常时,保留原始文本
            }
        }

        String text = TextUtil.colorize(prefix + player.getName() + suffix);
        Component component = LegacyComponentSerializer.legacySection().deserialize(text);

        Component previous = applied.put(player.getUniqueId(), component);
        if (!component.equals(previous)) {
            player.playerListName(component);
        }
    }

    /** 还原某玩家默认的 Tab 名称(仅当之前被本插件修改过) */
    public void restore(Player player) {
        if (applied.remove(player.getUniqueId()) != null) {
            player.playerListName(null);
        }
    }

    /** 还原所有在线玩家的 Tab 名称(插件禁用时调用) */
    public void restoreAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restore(player);
        }
        applied.clear();
    }

    /** 玩家退出时清理内部缓存 */
    public void forget(Player player) {
        if (player == null) {
            return;
        }
        applied.remove(player.getUniqueId());
        groupCache.remove(player.getUniqueId());
    }

    /**
     * 解析玩家命中的条目:
     * 优先在所有带匹配条件的条目中选 priority 最大(相同则配置靠前)的,
     * 都不命中时退回默认兜底条目。
     */
    private TabEntry resolve(Player player) {
        TabEntry fallback = null;
        TabEntry best = null;
        int bestPriority = Integer.MIN_VALUE;

        Set<String> lpGroups = luckPermsAvailable ? lpGroupsOf(player) : Collections.emptySet();

        for (TabEntry entry : entries) {
            if (entry.isFallback()) {
                if (fallback == null) {
                    fallback = entry;
                }
                continue;
            }
            if (entry.matches(player, lpGroups, groupNodePrefix)) {
                if (best == null || entry.priority() > bestPriority) {
                    best = entry;
                    bestPriority = entry.priority();
                }
            }
        }
        return best != null ? best : fallback;
    }

    /** 通过 LuckPerms API 获取玩家所属的权限组名(含继承的组),带短时缓存 */
    private Set<String> lpGroupsOf(Player player) {
        long now = System.currentTimeMillis();
        CachedGroups cached = groupCache.get(player.getUniqueId());
        if (cached != null && now - cached.at() < GROUP_CACHE_TTL_MS) {
            return cached.groups();
        }

        Set<String> groups = new HashSet<>();
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                groups.add(user.getPrimaryGroup());
                try {
                    for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
                        groups.add(group.getName());
                    }
                } catch (Throwable ignored) {
                    // 旧版本 API 差异等,仅保留主权限组
                }
            }
        } catch (Throwable ignored) {
            luckPermsAvailable = false;
        }

        groupCache.put(player.getUniqueId(), new CachedGroups(now, groups));
        return groups;
    }

    private record CachedGroups(long at, Set<String> groups) {
    }
}
