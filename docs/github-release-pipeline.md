# Android GitHub Release 流水线

## 发布模型

[`Release Android app`](../.github/workflows/release.yml) 按已推送的 Git 标签构建与发布。

1. 检出标签，识别 Android Gradle 工程的位置并计算版本号。
2. 运行格式检查、单元测试和 Android Lint。
3. 在签名任务中恢复临时签名文件，构建并验证 APK 和 AAB，再生成 `SHA256SUMS.txt` 与构建信息。
4. 由独立的发布任务使用校验后的产物创建或更新 GitHub Release。

版本标签严格使用 `vMAJOR.MINOR.PATCH`，例如 `v0.2.0`。

Android `versionName` 是去掉 `v` 的标签；`versionCode` 按 `MAJOR × 1,000,000 + MINOR × 1,000 + PATCH + 1` 计算。`MAJOR` 最大为 2099，`MINOR` 和 `PATCH` 最大为 999。

## 一次性配置

### 1. 创建并妥善保管发布密钥

在仓库外执行：

```powershell
keytool -genkeypair -v `
  -keystore xike-release.jks `
  -alias xike `
  -keyalg RSA `
  -keysize 4096 `
  -storetype JKS `
  -validity 10000
```

发布过的 Android 应用必须长期使用同一签名证书才能覆盖安装。请将 JKS 文件及密码分别备份到至少两个安全位置；丢失后，已有用户将无法正常升级到使用新密钥签名的 APK。

`.gitignore` 已排除 `*.jks` 和 `*.keystore`，但仍应在提交前检查 `git status`。

### 2. 配置四个 Repository secrets

先在 PowerShell 生成单行 Base64：

```powershell
$keystoreBase64 = [Convert]::ToBase64String(
  [IO.File]::ReadAllBytes("C:\安全位置\xike-release.jks")
)
$keystoreBase64 | Set-Clipboard
```

进入仓库 `Settings → Secrets and variables → Actions`，添加以下四个 Repository secrets。

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | JKS 文件的单行 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore 密码 |
| `ANDROID_KEY_ALIAS` | 示例为 `xike` |
| `ANDROID_KEY_PASSWORD` | Key 密码 |

流水线只把 JKS 解码到 GitHub 托管运行器的临时目录，任务结束后随运行器销毁。

## 日常发布

先完成[最小发布检查清单](release-checklist.md)，确认 `main` 上的版本已经可以发布，然后创建带注释标签：

```powershell
git switch main
git pull --ff-only
git tag -a v0.2.0 -m "息刻 0.2.0"
git push origin v0.2.0
```

推送后，进入仓库 `Actions → Release Android app` 查看执行情况。

也可以在 Actions 页面手动运行工作流，输入已有标签。工作流检出该标签，再从其中的 `android/` 或早期标签的仓库根目录运行 Gradle。

## Release 产物

- `Xike-VERSION.apk`：用户可直接安装的签名 APK。
- `Xike-VERSION.aab`：用于应用商店的签名 Android App Bundle。
- `SHA256SUMS.txt`：下载文件完整性校验值。
- `build-info.txt`：标签、版本号、GitHub 工作流上下文中的提交 SHA 和仓库信息。

相同内容还会保留为 7 天的 GitHub Actions artifact，便于发布 API 临时失败时排查。

## 安全与失败处理

- 签名任务只有仓库只读权限；独立发布任务只有最小的 `contents: write` 权限，且不会接收签名密钥或密码。
- 同一标签的并发发布会排队，不会互相取消。
- 手动重跑同一标签时会替换同名附件，不会创建重复 Release。
- 测试、Lint、密钥解码、签名验证任一步失败都不会创建正式 Release。
- 如果 GitHub 在上传过程中留下草稿 Release，先在 Releases 页面检查并人工删除草稿，再重跑；不要删除已经公开且被用户下载的稳定版本。
- 发布后可下载 APK，并使用 Android SDK 的 `apksigner verify --print-certs 文件.apk` 再次核对证书指纹。

## 设计依据

- 签名任务从 Repository secrets 获取密钥和密码，发布任务使用已验证的产物。
- `gh release create --verify-tag` 保证 Release 绑定到已经推送的标签；自动生成发布说明并在附件上传完成后发布。
- Gradle Wrapper 与 `gradle/actions/setup-gradle` 提供可重复的 Gradle 版本和依赖缓存。
