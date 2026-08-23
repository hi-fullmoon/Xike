# 息刻

> 停一刻，听见自己。

息刻是一款离线优先的个人情绪日记 Android 应用。首版包含快速记录、图片附件、周期洞察、日历回顾、本地加密存储，以及可导出至系统文件提供方（本地或云盘）的密码加密备份。

## 运行

1. 使用 Android Studio 打开本目录。
2. 确认安装 Android SDK Platform 36、Build Tools 36.1.0 和 JDK 21（Android Studio 自带的 JBR 也可以）。
3. 运行 `gradlew.bat assembleDebug`，或直接运行 `app` 配置。

应用不需要账号。日记、标签和图片索引由 Room 管理，并通过 SQLCipher 整库加密；随机数据库口令再由 Android Keystore 中不可导出的密钥封装。图片附件继续独立加密保存在应用私有目录。每条记录最多添加 9 张图片，单张上限为 20 MB；选择图片时不需要授予全量相册权限。备份在导出前使用用户输入的密码进行 AES-GCM 加密，并包含日记中的全部图片附件。
