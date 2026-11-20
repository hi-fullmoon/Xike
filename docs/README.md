# 息刻文档

## 平台入口

| 平台 | 用户入口 | 开发入口 |
| --- | --- | --- |
| Android | [安装、功能与隐私](../android/README.md) | [Android 构建与测试](local-development.md) · [发布流水线](github-release-pipeline.md) · [发布检查清单](release-checklist.md) |
| iOS | iPhone 开发版 | [iOS 构建与验收](../ios/README.md) |

## 架构与数据

- [仓库说明](repository-overview.md)介绍双端目录、入口和主要代码路径。
- [备份格式与离线阅读](backup-format.md)说明跨端归档、加密封装及恢复范围。
- [Android 应用锁设计](app-lock-design.md)介绍身份验证和自动锁定。
- [Android 收藏与往日回望](favorites-memories.md)说明日期规则、数据兼容和测试入口。
- [心情素材许可](licenses/fluent-emoji.md)记录 Fluent Emoji 素材来源及许可。

## 验收记录

- [按日期归档的 Android 验收](validation/)包含备份、草稿、提醒、收藏及发布记录。
- Android 位置与权限可查阅[应用锁与位置权限](permission-lock-validation.md)、[此刻窗外地点来源](outdoor-location-source-validation.md)和[回望地点来源](archive-location-source-validation.md)的验证记录。
- Android 高德定位可查阅[环境与海外链路调查](amap-location-investigation.md)、[POI／AOI 显示](amap-poi-validation.md)、[配置入口](amap-hidden-entry-validation.md)和[设置弹框](amap-settings-layout-validation.md)的验证记录。
- iOS 开发版的构建、跨端恢复实测及真机验收项见 [iOS 验证范围](../ios/README.md#验证范围)。
