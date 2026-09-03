# GoWorkBro

本地单机的待办 / 习惯 / 专注 / 倒计时 / 打卡 Android 应用（原生 Kotlin + Jetpack Compose），
附每日自动抓取的 USTC 要闻。**无账号、无云端同步、无跨端**——数据只存本机 SQLite，随时导出 JSON 备份。

## 功能

- **待办 Todo**：正向计时 / 倒向计时（15/25/40/自定义分钟）/ 不记时；点按完成；"明天继续"完成后自动为次日生成副本；手动拖拽排序。
- **已完成分区**：已完成的待办/习惯进入独立的"已完成"折叠区（默认收起，点按展开），不再混在未完成列表里，也不会被自动清空——直到你手动删除。
- **习惯 Habit**：每日目标计数（次/分钟/小时/个/页/道 + 自定义单位记忆），进度条，每日自动重置。
- **专注计时器**：全屏正/倒计时圆环，暂停/继续/记录/放弃，基于时间戳的恢复（退后台、甚至进程被杀，回来都在），完成即记录专注段并勾选待办。
- **倒计时 Countdown**：6 色卡片、实时 D:H:M:S、剩余比例环、长按编辑、过期自动清理。
- **Today**：今日专注总时长、来源饼图、近 7 天柱状图、专注记录列表、**USTC 每日要闻**（Markdown 渲染 + 霞鹜文楷，离线缓存，可翻往期）。
- **我**：头像/昵称；起床/健身/睡觉打卡（智能按日分桶、凌晨 4 点深夜模式、历史回改、3 条 7 天趋势图）；累计/今日统计；设置（中英双语、浅深主题、4 款字体、数据导出/导入、清空数据）。

## 要闻数据链路

ZCode 定时任务（每天 08:00）抓取 USTC 官网生成日报 → 写入 Obsidian 笔记 → 发布到公开
[GitHub Gist](https://gist.github.com/AzarAI-TOP/9eab46f314c078ed87dfb1fa9667df78) →
App 从 Gist 拉取并缓存（唯一网络请求，无需任何密钥）。手动补跑：`/ustc-news`。

## 构建

```bash
# JDK 21 + Android SDK 35；wrapper 自带 Gradle 8.12
./gradlew assembleDebug            # 调试 APK
./gradlew testDebugUnitTest        # 单元测试
./gradlew assembleRelease          # 签名 APK（需 app/key.properties + jks）
```

签名：`app/key.properties` + `app/goworkbro-release.jks`（均已 gitignore）。applicationId 沿用
`com.azarai.goworkbro`，可直接覆盖安装升级。

## 数据格式

导出/导入 JSON：`format: goworkbro-data-export`，`format_version: 2`（兼容导入 v1 Flutter 版导出的
version 1 文件）。导入为整库替换，操作前建议先导出备份。
