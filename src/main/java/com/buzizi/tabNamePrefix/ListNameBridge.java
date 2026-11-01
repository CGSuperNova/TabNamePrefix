package com.buzizi.tabNamePrefix;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Tab 名称写入兼容层。
 *
 * <p>各服务端可用的接口并不一致:</p>
 * <ul>
 *   <li>Bukkit / Spigot:只有 {@code setPlayerListName(String)},从 1.16 一直保留到 26.x
 *       (1.17 起标记为废弃,但仍然可用);</li>
 *   <li>Paper:额外提供 Adventure 的 {@code playerListName(Component)},可以完整保留
 *       十六进制颜色,是官方推荐的新接口。</li>
 * </ul>
 *
 * <p>本类在运行时探测 Paper 接口,探测成功就优先使用它,否则退回 Bukkit 方法。
 * 由于 Paper 的接口只在运行时通过反射访问,插件本身不依赖 Adventure,
 * 因此同一份 jar 可以在 Spigot 1.16 ~ Paper 26.2 上加载。</p>
 */
final class ListNameBridge {

    private static final String ADVENTURE_COMPONENT = "net.kyori.adventure.text.Component";
    private static final String ADVENTURE_LEGACY_SERIALIZER =
            "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer";

    /** Paper: Player#playerListName(Component);不存在时为 null */
    private final Method adventureSetter;
    /** Adventure 的 LegacyComponentSerializer 实例;不可用时为 null */
    private final Object legacySerializer;
    /** LegacyComponentSerializer#deserialize(String);不可用时为 null */
    private final Method deserialize;
    /** Bukkit: Player#setPlayerListName(String);不存在时为 null */
    private final Method bukkitSetter;

    private ListNameBridge(Method adventureSetter, Object legacySerializer, Method deserialize, Method bukkitSetter) {
        this.adventureSetter = adventureSetter;
        this.legacySerializer = legacySerializer;
        this.deserialize = deserialize;
        this.bukkitSetter = bukkitSetter;
    }

    /** 探测当前服务端支持的写入方式,不会抛出任何异常 */
    static ListNameBridge create() {
        Method bukkit = null;
        try {
            bukkit = Player.class.getMethod("setPlayerListName", String.class);
        } catch (Throwable ignored) {
            // 极新的服务端可能已经移除该方法,此时只能依赖 Paper 接口
        }
        try {
            Class<?> componentType = Class.forName(ADVENTURE_COMPONENT);
            Class<?> serializerType = Class.forName(ADVENTURE_LEGACY_SERIALIZER);
            Method setter = Player.class.getMethod("playerListName", componentType);
            Object serializer = serializerType.getMethod("legacySection").invoke(null);
            Method deserializeMethod = serializerType.getMethod("deserialize", String.class);
            return new ListNameBridge(setter, serializer, deserializeMethod, bukkit);
        } catch (Throwable ignored) {
            return new ListNameBridge(null, null, null, bukkit);
        }
    }

    /** 是否使用 Paper 的 Adventure 接口 */
    boolean isAdventure() {
        return adventureSetter != null;
    }

    /** 供日志展示的写入方式描述 */
    String describe() {
        if (isAdventure()) {
            return "Paper Adventure (playerListName(Component))";
        }
        return "Bukkit legacy (setPlayerListName(String))";
    }

    /**
     * 写入 Tab 名称。
     *
     * @param player     目标玩家
     * @param legacyText 已经转换好的 § 颜色代码文本
     * @return 是否写入成功
     */
    boolean set(Player player, String legacyText) {
        if (adventureSetter != null) {
            try {
                Object component = deserialize.invoke(legacySerializer, legacyText);
                adventureSetter.invoke(player, component);
                return true;
            } catch (Throwable ignored) {
                // Paper 接口异常时退回 Bukkit 方法
            }
        }
        return invokeBukkit(player, legacyText);
    }

    /**
     * 还原为默认 Tab 名称。
     *
     * @param player 目标玩家
     * @return 是否还原成功
     */
    boolean reset(Player player) {
        if (adventureSetter != null) {
            try {
                adventureSetter.invoke(player, new Object[]{null});
                return true;
            } catch (Throwable ignored) {
                // 继续尝试 Bukkit 方法
            }
        }
        return invokeBukkit(player, null);
    }

    private boolean invokeBukkit(Player player, String legacyText) {
        if (bukkitSetter == null) {
            return false;
        }
        try {
            bukkitSetter.invoke(player, new Object[]{legacyText});
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
