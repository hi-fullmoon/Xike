# 提醒进程中断恢复验收

2026-10-14，在本次创建的独立 API 36 模拟器 `xike_reminder_crash`（`emulator-5590`）执行。分辨率 1080×2400、密度 420、字体比例 1.0。没有使用已有模拟器的数据或用户日记。

## 结果

三种中断各执行两轮，六次新进程恢复均通过。第二轮保留完整断点位置、杀进程后读取的磁盘 XML、系统闹钟记录和仪器测试结果。

| 中断位置 | 第二轮进程切换 | 杀进程后的保存状态 | 恢复结果 |
| --- | --- | --- | --- |
| `showReminder` 入口，通知尚未提交 | 5490 → 5539 | 待投递标识保留；15 分钟重试闹钟仍在系统中 | 提交一条通知，标识与磁盘一致 |
| `notify` 返回后、完成状态提交前 | 5574 → 5676 | 待投递标识保留；15 分钟重试闹钟仍在系统中 | 复用已有通知；数量和原提交时间不变 |
| 完成状态提交后、下一次闹钟替换前 | 5716 → 5779 | 待投递标识已移除，待提醒时间已推进到次日 | 保留已有通知，不再次提交 |

每次恢复后再次执行三轮生产调度及广播接收逻辑，均保持一条对应通知，且 `postTime` 不变。新的待提醒时间必须在未来，待投递标识必须已经移除。

## 方法与证据

设备端测试使用真实 `HabitPreferences` 保存下一分钟的提醒，关闭该次测试的免打扰时段，并通过真实生产调度注册闹钟。等待实际时钟到达设定时间后调用生产恢复入口。主机使用 JDK 17 的 JDWP 调试器，在生产 Kotlin 代码断点暂停整个进程，再以 `run-as com.xike.app kill -9 PID` 终止进程；没有修改生产代码加入测试钩子，没有伪造通知或写入虚构日记。

恢复验证通过新的仪器测试进程读取磁盘状态，并检查真实 `NotificationManager.activeNotifications`。通知已经提交的场景同时断言恢复前后通知的 `tag` 和 `postTime` 一致。

- `*-debugger.txt`：实际命中的生产方法与源代码行。
- `*-persisted.xml`：进程死亡后、恢复前通过 `run-as` 读取的提醒状态。
- `*-alarms.txt`：进程死亡后、恢复前的系统闹钟记录。
- `*-verify.txt`：新进程恢复断言的执行结果。
- [process-logcat.txt](process-logcat.txt)：设定时间、投递标识及各次进程 PID。
- [notification.png](notification.png)、[notification-ui.xml](notification-ui.xml)：最后一次恢复后的真实通知中心；息刻只有一条提醒。

应用 APK 的 SHA-256：`766d96f8d3d2cf3fbeab7c5c5166d54578b505e998b44e91bc5383d951e73dcf`。

测试 APK 的 SHA-256：`30d2fda7db02bf268c1b8ef638fd2ef8cc3fa853dd27f64c5a769ec8297a45a1`。

## 重跑入口

测试代码为 `app/src/androidTest/java/com/xike/app/ReminderProcessRecoveryTest.kt`，主机脚本为 `scripts/test-reminder-process-recovery.py`。脚本只允许操作名为 `xike_reminder_crash` 的专用模拟器；设备端测试会调整提醒设置并清除该专用设备的应用通知，禁止在用户设备上运行。

先创建该名称的独立模拟器，核对设备序列号，设置 `JAVA_HOME` 为 JDK 17，执行环境自检及构建，然后显式安装同一次构建的应用和测试 APK，并授予通知权限。确认设备后执行：

```bash
export XIKE_DEVICE_SERIAL=emulator-5590
export XIKE_REMINDER_REPORT_DIR=/tmp/xike-reminder-crash-report
python3 scripts/test-reminder-process-recovery.py
```

`emulator-5590` 是本次设备序列号；重跑时应填写实际核对过的独立设备序列号。

## 验证范围

本次验证了 SIGKILL 后通过新进程执行恢复逻辑，以及中断后的系统重试闹钟仍然存在。没有等待 15 分钟重试闹钟自然触发，不据此声称系统会准点重启应用或投递通知；没有覆盖用户手机的厂商后台限制、强制停止、其他 Android 版本，或所有通知清除与进程终止的并发组合。

JDK 17 自检、`spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 均通过。生产代码在本轮没有新增修改；本轮增加了设备端跨进程测试、主机调试器脚本和验收证据。

后续已完成普通应用进程死亡后的后台闹钟自然触发验收，见 [自然重试报告](../reminder-natural-retry-2026-10-14/README.md)。本报告上面的范围描述仅对应第一阶段的仪器测试恢复验证。
