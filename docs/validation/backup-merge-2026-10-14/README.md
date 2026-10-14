# 默认合并恢复验证记录

验证日期：2026-10-14。

## 最终行为

- 恢复摘要默认选中合并，保留本机记录，补入备份记录。同一标识的相同版本跳过，冲突版本作为独立记录保留，原文、日期与本机版本不被改写。
- 照片、录音、视频与封面按解密后的内容摘要比较，附件重命名不会导致重复导入。同一备份再次导入及合并结果重新导出后导入均不增加相同版本。
- 不同标识的独立记录仍保留；已导入的冲突副本被编辑后，再导入原备份会保留编辑结果并另存备份版本。
- 全量替换仍可选，必须再次确认。有效恢复前创建加密安全快照；无新增内容的合并不写数据库、不新增附件、不覆盖原撤销快照。
- 读取、验证、比较或快照创建失败时不替换已有内容。数据库提交后的媒体清理失败不会删除已安装媒体或回滚有效撤销快照。

## 构建与单元测试

使用 Homebrew JDK 17.0.20.1，`./scripts/check-jdk17.sh` 通过。最终代码执行以下任务通过：

```bash
./gradlew spotlessApply spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

111 项 JVM 测试通过，无失败或跳过；其中新增合并测试 6 项，覆盖相同版本、独立标识、稳定冲突副本、编辑后的副本、原记录删除后重复导入、备份内部重复与冲突、元数据变化及长 Unicode 原文。

最终调试 APK SHA-256：

```text
ebe26ba1d9c052bff464e6204268ea08908201bcf01da5810076abc2b01726ee
```

最终测试 APK SHA-256：

```text
1227fde0336cda5c941dda4a931868480dbc0f265ad1cf033ad9e85a7ab1ea37
```

## 独立设备验证

本次新建 API 36 模拟器 `xike_backup_merge_20261014`，序列号 `emulator-5590`。没有操作其他设备，没有卸载应用或清空其他任务数据。常规配置为 1080×2400、420 dpi、字体比例 1.0；窄屏配置为 840×1920、420 dpi，即 320 dp 宽，字体比例 2.0。

`BackupMergeProcessTest` 的原始记录通过真实应用页面保存，照片由系统摄像机实际拍摄，录音由实际 MediaRecorder 录制。初始备份包含两条记录；随后修改本机原版本、删除待找回记录，并通过应用新增记录。下列阶段分别在独立仪器测试进程中执行，均通过：

| 阶段 | 可观察结果 | 输出 |
| --- | --- | --- |
| 合并 | 本机修改与新增记录保留；找回缺失记录，另存原版本，共 4 条 | [merge.txt](merge.txt) |
| 重复导入 | 加密备份不增加记录或媒体文件，不替换撤销快照；缺失现有附件时失败且不改变数据 | [repeat.txt](repeat.txt) |
| 全量替换 | 仅保留备份中的 2 条记录 | [replace.txt](replace.txt) |
| 撤销替换 | 新进程恢复此前 4 条记录及全部附件内容 | [undo.txt](undo.txt) |
| 重新导出导入 | 含双方版本的合并备份再次导入不增加记录 | [verify.txt](verify.txt) |
| 再次有效合并 | 原有本机内容不变，补入缺失与冲突版本 | [undoMerge.txt](undoMerge.txt) |
| 撤销合并 | 新进程找回合并前的本机内容 | [verifyMergeUndo.txt](verifyMergeUndo.txt) |
| 再次启动读取 | 记录及照片、录音摘要与恢复前一致，撤销入口已消耗 | [finalRead.txt](finalRead.txt) |

视频由系统摄像机实际录制，并通过真实系统媒体选择器加入应用草稿。`JournalVideoWorkflowTest` 分写入、读取两个进程执行，验证加密播放、视频与封面摘要、视频重复跳过、冲突保留双方、重复导入后仍可撤销有效合并、全量替换、恢复撤销及删除撤销。结果见 [video-write.txt](video-write.txt)、[video-read.txt](video-read.txt)。

`BackupRestoreLifecycleTest` 两项通过，覆盖录音发布与恢复串行化、页面重建、后台等待、等待方取消后继续完成、撤销时的新录音发布及失败反馈保留。现有记录包含真实照片和录音；断言比较全部记录字段及解密媒体内容，允许全量替换和撤销按原约定重新命名私有副本。结果见 [lifecycle.txt](lifecycle.txt)。

`JournalStoreBackupTest` 九项通过，覆盖原有加密/未加密与旧版备份、附件恢复、撤销、错误密码、截断或缺失附件、大小限制及删除引用清理。结果见 [store-backup.txt](store-backup.txt)。这组存储边界测试使用已有测试输入；真实照片、录音与视频的证明来自上述独立流程。

## 实际界面

通过应用设置入口和真实系统文件选择器打开实际导出的 ZIP：默认合并选中，全量替换进入二次确认；取消二次确认返回方式选择，未执行替换。实际点击合并后提示现有 4 条记录，点击撤销返回此前内容。

- [中文浅色常规字号](restore-chinese-light.png)
- [英文浅色常规字号](restore-english-light.png)
- [英文深色、320 dp、两倍字体：选择与按钮](restore-english-dark-320dp-font2.png)
- [英文深色、320 dp、两倍字体：滚动至说明底部](restore-english-dark-320dp-font2-bottom.png)
- [替换二次确认](replace-confirmation.png)

窄屏大字体下说明可以完整滚动，操作按钮可见且可点击；恢复选项整行点击区域至少 48 dp。截图仅保留应用配置和备份摘要，不包含日记原文、照片、录音或密码。

## 重新审核

重新追踪 `MainActivity → XikeApp → XikeBackupFlow → JournalViewModel → JournalBackupOperations → JournalStore`，检查默认方式、替换确认、冲突标识、附件解密比较、数据库事务、失败清理、撤销快照和 ViewModel 生命周期。变更局限于恢复流程、相关测试与说明，共享主题、其他页面布局、数据库 schema 和备份格式版本未改动。最终审核未发现未解决的问题。

验证范围为 API 36 模拟器；未覆盖真机、API 26、接近附件大小上限的性能或所有视频编码。后续撤销资格设计已调整为日记写入后失效，具体规则与验证见 [撤销资格验证](../restore-undo-validity-2026-10-14/README.md)。
