# Android 本地开发环境

用 Android Studio 打开 [`android/`](../android/)。应用模块为 [`android/app/`](../android/app/)，构建使用 JDK 17、Android SDK Platform 36 和 Build Tools 36.1.0。以下命令在 `android/` 目录执行。

## 环境自检

macOS 或 Linux 可使用仓库里的自检脚本。

```bash
./scripts/check-jdk17.sh
```

如果需要切换 Java，将 `JAVA_HOME` 指向已安装的 JDK 17，并把它的 `bin` 加入 `PATH`。若 Android Studio 自带的 JBR 版本为 17，macOS 上可使用以下路径：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

## 调试构建与安装

在 macOS 或 Linux 上构建调试 APK。

```bash
./gradlew :app:assembleDebug
```

在 Windows 上使用 `gradlew.bat :app:assembleDebug`。也可以在 Android Studio 中选择设备并运行 `app` 配置。

用 `adb devices -l` 核对已连接的设备，将选定序列号设为 `XIKE_DEVICE_SERIAL`，再安装构建生成的 APK。

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" install -r app/build/outputs/apk/debug/app-debug.apk
```

## 格式化与本地检查

Kotlin 源码、单元测试、设备测试和 Gradle Kotlin 脚本统一使用 Spotless + ktfmt，版本固定在 [`android/build.gradle.kts`](../android/build.gradle.kts)，采用四空格缩进的 Kotlin 风格。生成代码不参与格式化。

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

## 设备端测试

连接 API 26 及以上的独立测试设备。先用 `adb devices -l` 核对设备并设置 `XIKE_DEVICE_SERIAL`，再安装同一次构建生成的两个 APK 并运行设备端测试：

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" shell am instrument -w com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
```

主题和活动入口移除后的历史数据兼容回归见[分阶段设备验证说明](validation/retired-labels-regression-2026-11-10.md)。涉及保存或恢复的用例必须使用独立环境，并显式传入 `labelsIsolated=true`；设备操作仍须按仓库规范限定到已核对的序列号。

## 高德定位的模拟器 ABI

当前高德定位 SDK 提供 `armeabi-v7a` 和 `arm64-v8a` 原生库。多 ABI 调试 APK 在 x86_64 模拟器上默认可能以 x86_64 进程启动，无法解析高德的 `libapssdk.so`；这与 Key 配置无关。

先按仓库规则核对目标设备，并设置 `XIKE_DEVICE_SERIAL`。检查设备是否同时提供 ARM64 ABI 和原生转译能力：

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" shell getprop ro.product.cpu.abilist
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" shell getprop ro.dalvik.vm.native.bridge
```

只有设备确实支持 ARM64 转译时，才可用同一份 APK 的 ARM64 库进行验证，`-r` 保留应用数据：

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" install -r --abi arm64-v8a app/build/outputs/apk/debug/app-debug.apk
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" shell am instrument --abi arm64-v8a -w -e class com.xike.app.AmapRequestOptionsTest com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
```

该用例检查 APK 内的 ARM 库、当前类加载器的原生库解析能力、HTTPS 和单次请求选项。ARM 转译模拟器仍不等同于真实 ARM 设备；解析到原生库也不代表联网定位已成功。[调查记录](amap-location-investigation.md)区分了这两层结果。
