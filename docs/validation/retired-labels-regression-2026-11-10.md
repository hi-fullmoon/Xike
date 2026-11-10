# 主题和活动移除后的兼容回归

日期：2026-11-10。

## 当前覆盖范围

主题和活动入口已移除，设备用例不再点击“再留下一点”“管理主题”或“管理活动”。原测试类名与分阶段参数保留，测试内容改为验证历史数据兼容性：

| 测试类 | 参数及顺序 | 当前行为 |
| --- | --- | --- |
| `JournalLabelsUiTest` | `labelsPhase=write`、`read`、`verify` | 通过实际编辑界面原样保存旧记录；加密备份替换恢复、合并恢复及撤销；另起进程核对结果。 |
| `JournalTopicDefaultsUiTest` | `topicDefaultsPhase=fresh`、`preview`、`verify` | 核对历史目录的稳定标识和类型，确认首页及重建后无标签入口，目录、记录与草稿不变。`fresh` 只建立校验基线，不清空数据或重置目录。 |
| `JournalTopicManagerRecoveryTest` | `topicRecoveryPhase=write`、`verify` | 重建与短窗口切换后标签入口仍不可见，原有数据不变；另起进程核对结果。 |

其他定向参数也继续有效：`labelsPreview=true`、`managerCancelCheck=true`、`managerEmptyCheck=true`、`topicHeaderPreview=true`、`topicShortWindow=true`。它们分别检查当前首页、取消编辑、旧管理入口不可见及短窗口操作，不再操作已移除的管理界面。`previewLanguage=en/zh` 和 `previewStyle=BREATHE/PAPER/FOCUS` 可配置预览。

## 隔离与数据要求

所有上述用例都必须显式传入 `-e labelsIsolated true`。运行前按仓库规范核对序列号、AVD 名称、尺寸、密度、字体及前台页面，设置 `XIKE_DEVICE_SERIAL`，只操作该目标。

使用独立模拟器副本或专用设备，保留实际通过应用创建的旧记录；不要向数据库注入虚构记录。保存用例需要一条带历史标签且注脚没有首尾空白的记录；没有合适记录时明确失败。恢复用例会实际替换、合并及撤销该隔离环境中的数据，不能在日常使用的设备上运行。

基线仅保存记录、目录和草稿的 SHA-256 摘要，不把原文、标签名称、照片、录音或备份密码写入偏好文件或报告。备份使用本次进程随机生成的密码加密，归档和密码只留在进程内存中。恢复允许内部附件文件名变化，比较时仍核对完整记录元数据和附件解密后的真实内容摘要；不忽略附件或历史关联。

## 执行示例

先用同一次构建的调试 APK 和测试 APK 覆盖安装到已核对的隔离目标，然后逐次执行，每条命令都启动独立仪器测试进程：

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先核对隔离设备}" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "${XIKE_DEVICE_SERIAL:?请先核对隔离设备}" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "${XIKE_DEVICE_SERIAL:?请先核对隔离设备}" shell am instrument -w -e labelsIsolated true -e class 'com.xike.app.JournalLabelsUiTest#legacyLabelsSurviveSavingBackupRestoreAndRestart' -e labelsPhase write com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s "${XIKE_DEVICE_SERIAL:?请先核对隔离设备}" shell am instrument -w -e labelsIsolated true -e class 'com.xike.app.JournalLabelsUiTest#legacyLabelsSurviveSavingBackupRestoreAndRestart' -e labelsPhase read com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s "${XIKE_DEVICE_SERIAL:?请先核对隔离设备}" shell am instrument -w -e labelsIsolated true -e class 'com.xike.app.JournalLabelsUiTest#legacyLabelsSurviveSavingBackupRestoreAndRestart' -e labelsPhase verify com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
```

其他两组使用表中的类名和阶段参数。每次核对实际 JUnit 结果，不仅依赖 `adb shell am instrument` 的 shell 退出码。

## 本次结果与边界

- JDK 17 下的格式检查、单元测试、Lint、调试 APK 及设备测试 APK 构建通过。
- 在独立 API 36 ARM64 模拟器副本上，三组分阶段用例及其余定向方法均通过。使用既有应用记录和附件，没有新造记录。
- 检查了中英文、呼吸／纸页／专注风格、1080×2400 默认尺寸、1866×840 短窗口，以及 840×1866、字体比例 1.6 的窄屏大字体配置。尺寸切换恢复原设置。
- 保存、恢复与撤销后通过独立进程复核摘要。该结果覆盖实际样本，不代表全部历史数据组合、API 26 或真机验收；未执行整个设备测试套件。
- 提交前另从暂存区导出不含 AI 回顾改动的快照，格式检查、单元测试、Lint 和两个 APK 构建均通过。使用该快照的 APK 复测了中文旧记录保存、备份恢复、取消编辑、页面入口与跨进程校验；重复设置当前窗口尺寸也通过。时间流按钮直接复用页面本地化文案。
- 本次专用模拟器副本在验收后清理；原模拟器不执行恢复操作。
