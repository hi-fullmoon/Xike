# 息刻

> 停一刻，听见自己。

息刻是一款离线优先的个人情绪日记 Android 应用。首版包含快速记录、图片附件、周期洞察、日历回顾、本地加密存储、系统身份验证应用锁，以及可导出至系统文件提供方（本地或云盘）的密码加密备份。

## 运行

1. 使用 Android Studio 打开本目录。
2. 确认安装 Android SDK Platform 36、Build Tools 36.1.0 和 JDK 17（Android Studio 自带的 JBR 也可以）。
3. 运行 `gradlew.bat assembleDebug`，或直接运行 `app` 配置。

应用不需要账号。日记、标签和图片索引由 Room 管理，并通过 SQLCipher 整库加密；随机数据库口令再由 Android Keystore 中不可导出的密钥封装。图片附件继续独立加密保存在应用私有目录。每条记录最多添加 9 张图片，单张上限为 20 MB；选择图片时不需要授予全量相册权限。备份在导出前使用用户输入的密码进行 AES-GCM 加密，并包含日记中的全部图片附件。

应用锁使用 Android 系统的面容、指纹或设备密码验证，可设置离开应用后立即、1 分钟、5 分钟或 30 分钟自动锁定。详细取舍见 [应用锁设计](docs/app-lock-design.md)。

## 发布

推送 `vMAJOR.MINOR.PATCH` 标签后，GitHub Actions 会执行测试、Lint、签名构建，并把 APK、AAB 和 SHA-256 校验文件发布到 GitHub Release。首次使用前需要配置发布签名，完整说明见 [GitHub Release 流水线](docs/github-release-pipeline.md)。
