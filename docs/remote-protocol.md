# 电脑协同 · 线协议

电脑端（独立仓库 [`goworkbro-mcp`](https://github.com/AzarAI-TOP/goworkbro-mcp)，bleak）之间的约定。协议版本 `RemoteApi.PROTOCOL = 2`（v2 只**新增** op 和 `todos.list` 的可选 `only`，v1 的每个 op 行为不变，所以旧版 PC 照旧能用；新版 PC 用 `ping.protocol >= 2` 判断新能力在不在）。

手机是唯一数据源：每个 op 都是**单次写入**，走的是 UI 用的同一批 DAO，所以 Room 的 Flow 会自动刷新界面，逻辑日规则（`done_date`、习惯跨天清零）与手点完全一致。

## 传输层（可替换）

| 通道 | 状态 | 用途 |
|---|---|---|
| adb `am broadcast` | ✅ 真机验过 | 开发/应急；插线或无线调试时零成本 |
| BLE GATT（手机广播，PC 连） | ✅ 真机验过 | 日常；不同网络、不插线 |
| 备份导出/导入 | ✅ 既有 | 全量灾难恢复，不是命令通道 |

三者共用同一份 JSON 和同一套鉴权，传输层只负责搬字节。

### 帧格式（BLE 用）

```
[4 字节大端长度][UTF-8 JSON]，单帧上限 64 KiB
```

GATT 一次属性写最多 `MTU - 3` 字节，所以必须分片重组 —— 手机端 `core/remote/Framing.kt` 与电脑端 `gwbcore.py` 各实现一次。

### BLE 参数

| 项 | UUID |
|---|---|
| 服务 | `5c0de000-9a3e-4f6b-b1d2-7e8f4a6c2d10` |
| `INFO` | `…001…` read：`{"app":"GoWorkBro","protocol":1}` |
| `UP` | `…002…` write / write-no-response：PC → 手机的帧字节流 |
| `DOWN` | `…003…` notify：手机 → PC 的帧字节流 |

手机**只广播不扫描**（无需定位权限）。Android 的 LE 地址会轮换，**不要缓存 MAC**，每次扫。

手机侧分片 `min(mtu − 3, 512)`，MTU 未知按 23；PC 侧写请求同理。

**MTU 要由 PC 主动要**：BlueZ 不会自己协商，不请求就两边都停在 23（每次属性写只剩 20 字节，3.7 KB 的待办列表要发 180 条 notify）。客户端在 `BleServer.kt#onMtuChanged` 这一侧是自适应的，PC 一请求就切到 247/517。实测（2026-11-03，Redmi 2602BRT18C ↔ Ubuntu/BlueZ，13 条待办）：MTU 23 时 `ping` 0.34 s、`todos.list` 0.5 s、`habits.list` 0.4 s；协商 MTU 后分别降到 0.09 s / 0.32 s / 0.10 s。

### 请求 / 响应的配对

帧里**没有请求 id**：一条回复靠它回显的 `op` 与请求配对。所以电脑端必须「同一时刻只有一个请求在途」，并且收到 `op` 对不上的回复就判定链路失步、丢弃连接重连，不能复用。2026-11-03 就因为 id 解析绕过了那把锁，两个并发的工具调用共用同一个待回复槽位，回复互相串台，用串来的 id 标错了待办。

Linux 侧两个坑已在 `core/remote/BleServer.kt` 里处理：写响应必须离开回调线程并重试（BlueZ 在我们 notify 在途时会拒收 response，然后等 30 s 断链）；notify 必须串行等 `onNotificationSent`。

## 请求 / 响应

```json
{"v":1,"token":"<设置 → 电脑协同 里那串>","op":"todo.add","data":{"title":"写毕设"}}
```

```json
{"ok":true,"op":"todo.add","data":{"id":"…","created":true}}
{"ok":false,"op":"todo.add","error":"bad_request","message":"缺少 title"}
```

`error` 取值：`disabled`（手机没开协同）、`unauthorized`（token 不符）、`bad_version`（PC 协议比手机新）、`bad_request`（字段不合法）、`not_found`、`bad_op`、`internal`。

`data` 可省略。字段名与备份 JSON 一致（见 `core/Backup.kt`）。

## op 表

| op | data 入参 | 返回 |
|---|---|---|
| `ping` | — | `protocol` `today` `todos` `habits` `countdowns` |
| `todos.list` | `only?` = `all` / `open` / `today` | `items[]`（与待办页同序：未完成在前，新在上） |
| `todo.add` | `title` 必填；`id?` `icon?` `color_index?` `timing_type?` `duration_minutes?` | `id` `created` |
| `todo.update` | `id` 必填；其余同上（不传的字段不动） | `id` `created` |
| `todo.done` | `id` 必填；`done?`（默认 true） | `id` `is_done` `done_date` |
| `todo.delete` | `id` | `id` `deleted` |
| `habits.list` | — | `items[]` |
| `habit.add` | `title` 必填；`id?` `icon?` `color_index?` `target_count?` `unit?` | `id` `created` |
| `habit.update` | `id` 必填；其余同上 | `id` `created` |
| `habit.check` | `id` 必填；`delta?`（不传 = 手机上的那一下：未完成 +1，已完成 −1） | `id` `current_count` `target_count` `is_completed` |
| `habit.delete` | `id` | `id` `deleted` |
| `countdowns.list` | — | `items[]`（按到期时间升序） |
| `countdown.add` | `title` + `target_at` 必填；`id?` `color_index?` | `id` `created` |
| `countdown.update` | `id` 必填；`title?` `target_at?` `color_index?` | `id` `created` |
| `countdown.delete` | `id` | `id` `deleted` |
| `today.summary` | — | `today` `todos{undone,done_today,total}` `habits{total,done}` `water{ml,goal_ml}` `sleep{today_minutes,streak,last}` `focus{sessions,minutes}` `next_countdown` |
| `water.log` | `ml?`（省略 = 第一档快捷杯量）；`id?` | `id` `ml` `today_ml` `goal_ml` |
| `water.today` | — | `date` `ml` `goal_ml` `cups[]` `logs[]{id,ml,logged_at}` |
| `sleep.list` | `days?`（默认 14，上限 370） | `from` `today_minutes` `items[]{id,sleep_at,wake_at,minutes,day}` |
| `sleep.log` | `sleep_at` + `wake_at`（本地时间）+ `id?` | `id` `minutes` `day` |
| `focus.today` | — | `date` `sessions` `minutes` `by_todo[]{todo_id,title,minutes}` |

`sleep.*` 的 `day` 是这条记录**醒来那天**（和界面统计口径一致）；`wake_at` 必须晚于 `sleep_at`、且不能是未来。`water.log` / `sleep.log` 带 `id` 时是 upsert —— 和 `*.add` 一样，重放不会多出一条。

### 校验规则

- `title`：trim 后非空，≤ 80 字
- `icon`：必须是 `CartoonIcons.all` 里的 27 个 key 之一，否则报错并列出可用值
- `color_index`：越界则夹到 0..7
- `timing_type`：`none` / `forward` / `countdown`
- `duration_minutes`：夹到 1..480
- `target_count`：夹到 1..99；`unit`：≤ 8 字，空则 `次`
- `target_at`：本地时间 `yyyy-MM-ddTHH:mm(:ss)`，无时区；存成 `yyyy-MM-ddTHH:mm:ss`

### 幂等

同一个 `id` 重复 `*.add` 会**更新**而不是插入第二条，返回 `created:false`。所以 PC 丢响应直接重试是安全的。不带 `id` 时手机生成 UUID；想拿回 id 就从响应里读。`water.log` / `sleep.log` 同理。

反过来，**不能重放的 op**（`habit.check`、`todo.done`、`*.update`、`*.delete`）在链路中断时只报 `link_lost`，由调用方先 list 核对——BLE 上「丢响应」和「写已生效」无法区分。macOS/手机端提供的 `ping.protocol` 也让新版电脑能先判断能力再调用（协议 v2 的新 op 在旧版 app 上会得到 `bad_op`）。

## 电脑端：`goworkbro-mcp`

客户端不在本仓库：**<https://github.com/AzarAI-TOP/goworkbro-mcp>**（v0.1.0 起独立发布，
`uv tool install git+https://github.com/AzarAI-TOP/goworkbro-mcp`）。

```bash
gwb token <设置页复制的串>        # 存一次到 ~/.config/gwb/config.json（0600）
gwb ping                          # 连通性（首次 ~3-6 s 含扫描）
gwb todos | habits | countdowns   # 列表（--json 给脚本）
gwb todo add "写毕设" --icon book --color 2
gwb todo done 5c32b591            # id 支持前缀；--undone 改回
gwb todo rm 5c32b591 -y           # 不加 -y 不删
gwb habit check 6f9ccd4d --delta -1
gwb cd add "二课·香文化" 2026-11-05T14:00
```

MCP（给 agent 用，13 个工具）：

```bash
pi mcp add goworkbro -- ~/.local/bin/gwb-mcp
```

连接在工具调用之间复用（之后单次 ~0.36 s），空闲 5 分钟断开；删除类必须 `confirm=true`。

## 安全

- 协同默认关闭；`设置 → 电脑协同` 打开后才生成并显示 token。
- BLE 配对是 Just Works（不做认证），导出的广播接收器任何 app 都能发 —— **真正授权的是 token**，不是链路。
- token 存在 `user_settings` 里，因此会随备份文件一起导出：**备份文件等于凭据**，别随手发出去；换机器时重新生成更省心。
- PC 端的删除类 op 默认要 `--yes`（CLI）或 `confirm=true`（MCP）。

## adb 调试配方

```bash
TOKEN='…'   # 设置 → 电脑协同 → 协同 token
# -n 不能省：Android 8+ 不会把隐式广播投给 manifest 里声明的 receiver
adb shell am broadcast -n com.azarai.goworkbro/.core.remote.AdbBridge \
  -a com.azarai.goworkbro.ADB_CMD \
  --es json "{\"v\":1,\"token\":\"$TOKEN\",\"op\":\"todos.list\"}"
adb logcat -s GWBCmd    # 响应在这里
```

中文能过，`adb shell input text` 过不了 —— 这是这条通道存在的理由之一。
