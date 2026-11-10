package com.buzizi.tabNamePrefix;

import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;

/**
 * 队伍前缀 / 后缀写入兼容层。
 *
 * <ul>
 *   <li>Paper:提供 Adventure 的 {@code Team#prefix(Component)} / {@code suffix(Component)},
 *       可以完整保留十六进制颜色;</li>
 *   <li>Spigot / 旧版本:只有 {@code setPrefix(String)} / {@code setSuffix(String)}。</li>
 * </ul>
 *
 * <p>与 {@link ListNameBridge} 一样,Paper 接口通过反射访问,插件不依赖 Adventure。</p>
 */
final class TeamTextBridge {

    private static final String ADVENTURE_COMPONENT = "net.kyori.adventure.text.Component";
    private static final String ADVENTURE_LEGACY_SERIALIZER =
            "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer";

    /** Paper: Team#prefix(Component) */
    private final Method adventurePrefix;
    /** Paper: Team#suffix(Component) */
    private final Method adventureSuffix;
    private final Object serializer;
    private final Method deserialize;
    /** Bukkit: Team#setPrefix(String) */
    private final Method legacyPrefix;
    /** Bukkit: Team#setSuffix(String) */
    private final Method legacySuffix;

    private TeamTextBridge(Method adventurePrefix, Method adventureSuffix, Object serializer,
                           Method deserialize, Method legacyPrefix, Method legacySuffix) {
        this.adventurePrefix = adventurePrefix;
        this.adventureSuffix = adventureSuffix;
        this.serializer = serializer;
        this.deserialize = deserialize;
        this.legacyPrefix = legacyPrefix;
        this.legacySuffix = legacySuffix;
    }

    /** 探测当前服务端支持的写入方式,不会抛出异常 */
    static TeamTextBridge create() {
        Method legacyPrefix = null;
        Method legacySuffix = null;
        try {
            legacyPrefix = Team.class.getMethod("setPrefix", String.class);
            legacySuffix = Team.class.getMethod("setSuffix", String.class);
        } catch (Throwable ignored) {
            // 极新的服务端可能移除字符串版本
        }
        try {
            Class<?> componentType = Class.forName(ADVENTURE_COMPONENT);
            Class<?> serializerType = Class.forName(ADVENTURE_LEGACY_SERIALIZER);
            Method prefix = Team.class.getMethod("prefix", componentType);
            Method suffix = Team.class.getMethod("suffix", componentType);
            Object serializer = serializerType.getMethod("legacySection").invoke(null);
            Method deserialize = serializerType.getMethod("deserialize", String.class);
            return new TeamTextBridge(prefix, suffix, serializer, deserialize, legacyPrefix, legacySuffix);
        } catch (Throwable ignored) {
            return new TeamTextBridge(null, null, null, null, legacyPrefix, legacySuffix);
        }
    }

    boolean isAdventure() {
        return adventurePrefix != null;
    }

    /**
     * 设置队伍前缀与后缀。
     *
     * @param team   目标队伍
     * @param prefix 已转换好的 § 颜色代码文本
     * @param suffix 已转换好的 § 颜色代码文本
     * @return 是否写入成功
     */
    boolean set(Team team, String prefix, String suffix) {
        String safePrefix = prefix == null ? "" : prefix;
        String safeSuffix = suffix == null ? "" : suffix;
        if (adventurePrefix != null) {
            try {
                adventurePrefix.invoke(team, deserialize.invoke(serializer, safePrefix));
                adventureSuffix.invoke(team, deserialize.invoke(serializer, safeSuffix));
                return true;
            } catch (Throwable ignored) {
                // 退回字符串接口
            }
        }
        if (legacyPrefix == null || legacySuffix == null) {
            return false;
        }
        try {
            legacyPrefix.invoke(team, safePrefix);
            legacySuffix.invoke(team, safeSuffix);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
