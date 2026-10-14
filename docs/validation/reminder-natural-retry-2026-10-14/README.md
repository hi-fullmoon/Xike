# 提醒闹钟自然重试验收

2026-10-14，在两台独立 API 36 模拟器完成验收。普通应用进程被 SIGKILL 后，没有重新打开应用，没有再启动目标应用的仪器测试，也没有手动发送提醒广播；系统重试闹钟自然启动 `ReminderReceiver` 并完成恢复。两个场景均通过，恢复后继续观察至少 5 分钟，没有重复提交通知。

## 实际结果

以下时间均为 Asia/Shanghai。系统进程启动日志、通知回调、状态文件相互对应。

| 场景 | 设备及进程死亡时间 | 注册的最早重试时间 | 系统广播自然启动新进程 | 通知结果 |
| --- | --- | --- | --- | --- |
| 通知提交前被杀 | `emulator-5590`，PID 4386，19:16:03.597 | 19:31:03.312 | PID 5037，19:34:04.509，`ReminderReceiver` | 19:34:05.192 补发一条；累计回调 1 次 |
| 通知已提交、完成状态未保存时被杀 | `emulator-5592`，PID 5761，19:16:03.030 | 19:31:02.565 | PID 6455，19:35:02.085，`ReminderReceiver` | 沿用 19:16:02.609 提交的原通知；累计回调始终为 1 次 |

两份恢复后的状态文件都已移除 `delivery-id`，`pending-at` 推进到 2026-10-15 19:16。最终活动通知列表各只有一条息刻提醒，通知标识与中断前保存的标识一致。独立通知观察器的 PID 分别为 4185 和 5561，持续观察期间没有重启。

本次实际自然触发比注册的最早时间晚约 3 分钟和 4 分钟。当前实现使用非精确闹钟，本次结果证明后台自然恢复，不证明准点投递。

## 验证方法

1. 设备端 `arm` 阶段通过真实 `HabitPreferences` 设置下一分钟的提醒、保存真实调度记录，并正常结束仪器测试。
2. 等待实际设备时钟经过设定时间，启动普通 `MainActivity`，使用 JDWP 在生产 `showReminder` 入口或 `notify` 返回后暂停。系统前台切回 Launcher 后，以 `run-as com.xike.app kill -9 PID` 杀掉普通应用进程。
3. 清除本次调试状态，确认包仍为 `stopped=false`、前台是 Launcher、重试闹钟仍在系统中。之后只读取状态、系统通知和进程日志，不启动应用或发送提醒广播。
4. 在测试 APK 的独立进程运行真实 `NotificationListenerService`，持续保存息刻通知的标识、`postTime` 和观察时间，不读取或保存日记内容。
5. 等待原定 15 分钟间隔及系统实际投递窗口，确认自然恢复及状态提交，再持续观察至少 5 分钟，要求累计通知回调恰好为 1 次且活动通知只有一条。

正常结束设置阶段后再杀普通应用进程，是为了不让 Android 的仪器测试结束清理干扰后台闹钟。生产代码未加入测试钩子，未改变重试间隔、设备时钟或系统投递策略。

## 原始证据

- [before-submit/natural-result.json](before-submit/natural-result.json)、[after-submit/natural-result.json](after-submit/natural-result.json)：两项验收通过及唯一通知回调。
- 两个目录的 `*-debugger.txt`：实际命中的生产代码断点。
- `post-kill-package.txt`、`pre-kill-activity.txt`：进程被杀后的包状态与切回 Launcher 的证据。
- `*-persisted.xml`、`*-alarms.txt`：进程被杀后保留的投递记录与重试闹钟。
- `natural-process-logcat.txt`：原进程死亡和系统通过 `ReminderReceiver` 启动新进程的日志。
- `natural-observation.json`、`natural-early-observation.json`：只读观察记录；`elapsedSeconds` 是相应主机观察阶段的时长，不是进程死亡后的总时长。
- `natural-recovered-state.xml`、`natural-recovered-alarms.txt`：投递已确认、下一次提醒已安排的状态。
- `natural-final-observer.xml`、`natural-final-notification.txt`：持续观察结束后的累计回调和真实系统通知。
- 两个目录的 `notification.png`、`notification-ui.xml`：验收结束后的真实通知中心。

应用 APK SHA-256：`766d96f8d3d2cf3fbeab7c5c5166d54578b505e998b44e91bc5383d951e73dcf`。

测试 APK SHA-256：`4103ccf3696c5f6c9c2074477fa1632c0fbecad93bf6feb523c1ce32ab8261e4`。

## 重跑

仅允许使用本次专用名称 `xike_reminder_crash` 或 `xike_reminder_crash_post` 的独立模拟器。脚本设置阶段会修改提醒设置并清除应用通知，禁止对用户设备执行。

核对实际设备序列号、设置 JDK 17、构建并安装同一次生成的应用和测试 APK，并授予应用通知权限后运行：

```bash
export XIKE_DEVICE_SERIAL=emulator-5590
export XIKE_REMINDER_REPORT_DIR=/tmp/xike-reminder-natural-before-submit
export XIKE_REMINDER_NATURAL=before-submit
python3 scripts/test-reminder-process-recovery.py
```

另一场景使用独立设备并设置 `XIKE_REMINDER_NATURAL=after-submit`。测试脚本自行绑定仅存在于测试 APK 的观察服务；杀掉普通应用进程后不再启动目标应用。主机观察器需要重连时，可在保留原报告目录的前提下设置 `XIKE_REMINDER_RESUME_NATURAL=yes`，只恢复只读观察，不重新执行设置或启动应用。

## 范围与检查

本次验证普通进程死亡后的自然后台恢复及恢复后至少 5 分钟内没有重复通知。未覆盖强制停止、用户手机的厂商后台限制、其他 Android 版本、跨日失效策略及所有进程死亡与通知清除的组合。

`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 已通过；新增观察服务只打包进测试 APK，应用 APK 与上一阶段验收保持同一 SHA-256。
