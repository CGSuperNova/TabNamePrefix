# Changelog

本项目的所有重要变更都记录在此文件中。

格式遵循 [Keep a Changelog 1.1.0](https://keepachangelog.com/zh-CN/1.1.0/),
版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

## [Unreleased]

## [2.1.0] - 2026-11-07

### Added

- 悬浮姓名牌支持:新增 `settings.nametag` 配置,可把条目的前缀 / 后缀同步到玩家头顶的名字,
  也可以单独配置姓名牌的 `prefix` / `suffix`;基于计分板队伍实现,兼容 1.16 ~ 26.2。
- Tab 列表自定义排序:新增 `sorting` 配置。
  - `numeric`:按 PlaceholderAPI 数值变量排序,支持升序 / 降序,无法解析的玩家排在最后;
  - `list`:按预设固定顺序排序,取值来源支持前缀、后缀或变量,可配置未匹配玩家的位置。
- 排序同时写入计分板队伍名(旧版本客户端按队伍名排序)与 Paper 1.21.9+ 的
  `Player#setPlayerListOrder(int)` 接口。
- bStats 新增 `nametag`、`sorting` 两个自定义图表。

### Changed

- 玩家进服时改为整表刷新,让新玩家立即插入正确的排序位置。
- 项目版本号提升至 `2.1.0`。

### Notes

- 姓名牌与排序都会接管玩家的计分板队伍,可能与其它基于队伍实现功能的插件冲突。

## [2.0.1] - 2026-11-02

### Added

- 集成 [bStats](https://bstats.org/) 匿名统计(插件 ID `35063`),新增 `write_mode`、
  `placeholderapi`、`luckperms` 三个自定义图表,用于了解插件在不同服务端上的运行情况。
- 新增 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md),说明随 jar 一起分发的 bStats 组件。

### Changed

- bStats 代码通过 `maven-shade-plugin` 重定位到 `com.buzizi.tabNamePrefix.bstats`,
  避免与其他同样内置 bStats 的插件发生类冲突。
- 项目版本号提升至 `2.0.1`。

## [2.0.0] - 2026-11-01

### Added

- 新增跨版本写入层 `ListNameBridge`:运行时探测 Paper 的 Adventure
  `playerListName(Component)` 接口,不可用时自动回退到 Bukkit 的
  `setPlayerListName(String)`,并通过反射避免编译期依赖 Adventure。
- 支持 Paper / Spigot `1.16` ~ `26.2` 全版本区间。

### Changed

- 编译目标改为 **Java 8 字节码**(`--release 8`),同一份 jar 可在
  Java 8 / 11 / 16 / 17 / 21 / 25 的服务端上加载。
- 编译基线 API 调整为 Spigot API `1.16.5`,插件只引用 1.16 就存在的 Bukkit 接口。
- PlaceholderAPI 与 LuckPerms API 回归到 1.16 时代的兼容基线(`2.11.6` / `5.4`),
  运行时与更高版本仍二进制兼容。
- `plugin.yml` 的 `api-version` 调整为 `1.16`,`version` 改为由 Maven 过滤注入。
- 项目版本号提升至 `2.0.0`。

### Fixed

- 重载配置时不再丢失已应用状态的记录:若某玩家原先命中的条目被删除且没有兜底条目,
  重载 / 禁用插件后会正确还原其默认 Tab 名称,而不是残留旧前缀。

### Removed

- 移除对 `net.kyori.adventure` 的编译期依赖(改为运行时反射)。
- 移除 Java 16 的 `record` 语法,改用普通内部类,以兼容 Java 8 运行时。

## [1.1.0] - 2026-11-01

### Changed

- 适配 Paper 1.21.11:`paper-api` 升级至 `1.21.11-R0.1-SNAPSHOT`,编译目标 Java 21。
- `plugin.yml` 的 `api-version` 调整为 `1.21`。
- 移除对已废弃 `ChatColor` 的依赖,颜色代码转换改为内部实现,输出与原实现完全一致。
- 使用 `getPluginMeta()` 替代已废弃的 `getDescription()`。
- PlaceholderAPI 升级至 `2.12.2`,LuckPerms API 升级至 `5.5`。

## [1.0.0] - 2026-11-01

### Added

- 首个版本:按 LuckPerms 权限组 / 权限节点为 Tab 列表添加前缀与后缀。
- 支持 `&0` ~ `&f`、`&k` ~ `&r` 颜色代码,`&#RRGGBB` 与 `&x&R&R&G&G&B&B` 十六进制颜色。
- 支持 PlaceholderAPI 变量。
- 支持 `priority` 优先级与默认兜底条目。
- 定时刷新 Tab 名称,并提供 `/tabnameprefix reload` 重载命令。
