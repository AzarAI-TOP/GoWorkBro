# Changelog

All notable changes to GoWorkBro are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows
[SemVer](https://semver.org/spec/v2.0.0.html).

## [1.1.2] - 2026-08-16

### Added

- **倒计时长按编辑**：长按倒计时卡片可编辑标题、日期与颜色，删除入口保留。
- **全量数据导出**：设置页新增「导出所有数据」，生成带版本号、可跨设备恢复的完整快照（全部本地表 + 用户偏好 + 头像），认证凭据永不进入导出；Windows 使用系统保存对话框，Android 走系统文件选择器（SAF）。
- **健身打卡升级**：健身记录改为「持续时间 + 可选文字描述」，历史打卡时刻数据完整兼容。
- **熬夜模式**：开启后午夜至中午 12:00 的专注、健身等记录自动归入前一天；睡眠/起床配对、每日翻页、周统计与跨设备同步全部按逻辑日计算，关闭边界单调不回退。
- **待办分区视觉分隔**：待完成与已完成区域之间新增分隔。

### Fixed

- **睡眠同步重复行**：云端按 (用户, 记录日) 建立唯一键，历史重复行无损合并（保留各字段最新非空值）。
- **睡眠记录跨设备 LWW**：新增 `updated_at` 时间戳，旧设备数据不再覆盖新编辑。
- **熬夜设置离线冲突**：本地设置带脏标记与时间戳，离线修改不会被旧云端值覆盖；设置推送串行化，仅在服务器确认接受后才清除待同步状态。
- **跨逻辑日专注同步**：睡眠闭日后，归入前一天的未上传专注记录仍会持续重试，不再依赖当天可见列表。
- **设置推送竞态**：启动/登录时的默认推送不再携带干净的熬夜设置；推送前做服务端时间戳比较，读取失败即保留重试；Supabase 端新增 last-write-wins 触发器兜底。

### Security

- **新闻写入收紧**：USTC 新闻改为通过带校验的 `upsert_ustc_news` RPC 写入（拒绝未来日期、长度限制），匿名直写被拒；上传脚本不再持有任何密钥。
- **公开 schema 权限面收紧**：`anon`/`authenticated` 不再拥有全表权限，改为显式 grant 矩阵；未来新建对象也不会自动暴露。
- **匿名登录关闭、最小密码长度提升至 10 位**（Supabase Dashboard 配置；应用内校验同步收紧）。
- **RLS 策略收紧**：个人数据表仅 `authenticated` 角色可访问，`auth.uid()` 每条语句只求值一次。

### Changed

- 本地数据库 schema 升至 v6（睡眠记录 `updated_at`、同日唯一索引）。
- Supabase 变更改为版本化迁移管理（`supabase/migrations/`）。

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
