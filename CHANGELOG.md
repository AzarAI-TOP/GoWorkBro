# Changelog

All notable changes to GoWorkBro are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[SemVer](https://semver.org/spec/v2.0.0.html).

## [1.1.1] - 2026-08-14

### Added

- **头像云端同步**：头像图片上传 Supabase Storage（`avatars` 桶），云端只存
  对象路径，其他设备拉取时自动下载到本地缓存——Windows 与手机互相可见头像。
  （需在 Supabase Dashboard 执行 schema.sql 新增的建桶 SQL 一次；未执行时
  头像保持单机生效，其余同步不受影响。）
- **同步轮询兜底**：每 60 秒自动拉取云端变更，App 恢复前台立即拉取，
  弥补手机后台/断线期间 realtime 漏掉的事件。
- **待办排序规则重构**：未完成 Habit 强制置顶；完成待办落到列表最底部
  （完成区按完成时间倒序）；完成区不可拖动。

### Fixed

- **用户名跨端同步竞态**：登录时 `applyAuthUser` 曾抢在启动 pull 之前把
  邮箱前缀推上云，覆盖另一台设备的自定义昵称；现在邮箱前缀仅存本地，且
  登录流程先等 init/pull 完成。
- **realtime DELETE 崩溃**：删除事件此前取 `newRecord`（删除时为 null）导致
  远端删除永远不生效；现按 `eventType` 分发，删除正确落到本地。
- **每日翻页不删云端**：已完成待办翻页清除后不再在下一次 pull 时复活。
- **Habit 每日重置不同步**：重置现在带 `updated_at` 戳并推送，另一台设备
  次日不会残留昨日计数。
- **realtime 到达后 UI 不刷新**：远端变更写入 SQLite 后现在会刷新界面，
  两端的列表与资料卡实时更新。
- **pull 无条件覆盖本地更新**：todos/habits/countdowns 改为
  last-write-wins（按 `updated_at`），轮询拉取不再回退本机新改动。

### Changed

- 本地数据库 schema v4（todos/habits/countdowns 增加 `updated_at` 列，
  自动迁移保留数据）。

## [1.1.0] - 2026-08-13

### Added

- **中文字体选择**：设置页新增字体选项（系统默认 / 霞鹜文楷 / 思源黑体），
  选择持久化并在多端生效；内置 Noto Sans SC（思源黑体）可变字体，中文获得真实字重。
- 全局 CJK 字体回退链（NotoSansSC → 微软雅黑 → 苹方 → sans-serif），
  跨平台中文渲染粗细一致，消除伪粗体。

### Fixed

- 手机端邮箱登录后用户名不再显示"离线用户"：登录/恢复会话后从 Supabase
  账号派生用户名（邮箱前缀或 user_metadata），头像同步；`user_settings`
  加入云端 pull/push/realtime 同步。
- 近七天睡眠趋势图表 tooltip：显示 `HH:MM`（起床/入睡）与 `xh ym`（时长）
  可读格式；浮窗限制在图表内，不再被卡片标题遮挡。
- "我的"页面主题选择 SegmentedButton 切换时宽度不再跳变
  （语言选择器同类问题一并修复）。
- Windows 安装/升级后自动刷新图标缓存（`ie4uinit.exe -show`），
  任务栏正确显示新图标；快捷方式显式指向 exe 内嵌图标。

### Refactored

- 架构重构为分层布局：`app/`（壳）、`features/`（按域 UI）、
  `core/`（基础设施：数据库仓库、数据驱动同步注册表、主题、l10n、工具）、
  `providers/`（状态协调）。
- `DatabaseService` 拆分为 `AppDatabase`（连接/迁移/重置）+ 7 个领域仓库
  （Todo/Habit/Countdown/Focus/Sleep/Settings/NewsCache）。
- 云同步表注册表 `sync_table_registry.dart`：启动拉取与实时订阅改为
  单循环数据驱动，移除 6 份手写重复代码。
- `me_screen.dart`（1334 行）拆分：睡眠趋势图表独立为
  `features/me/widgets/sleep_charts_section.dart`。
- 共享日期/时长格式化工具 `core/utils/date_utils.dart`，消除三处重复实现。
- 测试从 8 例增至 30 例（日期工具、仓库 CRUD、同步注册表行为、
  数据库 schema 与重置），并修复 `formatHours` 分钟进位边界 bug。

## [1.0.2] - 2026-08-08

### Added

- 睡眠追踪：起床 / 健身 / 睡觉打卡、近七天睡眠趋势图、打卡记录列表。
- 隐私友好的离线设备 ID；"我的"页头像管理。
- 数据层 v3：累计统计计数器、新闻日期缓存、睡眠图表工具。
- 应用图标全套（标题栏 / tray / 安装器）；`INTERNET` 权限。
- 安装器升级保留用户数据；桌面右键菜单；i18n 全量覆盖。
- 发布脚本 `scripts/build_release.sh` 与版本一致性校验
  `scripts/release_config.py`。

### Fixed

- 云端 `sleep_records` 缺少 `workout_time` 列（PGRST204）。
- App 导航壳导航胶囊样式重设计。

## [1.0.1] - 2026-08-05

### Added

- Windows 安装器（Inno Setup）。

### Fixed

- "我的"页面空白、缺失登录入口、构建脚本问题。

### Changed

- 移除 GitHub Actions（改为本地构建与发布流程）。

## [1.0.0] - 2026-08-01

### Added

- 待办（一次性任务 + 每日习惯），正向/倒向计时。
- 倒计时（考试/截止日期）。
- 今日统计（专注时长、番茄数、时间分配饼图、7 天柱状图、USTC 每日要闻）。
- Supabase 账号登录/注册/退出，云端同步，离线模式。
- Windows 系统托盘常驻，关闭窗口隐藏到托盘。
- 中英双语界面，浅色/深色/跟随系统主题，更新检查。
- 产品级完善：i18n 全量接入、11 项 UX 修复、19 项代码质量审计修复、
  6 项对抗性审查关键 bug 修复。
