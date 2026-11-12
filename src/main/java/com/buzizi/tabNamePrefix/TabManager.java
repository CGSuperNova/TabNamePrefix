package com.buzizi.tabNamePrefix;

import me.clip.placeholderapi.PlaceholderAPI;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 核心管理器: 负责加载配置、解析玩家命中的条目,并写入 Tab 名称、
 * 悬浮姓名牌与 Tab 列表排序。
 */
public final class TabManager {

    /** LuckPerms 组缓存有效期(毫秒),避免高频刷新时频繁查询组数据 */
    private static final long GROUP_CACHE_TTL_MS = 5000L;
    /** Bukkit 队伍前缀 / 后缀的字符上限 */
    private static final int TEAM_TEXT_MAX = 64;

    private final TabNamePrefix plugin;
    private final List<TabEntry> entries = new ArrayList<TabEntry>();
    /** 记录每个玩家当前已写入的 Tab 文本,避免重复发包 */
    private final Map<UUID, String> applied = new HashMap<UUID, String>();
    private final Map<UUID, CachedGroups> groupCache = new HashMap<UUID, CachedGroups>();
    /** 跨版本写入兼容层:Paper 用 Adventure,Spigot 等退回 Bukkit 方法 */
    private final ListNameBridge listNameBridge = ListNameBridge.create();
    /** 悬浮姓名牌 + Tab 排序 */
    private final NametagSorter nametagSorter;

    private FeatureSettings features = FeatureSettings.disabled();
    private String groupNodePrefix = "tabnameprefix.group.";
    private boolean enabled = true;
    private boolean papiAvailable;
    private boolean luckPermsAvailable;

    public TabManager(TabNamePrefix plugin) {
        this.plugin = plugin;
        this.nametagSorter = new NametagSorter(plugin);
        plugin.getLogger().info("Tab 名称写入方式: " + listNameBridge.describe());
        plugin.getLogger().info("排序 / 姓名牌写入方式: " + nametagSorter.describe());
    }

    /** 重新读取配置文件并重建内部状态 */
    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        entries.clear();
        groupCache.clear();

        // 注意:这里不清空 applied。
        // 重载后 refreshAll() 会重新计算每个玩家的文本:
        // 文本变化则重新写入,条目被删除时则依靠 applied 中记录的状态还原默认名称。

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
        features = FeatureSettings.parse(config);

        plugin.getLogger().info("已加载 " + entries.size() + " 个显示条目"
                + " | PlaceholderAPI: " + (papiAvailable ? "已安装" : "未安装")
                + " | LuckPerms: " + (luckPermsAvailable ? "已安装" : "未安装"));
        plugin.getLogger().info("悬浮姓名牌: " + (features.nametagEnabled() ? "开启" : "关闭")
                + " | Tab 排序: " + features.sortRule().describe());
        plugin.getLogger().info("名称渐变颜色: " + features.nameColorRule().describe());

        List<String> missing = new ArrayList<String>();
        if (!config.isSet("settings.nametag")) {
            missing.add("settings.nametag");
        }
        if (!config.isSet("settings.name-color")) {
            missing.add("settings.name-color");
        }
        if (!config.isSet("sorting")) {
            missing.add("sorting");
        }
        if (!missing.isEmpty()) {
            plugin.getLogger().warning("当前 config.yml 缺少配置块: " + String.join(", ", missing)
                    + "(通常是旧版本配置文件)。请参考 README 补全,否则对应功能会保持关闭;"
                    + "可用 /tabnameprefix debug 查看当前生效状态。");
        }
    }

    public int entryCount() {
        return entries.size();
    }

    /** 当前使用的 Tab 名称写入方式(用于 bStats 自定义图表) */
    public String writeMode() {
        return listNameBridge.isAdventure() ? "Paper Adventure" : "Bukkit legacy";
    }

    /** PlaceholderAPI 是否可用(用于 bStats 自定义图表) */
    public boolean isPlaceholderApiAvailable() {
        return papiAvailable;
    }

    /** LuckPerms 是否可用(用于 bStats 自定义图表) */
    public boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }

    /** 是否启用悬浮姓名牌(用于 bStats 自定义图表) */
    public boolean isNametagEnabled() {
        return features.nametagEnabled();
    }

    /** 当前排序模式(用于 bStats 自定义图表) */
    public String sortingMode() {
        SortRule rule = features.sortRule();
        return rule.isEnabled() ? rule.mode().name().toLowerCase(Locale.ROOT) : "none";
    }

    /** 是否启用名称渐变颜色(用于 bStats 自定义图表) */
    public boolean isNameColorEnabled() {
        return features.nameColorRule().isEnabled();
    }

    /** 为所有在线玩家刷新 Tab 名称、姓名牌与排序 */
    public void refreshAll() {
        if (!enabled) {
            restoreAll();
            return;
        }
        List<Player> online = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        List<PlayerData> data = new ArrayList<PlayerData>(online.size());
        for (Player player : online) {
            data.add(build(player));
        }
        for (PlayerData item : data) {
            applyTabName(item);
        }
        nametagSorter.update(data, features);
    }

    /** 为单个玩家解析条目、PAPI 变量与各项文本 */
    private PlayerData build(Player player) {
        TabEntry entry = resolve(player);

        String rawPrefix = entry == null ? "" : resolvePlaceholders(player, entry.prefix());
        String rawSuffix = entry == null ? "" : resolvePlaceholders(player, entry.suffix());

        NameColorRule colorRule = features.nameColorRule();
        String nameColorCode = "";
        int nameRgb = -1;
        if (colorRule.isEnabled()) {
            double ratio = colorRule.ratioOf(resolvePlaceholders(player, colorRule.placeholder()));
            if (ratio >= 0.0D) {
                nameRgb = colorRule.colorAt(ratio);
                nameColorCode = TextUtil.legacyHex(nameRgb);
            }
        }

        String tabText;
        if (nameColorCode.isEmpty()) {
            tabText = TextUtil.colorize(rawPrefix + player.getName() + rawSuffix);
        } else {
            // 只给玩家名单独着色,前缀 / 后缀自身的颜色保持不变
            tabText = TextUtil.colorize(rawPrefix) + nameColorCode + player.getName()
                    + TextUtil.colorize(rawSuffix);
        }

        String tagPrefix = "";
        String tagSuffix = "";
        if (features.nametagEnabled()) {
            String nametagPrefixRaw = features.useEntryValues()
                    ? rawPrefix : resolvePlaceholders(player, features.nametagPrefix());
            String nametagSuffixRaw = features.useEntryValues()
                    ? rawSuffix : resolvePlaceholders(player, features.nametagSuffix());
            tagPrefix = TextUtil.truncate(TextUtil.colorize(nametagPrefixRaw), TEAM_TEXT_MAX);
            tagSuffix = TextUtil.truncate(TextUtil.colorize(nametagSuffixRaw), TEAM_TEXT_MAX);
        }

        // 姓名牌优先使用自定义名称:部分客户端/版本不会把队伍前缀显示在头顶,
        // 自定义名称可以完整控制头顶文本;队伍前缀 / 后缀仍然保留作为兜底。
        String customName = null;
        if (features.nametagEnabled()) {
            String coloredName = nameColorCode.isEmpty()
                    ? player.getName()
                    : nameColorCode + player.getName();
            customName = tagPrefix + coloredName + tagSuffix;
        }

        String sortValue = "";
        String numericValue = "";
        SortRule rule = features.sortRule();
        if (rule.isEnabled()) {
            if (rule.mode() == SortRule.Mode.NUMERIC) {
                numericValue = resolvePlaceholders(player, rule.numericPlaceholder());
            } else if (rule.mode() == SortRule.Mode.LIST) {
                switch (rule.listSource()) {
                    case PREFIX:
                        sortValue = rawPrefix;
                        break;
                    case SUFFIX:
                        sortValue = rawSuffix;
                        break;
                    default:
                        sortValue = resolvePlaceholders(player, rule.listValue());
                        break;
                }
            }
        }

        return new PlayerData(player, tabText, tagPrefix, tagSuffix, sortValue, numericValue,
                nameRgb, customName);
    }

    private void applyTabName(PlayerData item) {
        UUID id = item.player.getUniqueId();
        String previous = applied.get(id);
        if (item.tabText.equals(previous)) {
            return;
        }
        if (listNameBridge.set(item.player, item.tabText)) {
            applied.put(id, item.tabText);
        } else {
            // 写入失败时不缓存,下一轮刷新会重试
            applied.remove(id);
        }
    }

    /** 还原某玩家默认的 Tab 名称(仅当之前被本插件修改过) */
    public void restore(Player player) {
        if (applied.remove(player.getUniqueId()) != null) {
            listNameBridge.reset(player);
        }
    }

    /** 还原所有在线玩家的 Tab 名称、姓名牌与排序(插件禁用时调用) */
    public void restoreAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (applied.remove(player.getUniqueId()) != null) {
                listNameBridge.reset(player);
            }
        }
        applied.clear();
        nametagSorter.restoreAll();
    }

    /** 玩家退出时清理内部缓存 */
    public void forget(Player player) {
        if (player == null) {
            return;
        }
        applied.remove(player.getUniqueId());
        groupCache.remove(player.getUniqueId());
        nametagSorter.forget(player);
    }

    /** 输出当前生效的配置与每个玩家的实际写入状态,便于排查姓名牌问题 */
    @SuppressWarnings("deprecation")
    public void debug(CommandSender sender) {
        sender.sendMessage("§e§lTabNamePrefix 调试信息");
        sender.sendMessage("§7版本 §f" + plugin.getDescription().getVersion()
                + " §7| Tab 写入 §f" + writeMode()
                + " §7| 姓名牌/排序写入 §f" + nametagSorter.describe());
        sender.sendMessage("§7姓名牌 " + (features.nametagEnabled() ? "§a开启" : "§c关闭")
                + " §7| 排序 §f" + features.sortRule().describe()
                + " §7| 名称颜色 §f" + features.nameColorRule().describe());

        Scoreboard board = Bukkit.getScoreboardManager() == null
                ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = build(player);
            sender.sendMessage("§e" + player.getName());
            sender.sendMessage("  §7Tab 文本: §f" + visible(data.tabText));
            Team team = board == null ? null : board.getEntryTeam(player.getName());
            if (team == null) {
                sender.sendMessage("  §7队伍: §c未加入(姓名牌 / 排序未生效)");
            } else {
                sender.sendMessage("  §7队伍: §f" + team.getName()
                        + " §7前缀: §f" + visible(team.getPrefix())
                        + " §7后缀: §f" + visible(team.getSuffix()));
            }
            sender.sendMessage("  §7自定义名称: §f"
                    + (data.customName == null ? "§c未设置" : visible(data.customName)));
            sender.sendMessage("  §7名称颜色: §f"
                    + (data.nameRgb < 0 ? "默认" : String.format("#%06X", data.nameRgb))
                    + " §7| 列表顺序: §f" + nametagSorter.orderOf(player));
        }
    }

    private static String visible(String text) {
        return text == null ? "" : text.replace('\u00A7', '&');
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

        Set<String> lpGroups = luckPermsAvailable ? lpGroupsOf(player) : Collections.<String>emptySet();

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

    /** 解析文本中的 PlaceholderAPI 变量;未安装时原样返回 */
    private String resolvePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (!papiAvailable) {
            return text;
        }
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }

    /** 通过 LuckPerms API 获取玩家所属的权限组名(含继承的组),带短时缓存 */
    private Set<String> lpGroupsOf(Player player) {
        long now = System.currentTimeMillis();
        CachedGroups cached = groupCache.get(player.getUniqueId());
        if (cached != null && now - cached.at < GROUP_CACHE_TTL_MS) {
            return cached.groups;
        }

        Set<String> groups = new HashSet<String>();
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

    /** 单个玩家本轮刷新所需的全部数据 */
    static final class PlayerData {
        final Player player;
        /** 已转换颜色代码的 Tab 文本 */
        final String tabText;
        /** 已转换颜色代码的姓名牌前缀 / 后缀 */
        final String tagPrefix;
        final String tagSuffix;
        /** 预设顺序排序时用于匹配的原始值 */
        final String sortValue;
        /** 数值排序时用于解析的原始值 */
        final String numericValue;
        /** 名称渐变颜色对应的 RGB, -1 表示未启用 / 无法解析 */
        final int nameRgb;
        /** 头顶自定义名称文本(含颜色), null 表示不设置 */
        final String customName;

        PlayerData(Player player, String tabText, String tagPrefix, String tagSuffix,
                   String sortValue, String numericValue, int nameRgb, String customName) {
            this.player = player;
            this.tabText = tabText;
            this.tagPrefix = tagPrefix;
            this.tagSuffix = tagSuffix;
            this.sortValue = sortValue;
            this.numericValue = numericValue;
            this.nameRgb = nameRgb;
            this.customName = customName;
        }
    }

    /** LuckPerms 组查询结果缓存(Java 8 环境下不能用 record,因此写成普通类) */
    private static final class CachedGroups {
        private final long at;
        private final Set<String> groups;

        private CachedGroups(long at, Set<String> groups) {
            this.at = at;
            this.groups = groups;
        }
    }
}
