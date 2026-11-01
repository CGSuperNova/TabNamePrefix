package com.buzizi.tabNamePrefix;

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

    private TabManager tabManager;
    private BukkitTask refreshTask;

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

        getLogger().info("已启用 " + getPluginMeta().getDisplayName() + ",使用 /tabnameprefix reload 可重载配置。");
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
            sender.sendMessage("§e§lTabNamePrefix §7v" + getPluginMeta().getVersion());
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
