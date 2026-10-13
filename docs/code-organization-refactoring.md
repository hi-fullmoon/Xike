# 代码组织重构（2026-10-13）

## 范围与基线

按媒体基础设施、模型与平台实现、Store 协作类、ViewModel 操作协调、共享 UI 与页面入口五个阶段实施。保留单 app 模块、现有外部接口和用户可观察行为；数据库 schema、加密格式、恢复及撤销语义不在修改范围内。

开始时工作区干净。Windows 使用 Microsoft OpenJDK 17.0.10，`scripts/check-jdk17.sh` 通过。

## 第一阶段：临时播放文件清理

`pruneVoiceTemporaryFiles` 从播放 UI 提取到 `media/VoiceTemporaryFiles.kt`。ViewModel 初始化和跨进程编辑测试继续在原时机调用；只删除 cacheDir 顶层名称以 `xike-playback-` 开头的文件，保留录音候选文件。

基本检查首次因 Java NIO 本地连接初始化失败而未进入编译。重试仅在命令环境中设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.WindowsSelectorProvider`，不修改项目配置。仅设置 SelectorProvider 仍会失败，需要上述两个选项一起使用。

四项基本检查全部通过：98 项 JVM 测试无失败或跳过；Lint 为 0 错误、65 警告、7 提示；Debug APK 和 Android 测试 APK 构建通过。`git diff --check` 通过。

设备使用本次创建的 API 36 `Xike_Organization_20261013`，序列号 `emulator-5594`，320×640、160dpi、字体 1.0，初始前台为系统桌面。数据位于独立临时目录；不操作已有 AVD 数据。安装同一次构建生成的 APK 后，即时 ViewModel 重建测试通过；清理 ViewModel 后保留最新输入的测试首次在初始化等待处超时，重跑原测试通过。未调整超时或产品逻辑。

已有 `JournalDraftProcessTest` 分别在独立仪器进程执行 write、read，两阶段通过，证明草稿从磁盘跨进程读取并恢复原状态。该测试不构成真实记录或媒体验收。编辑录音跨进程回归需要先通过应用创建真实记录，尚未执行；API 26 和真机未验证。

## 第二阶段：模型与平台实现

`JournalVideo`、`OutdoorSnapshot`、`JournalDraft` 归入 `data/model`，保留 JSON 转换、默认值、校验、主题规范化和日期规则。视频存储与可寻址解密源归入 `media`，定位天气实现归入 `outdoor`，加密草稿存储归入 `data/draft`。天气显示文案仍由原来的本地化函数生成。

草稿写入协调器、偏好文件名、密钥别名、视频目录和备份限制保持原值；只调整声明归属与引用。逐段比对模型和平台实现，三个模型、草稿存储、视频存储、定位天气及本地化映射函数体保持一致；模型不再导入 Android 类型。数据库实体、迁移及 schema 未改变。

四项基本检查全部通过（98 项 JVM 测试，无失败或跳过；Lint 0 错误、65 警告、7 提示；应用和测试 APK 构建通过）。本阶段安装的两份 APK 来自同一次构建。

独立设备保留原数据，以四核、4GB 内存重启，屏幕和字体配置不变。四项 `JournalDraftStoreTest` 通过，覆盖旧会话不能覆盖新会话、排队写入顺序和加密落盘；即时 ViewModel 重建测试通过。最新输入清理测试首次在初始化等待处超时，单独重跑通过，未修改测试超时。`JournalDraftProcessTest` 在两个仪器进程分别执行 write/read，通过跨进程磁盘读取验证。

前置记录准备曾在并行构建期间因应用初始化等待超时失败，截图显示系统 UI 无响应；该次未创建成功的记录不计为验收。视频实录、真实记录附件、实际定位天气网络、API 26 和真机尚未验证，继续在后续相关阶段补充或明确限制。
