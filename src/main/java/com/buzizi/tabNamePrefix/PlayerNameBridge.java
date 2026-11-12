package com.buzizi.tabNamePrefix;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * 玩家「自定义名称(CustomName)」写入兼容层。
 *
 * <p>Minecraft 中玩家头顶的名字优先取实体的 CustomName;设置它之后可以完整控制
 * 头顶文本的样式(包括十六进制颜色)。Paper 提供 Adventure 的
 * {@code Entity#customName(Component)},Spigot 只有 {@code setCustomName(String)}。</p>
 *
 * <p>与其它兼容层一样,Adventure 接口通过反射访问,插件不依赖 Adventure。</p>
 */
final class PlayerNameBridge {

    private static final String ADVENTURE_COMPONENT = "net.kyori.adventure.text.Component";
    private static final String ADVENTURE_LEGACY_SERIALIZER =
            "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer";

    /** Paper: Entity#customName(Component) */
    private final Method adventureSetter;
    private final Object serializer;
    private final Method deserialize;
    /** Bukkit: Entity#setCustomName(String) */
    private final Method legacySetter;

    private PlayerNameBridge(Method adventureSetter, Object serializer, Method deserialize, Method legacySetter) {
        this.adventureSetter = adventureSetter;
        this.serializer = serializer;
        this.deserialize = deserialize;
        this.legacySetter = legacySetter;
    }

    static PlayerNameBridge create() {
        Method legacy = null;
        try {
            legacy = Player.class.getMethod("setCustomName", String.class);
        } catch (Throwable ignored) {
            // 极新的服务端可能移除该方法
        }
        try {
            Class<?> componentType = Class.forName(ADVENTURE_COMPONENT);
            Class<?> serializerType = Class.forName(ADVENTURE_LEGACY_SERIALIZER);
            Method setter = Player.class.getMethod("customName", componentType);
            Object serializer = serializerType.getMethod("legacySection").invoke(null);
            Method deserialize = serializerType.getMethod("deserialize", String.class);
            return new PlayerNameBridge(setter, serializer, deserialize, legacy);
        } catch (Throwable ignored) {
            return new PlayerNameBridge(null, null, null, legacy);
        }
    }

    boolean isSupported() {
        return adventureSetter != null || legacySetter != null;
    }

    boolean isAdventure() {
        return adventureSetter != null;
    }

    String describe() {
        if (adventureSetter != null) {
            return "Paper customName(Component)";
        }
        return legacySetter != null ? "Bukkit setCustomName(String)" : "不支持";
    }

    /**
     * 设置玩家头顶名字。
     *
     * @param player     目标玩家
     * @param legacyText 已转换好的 § 颜色代码文本
     * @return 是否写入成功
     */
    boolean set(Player player, String legacyText) {
        if (legacyText == null) {
            return reset(player);
        }
        if (adventureSetter != null) {
            try {
                Object component = deserialize.invoke(serializer, legacyText);
                adventureSetter.invoke(player, component);
                return true;
            } catch (Throwable ignored) {
                // 退回字符串接口
            }
        }
        if (legacySetter == null) {
            return false;
        }
        try {
            legacySetter.invoke(player, legacyText);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 清除自定义名称,恢复默认显示 */
    boolean reset(Player player) {
        if (adventureSetter != null) {
            try {
                adventureSetter.invoke(player, new Object[]{null});
                return true;
            } catch (Throwable ignored) {
                // 继续尝试字符串接口
            }
        }
        if (legacySetter == null) {
            return false;
        }
        try {
            legacySetter.invoke(player, new Object[]{null});
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
