# 息刻（Xike）仓库说明

## 工程目录

| 路径 | 内容 |
| --- | --- |
| [`android/`](../android/) | Android Gradle 工程、Wrapper 和验证脚本，应用模块位于 [`android/app/`](../android/app/) |
| [`ios/Xike.xcodeproj`](../ios/Xike.xcodeproj/) | iPhone 的 Xcode 工程，SwiftUI 应用位于 [`ios/Xike/`](../ios/Xike/) |
| [`ios/native/`](../ios/native/) | iOS 使用的原生加密、归档和视频容器读取代码 |
| [`docs/`](README.md) | 开发指南、备份格式、截图和验收记录 |
| [`.github/workflows/`](../.github/workflows/) | Android PR 验证和发布流程 |

## Android 代码入口

Android 使用单个 Gradle 应用模块。应用包名为 `com.xike.app`，采用 Kotlin、Jetpack Compose、Material 3、ViewModel 和 Coroutines。Android SDK、编译工具链和应用依赖以 [Android 构建配置](../android/app/build.gradle.kts) 为准，插件与格式化配置见 [Gradle 根配置](../android/build.gradle.kts)。

应用由 `MainActivity` 进入 `XikeApp`。Compose 页面由 `JournalViewModel` 提供状态并编排异步操作，数据库读写由仓储和 Room DAO 执行。保存时，应用加密并登记附件，在数据库事务中写入日记和搜索索引，随后通过 Flow 或 Paging 刷新浏览结果。

| 入口 | 职责 |
| --- | --- |
| [`MainActivity.kt`](../android/app/src/main/java/com/xike/app/MainActivity.kt) | 权限、生物识别、应用生命周期和系统结果回调 |
| [`XikeApp.kt`](../android/app/src/main/java/com/xike/app/XikeApp.kt) | 此刻、轨迹、回望与设置的导航和状态组合 |
| [`JournalViewModel.kt`](../android/app/src/main/java/com/xike/app/JournalViewModel.kt) | 初始化、草稿、日记写入和页面操作 |
| [`JournalReadRepository.kt`](../android/app/src/main/java/com/xike/app/JournalReadRepository.kt) | 回望、搜索、收藏和轨迹的数据读取 |
| [`JournalStore.kt`](../android/app/src/main/java/com/xike/app/JournalStore.kt) 与 [`JournalDatabase.kt`](../android/app/src/main/java/com/xike/app/JournalDatabase.kt) | 日记与恢复事务、删除和恢复撤销、附件引用及 Room 迁移 |
| [`JournalDraftStore.kt`](../android/app/src/main/java/com/xike/app/data/draft/JournalDraftStore.kt) 与 [`media/`](../android/app/src/main/java/com/xike/app/media/) | 加密草稿，以及照片、录音和视频的加密读写 |
| [`JournalBackupStorage.kt`](../android/app/src/main/java/com/xike/app/data/backup/JournalBackupStorage.kt) 与 [`backup/`](../android/app/src/main/java/com/xike/app/backup/) | 备份导出、归档校验、恢复暂存及自动备份调度 |
| [`OutdoorContextRepository.kt`](../android/app/src/main/java/com/xike/app/outdoor/OutdoorContextRepository.kt) 与 [`AiReviewOperations.kt`](../android/app/src/main/java/com/xike/app/ai/AiReviewOperations.kt) | 窗外天气与地点、AI 回顾预览和发送 |
| [`ReminderScheduler.kt`](../android/app/src/main/java/com/xike/app/ReminderScheduler.kt) 与 [`feature/lock/`](../android/app/src/main/java/com/xike/app/feature/lock/) | 本地提醒和应用锁 |

Android 页面、共享组件与测试目录的完整入口见 [仓库协作规范](../AGENTS.md#android-主要代码入口)。

## iOS 代码入口

iOS 开发版以 iPhone 与 iOS 17 为目标，使用 SwiftUI、SQLCipher 和 Keychain。`XikeApp` 管理入口与应用生命周期，页面通过 `JournalAppModel` 调用 `JournalStore`。存储和媒体操作由数据层执行。

| 入口 | 职责 |
| --- | --- |
| [`XikeApp.swift`](../ios/Xike/XikeApp.swift) | 应用入口、导航、前后台生命周期和后台任务注册 |
| [`UI/`](../ios/Xike/UI/) | 此刻、回望、轨迹、设置、备份和媒体页面 |
| [`JournalAppModel.swift`](../ios/Xike/Model/JournalAppModel.swift) | 页面状态、草稿、收藏、恢复和应用锁交互 |
| [`JournalStore.swift`](../ios/Xike/Data/JournalStore.swift) | SQLCipher 数据库、草稿、日记和恢复事务 |
| [`JournalBackup.swift`](../ios/Xike/Data/JournalBackup.swift) 与 [`JournalMedia.swift`](../ios/Xike/Data/JournalMedia.swift) | 备份清单验证、加密附件、导入和按范围读取 |
| [`NativeFiles.swift`](../ios/Xike/Data/NativeFiles.swift) 与 [`XikeNative.cc`](../ios/native/XikeNative.cc) | Keychain、原生加密流、ZIP 归档和视频容器校验 |
| [`Preferences.swift`](../ios/Xike/Model/Preferences.swift) 与 [`Services/`](../ios/Xike/Services/) | 提醒、主题与语言、自动备份、天气和 AI 回顾 |

## 数据与联网

- Android 使用 Room／SQLCipher。随机数据库口令由 Android Keystore 中的密钥封装，照片、录音、视频和视频封面独立加密。当前 [Room schema 版本为 11](../android/app/schemas/com.xike.app.JournalDatabase/11.json)，全文检索使用 FTS4，兼容旧记录的主题和活动文字。
- iOS 由本机 Keychain 管理数据库和附件密钥。日记、草稿、历史标签及恢复快照存入 SQLCipher，媒体按段加密后放在应用私有目录。
- 已保存的日记可使用 `.xike` 或 `.zip` 在两端迁移。归档结构、历史格式、恢复规则及自动备份见 [备份格式与离线阅读](backup-format.md)。
- 写日记、回望和本地回顾在设备上运行。天气查询或 AI 回顾由用户主动发起。地点与天气快照保留地点文字，定位坐标仅用于请求过程。Android 的设置入口、请求范围与隐私说明见 [Android 使用说明](../android/README.md#数据与隐私)，iOS 的实现与验收见 [iOS 工程说明](../ios/README.md)。

## 构建、验收与发布

Android 的 JVM 测试覆盖统计、日期、搜索、草稿、提醒和备份格式。Android 设备测试覆盖迁移、媒体、备份恢复和 Compose 交互。[Android CI](../.github/workflows/ci.yml) 在 PR 上运行格式检查、单测、Lint 以及应用和测试 APK 构建。设备验证和正式签名发布见 [Android 本地开发环境](local-development.md)、[发布检查清单](release-checklist.md) 与 [发布流水线](github-release-pipeline.md)。

iOS 使用 [`XikeTests/`](../ios/XikeTests/) 验证 Android 备份互通，使用 [`XikeUITests/`](../ios/XikeUITests/) 检查记录流程。构建命令、模拟器结果及待完成的真机验收见 [iOS 工程说明](../ios/README.md)。

[验收记录](validation/)保存此前各次验证的环境、范围和结果。
