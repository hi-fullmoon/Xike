# 恢复撤销资格验证

日期：2026-10-14。

## 设计与实现

恢复成功后保留短时撤销提示，并在设置中显示“撤销上次恢复”。未改动已保存日记时，资格可以跨重启保留，不按固定天数过期。

成功新增、编辑、删除日记或撤销删除后，撤销资格失效，清理加密快照，设置入口消失；仍在显示的恢复撤销提示也会关闭。失败写入、未保存草稿、外观和语言设置不影响资格。再次有效恢复会建立新的资格；无新增内容的重复合并不重新建立资格。

资格使用已有 `app_settings` 中的 `restore-undo-token`，绑定本次快照文件。日记写入与资格失效在同一 Room 事务中提交；批量恢复在事务末尾绑定快照，避免逐条重复更新标识。撤销替换在事务开始时再次核对标识，过期操作在删除任何日记前被拒绝。未绑定的旧快照不再允许撤销。

数据库标识决定资格，文件和加密偏好清理只负责回收资源。日记提交后即使还没清理快照就结束进程，新进程仍不能撤销这次恢复。资格检查与文件清理在后台执行；读取失败按不可撤销处理，不修改已有日记。数据库 schema、备份格式、共享主题和其他页面布局未改动。

## 最终构建与验证

JDK 17.0.20.1，自检通过。以下任务通过：

```bash
./gradlew spotlessApply spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

111 项 JVM 测试通过，失败、错误和跳过均为 0。

终版调试 APK SHA-256：`ccef6e6e342805005f1695785772902197619776dd05f0e331dc5b85cadc39dd`。

测试 APK SHA-256：`163b01973cb6e4fdff4361475bd547023547b73795e7de80b59a6ca1c16d747a`。

本次新建独立 API 36 模拟器，序列号 `emulator-5590`，1080×2400、420 dpi、字体比例 1.0。测试使用通过真实应用保存的日记和实际 MediaRecorder 录音。终版的 16 个跨进程阶段均通过；重复的准备与重启阶段在下表列出代表性输出：

| 场景 | 验证结果 | 输出 |
| --- | --- | --- |
| 恢复、失败写入、草稿及外观修改 | 恢复后可撤销；插入冲突、不存在的修改和删除均回滚，草稿与外观不影响资格 | [prepareValidity.txt](prepareValidity.txt) |
| 重启 | 新进程仍可撤销，实际设置页存在入口 | [validityRestart.txt](validityRestart.txt) |
| 新增日记 | 实际应用保存新记录后，资格失效、快照清理、菜单消失 | [invalidateAdd.txt](invalidateAdd.txt) |
| 编辑日记 | 已保存记录变化后，资格失效且旧撤销被拒绝 | [invalidateEdit.txt](invalidateEdit.txt) |
| 删除日记 | 删除后不可撤销恢复，旧操作不能找回或覆盖记录 | [invalidateDelete.txt](invalidateDelete.txt) |
| 撤销删除 | 即使恢复了相同记录，也不重新启用恢复撤销资格 | [invalidateDeleteUndo.txt](invalidateDeleteUndo.txt) |
| 事务提交后未清理快照就结束进程 | 直接通过实际 DAO 提交日记修改，保留快照文件供下一进程验证 | [invalidateDirect.txt](invalidateDirect.txt) |
| 新进程读取失效状态 | 快照清理、资格不复活，拒绝撤销前后记录与媒体摘要一致 | [verifyInvalidity.txt](verifyInvalidity.txt) |

新增、编辑、删除和撤销删除场景还直接验证了捕获旧标识后调用数据库替换会失败，现有记录不变。原有合并、重复导入、替换和恢复撤销的跨进程流程也已回归通过。

## 实际界面与审核

- [英文：资格有效时显示入口](undo-menu-valid.png)
- [英文：日记修改后入口消失](undo-menu-invalid.png)
- [中文：资格说明](undo-menu-chinese.png)

实际页面确认文案正常显示，沿用原有设置行、图标、点击区域与排列方式。截图仅包含应用设置，不包含日记原文、录音、照片或密码。

重新检查了 DAO 事务、恢复快照绑定、失效清理、撤销提交校验、ViewModel 数据观察和 Snackbar 关闭路径。有效期由已保存日记的后续写入决定，草稿、媒体临时导入和设置操作不误触发失效。最终检查未发现未解决的问题。

设备证据限于 API 36 模拟器，未覆盖真机或 API 26。
