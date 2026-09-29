# GoWorkBro

本地单机的生活自律应用（原生 Kotlin + Jetpack Compose）：单页六模块 —— 待办 / 习惯 /
喝水 / 健身 / 起床 / 睡觉。**无账号、无云端同步**，数据只存本机 Room 数据库；手绘卡通
森林风 UI，全中文界面。

## 功能

- **待办**：不计时 / 正向计时 / 倒计时（10/20/30/45/自定义）；点按开始专注，长按编辑；
  已完成待办保留为历史。26 个手绘图标 + 8 色粉彩底色自由搭配，新建时随机配色。
- **习惯**：每日目标计数（自定义单位），每天自动重置，连续天数统计。
- **喝水**：每日 ml 目标，快捷杯子（长按改容量），近 7 天柱状图。
- **健身**：运动计时制 —— 分钟只能靠秒表挣出来（开始/暂停/结束），满 1 分钟才入账。
- **起床/睡觉**：事件配对模型，一次入睡 + 一次起床 = 一晚睡眠；起床时缺入睡记录会
  强制补填或标记通宵；连续 / 平均 / 近 7 天折线图。
- **专注番茄钟**：全屏正/倒计时；主页底部常驻进行中卡片。
- **数据纵览**：总专注统计、今日专注分布环形图、近七天睡眠时长折线。
- **作息逻辑日**：0–4 点的活动算前一天（可在设置关闭）；跨 04:00 自动翻日。
- **自动更新**：启动时检查 GitHub Releases（可关闭），后台下载新版本 APK，主页横幅
  一键安装 —— 这是全应用唯一的网络用途。

## 构建

```bash
# JDK 21 + Android SDK 36；wrapper 自带 Gradle
./gradlew assembleDebug            # 调试 APK
./gradlew testDebugUnitTest        # 单元测试（Robolectric，全离线密封）
./gradlew assembleRelease          # 签名 APK（需 app/key.properties + jks）
```

签名：`app/key.properties` + `app/goworkbro-release.jks`（均已 gitignore）。
applicationId 沿用 `com.azarai.goworkbro`，可直接覆盖安装升级。
