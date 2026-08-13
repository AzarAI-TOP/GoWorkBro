# Changelog

All notable changes to GoWorkBro are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[SemVer](https://semver.org/spec/v2.0.0.html).

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
