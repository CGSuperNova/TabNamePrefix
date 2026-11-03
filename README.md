# TabNamePrefix
[![GitHub release](https://img.shields.io/github/v/release/CGSuperNova/TabNamePrefix)](https://github.com/CGSuperNova/TabNamePrefix/releases)
[![License](https://img.shields.io/github/license/CGSuperNova/TabNamePrefix)](LICENSE)
[![Paper API](https://img.shields.io/badge/Paper-1.16~26.2-blue)](https://papermc.io/)
<p align="center">
  <img src="assets/logo-256.png" width="220" alt="TabNamePrefix logo">
</p>

在 Tab（玩家列表）中为不同权限组 / 权限节点的玩家显示可配置的前缀与后缀。

```text
原版显示:  PlayerName
配置后:    [VIP] PlayerName
```

## 兼容性

| 服务端 | 支持版本 |
| --- | --- |
| Paper | 1.16 ~ 26.2 |
| Spigot | 1.16 ~ 26.2 |
| Java 运行环境 | Java 8 / 11 / 16 / 17 / 21 / 25 |

插件以 **Java 8 字节码** 发布,编译基线为 Spigot API 1.16.5,因此同一份 jar 可以同时运行在
1.16 与 26.x 的服务端上。

Tab 名称的写入方式会在运行时自动选择:

- Paper:通过反射调用 Adventure 的 `playerListName(Component)`,十六进制颜色可以完整保留;
- Spigot / 其它服务端:退回 Bukkit 的 `setPlayerListName(String)`,功能一致。

## 安装

1. 将 `TabNamePrefix-x.y.z.jar` 放入服务端的 `plugins/` 目录。
2. 启动服务器,插件会生成 `plugins/TabNamePrefix/config.yml`。
3. 修改配置后执行 `/tabnameprefix reload` 重载。

可选依赖(均为软依赖,未安装时会自动降级):

- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/):解析 `%变量%`;
- [LuckPerms](https://luckperms.net/):按权限组匹配。

## 配置

```yaml
enabled: true

settings:
  # Tab 名称刷新间隔(tick),0 表示只在进服 / reload 时刷新一次
  refresh-interval-ticks: 20
  # 未安装 LuckPerms 时,用该权限节点前缀 + 组名做回退匹配
  group-node-prefix: "tabnameprefix.group."

entries:
  owner:
    group: owner
    prefix: "&#FF5555[服主] &f"
  vip:
    groups: [ vip, mvip ]
    prefix: "&#55FF55[VIP] &f"
  default:
    prefix: "&7"
```

- 匹配条件:`group` / `groups` / `permission`,满足任意一个即可命中;
- 多个条目同时命中时,`priority` 大者优先,相同时配置靠前者优先;
- 不填写任何匹配条件的条目作为默认兜底条目;
- `prefix` / `suffix` 支持 `&0`~`&f`、`&k`~`&r` 颜色代码、`&#RRGGBB` 十六进制颜色,
  以及 PlaceholderAPI 变量。

## 命令与权限

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/tabnameprefix`(`/tabp`、`/tnp`) | 查看插件信息 | - |
| `/tabnameprefix reload` | 重载配置并重新应用 | `tabnameprefix.reload`(默认 OP) |

## 统计

插件集成了 [bStats](https://bstats.org/) 匿名统计(插件 ID `35063`),会收集服务端版本、
插件版本、Java 版本,以及 `write_mode` / `placeholderapi` / `luckperms` 等运行环境信息。
统计数据不包含玩家名称、IP 或任何个人身份信息。

如需关闭,编辑 `plugins/bStats/config.yml`,把 `enabled` 改为 `false` 即可(该设置对所有使用 bStats 的插件生效)。

## 构建

需要 JDK 17 或更高版本(编译目标为 Java 8 字节码):

```bash
mvn clean package
```

产物位于 `target/TabNamePrefix-<version>.jar`。

## 许可证

本项目使用 [MIT License](LICENSE)。

发布包中内置的 bStats 组件的许可证与版权信息见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

![bStats 统计](https://bstats.org/signatures/bukkit/TabNamePrefix.svg)
