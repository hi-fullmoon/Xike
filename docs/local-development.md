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

Kotlin 源码、单元测试、设备测试和 Gradle Kotlin 脚本统一使用 Spotless + ktfmt，版本固定在根目录 `build.gradle.kts`，采用四空格缩进的 Kotlin 风格。生成代码不参与格式化。

修改代码后执行自动格式化：

```bash
./gradlew spotlessApply
```

只检查格式而不修改文件时执行 `./gradlew spotlessCheck`。PR 和发布流水线均会自动检查格式；格式不符合时先在本地执行 `spotlessApply`。

Android Studio 的基本缩进设置由 `.editorconfig` 提供。也可按 [ktfmt 官方说明](https://github.com/Kotlin/ktfmt#intellij-android-studio-and-other-jetbrains-ides)安装并启用 ktfmt 插件，选择 Kotlin 风格后使用 IDE 的格式化操作；最终以 Gradle 中固定的格式化规则为准。

自检通过后执行 PR 使用的同一组验证任务：

```bash
./gradlew spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Windows 可在 JDK 17 已加入环境变量后执行：

```powershell
gradlew.bat spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

连接 API 26 以上的真机或模拟器后，运行备份恢复等设备端测试：

```bash
./gradlew connectedDebugAndroidTest
```
