# 默认主题精简验证

日期：2026-11-04。本记录补充[自定义主题与活动验证](custom-labels-2026-11-04.md)，对应验证时的本地工作区；验证时尚未提交或发布。

本记录保留默认主题精简时的验证与产物摘要。后续管理入口布局调整及最新调试产物见[主题标题行验证](topic-header-2026-11-04.md)。

## 最终行为

- 新安装默认展示工作、学习、关系、身体、兴趣、自我 6 个主题。
- 家庭、睡眠、饮食、金钱、出行、居住、其他 7 个主题默认归档，可在“管理主题 → 已归档”恢复。
- 完整内置定义、ID、原有排序和图标仍保留；活动默认值不变。
- 首次目录使用独立的 `defaultJournalLabels`，完整 `builtinJournalLabels` 继续供身份识别、旧数据迁移和旧备份兼容使用。
- 目录初始化仍通过 `INSERT IGNORE` 补齐缺失项，不覆盖已有名称、显隐状态、排序或自定义项。旧 Room 数据库升级保留全部 13 个内置主题；更早的偏好设置迁移按已有数据标记选择原有预设。
- 未改动数据库结构、草稿格式或备份格式版本，没有重写历史记录或批量归档已有目录。

## 构建与测试

JDK 17.0.20.1 环境自检通过；以下任务通过：

```bash
./gradlew spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

JVM 共 190 项测试通过，无失败、错误或跳过。新增默认值回归核对可见/归档集合、ID、排序和活动定义。

独立 API 36 ARM64 模拟器 `emulator-5586` 上，安装同次构建的调试和测试 APK，31 项设备回归通过：

- `JournalLabelsMigrationTest`：旧数据库升级后仍有 13 个可见内置主题，旧名称及引用完整；重新初始化不覆盖已有归档与排序。
- `JournalDaoTest`、`JournalStoreBackupTest`、`JournalDraftStoreTest`：数据库、备份恢复和草稿回归。
- `JournalDraftUiTest.defaultTopicsAreCompactAndUseStableIds`：默认选择器仅展示精简主题，点击仍提交稳定 ID。

另以真实 `MainActivity` 执行 `JournalTopicDefaultsUiTest` 的三个独立进程阶段，均通过：

1. `preview`：英文、纸页、深色、宽 320 dp、字体 2.0，检查六个默认主题和归档列表。
2. `fresh`：中文、呼吸、浅色、1080 × 2400、420 dpi、字体 1.0，核对默认六项；通过管理弹窗恢复“家庭”、上移排序并归档“工作”。
3. `verify`：新进程重新初始化目录，对比完整持久化结果；界面仍保留前一阶段的显隐和排序修改。

已查看对应真实截图。没有预置日记、复制页面或清空其他设备的数据。临时日志和截图位于 `/tmp/xike-topic-defaults-20261104/`。

本轮未重复运行全部设备 UI 测试；前一记录中的两个既有天气 UI 用例问题不在本次修改范围。设备结果仅覆盖 API 36 模拟器，不代表真机或 API 26 验收。

## 本次验证时的调试产物

- APK：`app/build/outputs/apk/debug/app-debug.apk`
- SHA-256：`713252a7b20de44824774c000716ac17f653a2ca8572a7339b4e72e25da10d76`
