# TabNamePrefix
[![GitHub release](https://img.shields.io/github/v/release/CGSuperNova/TabNamePrefix)](https://github.com/CGSuperNova/TabNamePrefix/releases)
[![License](https://img.shields.io/github/license/CGSuperNova/TabNamePrefix)](LICENSE)
[![Paper API](https://img.shields.io/badge/Paper-1.16~26.2-blue)](https://papermc.io/)
<p align="center">
  <img src="assets/logo-256.png" width="220" alt="TabNamePrefix logo">
</p>

为 Tab（玩家列表）与玩家头顶的悬浮姓名牌显示可配置的前缀与后缀，支持自定义 Tab 列表排序，
并可根据数值变量为玩家名字生成渐变颜色。

```text
原版显示:  PlayerName
配置后:    [VIP] PlayerName
```

## 特性

- **Tab 列表前后缀**：按 LuckPerms 权限组 / 权限节点匹配条目，支持优先级与兜底条目；
- **悬浮姓名牌**：把前缀 / 后缀同步到玩家头顶的名字（也可单独配置，2.1.0+）；
- **名称渐变颜色**（2.1.1+）：读取数值变量，在最小 / 最大颜色之间插值出玩家名字颜色，支持十六进制；
- **自定义排序**（2.1.0+）：
  - `numeric`：按 PlaceholderAPI 数值变量排序，支持升序 / 降序；
  - `list`：按预设固定顺序排序，取值来源支持前缀、后缀或变量；
- 颜色代码（`&0`~`&f`、`&k`~`&r`）、十六进制颜色（`&#RRGGBB`、`&x&R&R&G&G&B&B`）与 PlaceholderAPI 变量；
- 一份 jar 兼容 Paper / Spigot 1.16 ~ 26.2。

## 兼容性

| 服务端 | 支持版本 |
| --- | --- |
| Paper | 1.16 ~ 26.2 |
| Spigot | 1.16 ~ 26.2 |
| Java 运行环境 | Java 8 / 11 / 16 / 17 / 21 / 25 |

插件以 **Java 8 字节码** 发布,编译基线为 Spigot API 1.16.5,因此同一份 jar 可以同时运行在
1.16 与 26.x 的服务端上。

写入方式会在运行时自动选择:

- Tab 名称:Paper 使用 Adventure 的 `playerListName(Component)`（保留十六进制颜色）,
  Spigot 退回 `setPlayerListName(String)`;
- 姓名牌 / 排序:通过计分板队伍实现;Paper 1.21.9+ 还会额外使用官方
  `Player#setPlayerListOrder(int)` 接口。

> ⚠ **姓名牌与排序都会接管玩家的计分板队伍**。如果你同时使用了其它基于队伍实现
> 彩色名字 / 碰撞规则 / 发光等功能的插件,可能会出现冲突;两者只能有一个生效。

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

  # 悬浮姓名牌(玩家头顶的名字)
  nametag:
    enabled: false
    # true: 复用条目里的 prefix / suffix; false: 使用下面的单独配置
    use-entry-values: true
    prefix: ""
    suffix: ""

  # 名称渐变颜色(2.1.1+)
  name-color:
    enabled: false
    placeholder: "%player_health%"
    min-value: 0
    max-value: 20
    min-color: "&c"
    max-color: "&a"

# Tab 列表排序
sorting:
  enabled: false
  # none / numeric / list
  mode: none

  numeric:
    placeholder: "%player_level%"
    # true: 数值大的在前; false: 数值小的在前
    descending: true

  list:
    # prefix / suffix / placeholder
    source: placeholder
    value: "%rank_rankname%"
    order:
      - "Diamond"
      - "Gold"
      - "Iron"
      - "Copper"
      - "Coal"
    # 不在列表中的玩家: last / first
    unmatched: last

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

### 悬浮姓名牌

开启 `settings.nametag.enabled` 后,玩家头顶的名字会显示为「前缀 + 玩家名 + 后缀」。
默认复用条目里的前后缀(`use-entry-values: true`),与 Tab 列表保持一致;
也可以关掉复用,单独配置姓名牌的 `prefix` / `suffix`。

姓名牌基于计分板队伍的前缀 / 后缀实现,队伍前缀上限为 64 个字符,超出部分会被安全截断。

2.1.2 起,插件还会把「前缀 + 名字 + 后缀」写入玩家的自定义名称(CustomName),
这样即使某些客户端 / 服务端版本不显示队伍前缀,头顶名字也能正确变化;队伍前缀仍然保留作为兜底。

如果开启后头顶仍然没有变化,请执行 `/tabnameprefix debug`:

- 输出中 `姓名牌 开启` 表示配置已生效;
- 每个玩家的 `队伍前缀` / `自定义名称` 应能看到你的前后缀;
- 若 `姓名牌 关闭`,说明 `settings.nametag.enabled` 没有设为 `true`,或使用的是旧版配置文件(缺少该配置块)。

### 名称渐变颜色（2.1.1+）

读取一个数值型变量,根据它相对最小值 / 最大值的位置,在两种颜色之间做线性插值:

```yaml
settings:
  name-color:
    enabled: true
    placeholder: "%player_health%"
    min-value: 0        # 0 点血对应...
    max-value: 20       # 20 点血对应...
    min-color: "&c"     # ...红色
    max-color: "&a"     # ...绿色
```

- 血量 4 → 偏红,10 → 偏黄,16 → 偏绿;
- 颜色支持 `&c`、`#FF5555` / `&#FF5555` 与 `red` / `green` 等颜色名;
- 数值超出范围会自动钳制到两端颜色,变量无法解析为数字时保持默认颜色;
- **Tab 列表使用精确的十六进制颜色**;头顶姓名牌需要同时开启 `settings.nametag`,
  会通过玩家自定义名称同步同一颜色(队伍颜色提供 16 色兜底)。

### 自定义排序

- `mode: numeric`:用 `sorting.numeric.placeholder` 解析每个玩家的数值并按大小排序,
  `descending` 控制大值还是小值在前;无法解析为数字的玩家排在最后。
- `mode: list`:按 `sorting.list.order` 的顺序给玩家排序,越靠上的值越优先:
  - `source: prefix` / `suffix`:取玩家命中条目的前缀 / 后缀(自动去除颜色代码后匹配);
  - `source: placeholder`:使用 `sorting.list.value` 变量值匹配;
  - 不在列表中的玩家由 `unmatched` 决定排在最前还是最后。

排序同时写入队伍名(旧版本客户端按队伍名排序)与 Paper 1.21.9+ 的 `playerListOrder`,
因此在支持的各版本上顺序保持一致。

## 命令与权限

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/tabnameprefix`(`/tabp`、`/tnp`) | 查看插件信息 | - |
| `/tabnameprefix reload` | 重载配置并重新应用 | `tabnameprefix.reload`(默认 OP) |
| `/tabnameprefix debug` | 查看每个玩家实际写入的 Tab 文本、队伍前缀、自定义名称与排序 | `tabnameprefix.debug`(默认 OP) |

## 统计

插件集成了 [bStats](https://bstats.org/) 匿名统计(插件 ID `35063`),会收集服务端版本、
插件版本、Java 版本,以及 `write_mode` / `placeholderapi` / `luckperms` / `nametag` /
`sorting` / `name_color` 等运行环境信息。统计数据不包含玩家名称、IP 或任何个人身份信息。

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
