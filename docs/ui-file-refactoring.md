# UI 文件整理与验收记录

## 第一批边界（2026-10-13）

保留单个 Gradle 模块和 `package com.xike.app`，只提取设置页面、主题与导航、共享日记详情。页面布局、文案、交互、状态生命周期、存储行为及依赖版本不在本批修改范围内。

开始时已有七个文件的暂存修改：`MomentRaceUiTest.kt`、`JournalViewModel.kt`、`MainActivity.kt`、`PhotoCapture.kt`、`SystemActivityResultLauncher.kt`、`XikeUi.kt`、`SystemActivityResultStateTest.kt`。这些修改涉及草稿保存、拍摄及系统结果处理；整理以工作区内容为基线，未修改其实现。工作期间这些修改已进入提交 `372cd6c`，本批不执行提交、推送或发布。

保持以下流程及协调边界：

- 此刻页面的草稿、媒体导入、拍摄返回和保存流程。
- 回望筛选、详情和编辑状态，以及轨迹打开同一日记详情的流程。
- 六种主色、三种风格、中英文和应用锁。
- 备份导出、恢复和撤销；数据库完整类名、schema、版本与组件注册。

## 文件归属

| 文件 | 本批职责与访问边界 |
| --- | --- |
| [SettingsScreen.kt](../app/src/main/java/com/xike/app/feature/settings/SettingsScreen.kt) | 设置入口、专用弹窗、预览、行组件、页面列布局和分节标题整体移动；原有私有组件保持私有，提醒弹窗保留既有设备测试访问入口。 |
| [XikeTheme.kt](../app/src/main/java/com/xike/app/ui/theme/XikeTheme.kt) | 主色、风格、字体、尺寸和主题 Provider；完整风格配置保持私有，提供当前风格、导航处理与圆角、预览圆角及原生弹窗形状所需接口。 |
| [XikeNavigationBar.kt](../app/src/main/java/com/xike/app/ui/components/XikeNavigationBar.kt) | `AppScreen` 与底部导航；选中状态、滚动请求、测量和 Effect 原样保留。 |
| [JournalEntryDetailDialog.kt](../app/src/main/java/com/xike/app/feature/archive/JournalEntryDetailDialog.kt) | 回望与轨迹共用的详情、照片页状态与专用卡片、分节组件；日期格式辅助函数供原有删除弹窗继续复用。 |

逐段核对搬迁内容，除必要访问接口外，函数体保持一致。原文件剩余函数体与实施前工作区一致；`MomentQuickAction` 仅读取新增的当前风格接口。

`XikeUi.kt` 的最后四项声明已分别归入下列文件；原文件无剩余职责后删除：

| 职责 | 剩余声明 |
| --- | --- |
| 系统结果协调（`SystemActivityCallbacks.kt`） | `SystemActivityCallbacks`、`LocalSystemActivityCallbacks` |
| 应用锁（`AppLockUi.kt`） | `AppLockDialog`、`AppLockScreen` |

## 基线和基本检查

实施前使用 Homebrew OpenJDK 17.0.20.1，JDK 自检和以下任务通过，未发现阻断基线问题（多数任务命中已有构建结果）：

```bash
./scripts/check-jdk17.sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

拆分后同一组基本检查通过：98 项 JVM 测试通过，无失败或跳过；Lint、Debug APK 与 Android 测试 APK 构建通过。`git diff --check` 通过。

## 设备验证边界

使用本次启动的 API 36 `emulator-5590`，以 `-read-only -no-snapshot` 和独立 `/tmp/xike-split-20261013-device` 数据目录运行，没有操作其他会话的数据。安装同一次构建生成的应用和测试 APK。

初始屏幕为 1080×2400、420dpi、字体 1.0。设置外观验证使用 840×1800、420dpi（宽 320dp）和字体 2.0，直接通过真实 `MainActivity` 的设置页操作，不注入日记记录或复制页面。

已有 `ThemeStyleUiTest`、`LanguageUiTest`、`NativePickerUiTest` 与提醒星期状态恢复共 7 项通过。依赖已有日记的 `PageStateRecoveryTest` 两项和 `LocalReviewNavigationTest` 一项因没有真实记录而跳过，不能计为详情与媒体验收。

新增 [SettingsAppearanceUiTest](../app/src/androidTest/java/com/xike/app/SettingsAppearanceUiTest.kt)，覆盖六主色 × 三风格 × 中英文 × 浅深色，共 72 次真实设置选择。四次仪器测试运行均通过；检查标题和副标题没有省略或截断、导航可达、设置值及 Activity 重建后的外观与语言保留。保存 72 张截图，并检查三风格、中英文、浅深色的截图。测试仅在独立设备运行，会修改其外观和语言偏好。

已有 `ResponsiveChoicesUiTest` 中三项导航回归检查通过，覆盖大字体完整标签、初始设置选择及字体、宽度与页面切换后的选中项可见性。

2026-10-13，用户授权启动独立模拟器，通过真实应用创建验收日记和媒体，补齐详情验证后继续拆分编辑和此刻页面。带摄像头的独立 AVD 位于 `/tmp/xike-refactor-avds`，设备序列号为 `emulator-5592`，使用 API 36、四核与 4GB 内存。现有 AVD 未修改。

已通过息刻的拍照入口调用系统相机，实际拍摄并确认返回；通过应用内录音按钮录制约 5 秒音频，再从“记下此刻”保存。另通过真实页面保存两条心情记录，其中一条选择工作主题。相机影像来自模拟器的软件摄像头，不代表真机拍摄验收；没有向数据库注入记录或替换媒体文件。

回望和轨迹均已打开同一条包含照片、录音的详情，确认照片展示、图片查看、录音播放与关闭流程。三项 `PageStateRecoveryTest` 在已有真实记录上通过；`LocalReviewNavigationTest` 在实际 320dp、字体 2.0 的设备配置上通过，覆盖返回原始记录后的滚动位置保留。修正了已有测试中的未合并标题节点定位和弹窗滚动区域定位，没有修改产品行为。第一批的真实记录验证缺口已补齐，API 26 和真机仍未验证。

## 第二批：编辑弹窗

提取 `JournalEntryEditDialog.kt`，编辑弹窗与 `EditPhotoGrid`、`EditPhotoTile` 整体移动；入口为必要的 `internal` 声明，专用照片组件保持私有。状态、Effect、操作协调和保存回调原样保留。JDK 自检及基本检查通过。

新增 `JournalRefactoringUiTest` 在真实 `MainActivity` 上操作前述含照片、录音的记录，覆盖注脚修改、Activity 重建、保存、再修改后取消，并从真实存储读取结果，确认原有媒体引用保留、取消不落盘。编辑阶段的设备回归通过，未注入记录或使用替代页面。

## 第三批：此刻页面与窗外组件

提取 `MomentScreen.kt` 与 `OutdoorContextUi.kt`，保留 `package com.xike.app`。此刻入口及 `DraftSecurityRow`、`BrandHeader`、`MultiImagePicker`、`SelectedPhotoTile`、`DailyQuestion` 整体移动；共享注脚、工具栏、心情选择和媒体操作仍由编辑与此刻复用，暂留 `XikeUi.kt`。窗外组件和城市弹窗整体移动，只为真实跨文件调用开放必要入口。

核对搬迁前后的函数体一致，保留所有状态、Effect、系统结果处理、草稿保存回调和原有 OptIn。未修改 `JournalViewModel`、`MainActivity`、拍摄与系统结果处理实现、依赖配置或数据库。

JDK 自检及基本检查通过。`JournalRefactoringUiTest` 两项通过，覆盖编辑回归，以及通过真实 UI 输入草稿、重建、跨页返回、保存和保存后草稿清空。设备使用实际 320dp、字体 2.0；测试仅在独立设备运行，会修改本次创建的验收记录并新增一条日记。

已有 `MomentRaceUiTest` 五项通过，覆盖照片回执完成前阻止保存、保存期间照片返回不恢复旧草稿，以及移除天气、补记时间、放弃草稿后的请求失效与保存限制释放。这组测试使用真实 ViewModel、加密文件、数据库与请求路径。

最终构建再次通过真实应用调用系统相机拍照返回，并从系统照片选择器导入之前拍摄的照片；草稿显示两张照片。连续两次点击保存后，记录数量从 4 增至 5，未出现重复保存。终止并重新启动该独立设备上的应用进程后，仍能读取 5 条记录，并从回望打开最新记录，确认两张照片显示正常。

在最终构建与上述 5 条真实应用记录上，重新运行 `PageStateRecoveryTest` 和 `LocalReviewNavigationTest`，四项均通过，无跳过。分阶段保留编辑提取前的 `JournalArchiveUi.kt`、此刻提取前的 `XikeUi.kt` 源码快照，以及对应新文件和通过的构建、设备测试日志，便于后续分阶段审查与提交。

最终 JDK 自检、98 项 JVM 测试、Lint、应用与测试 APK 构建及差异检查通过。单模块、包名、界面文案、布局和持久化边界保持不变。未提交、推送或发布；独立模拟器及本次创建的验收记录保留。共享 UI、应用入口与其他大文件提取，以及后续包迁移仍按原方案分阶段实施。

## 第四批：真正复用的 UI

本批重新核对工作区并保留前三批未提交改动，基线 JDK 17 自检及基本检查通过。所有文件继续使用 `package com.xike.app`，保留单个 Gradle 模块。

| 文件 | 实际复用关系与边界 |
| --- | --- |
| `JournalInputComponents.kt` | 此刻与编辑共用的 `MomentNoteCard`、`MomentContentToolbar`、`MomentMediaAction`；页面状态仍由原调用方持有。 |
| `RecordedAtPicker.kt` | 此刻与编辑共用的原生日期时间选择；保留校验、回调和弹窗释放方式。 |
| `MoodPicker.kt` | 此刻与编辑共用的心情选择与选择参考；说明弹窗、颜色规则和专用数据类保持私有。 |
| `JournalTopicPicker.kt` | 此刻与编辑共用的主题目录和选择组件；源主题标识和本地化显示方式不变。 |
| `JournalEntryCard.kt` | 回望与轨迹共用的记录卡片；时间格式辅助函数保持私有。 |
| `JournalPhotoUi.kt` | 此刻、编辑、回望、轨迹与详情使用的照片添加、缩略图、拼图、图库和位图加载；只将记录卡片跨文件调用所需的 `SavedJournalImage` 改为 `internal`。 |
| `XikePageComponents.kt` | 设置、轨迹、窗外信息和编辑复用的页头与内容卡片。 |

`MomentQuickAction` 仅由 `VideoAddButton` 调用，整体移动至 `VideoUi.kt` 并改为 `private`，保留现有视频按钮测试覆盖。没有删除测试资产或现有组件。

逐段核对七个新文件、视频按钮辅助函数及原文件剩余函数体，除必要可见性外保持一致；没有新建状态层或 Composable 包装层。`remember`、`rememberSaveable`、Effect、动画、图片缓存、点击与焦点逻辑随原所属组件保留。

`XikeUi.kt` 目前为 139 行，仅保留 `SystemActivityCallbacks`、`LocalSystemActivityCallbacks`、`AppLockDialog` 和 `AppLockScreen`，仍有明确职责，因此不删除。同步更新仓库代码入口说明。

设备重新使用本次独立克隆的 API 36 `emulator-5584`，AVD 保存在当前聊天的可视化目录下，四核、4GB 内存、1080×2400、420dpi。通过真实应用调用系统相机拍照、应用内录音并保存日记，另保存心情与工作主题记录；没有向数据库注入验收记录。原有 AVD 与其他任务的数据未改动。

设备回归使用实际 840×1800（320dp）、字体 2.0，安装同一次构建的应用和测试 APK：`ResponsiveChoicesUiTest` 14 项、`NativePickerUiTest` 2 项、`JournalRefactoringUiTest` 2 项、`PageStateRecoveryTest` 3 项、`LocalReviewNavigationTest` 1 项、`MomentContentUiTest` 1 项、`PhotoGalleryCacheTest` 两项真实文件移走与恢复后的重试检查，合计 25 项通过，无跳过。另在系统深色、英文、专注风格下运行 `ButtonPresentationUiTest`，1 项通过。覆盖实际组件的中英文、三种风格、文字绘制、选中状态、键盘和点击区域，以及真实记录的编辑、保存、取消和状态恢复。

本批 JDK 17 自检、98 项 JVM 测试、Lint、应用与 Android 测试 APK 构建、差异检查均通过。未修改布局、文案、交互、页面状态生命周期、存储或依赖版本；未提交、推送或发布。设备验收范围为 API 36 模拟器，API 26 与真机仍未验证。

## 第五批：应用入口、应用锁与备份弹窗

基线基本检查通过后，提取 `XikeApp.kt`、`AppLockSessionState.kt`、`BackupDialogs.kt` 和 `AppOperationDialogs.kt`。`MainActivity.kt` 缩为 546 行，生命周期、权限、认证、前后台与系统结果回调以及原有主题反馈协调函数体保持一致。

应用组合入口连同原有导航、页面保存状态、Effect、备份选择器、检查与恢复协调整体移动。锁会话 ViewModel 保留原有完整类名、构造可见性、字段及监控释放方式；备份导出状态保持原 ViewModel 生命周期，密码仍只在原有内存状态中保留。没有另建状态层，也没有改变系统启动器注册或调用顺序。

`AppLockUi.kt` 承接原有两个锁定 Composable，`SystemActivityCallbacks.kt` 承接原有系统结果协调数据类与 CompositionLocal。四项声明均已归属，`XikeUi.kt` 无剩余职责后删除。跨文件必需的应用入口和弹窗为 `internal`，私有的恢复状态与备份动作枚举继续留在所属入口文件。

逐段确认六个新文件、Activity 与主题反馈协调的函数体一致。同步更新仓库说明、代码入口与应用锁文档。没有改变单模块、包名、依赖、Room 完整类名、数据库迁移或草稿并发逻辑。

API 36 独立 `emulator-5584` 在实际 320dp、字体 2.0 下通过 13 项回归：编辑与草稿重建 2 项、页面状态恢复 3 项、本地回顾返回位置 1 项、语言切换及本地化 2 项、真实备份导出与回执生命周期 3 项、备份键盘操作 1 项、锁定页解锁操作可达性 1 项。语言测试在切换和重建后显式滚动到标签再断言，避免把大字体下的暂时不可见误判为语言切换失败；没有改变页面布局。

另外通过真实设置页导出包含 5 条日记、1 张照片与1 段录音的未加密备份，使用系统文件选择器保存与选择同一文件；新增一条日记后，恢复确认页正确提示备份 5 条与设备 6 条，确认后提示已恢复 5 条，再通过设置页撤销恢复找回 6 条。全程使用本次独立设备上创建的真实记录，没有注入备份或日记数据，也没有操作其他会话。

JDK 17 自检、98 项 JVM 测试、Lint、应用和测试 APK 构建及差异检查通过。未提交、推送或发布。设备结果限于 API 36 模拟器；真机生物识别、设备凭据和 API 26 验收未扩展。轨迹、音频、存储辅助提取及包结构迁移继续按原方案分阶段实施。

## 第六批：轨迹展示组件与弹窗

基线检查通过后提取 `JournalInsightsCards.kt`、`JournalInsightsCharts.kt`、`JournalInsightsSection.kt`、`LocalReviewDialog.kt` 与 `InsightDrilldownDialog.kt`。入口 `JournalInsightsUi.kt` 缩为 280 行，保留周期选择、统计计算状态与执行方式、下钻请求、本地回顾开关、回顾与列表滚动状态及跳转协调。

图表的绘制、测量、滚动和 Effect 随图表组件移动；原始记录弹窗保留自身选中记录、图库和恢复状态，并继续使用唯一的共享详情实现。卡片和弹窗私有辅助实现整体移动，仅开放真实跨文件调用所需的入口、分节组件及显示格式辅助函数。函数体逐段核对保持一致，没有另建状态层。

API 36 独立设备 `emulator-5584`、实际 320dp、字体 2.0 下，`PageStateRecoveryTest` 3 项、`LocalReviewNavigationTest` 1 项、`LockAndTrendLayoutUiTest` 6 项及 `ResponsiveChoicesUiTest` 14 项，合计 24 项通过，无跳过。覆盖本次创建的真实记录下钻与回顾返回状态，实际图表像素与轴线对齐、中英文文字、三种风格、字体变化以及周期切换和手动滚动保留。统计为空的布局测试使用现有组件与真实统计计算，不注入日记记录。

JDK 17 基线自检及基本检查通过；拆分后 98 项 JVM 测试、Lint、应用与测试 APK 构建及差异检查通过。未调整布局、文案、业务计算、持久化标识或依赖版本。

## 第七批：录音器、录音与播放 UI

提取 `XikeAudioRecorder.kt`、`VoiceCaptureUi.kt`、`VoicePlaybackUi.kt`、`AudioEditServices.kt`。录音器及文件生成保持原实现；录音手势、返回处理、生命周期监听、录音波形与待保存界面整体移动；播放准备、焦点、互斥、取消、临时文件归属和释放顺序随播放组件保留。播放进度控件仍供视频组件使用，时长显示及临时播放文件清理保持既有调用路径。

全部函数体逐段确认不变，私有辅助组件继续私有；没有新增状态层、代理接口或释放操作。`VoiceRecording.kt` 已无剩余职责后删除。录音导入与释放服务仅移动原有 CompositionLocal 声明，未修改 ViewModel、编辑操作、存储或数据库。

同一独立 API 36 设备、实际 320dp、字体 2.0，在英文与纸页风格下运行 `VoiceCaptureGestureTest` 8 项、真实麦克风录制并加密后的 `MediaPlaybackUiTest.audioSwitchPauseSeekAndBackgroundUseRealEncryptedRecording` 1 项及真实日记编辑与草稿 `JournalRefactoringUiTest` 2 项，合计 11 项通过，无跳过。覆盖真实录音长按、松手、取消、无障碍操作、返回和后台取消；播放器切换、暂停、拖动进度、真实音频焦点争用及后台返回。测试创建的录音和临时文件沿各测试既有清理流程释放。

JDK 17 自检、98 项 JVM 测试、Lint、应用和测试 APK 构建、差异检查通过。同步更新代码入口与文档引用，保留单模块及 `package com.xike.app`，未提交、推送或发布。此阶段未扩展为真机、API 26 或视频播放全流程验收；存储辅助能力提取及分组包迁移仍待后续阶段。

## 第八批：存储模型与备份辅助能力

本阶段与 UI 搬迁分开实施，基线基本检查通过。提取 `JournalModels.kt`，承接心情、录音、日记、数据异常与快照；提取 `JournalBackupModels.kt`，承接恢复归一化和备份摘要。完整类名、字段、默认值、JSON 键、心情与主题持久化标识保持原样。

`JournalBackupCodec.kt` 承接原有 JSON 条目解析、有界流读取与校验、流式 AES-GCM 及旧备份加解密。编码版本、文件头、算法、盐、随机数、密钥派生参数和错误分类不变。Store 的私有条目解析方法只委托既有逻辑，传入原有数量上限及安全文件名判断，未新增字符串或字节解析实现。

`JournalStore.kt` 目前为 1113 行，保留 ZIP 写入与顺序检查、版本及结束标记检查、暂存目录、附件加密写入、恢复安装、撤销快照、失败回滚、附件清理、数据库调用及全部同步锁。恢复与撤销事务没有搬迁或改写，`JournalViewModel`、`JournalDatabase`、Room schema、单模块、包名及依赖版本不变。

已有设备备份测试会清空存储并写入构造记录，不用于本阶段的真实记录验收。新增 `JournalBackupProcessTest`，仅在本次独立设备运行，以此前通过真实应用创建的日记、照片与录音为输入；分三次独立仪器进程执行：

1. 导出未加密与加密备份，保留内容指纹和随机测试密码于加密测试偏好，仅移除独立设备的一条已备份记录作为恢复前状态。
2. 新进程读取恢复前状态，检查备份分类、摘要和错误密码不改变记录；加密恢复后确认正文、主题、日期、照片与录音解密内容指纹一致，再撤销恢复并确认回到恢复前状态，最后从未加密备份恢复原始内容。
3. 第三个新进程再次读取并验证全部原始记录和媒体指纹，随后清理本测试创建的备份文件及加密测试偏好。没有清空设备或其他任务的数据，没有注入验收日记或替换媒体。

API 36 `emulator-5584` 三进程验证均通过；随后 `JournalRefactoringUiTest` 2 项与 `BackupExportLifecycleTest` 3 项通过，无跳过。测试不输出日记正文、附件字节、密码或密钥，内容比较使用实际读取后的指纹，媒体路径重命名不作为内容变化。

JDK 17 自检、98 项 JVM 测试、Lint、应用与测试 APK 构建及差异检查通过。新文件及 Store 剩余实现逐段核对符合提取边界，更新入口说明。未提交、推送或发布；包结构迁移留待后续阶段，`JournalDatabase` 继续保留原完整类名。

## 共享组件整理最终复核

本次交付继续保留 `package com.xike.app` 和单个 Gradle 模块。共享组件按第四批表中的七类职责组织，专用辅助声明保持私有；页面状态、Effect 和真实媒体处理随所属页面保留。`XikeUi.kt` 的剩余应用锁与系统回调已分别归入 `AppLockUi.kt` 和 `SystemActivityCallbacks.kt`，原文件没有剩余职责后删除。代码入口与文档引用已同步更新。

本次 JDK 17 自检及 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 全部通过，98 项 JVM 测试无失败、无跳过。独立 API 36 `emulator-5584` 使用 840×1800、420dpi、字体 2.0，安装同次构建的应用与测试 APK；共享选择控件、原生日期选择、真实媒体编辑、页面恢复、轨迹回望、此刻内容、图片缓存及备份导出共 31 项回归通过，另中英文照片失败重试 2 项通过。真实媒体记录来自本次独立环境中的应用拍摄与录音，没有注入日记数据。

逐段复核共享组件和相关页面正文一致；Gradle 配置、依赖、Manifest、数据库及 Room schema 无差异。未提交、推送或发布。设备结论仅覆盖本次 API 36 模拟器，不扩大为 API 26 或真机验收。

## 包结构第一组：主题

在用户明确同意进入包结构阶段后，仅将 `XikeTheme.kt` 移至 `ui/theme/`，使用 `com.xike.app.ui.theme`。调用方增加显式导入，主题对根包提示、图片缓存及资源的依赖保持原有实现。主题声明正文、六种颜色、三种风格、字体尺寸、原生弹窗形状和私有配置均核对不变，设置仍持久化原有枚举名称。

本组 JDK 17 自检、98 项 JVM 测试、Lint、应用及测试 APK 构建通过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下，通过真实设置操作完成浅深色、中英文、三种风格与六种主色的 72 种组合验收；四次矩阵仪器运行均成功，另主题风格、原生日期选择和语言切换 6 项回归通过。未提交、推送或发布。其余功能和共享组件仍在原包，继续分组迁移；单模块、应用 ID、系统组件、`JournalDatabase` 完整类名及 Room schema 保持不变。

## 包结构第二组：共享组件

将注脚与媒体操作、日期选择、心情与主题选择、记录卡片、照片展示、页面基础组件、导航、操作弹窗、图标、心情绘制、提示、位图缓存、图库加载和删除确认共 15 个文件迁入 `com.xike.app.ui.components`。仅修改位置、包声明和必要的显式导入；全部正文逐段确认一致，原有可见性、状态和 Effect 不变，没有增加包装层或转发声明。

JDK 17 自检、98 项 JVM 测试、Lint、应用及测试 APK 构建通过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下完成 33 项相关回归；32 项在组合运行中通过，`MomentContentUiTest` 在键盘关闭后先滚动到日期按钮再断言可见，调整后单独重跑通过。测试仍检查原草稿不变，未修改产品布局或交互。覆盖共享选择组件、原生日期选择、实际媒体编辑及恢复、两条详情入口相关状态、图片缓存、中英文照片重试和备份导出。

入口文档与受影响链接已更新并检查有效。Gradle 配置、依赖、Manifest、数据库及 Room schema 无差异。未提交、推送或发布；后续继续逐组迁移此刻、回望、轨迹、设置、应用锁及数据辅助能力。

## 包结构第三组：此刻页面

将 `MomentScreen.kt` 和 `OutdoorContextUi.kt` 迁入 `com.xike.app.feature.moment`，页面入口与窗外卡片、操作和城市弹窗的调用方使用显式导入。两个文件正文逐段确认一致，页面的 `rememberSaveable`、Effect、系统启动器、草稿、媒体导入和保存协调方式保留。系统媒体、定位和提醒实现继续保留现有位置。

JDK 17 自检、98 项 JVM 测试、Lint、应用及测试 APK 构建通过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下完成 9 项回归：真实媒体编辑与草稿恢复 2 项、媒体结果与天气保存并发 5 项、此刻媒体操作 1 项、定位说明与手动城市选择 1 项。媒体操作测试在关闭键盘后等待日期按钮重新出现再滚动和断言，单独重跑通过；没有增加固定延时或改变产品代码。

另通过真实页面调用系统相机拍摄，返回后确认照片进入草稿，重新启动应用进程后确认未保存照片仍存在。通过页面保存另一条实际拍摄记录，再次重启后在回望看到记录数量增加、最新记录包含一张照片，并打开详情确认媒体正常显示。未向数据库注入验收记录，未清空应用或更改其他设备数据。

代码入口和相关路径已更新。单模块、依赖版本、应用 ID、组件注册、数据库完整类名与 schema 不变。未提交、推送或发布；下一组迁移回望、详情与编辑。

## 包结构第四组：回望、详情与编辑

将 `JournalArchiveUi.kt`、`JournalEntryDetailDialog.kt` 和 `JournalEntryEditDialog.kt` 迁入 `com.xike.app.feature.archive`。三个文件正文逐段确认一致，筛选、月历、详情、编辑状态及 Effect 随原所属实现保留。轨迹的 `InsightDrilldownDialog` 显式引用同一个详情函数，没有复制详情或增加转发实现。

JDK 17 自检、98 项 JVM 测试、Lint、应用和测试 APK 构建通过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下通过真实媒体编辑与草稿恢复 2 项、页面状态恢复 3 项、本地回望返回位置 1 项、选择和筛选控件 14 项，共 20 项。检查覆盖后补充 `insightsOpensSharedMediaDetailAndRestoresIt`：在真实应用从轨迹概览进入原始记录列表，点击已有照片和录音记录打开共享详情，重建 Activity 后确认详情与照片仍可见，关闭后返回记录列表，并核对存储内容未改变。该新增测试独立运行通过，本组总计 21 项。

补充测试明确使用未合并的概览标签、弹窗列表及详情正文范围定位控件，避免匹配同时保留的底层页面；未构造验收日记或改变产品 UI。入口说明、路线图及相关链接已更新。单模块、依赖版本、应用 ID、组件注册、数据库完整类名与 schema 不变。未提交、推送或发布；下一组迁移轨迹、图表和本地回望。

## 包结构第五组：轨迹、图表与本地回望

将 `JournalInsightsUi.kt`、`JournalInsightsCards.kt`、`JournalInsightsCharts.kt`、`JournalInsightsSection.kt`、`LocalReviewDialog.kt` 和 `InsightDrilldownDialog.kt` 迁入 `com.xike.app.feature.insights`。仅调整位置、包声明与显式导入，六个文件正文逐段确认一致。期间选择、计算、原始记录、回望弹窗及滚动位置状态留在原实现；详情仍使用 `feature.archive.JournalEntryDetailDialog`。统计和本地回望计算辅助能力继续保留原位置。

本组 JDK 17 自检、98 项 JVM 测试、Lint、应用与测试 APK 构建通过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下通过页面状态恢复 3 项、本地回望返回位置 1 项、图表轴线标签与时间段滚动等布局 6 项、真实轨迹媒体详情及重建恢复 1 项，共 11 项，无失败。图表空记录场景使用现有计算结果，记录跳转使用通过真实应用保存的记录与媒体，没有注入验收日记。

代码入口及受影响链接已同步更新，链接检查通过。单模块、依赖版本、应用 ID、组件注册、数据库完整类名与 schema 不变。未提交、推送或发布；下一组迁移设置页面与专用弹窗。

## 包结构第六组：设置页面与专用弹窗

将 `SettingsScreen.kt` 和 `BackupDialogs.kt` 迁入 `com.xike.app.feature.settings`，包含完整设置页面、私有辅助组件、提醒弹窗及备份导出、密码和恢复确认弹窗。`PendingExport`、`BackupExportState` 随原备份实现迁移，应用入口通过显式导入调用；Activity 生命周期、认证、系统启动器和恢复、撤销协调不改写。两个文件正文逐段确认一致，生成资源与构建信息继续引用根包的 `R` 和 `BuildConfig`。

JDK 17 自检、98 项 JVM 测试、Lint、应用及测试 APK 构建通过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下完成浅深色、中英文、三种风格与六种主色的 72 种实际设置组合，四次矩阵运行全部通过。另主题、原生日期选择及语言切换 6 项，提醒状态恢复与真实备份导出生命周期 4 项通过。备份验证使用已有真实应用记录，检查后台和重建后结果保留、真实写入失败及提示确认，没有清空数据。

入口文档及受影响链接更新并检查有效。单模块、依赖版本、应用 ID、组件注册、数据库完整类名与 schema 不变。未提交、推送或发布；下一组迁移应用锁 UI 与会话状态。

## 包结构第七组：应用锁

将 `AppLockUi.kt`、`AppLockSessionState.kt` 和 `AppLockPreferences.kt` 迁入 `com.xike.app.feature.lock`。三个文件正文逐段确认一致，锁屏、对话框、会话状态、认证代次、系统活动计数、超时判断及偏好存储键不变。会话 ViewModel 保持公开构造，Activity 的生命周期、认证回调及系统启动器仍在原路径；系统活动监视器继续保留根包。

JDK 17 自检、98 项 JVM 测试、Lint、应用及测试 APK 构建通过。其中应用锁会话 16 项、锁定偏好 4 项、系统结果状态 6 项全部通过，无跳过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下通过锁屏布局 1 项、语言切换和重建 2 项、原生系统日期选择返回 2 项、后台与重建后的备份结果保留 3 项，共 8 项。该设备结果不扩展为真机生物识别或设备凭据认证验收。

入口与应用锁设计文档路径已更新并检查有效。单模块、依赖版本、应用 ID、组件注册、数据库完整类名与 schema 不变。未提交、推送或发布；下一组单独迁移明确职责的数据模型与备份辅助能力。

## 包结构第八组：数据模型与备份辅助能力

将 `JournalModels.kt`、`JournalBackupModels.kt` 和 `JournalBackupCodec.kt` 迁入 `com.xike.app.data`。三个文件正文逐段确认一致，模型字段、默认值、JSON 键、媒体限制、备份编码版本、有界读取、校验、AES-GCM 及旧备份解密均保持原样。Store 仅增加必要导入，继续统一协调恢复、撤销、附件清理及同步锁。`JournalDatabase` 只增加模型导入，完整类名、类正文、数据库版本与 Room schema 未改变。

JDK 17 自检、98 项 JVM 测试、Lint、应用及测试 APK 构建通过。独立 API 36 `emulator-5584` 的三个独立仪器进程分别完成真实应用记录和照片、录音的备份导出、恢复与撤销、重新读取验证，三次均通过。内容使用实际读取的媒体指纹比较，错误密码不会改变原数据，恢复及撤销后记录和媒体保持一致；仅清理本测试创建的验证文件及加密测试偏好，没有清空应用。

随后真实媒体编辑、草稿恢复、轨迹详情与重建、备份导出生命周期共 6 项回归通过。入口文档及链接已更新并检查有效。单模块、依赖版本、应用 ID、组件注册及数据库 schema 不变。未提交、推送或发布；后续对整体方案逐项验收，检查剩余文件职责及验证缺口。

## 职责复核补充：复用音频 UI

核对实际调用发现 `VoiceCaptureUi.kt` 被此刻与编辑复用，`VoicePlaybackUi.kt` 被此刻、编辑、详情和视频进度操作复用，将两者补入 `com.xike.app.ui.components`。正文、状态、Effect、焦点、播放互斥、临时文件及释放顺序逐段确认一致。录音器、系统音频焦点和播放协调器保留现有位置，没有建立新的系统媒体包。

JDK 17 自检、98 项 JVM 测试、Lint、应用及测试 APK 构建通过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下完成真实麦克风录音手势 8 项，以及真实媒体编辑、草稿恢复和轨迹详情 3 项；另以 `playbackIsolatedDevice=true`、英文单独实际执行加密录音播放切换与暂停、进度和后台返回测试，1 项通过。实际通过共 12 项，不将缺少前置参数的跳过计入通过。测试清理仅涉及本次创建的临时录音与未引用副本，不删除既有记录或系统照片。

入口路径与文档引用已同步更新。单模块、依赖版本、应用 ID、组件注册及数据库 schema 不变。未提交、推送或发布。剩余根包职责继续核对，统计与本地回望计算辅助能力单独确定归属。

## 职责复核补充：轨迹计算与本地回望内容

将 `JournalAnalytics.kt` 与 `LocalJournalReview.kt` 归入 `com.xike.app.feature.insights`，使周期统计、证据规则、趋势计算及本地回望内容与所属功能一起维护。两个文件正文逐段确认一致，仅调整包声明、位置与显式导入；共享日期查询继续由此刻按原调用方式使用，主题规范化仍引用现有实现。

JDK 17 自检、98 项 JVM 测试、Lint、应用与测试 APK 构建通过，其中统计计算 14 项、本地回望 3 项无失败、无跳过。独立 API 36 `emulator-5584` 在 320dp、字体 2.0 下完成页面状态恢复、本地回望返回位置、图表轴线与时间段滚动、真实媒体详情与重建共 11 项回归，全部通过。入口与路线图链接已同步更新。未提交、推送或发布；继续核对整体验收条件。

## 整体方案最终验收

| 要求 | 当前结果与证据 |
| --- | --- |
| 保护原有草稿、拍摄和系统结果修改 | 整理基线为 `372cd6c`。ViewModel、拍摄和系统结果启动器仅增加必要导入，未改写其实现。 |
| 按职责拆文件并保留页面状态 | 设置、主题、导航、详情、编辑、此刻与共享组件均已提取。39 个迁移文件正文与阶段源码基线一致；页面状态和 Effect 留在原实现，没有增加状态层或包装层。 |
| 明确剩余声明并删除空职责文件 | `XikeUi.kt` 的应用锁与系统回调已归属独立文件，`XikeUi.kt` 和 `VoiceRecording.kt` 无剩余职责后删除，没有建立 `Utils.kt`。 |
| 精简入口、轨迹、音频和存储 | MainActivity 完整 Activity 实现与基线一致；页面选择留在 XikeApp 和轨迹入口，认证及系统结果仍按原路径协调。录音器、录音 UI、播放 UI 已分离，Store 的实现调整仅为委托提取后的条目解析。 |
| 分组包结构与单模块 | theme、components、moment、archive、insights、settings、lock、data 八组均有实际职责文件，模块仍仅为 `:app`。 |
| 保持数据库、依赖及系统入口 | Gradle 配置、依赖、Manifest 和 Room schema 无差异；JournalDatabase 仅增加模型导入，仍为 `com.xike.app.JournalDatabase`、版本 5，类正文未改变。 |
| 保持恢复、撤销、清理和同步锁 | Store 继续统一协调，实际应用记录与照片、录音的三进程导出、恢复、撤销、重读及内容指纹验证通过。 |
| 设备与显示验收 | 独立 API 36 设备的 72 种设置组合、两条详情入口、编辑保存与取消、系统相机返回、跨进程草稿恢复、录音和播放、图表与页面状态均有实际验证记录。 |
| 文档与提交边界 | AGENTS 代码入口及相关文档链接已更新并验证有效；保留阶段基线、源码补丁和实际日志。按 UI 拆分、存储辅助提取、包迁移分别组织本地提交，没有推送或发布。 |

最终 JDK 17 自检及 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` 全部通过，98 项 JVM 测试无失败、无跳过。最终构建的 30 项组合回归全部通过，明确传入 `refactoringIsolated=true` 和 `playbackIsolatedDevice=true`，实际执行播放场景。此刻媒体测试等待键盘变化后的“完成”按钮实际可见再点击，随后等待日期按钮恢复；保留可见性、文字完整、原草稿不变与媒体操作断言，没有固定延时或产品布局改动。

根包保留 MainActivity、XikeApp、ViewModel、Store、完整数据库类，以及现有草稿、加密密钥、系统结果、媒体、定位、提醒、语言及启动协调能力。视频文件同时包含模型、加密存储和系统媒体读取能力，暂时整体保留；搜索辅助由 Store、DAO 与回望共同使用，保留现有协调边界。本轮未继续拆解这些能力，也没有改写草稿并发、数据库迁移或恢复事务。

设备结论限于本次独立 API 36 模拟器，不扩展为 API 26、真机生物识别、设备凭据认证或视频全流程验收。这些限制不作为已通过场景。阶段日志及源码补丁保留在本次聊天可视化目录的 `final-refactoring-evidence/`，其中早期失败日志须与对应修正后的重跑结果一起阅读，不将跳过计为通过。

## 本地提交安排

用户授权提交后，将文件拆分、存储辅助提取和包结构调整分别组织为独立提交。前两个提交保留 `com.xike.app`，从暂存区独立导出中间版本，分别通过 JDK 17 自检及全部基本检查；最终包结构使用已经完成模拟器回归的工作区实现。UI 拆分提交为 `aa5ee50`，存储辅助提交为 `ac0706e`，包迁移提交见 Git 历史。阶段记录中的“未提交”描述各阶段验证时的状态，本节记录后续授权的提交安排。未推送或发布。
