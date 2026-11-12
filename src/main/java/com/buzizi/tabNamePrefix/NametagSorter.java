package com.buzizi.tabNamePrefix;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 悬浮姓名牌 + Tab 列表排序。
 *
 * <h2>姓名牌</h2>
 * Minecraft 中玩家头顶的悬浮名字由「计分板队伍(scoreboard team)」的前缀与后缀控制。
 * 本类为每个在线玩家维护一个独立队伍,把配置中的前缀 / 后缀写入队伍,
 * 从而同时影响 Tab 列表之外的悬浮姓名牌。
 *
 * <h2>排序</h2>
 * 旧版本客户端会按「队伍名」对 Tab 列表排序,因此这里把排序序号编码进队伍名
 * (如 {@code tnp0000}、{@code tnp0001});在 Paper 1.21.9+ 上还会额外调用
 * {@code Player#setPlayerListOrder(int)},两套机制同时生效以保证各版本顺序一致。
 *
 * <p>注意:队伍是全局计分板对象,启用本功能后会接管玩家所在的队伍,
 * 可能与其他使用队伍实现功能(彩色名字、碰撞、发光等)的插件冲突。</p>
 */
final class NametagSorter {

    /** Bukkit 队伍前缀 / 后缀的字符上限 */
    static final int TEAM_TEXT_MAX = 64;
    /** 本插件创建的队伍名前缀 */
    private static final String TEAM_NAME_PREFIX = "tnp";

    private final TabNamePrefix plugin;
    private final TeamTextBridge teamTextBridge = TeamTextBridge.create();
    private final PlayerNameBridge playerNameBridge = PlayerNameBridge.create();
    private final PlayerListOrderBridge orderBridge = PlayerListOrderBridge.create();

    private final Map<UUID, TeamState> applied = new HashMap<UUID, TeamState>();
    private final Map<UUID, Integer> appliedRanks = new HashMap<UUID, Integer>();
    private final Map<UUID, String> previousTeams = new HashMap<UUID, String>();
    private boolean warned;

    NametagSorter(TabNamePrefix plugin) {
        this.plugin = plugin;
    }

    /** 供启动日志展示当前使用的写入方式 */
    String describe() {
        return orderBridge.describe() + " + "
                + (teamTextBridge.isAdventure() ? "Adventure 队伍文本" : "传统队伍文本")
                + " + " + playerNameBridge.describe();
    }

    /** 读取玩家当前的列表顺序(用于调试) */
    int orderOf(Player player) {
        return orderBridge.get(player);
    }

    /**
     * 重新计算排序并写入队伍 / 姓名牌。
     *
     * @param data     所有在线玩家的已解析数据
     * @param settings 当前功能配置
     */
    void update(List<TabManager.PlayerData> data, FeatureSettings settings) {
        if (!settings.sorterActive()) {
            if (!applied.isEmpty() || !appliedRanks.isEmpty()) {
                restoreAll();
            }
            return;
        }
        Scoreboard board = board();
        if (board == null) {
            return;
        }

        // 清理已经离线、但队伍还留在计分板上的玩家
        Set<UUID> online = new HashSet<UUID>();
        for (TabManager.PlayerData item : data) {
            online.add(item.player.getUniqueId());
        }
        for (UUID id : new ArrayList<UUID>(applied.keySet())) {
            if (!online.contains(id)) {
                cleanup(board, id);
            }
        }

        final SortRule rule = settings.sortRule();
        List<TabManager.PlayerData> sorted = new ArrayList<TabManager.PlayerData>(data);
        Collections.sort(sorted, new Comparator<TabManager.PlayerData>() {
            @Override
            public int compare(TabManager.PlayerData a, TabManager.PlayerData b) {
                return rule.compareValues(a.numericValue, a.sortValue, a.player.getName(),
                        b.numericValue, b.sortValue, b.player.getName());
            }
        });

        Map<UUID, Integer> ranks = new HashMap<UUID, Integer>();
        for (int i = 0; i < sorted.size(); i++) {
            ranks.put(sorted.get(i).player.getUniqueId(), Integer.valueOf(i));
        }

        // 排序变化时整体重建,避免玩家在队伍之间移动时出现短暂的重叠
        if (!ranks.equals(appliedRanks)) {
            clearTeams(board);
            appliedRanks.clear();
        }

        boolean nametag = settings.nametagEnabled();
        int width = Math.max(4, String.valueOf(Math.max(0, sorted.size() - 1)).length());
        for (int i = 0; i < sorted.size(); i++) {
            TabManager.PlayerData item = sorted.get(i);
            String teamName = TEAM_NAME_PREFIX + pad(i, width);
            String prefix = nametag ? TextUtil.truncate(item.tagPrefix, TEAM_TEXT_MAX) : "";
            String suffix = nametag ? TextUtil.truncate(item.tagSuffix, TEAM_TEXT_MAX) : "";
            String customName = nametag ? item.customName : null;
            int colorRgb = nametag ? item.nameRgb : -1;
            try {
                apply(board, item.player, i, teamName, prefix, suffix, customName, colorRgb);
            } catch (Throwable throwable) {
                if (!warned) {
                    warned = true;
                    plugin.getLogger().warning("写入姓名牌队伍失败(将跳过该玩家): " + throwable);
                }
            }
        }

        appliedRanks.clear();
        appliedRanks.putAll(ranks);
    }

    /** 把某个玩家写入对应的队伍,并设置其列表顺序、头顶颜色与文本 */
    private void apply(Scoreboard board, Player player, int rank, String teamName, String prefix, String suffix,
                       String customName, int colorRgb) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        TeamState state = applied.get(id);

        if (state == null || !state.teamName.equals(teamName)) {
            if (state != null) {
                removeEntry(board, state);
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            } else if (!team.getEntries().isEmpty()) {
                // 正常情况下一个序号只会有一个玩家,清掉可能残留的条目
                for (String entry : new ArrayList<String>(team.getEntries())) {
                    if (!entry.equals(name)) {
                        team.removeEntry(entry);
                    }
                }
            }
            if (!previousTeams.containsKey(id)) {
                Team previous = board.getEntryTeam(name);
                boolean ours = previous != null && previous.getName().startsWith(TEAM_NAME_PREFIX);
                previousTeams.put(id, previous == null || ours ? "" : previous.getName());
            }
            team.addEntry(name);
            state = new TeamState(teamName, name);
            state.rank = -1;
            applied.put(id, state);
        }

        if (!prefix.equals(state.prefix) || !suffix.equals(state.suffix)) {
            Team team = board.getTeam(teamName);
            if (team != null && teamTextBridge.set(team, prefix, suffix)) {
                state.prefix = prefix;
                state.suffix = suffix;
            }
        }

        if (playerNameBridge.isSupported() && !equalText(state.customName, customName)) {
            boolean ok = customName == null
                    ? playerNameBridge.reset(player)
                    : playerNameBridge.set(player, customName);
            if (ok) {
                state.customName = customName;
            }
        }

        if (state.colorRgb != colorRgb) {
            Team team = board.getTeam(teamName);
            if (team != null) {
                applyTeamColor(team, colorRgb);
                state.colorRgb = colorRgb;
            }
        }

        if (state.rank != rank) {
            orderBridge.set(player, rank + 1);
            state.rank = rank;
        }
    }

    /** 名称颜色退化到 16 色队伍颜色(作为自定义名称不可用时的兜底) */
    @SuppressWarnings("deprecation")
    private void applyTeamColor(Team team, int colorRgb) {
        if (colorRgb < 0) {
            team.setColor(ChatColor.RESET);
            return;
        }
        char code = TextUtil.legacyColorChar(TextUtil.nearestLegacyColor(colorRgb));
        ChatColor color = ChatColor.getByChar(code);
        team.setColor(color == null ? ChatColor.RESET : color);
    }

    private static boolean equalText(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private void removeEntry(Scoreboard board, TeamState state) {
        Team team = board.getTeam(state.teamName);
        if (team == null) {
            return;
        }
        team.removeEntry(state.entryName);
        if (team.getEntries().isEmpty()) {
            team.unregister();
        }
    }

    /** 移除所有由本插件创建的队伍,但保留 previousTeams 以便之后恢复 */
    private void clearTeams(Scoreboard board) {
        for (TeamState state : new ArrayList<TeamState>(applied.values())) {
            removeEntry(board, state);
        }
        applied.clear();
    }

    /** 还原所有在线玩家的姓名牌、队伍与列表顺序 */
    void restoreAll() {
        Scoreboard board = board();
        for (Map.Entry<UUID, TeamState> entry : new ArrayList<Map.Entry<UUID, TeamState>>(applied.entrySet())) {
            restoreOne(board, entry.getKey(), entry.getValue());
        }
        applied.clear();
        appliedRanks.clear();
        previousTeams.clear();
    }

    /** 玩家退出时还原其队伍,避免队伍残留在计分板上 */
    void forget(Player player) {
        UUID id = player.getUniqueId();
        TeamState state = applied.remove(id);
        if (state != null) {
            restoreOne(board(), id, state);
        }
        appliedRanks.remove(id);
        previousTeams.remove(id);
    }

    private void restoreOne(Scoreboard board, UUID id, TeamState state) {
        if (board != null) {
            Team team = board.getTeam(state.teamName);
            if (team != null) {
                team.removeEntry(state.entryName);
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return;
        }
        orderBridge.reset(player);
        if (playerNameBridge.isSupported()) {
            playerNameBridge.reset(player);
        }
        String previous = previousTeams.get(id);
        if (board != null && previous != null && !previous.isEmpty()) {
            Team prev = board.getTeam(previous);
            if (prev != null) {
                prev.addEntry(player.getName());
            }
        }
    }

    /** 清理已经离线的玩家(没有 Player 对象,只移除队伍) */
    private void cleanup(Scoreboard board, UUID id) {
        TeamState state = applied.remove(id);
        if (state != null) {
            removeEntry(board, state);
        }
        appliedRanks.remove(id);
        previousTeams.remove(id);
    }

    private static String pad(int value, int width) {
        String raw = String.valueOf(value);
        if (raw.length() >= width) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(width);
        for (int i = raw.length(); i < width; i++) {
            builder.append('0');
        }
        return builder.append(raw).toString();
    }

    private static Scoreboard board() {
        return Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** 单个玩家当前所在的队伍状态 */
    private static final class TeamState {
        private final String teamName;
        private final String entryName;
        private String prefix = "";
        private String suffix = "";
        private String customName;
        private int colorRgb = -1;
        private int rank = -1;

        private TeamState(String teamName, String entryName) {
            this.teamName = teamName;
            this.entryName = entryName;
        }
    }
}
