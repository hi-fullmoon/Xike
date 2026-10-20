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
