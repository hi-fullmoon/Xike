# 息刻 iOS

iPhone 开发版使用 SwiftUI，最低部署目标为 iOS 17。已在 Apple Silicon Mac 与 Xcode 27.2 上完成本地构建和模拟器验证。

## 本地运行

安装 CMake 3.24 或以上版本后，在仓库根目录运行以下命令。首次构建会下载 SQLCipher、Tink、BoringSSL、libarchive、libwebm 及其依赖。

```bash
bash ios/scripts/build-native.sh simulator
xcodebuild build -project ios/Xike.xcodeproj -scheme Xike \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build/DerivedData
```

也可以在准备原生框架之后打开 `ios/Xike.xcodeproj`，选择 iPhone 模拟器并运行 `Xike`。模拟器可在 Xcode 的 Components 中安装。

连接 iPhone 时，先执行 `bash ios/scripts/build-native.sh device`。该命令还会下载并校验高德定位 SDK 和无 IDFA 的基础库。随后在 Xcode 的 Signing & Capabilities 里选择自己的开发团队。设备构建支持可选高德定位，需要与 Bundle ID 绑定的 iOS SDK Key。在「此刻窗外」标题上 2 秒内连点 3 次打开配置。在「轨迹」标题上使用同样的操作打开 AI 回顾。

## 数据与跨端迁移

- SQLCipher 保存记录、草稿、恢复快照、历史标签和全文索引。已有记录在数据库升级时补建索引，回望分页读取。数据库与附件密钥由本机 Keychain 保存。
- 照片、录音、视频和封面使用 Tink 分段加密。播放时按范围解密，备份写入时按流读取。
- iOS 导出使用两端共通的 v11 清单。加密备份和普通 ZIP 的结构、跨端迁移步骤及旧格式范围见 [备份格式与离线阅读](../docs/backup-format.md)。
- 恢复先校验文件并展示预计新增、冲突和收藏更新数量，执行时重新核对。默认合并，重复记录按内容去重，冲突保留双方版本，替换需再次确认。最近一次有效恢复可撤销。
- 自动备份验证归档和写入结果后登记，旧文件清理失败可单独重试。后台运行时机由 iOS 决定，打开应用时会检查到期任务。授权目录可由用户通过系统文件选择器指定。
- 恢复时会检查视频文件、封面、大小和时长，并保留原文件。WebM 和 MKV 使用 libwebm 校验容器，能否在应用内播放取决于设备的原生解码能力。

## 加密备份回归

使用独立模拟器，先在 Android 息刻通过应用界面生成带密码的备份，再将文件复制到 iOS 测试应用的数据容器 `Documents/AndroidInterop.xike`。通过 `TEST_RUNNER_XIKE_INTEROP_PASSWORD` 向 Xcode 测试进程提供本次验证密码。

```bash
xcodebuild test -project ios/Xike.xcodeproj -scheme Xike \
  -destination "platform=iOS Simulator,id=${XIKE_IOS_DEVICE_ID:?请设置测试设备 ID}" \
  -derivedDataPath ios/build/DerivedData -parallel-testing-enabled NO
```

测试检查错误密码、截断尾部、正确恢复、重复合并和重新导出。导出的 `Documents/iOSInterop.xike` 可以交给 Android 应用打开。日记、附件、备份密码和验证文件保存在独立测试环境。

验证含媒体的备份时，将 `TEST_RUNNER_XIKE_INTEROP_EXPECT_MEDIA` 设为 `1`。测试会额外检查照片、视频和解码后的照片预览。

界面测试可用 `-only-testing:XikeUITests/JournalFlowTests` 单独执行，覆盖草稿重启恢复、丢弃草稿、补记时间、收藏、本地回顾下钻和隐藏入口。验证数据库升级时，先在独立模拟器运行旧版 `testDraftAndSavedEntrySurviveRelaunch`，再覆盖安装新版，传入 `TEST_RUNNER_XIKE_VERIFY_UPGRADE=1` 并执行 `testSearchIndexAfterUpgradingExistingJournal`。

在新建的独立模拟器传入 `TEST_RUNNER_XIKE_VERIFY_AUTO_BACKUP=1` 并运行 `-only-testing:XikeTests/AutomaticBackupTests`，可验证降低保留份数后，旧文件会在新备份校验成功后才删除。测试使用实际计划时间和加密备份文件。

## 验证范围

2026-11-20 使用 Xcode 27.2 完成 iPhone 的未签名 Release 构建。在 iPhone 17 模拟器上通过界面及加密备份测试，并检查了 iPhone SE 模拟器的深色、大字体显示。加密备份回归使用 Android 应用实际导出的照片及 WebM 视频，涵盖错误密码、截断文件、重复合并和重新导出。Android 应用成功合并了 iOS 导出的备份，重启后仍能读到导入的记录。

相机、录音、生物识别、后台备份触发和高德定位还需要 iPhone 真机验收。AI 回顾的联网验收需要可用的服务配置，视频的实际播放覆盖也要在目标设备上确认。

心情图与 Android 共用 [Fluent Emoji 素材](../android/app/src/main/res/drawable-nodpi/)。中文衬线标题使用 Noto Serif CJK SC 的界面字形子集，附带 SIL Open Font License。
