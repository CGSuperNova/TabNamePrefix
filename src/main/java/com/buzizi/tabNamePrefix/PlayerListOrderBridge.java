package com.buzizi.tabNamePrefix;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Paper 1.21.9+ 的玩家列表顺序接口({@code Player#setPlayerListOrder(int)})。
 *
 * <p>旧版本与 Spigot 没有该接口,此时返回不支持,由队伍名排序兜底
 * (旧版客户端会按队伍名对 Tab 列表排序)。</p>
 */
final class PlayerListOrderBridge {

    private final Method setter;
    private final Method getter;

    private PlayerListOrderBridge(Method setter, Method getter) {
        this.setter = setter;
        this.getter = getter;
    }

    /** 探测当前服务端是否支持玩家列表顺序接口 */
    static PlayerListOrderBridge create() {
        try {
            Method setter = Player.class.getMethod("setPlayerListOrder", int.class);
            Method getter = null;
            try {
                getter = Player.class.getMethod("getPlayerListOrder");
            } catch (Throwable ignored) {
                // 只有 setter 也能工作
            }
            return new PlayerListOrderBridge(setter, getter);
        } catch (Throwable ignored) {
            return new PlayerListOrderBridge(null, null);
        }
    }

    boolean isSupported() {
        return setter != null;
    }

    /** 设置排序序号(从 1 开始) */
    boolean set(Player player, int order) {
        if (setter == null) {
            return false;
        }
        try {
            setter.invoke(player, order);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 还原为默认顺序 */
    void reset(Player player) {
        if (setter == null) {
            return;
        }
        try {
            setter.invoke(player, 0);
        } catch (Throwable ignored) {
            // 某些版本要求正数,忽略即可
        }
    }

    int get(Player player) {
        if (getter == null) {
            return 0;
        }
        try {
            Object value = getter.invoke(player);
            return value instanceof Integer ? ((Integer) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    String describe() {
        return setter != null ? "playerListOrder(1.21.9+)" : "队伍名排序";
    }
}
