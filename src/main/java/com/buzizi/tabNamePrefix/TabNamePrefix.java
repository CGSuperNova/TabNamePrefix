package com.buzizi.tabNamePrefix;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * TabNamePrefix
 * 在 Tab 列表中为不同权限组的玩家显示「前缀 + 玩家名 + 后缀」。
 */
public final class TabNamePrefix extends JavaPlugin implements CommandExecutor, Listener {

    /** bStats 插件 ID: https://bstats.org/what-is-my-plugin-id */
    private static final int BSTATS_PLUGIN_ID = 35063;

    private TabManager tabManager;
    private BukkitTask refreshTask;
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        tabManager = new TabManager(this);
        tabManager.load();

        if (getCommand("tabnameprefix") != null) {
            getCommand("tabnameprefix").setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        restartRefreshTask();
        tabManager.refreshAll();
        setupMetrics();

        getLogger().info("已启用 " + getDescription().getFullName() + ",使用 /tabnameprefix reload 可重载配置。");
    }

    /**
     * 初始化 bStats 匿名统计。
     *
     * <p>统计失败(例如离线环境或服务端过于特殊)时只记录一条警告,
     * 不影响插件本身的任何功能。</p>
     */
    private void setupMetrics() {
        try {
            metrics = new Metrics(this, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("write_mode",
                    () -> tabManager.writeMode()));
            metrics.addCustomChart(new SimplePie("placeholderapi",
                    () -> tabManager.isPlaceholderApiAvailable() ? "Installed" : "Not installed"));
            metrics.addCustomChart(new SimplePie("luckperms",
                    () -> tabManager.isLuckPermsAvailable() ? "Installed" : "Not installed"));
        } catch (Throwable throwable) {
            getLogger().warning("bStats 统计初始化失败(不影响插件功能): " + throwable);
        }
    }

    @Override
    public void onDisable() {
        stopRefreshTask();
        if (tabManager != null) {
            tabManager.restoreAll();
        }
        getLogger().info("已禁用,所有玩家的 Tab 名称已还原。");
    }

    private void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /** 依据当前配置的刷新间隔(重新)启动定时任务 */
    private void restartRefreshTask() {
        stopRefreshTask();
        int interval = getConfig().getInt("settings.refresh-interval-ticks", 20);
        if (interval > 0) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> tabManager.refreshAll(), interval, interval);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (tabManager != null) {
            tabManager.apply(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (tabManager != null) {
            tabManager.forget(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("");
            sender.sendMessage("§e§lTabNamePrefix §7v" + getDescription().getVersion());
            sender.sendMessage("§7在 Tab 列表中为不同权限组的玩家显示前缀/后缀(支持颜色与 PlaceholderAPI)");
            sender.sendMessage("§7用法: §f/" + label + " reload §7- 重载配置");
            sender.sendMessage("");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("tabnameprefix.reload")) {
                sender.sendMessage("§c你没有执行该命令的权限(需要 tabnameprefix.reload)");
                return true;
            }
            tabManager.load();
            restartRefreshTask();
            tabManager.refreshAll();
            sender.sendMessage("§a配置已重载!当前共加载 §e" + tabManager.entryCount() + " §a个条目。");
            return true;
        }

        sender.sendMessage("§c未知子命令: §f" + args[0] + " §c(可用: reload)");
        return true;
    }
}
