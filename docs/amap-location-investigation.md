# 高德定位 SDK 调查记录

验证日期：2026-10-20。依赖恢复为 `com.amap.api:location:11.3.101`，保留 HTTPS、单次定位、30 秒网络超时、加密 Key 配置和已有系统定位路径。

## 验证环境与事实

- 目标为本次独立创建的 API 36 模拟器 `emulator-5560`；它提供 x86_64 和 ARM64 转译能力。没有连接真实 ARM 手机。
- SDK 的 JAR 和实际生成的 APK 均包含 ARMv7、ARM64 的 `libapssdk.so`，不包含该库的 x86_64 版本。
- 普通安装时应用进程为 x86_64，类加载器无法解析 `apssdk`；真实 `System.loadLibrary` 尝试产生 `UnsatisfiedLinkError`。应用现会在这种情况下明确提示原生组件不可用。
- 同一份 APK 通过 `--abi arm64-v8a` 安装后，应用进程变为 ARM64，类加载器可以解析该库，运行日志确认原生库加载成功。
- ARM 转译进程中，使用原有已保存 Key 的真实请求仍失败。SDK 访问了 `abroad-lzd.apilocate.amap.com` 和 `dualstack-arestapi.amap.com`，网络管理器记录实际 HTTP 状态码 500，随后 SDK 回调 `4 / 0403`。
- 只读观察器将前一域名的调用分类为定位请求，后一域名的调用未匹配到逆地理编码或鉴权路径类别。未记录完整路径、查询参数、请求体或响应体；不能据此精确归因某条服务端接口，也不能认定 Key 已通过鉴权。
- 白名单诊断类别为 `RESPONSE`，没有匹配到 SDK 响应检查代码抛异常的类别。SDK 的响应为空或不可用，并不等于应用可以确认 Key 无效。
- 模拟器的 SIM 国家配置为美国，界面此前显示的也是默认海外位置。该请求链路不能代替国内定位验证。

## 做过的对照

1. x86_64 与 ARM64 进程使用同一份最新版 APK、同一个已保存 Key，未清空数据。前者缺原生库；后者库已加载，但仍有海外请求的 HTTP 500。
2. SDK 基础库的默认区域配置已是大陆区域。测试中显式调用文档提供的 `updateCountryCode("ZH")`，没有改变所访问的海外定位域名或失败结果；因此不能把问题简单归因为漏设此参数。此临时对照没有保留到生产逻辑。
3. 精确权限下 GPS 优先的参数对照没有使最新版请求成功，未将该调整保留到生产逻辑。
4. 前一轮的代理、直连和 DNS 对照也未消除 `4 / 0403`；网络配置均已恢复。没有通过注入国内坐标或虚构日记来证明定位成功。

## 当前结论与限制

原生库架构问题已确认并增加了明确提示，但它不能单独解释 ARM 进程中的 HTTP 500。尚不能确定海外服务拒绝请求是否与服务端、授权范围、模拟器环境或 SDK 请求本身有关；不能据此断言最新版 SDK 在真机上不兼容。

保留最新版，下一步应使用国内真实 ARM 设备和匹配包名、签名的 Key 对照。若仍出现 500，可向高德提供 SDK 版本、设备和系统信息、请求时间、服务域名及状态码；不要将 Key、坐标、地址或原始 SDK 日志写进公开报告。

旧版 6.5.1 曾取得位置，再由系统服务补全海外地址。这个结果没有证明旧版的高德海外联网定位服务通过，也不足以作为长期降级依据。

## 可重复验证

本轮基本检查通过：162 项 JVM 测试无失败，格式、Lint 和调试／测试 APK 构建通过。原生库与请求选项的 3 项设备测试通过；使用现有 Key 的真实联网用例仍失败，已保留失败断言，不将其计为定位成功。

先完成[本地开发与目标 ABI 核对](local-development.md#高德定位的模拟器-abi)，用同一次构建的应用 APK 和测试 APK 安装到已核对的设备。

原生库与请求选项验证：

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" shell am instrument --abi arm64-v8a -w -e class com.xike.app.AmapRequestOptionsTest com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
```

真实联网验证需要事先在应用中保存并启用 Key、同意高德说明并授予前台位置权限：

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" shell am instrument --abi arm64-v8a -w -e class com.xike.app.AmapLiveLocationTest com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
```

该用例启动真实应用并调用生产定位路径，不修改 Key、日记或草稿。它仅输出成功标记、白名单异常类别、SDK 服务域名和区域配置标记；测试观察器不替换服务地址，结束后恢复原有 SDK 代理回调。没有已启用 Key 时跳过，不将跳过视为定位成功；有 Key 但请求失败时测试应失败，不能用构建成功替代实地定位验收。

## 城市名称与地址语言修复（2026-10-20）

审核中实际请求 Open-Meteo 城市搜索，`杭州市`、`北京市` 没有结果，`杭州` 有结果。原来的国内定位路径将高德城市名交给该搜索，可能在定位成功后仍无法添加天气。

现已取消定位路径中的城市搜索。WGS84 直接查询天气，GCJ-02 使用 `cn.hutool:hutool-core:5.8.48` 的 `CoordinateUtil.gcj02ToWgs84` 近似转换后查询；库的适用范围判断用于保留海外坐标，出站请求仍取整到三位小数。转换只用于天气网格，不用于导航或测绘。该版本已在 Maven Central 发布，库采用 Mulan PSL v2；原始许可副本随 APK 打包在 `assets/licenses/hutool-MulanPSL2.txt`。没有手写转换公式，也不再引入同名城市选择或行政后缀匹配。手动选城市继续使用原有流程。

高德请求的 `GeoLanguage` 现按本次请求开始时的应用语言设置为 `ZH` 或 `EN`，地址展示使用同一语言快照。英文地址中的中文仍沿用已有 TinyPinyin 地区词典兜底，地址拼接格式不变。

本次验证：

- JDK 17 自检、格式检查、166 项 JVM 测试、Lint、调试和设备测试 APK 构建通过。Lint 为零错误、87 项警告、12 项提示。
- 在原独立 API 36 模拟器、ARM64 应用进程上，4 项 SDK 选项／原生库检查与 2 项真实天气请求测试通过。天气测试使用公开参考坐标，验证北京、杭州的 GCJ-02 天气路径和 WGS84 英文地址保留；不伪造 SDK 回调，也不创建日记或更改 Key。
- 使用现有已启用 Key 复跑真实高德定位仍失败，安全诊断为 `RESPONSE`，服务域名与前次相同。因此尚未完成真实高德定位到天气的全流程验收，英文地址服务的实际返回也有待国内 ARM 真机验证。

提交前复核修正了界面测试对“尚未配置 Key”的隐含假设：测试按真实已保存配置检查按钮状态，并确认取消没有更改配置。在已有 Key 的独立模拟器上，此界面用例通过；独立测试配置的写入和读取分两次仪器进程运行均通过，校验了跨进程持久化、关闭保留 Key 和清除配置，未修改真实配置。修正后重新执行格式、JVM 测试、Lint 和两类调试 APK 构建，均通过。

天气与语言回归验证命令：

```bash
adb -s "${XIKE_DEVICE_SERIAL:?请先设置目标序列号}" shell am instrument --abi arm64-v8a -w -e class com.xike.app.AmapRequestOptionsTest,com.xike.app.AmapWeatherTest com.xike.app.test/androidx.test.runner.AndroidJUnitRunner
```
