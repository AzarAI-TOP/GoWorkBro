# GoWorkBro 🍅

仿"番茄TODO"风格的待办与专注管理软件，支持 Android 和 Windows 双端。

## 功能

| 模块 | 功能 |
|------|------|
| **待办** | TODO（正向计时/倒向计时/不记时，明天继续选项）+ 习惯（每日自动重置，量词选择） |
| **倒计时** | 创建倒计时事件，到第二天自动删除，实时显示剩余时间 |
| **Today** | 专注时间饼图、数据可视化、USTC 每日要闻渲染 |
| **Me** | 个人信息、起床/睡觉打卡、专注统计、后端连接设置 |

## 架构

```
GoWorkBro/
├── lib/                    # Flutter 应用
│   ├── main.dart           # 入口 + 自适应导航 (手机底部导航 / 电脑侧边导航)
│   ├── models/             # 数据模型 (Todo, Habit, Countdown, FocusSession, SleepRecord)
│   ├── services/           # 数据库(SQLite) + API(后端同步)
│   ├── providers/          # 状态管理 (Provider)
│   ├── theme/              # 番茄TODO风格主题
│   └── screens/            # 四个主界面
│       ├── todo_screen.dart
│       ├── countdown_screen.dart
│       ├── today_screen.dart
│       └── me_screen.dart
├── server/                 # Go 后端 (REST API)
│   ├── main.go
│   ├── go.mod
│   └── goworkbro-server.exe
└── pubspec.yaml
```

## 快速开始

### Flutter 应用

```bash
# 确保 flutter 在 PATH 中
export PATH="/c/flutter/bin:$PATH"

# 安装依赖
cd D:\Workspace\GoWorkBro
flutter pub get

# 运行 (Windows 桌面)
flutter run -d windows

# 运行 (Android)
flutter run -d android

# 构建 Windows 发布版
flutter build windows --release

# 构建 Android APK
flutter build apk --release
```

### Go 后端

```bash
cd D:\Workspace\GoWorkBro\server

# 构建
go build -o goworkbro-server.exe .

# 运行 (默认端口 8765)
./goworkbro-server.exe

# 自定义端口和 Obsidian 路径
GOWORKBRO_PORT=9000 OBSIDIAN_VAULT_PATH="C:\Users\ASUS\Documents\Notes" ./goworkbro-server.exe
```

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/api/todos/sync` | 同步 TODO 列表 |
| GET | `/api/todos` | 获取所有 TODO |
| POST | `/api/habits/sync` | 同步习惯列表 |
| GET | `/api/habits` | 获取所有习惯 |
| POST | `/api/focus-sessions` | 记录专注会话 |
| GET | `/api/ustc-news/today` | 今日 USTC 要闻 |
| GET | `/api/ustc-news?date=YYYY-MM-DD` | 指定日期 USTC 要闻 |
| GET | `/api/ustc-news/dates` | 可用日期列表 |

## 技术栈

- **前端**: Flutter 3.44 + Dart 3.12
- **后端**: Go 1.26 + rs/cors
- **数据库**: SQLite (sqflite_common_ffi — 跨平台)
- **状态管理**: Provider
- **图表**: fl_chart
- **Markdown**: flutter_markdown (USTC 要闻渲染)

## 环境要求

- Flutter 3.44+ (stable channel)
- Go 1.26+ (后端)
- Android SDK 36+ (Android 端)
- Visual Studio 2022+ with C++ workload (Windows 桌面端)
- Java 21 (Android 构建)
