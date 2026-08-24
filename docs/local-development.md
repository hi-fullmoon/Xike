# 本地开发环境

息刻使用 JDK 17、Android SDK Platform 36 和 Build Tools 36.1.0。

在 macOS 或 Linux 上，先执行环境自检：

```bash
./scripts/check-jdk17.sh
```

如果系统找不到 Java，可将 `JAVA_HOME` 指向 Android Studio 自带的 JDK 后重试。在 macOS 上，常见路径为：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

自检通过后执行 PR 使用的同一组验证任务：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows 可在 JDK 17 已加入环境变量后执行：

```powershell
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```
