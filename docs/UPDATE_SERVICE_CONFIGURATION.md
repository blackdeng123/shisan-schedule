# 闪电课表更新服务配置与自测

> 第一次配置时，请先阅读更详细的 [新手操作手册](UPDATE_SERVICE_BEGINNER_GUIDE.md)。本文件主要作为配置参数、测试用例和故障恢复参考。

本文用于配置“腾讯云 COS 私有桶 + EdgeOne”更新分发，并逐项验证 Android 客户端的检查、下载、校验和安装流程。更新始终由用户确认，不支持静默安装或强制更新。

## 1. 架构与边界

```text
发布电脑
  └─ publish-update.ps1
       ├─ 使用固定 Release 密钥构建 APK
       ├─ 从 APK 读取包名和版本并计算大小、SHA-256
       └─ 先上传版本化 APK，最后上传 latest.json
              │
              ▼
腾讯云 COS 私有桶（广州） ── EdgeOne 私有回源 ── 中国大陆 Android 用户
                                              ├─ 启动时最多每 24 小时检查一次
                                              └─ 设置页随时手动检查
```

客户端只信任 HTTPS；Debug 构建额外允许通过 `adb reverse` 访问 `http://127.0.0.1`。COS 源站保持私有，用户只访问 EdgeOne 域名。更新服务不需要数据库、KV、Blob、WorkManager 或服务端计算。

当前 Android SDK 已将 `ACTION_INSTALL_PACKAGE` 标记为弃用，因此客户端使用 `ACTION_VIEW`、`application/vnd.android.package-archive` 和 FileProvider `content://` URI 打开系统安装器；未知来源授权仍使用 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`。这避免继续依赖弃用 API，不改变用户确认安装的安全边界。

## 2. 本地配置

### 2.1 Release 签名

复制示例并填写本机配置：

```powershell
Copy-Item .\keystore.properties.example .\keystore.properties
```

项目复用 `keys/kebiao`。可用以下命令确认别名，命令会交互式要求密钥库密码，但不会把密码打印到终端：

```powershell
keytool -list -v -keystore .\keys\kebiao
```

把输出中的 Alias name 填到 `keyAlias`。`keystore.properties` 已加入 `.gitignore`。缺少该文件时 Debug 可以构建，任何名称含 `Release` 的 Gradle 任务都会明确失败。固定签名密钥必须备份；签名私钥丢失后，已安装用户无法原位升级。

### 2.2 更新地址与 COS

```powershell
Copy-Item .\update.properties.example .\update.properties
```

填写：

```properties
manifestUrl=https://update.example.com/releases/latest.json
publicBaseUrl=https://update.example.com/releases
cosBucket=campuspro-update-APPID
cosRegion=ap-guangzhou
cosAlias=campuspro-update
debugManifestUrl=http://127.0.0.1:8080/update-server/dist/test/releases/latest.json
```

`manifestUrl` 和 `publicBaseUrl` 的域名必须是 EdgeOne 加速域名。正式地址必须为 HTTPS；示例域名不能用于发布。

### 2.3 COSCLI v1.0.8

从腾讯云官方页面下载 Windows amd64 版本，重命名为 `coscli.exe` 并放入 `PATH`，然后验证：

```powershell
coscli --version
coscli config init
coscli config show
```

建议使用 CAM 子用户或临时密钥，配置文件默认位于用户目录 `~/.cos.yaml`，不要放进仓库。桶别名必须与 `update.properties` 的 `cosAlias` 一致。

发布账号只授予目标桶所需的读取元数据和上传权限。下面是权限范围示意，替换 `APPID`、桶名和地域，并在 CAM 控制台保存后关联发布子用户：

```json
{
  "version": "2.0",
  "statement": [
    {
      "effect": "allow",
      "action": [
        "cos:HeadBucket",
        "cos:GetBucket",
        "cos:HeadObject",
        "cos:PutObject",
        "cos:InitiateMultipartUpload",
        "cos:UploadPart",
        "cos:CompleteMultipartUpload",
        "cos:ListMultipartUploads",
        "cos:ListParts"
      ],
      "resource": [
        "qcs::cos:ap-guangzhou:uid/APPID:campuspro-update-APPID",
        "qcs::cos:ap-guangzhou:uid/APPID:campuspro-update-APPID/releases/*"
      ]
    }
  ]
}
```

### 2.4 腾讯云控制台

1. 在广州创建标准存储 COS 桶，访问权限设为“私有读写”，不要开启静态网站公共读。
2. 准备用于更新的中国大陆域名并完成 ICP 备案。
3. 在 EdgeOne 添加站点与加速域名，源站类型选择“对象存储源站 > 腾讯云 COS”，选择目标桶。
4. 开启“私有访问授权”，允许 EdgeOne 对该桶执行 `HeadObject`、`OptionsObject`、`GetObject`。
5. 配置 CNAME，把更新域名解析到 EdgeOne；部署托管 HTTPS 证书并开启自动续期。
6. 规则引擎按路径配置缓存：`/releases/latest.json` 为 60 秒并回源校验；版本化 `.apk` 为 365 天。不要覆盖旧版本 APK。
7. 在 EdgeOne 数据分析和 COS 用量概览中设置流量、回源和费用告警。

Android 原生请求不受浏览器 CORS 限制，本方案不需要为 App 单独开放 CORS。

### 2.5 是否开启日志存储

不需要。EdgeOne 自带请求、流量、状态码、缓存命中率等聚合指标，足以完成基础监控和本文件的上线验收。只有需要逐请求审计、长期排障或安全分析时才开启实时日志投递；投递到 CLS 会额外产生流量与存储费用。初期建议不开启，先设置指标告警。

## 3. 本地完整升级自测

### 3.1 准备条件

- 安装 JDK 17、Android SDK build-tools、ADB、PowerShell 7 和 Python 3。
- 启用手机开发者选项与 USB 调试，执行 `adb devices` 能看到设备状态为 `device`。
- Debug 更新地址保持示例中的 `127.0.0.1`。
- 手机上没有签名不同的 `com.shisan.campuspro.debug`；如有，先卸载。

### 3.2 执行

在仓库根目录运行：

```powershell
.\update-server\prepare-local-test.ps1
python -m http.server 8080
```

另开一个终端确认端口映射仍存在：

```powershell
adb reverse tcp:8080 tcp:8080
```

预期过程：

1. 手机已安装 `1.0.0-test`（versionCode 100）。
2. 打开“设置”，点击“检查更新”。
3. 弹窗显示 `1.0.1-test`、安装包大小和两条测试更新说明。
4. 点击“下载更新”，进度从 0 增长到 100；切换页面不会启动第二次下载。
5. 首次安装时进入“允许来自此来源的应用”设置。
6. 开启授权并返回，应用自动打开系统安装器。
7. 安装完成后重新打开应用，设置页显示 `1.0.1-test`。
8. 再次检查更新，显示“已是最新版本”。

结束测试：

```powershell
# 在 http.server 终端按 Ctrl+C
adb reverse --remove tcp:8080
adb uninstall com.shisan.campuspro.debug
```

测试 APK 和清单位于 `update-server/dist/test/`，已被 Git 忽略。

## 4. 开发者手工测试用例

每执行一项都填写“记录”列，格式建议：`设备 / Android / 旧版本→新版本 / 通过或失败 / 日志路径 / 日期`。

| 完成 | 编号 | 场景 | 操作 | 预期结果 | 记录 |
|---|---|---|---|---|---|
| [ ] | U01 | 没有更新 | 清单版本等于当前版本并手动检查 | 显示“已是最新版本”，无弹窗 | |
| [ ] | U02 | 发现更新 | 清单版本高于当前版本 | 显示版本、大小、发布时间和更新说明 | |
| [ ] | U03 | 稍后更新 | 在更新弹窗点击“稍后” | 弹窗关闭，应用可继续使用 | |
| [ ] | U04 | 正常下载 | 点击“下载更新” | 显示进度，完成后出现“安装” | |
| [ ] | U05 | 未授权安装 | 禁止未知来源后点击安装 | 打开当前应用的未知来源授权页 | |
| [ ] | U06 | 授权返回 | 开启授权并返回 | 自动继续打开系统安装器 | |
| [ ] | U07 | 拒绝授权 | 不开启授权直接返回 | 不崩溃，显示授权提示，可重试 | |
| [ ] | U08 | 网络断开 | 断网后手动检查；再重启触发自动检查 | 手动显示网络错误；自动检查保持静默 | |
| [ ] | U09 | 清单 404 | 使用不存在的 `latest.json` | 显示服务不可用，不进入下载 | |
| [ ] | U10 | JSON 损坏 | `latest.json` 写入非法 JSON | 显示清单格式错误 | |
| [ ] | U11 | SHA 错误 | 修改清单中的 `sha256` | 下载后校验失败，APK 被删除 | |
| [ ] | U12 | APK 被篡改 | 生成清单后修改 APK 任意字节 | 校验失败，不打开安装器 | |
| [ ] | U13 | 包名错误 | 清单或 APK 使用其他包名 | 拒绝更新并显示包名不匹配 | |
| [ ] | U14 | 版本倒退 | APK versionCode 小于或等于当前版本 | 不提示更新或在校验时拒绝 | |
| [ ] | U15 | 下载中退出页面 | 下载时切换页面再返回 | 状态和进度保持，不重复下载 | |
| [ ] | U16 | 自动检查节流 | 清除数据后联网启动，再于 24 小时内连续重启 | 只有第一次产生自动清单请求 | |
| [ ] | U17 | 手动绕过节流 | 自动检查后立即手动检查 | 再次请求清单并显示结果 | |
| [ ] | U18 | 缓存清理 | 在 `cache/updates/` 放置修改时间超过 7 天的 APK 后启动 | 旧文件被删除 | |
| [ ] | U19 | 安装后复查 | 完成升级后重新检查 | 显示“已是最新版本” | |
| [ ] | U20 | 旋转与深色模式 | 弹窗和下载过程中旋转、切换主题 | 无重叠、无状态丢失、文本可读 | |

辅助日志命令：

```powershell
adb logcat -c
adb logcat | Tee-Object .\update-test-log.txt
adb shell dumpsys package com.shisan.campuspro.debug | Select-String version
```

U11/U12/U13/U14 测试后必须重新执行 `prepare-local-test.ps1`，恢复匹配的 APK 与清单。

## 5. 线上发布与云端验收

### 5.1 发布命令

先在本机只生成和校验，再正式上传：

```powershell
.\update-server\publish-update.ps1 -DryRun
.\update-server\publish-update.ps1
.\update-server\test-update-service.ps1 `
  -ManifestUrl "https://update.example.com/releases/latest.json"
```

发指定版本时可使用：

```powershell
.\update-server\publish-update.ps1 `
  -VersionCode 102 `
  -VersionName "1.0.2" `
  -ReleaseNotes "修复课程提醒问题","优化启动速度"
```

`publish-update.ps1` 的发布顺序固定为：签名构建、APK 元数据校验、生成清单、上传版本化 APK、最后上传 `latest.json`。任何命令失败都会返回非零退出码并停止，不会继续覆盖线上清单。

快速检查但不下载 APK：

```powershell
.\update-server\test-update-service.ps1 `
  -ManifestUrl "https://update.example.com/releases/latest.json" `
  -SkipApkDownload
```

### 5.2 云端验收清单

- [ ] `latest.json` 首次请求为 HTTP 200、`Content-Type: application/json`，并通过 Schema。
- [ ] 发布后最多约 60 秒能看到新清单；必要时在 EdgeOne 清除该 URL 缓存。
- [ ] APK URL 含版本号，旧版本对象仍存在且未被覆盖。
- [ ] APK 响应带有效 `Content-Length`，下载后的大小和 SHA-256 与清单一致。
- [ ] 同一 APK 第二次下载可在 EdgeOne 指标中观察到缓存命中。
- [ ] 未签名的 COS 私有源站 URL 无法直接下载，EdgeOne 域名可正常下载。
- [ ] COS 保持私有读后更新仍能工作，证明私有回源授权生效。
- [ ] EdgeOne 下行流量、COS 回源流量、状态码和费用告警可见。
- [ ] 不开启日志存储也能使用聚合指标完成基础监控。
- [ ] 至少用 Android 8、Android 12、Android 14 或更高版本各验收一次授权与安装流程。

## 6. 故障模拟与恢复

### 6.1 本地状态

- 重置 24 小时节流：系统设置中清除应用数据，或执行 `adb shell pm clear com.shisan.campuspro.debug`。
- 清理更新缓存：清除应用缓存；调试环境也可通过 Android Studio Device Explorer 删除 `cache/updates/`。
- 撤销安装授权：系统设置 > 应用 > 特殊应用权限 > 安装未知应用 > 闪电课表，关闭后重测。
- 恢复本地 APK 和清单：重新运行 `prepare-local-test.ps1`。
- 移除反向端口：`adb reverse --remove tcp:8080`。

### 6.2 线上状态

- APK 已上传但清单未更新：用户仍指向旧 APK，不受影响；修复问题后重新发布即可。
- 清单已更新但 APK 异常：立即把上一版 `latest.json` 上传回原路径，再清除 EdgeOne 的 `latest.json` 缓存。不要覆盖或删除仍被旧清单引用的 APK。
- 已安装坏版本：Android 不允许用更低 versionCode 覆盖。使用旧代码构建一个更高 versionCode 的修复版并正常发布。
- 清单缓存未刷新：在 EdgeOne 缓存刷新中按 URL 清除 `latest.json`，不要清除长期缓存的版本化 APK。
- COS 拒绝回源：检查桶是否仍为私有读写、EdgeOne 私有访问授权和桶策略是否存在，再用 EdgeOne 域名验证。
- 大量 4xx/5xx：先回滚清单并清缓存，再检查证书、CNAME、回源授权和对象路径。

## 7. 自动化验证

常规验证：

```powershell
.\gradlew.bat :core:update:test :feature:settings:test :app:assembleDebug
```

配置签名和正式更新地址后：

```powershell
.\gradlew.bat :app:assembleRelease
```

PowerShell 语法校验：

```powershell
Get-ChildItem .\update-server\*.ps1 | ForEach-Object {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($_.FullName, [ref]$tokens, [ref]$errors) | Out-Null
  if ($errors.Count) { $errors | Format-List; throw "$($_.Name) 语法错误" }
}
```

自动测试覆盖清单解析、包名和版本判断、URL/SHA 校验、自动节流与手动绕过、HTTP 失败、流式下载、长度与哈希校验。发布脚本生成的清单还必须通过 `manifest.schema.json`，并与 `aapt2` 读取的 APK 元数据一致。

## 8. 中国大陆成本估算

以下按 2026-07-10 可查到的中国大陆公开刊例做小规模估算，账单以腾讯云价格计算器和实际套餐为准：

- COS 广州单 AZ 标准存储约 `0.118 元/GB/月`。
- EdgeOne 个人版为 `29.9 元/月`，含中国大陆口径 50 GB 流量和 300 万次请求。
- COS 回源流量和请求按实际 EdgeOne 未命中量另计；版本化 APK 长缓存后，通常远小于用户总下载流量。

假设保留 10 个 30 MB APK，加上清单约可忽略：

```text
存储量 = 10 × 30 MB ÷ 1024 = 0.293 GB
COS 月存储费 = 0.293 × 0.118 ≈ 0.035 元/月
固定基础费用 ≈ EdgeOne 29.9 + COS 0.035 = 29.935 元/月
```

若每月 1,000 次升级、每包 30 MB，总用户下行约 `29.3 GB`，在个人版 50 GB 内；还需预留普通清单请求和缓存未命中的 COS 回源费用。若月下载量超过约 50 GB，应比较 EdgeOne 加量包与基础版，不要把 COS 公网地址直接下发给客户端，否则会绕过边缘缓存并产生 COS 公网下行费用。

## 9. 第三方组件与许可证

| 组件 | 版本 | 许可证 | 用途 |
|---|---|---|---|
| Ktor Client | 3.2.3 | Apache-2.0 | 清单请求与 APK 流式下载 |
| kotlinx.serialization | 项目现有版本目录 | Apache-2.0 | 严格解析更新清单 |
| AndroidX Core / FileProvider | 项目现有版本目录 | Apache-2.0 | 安全共享 APK content URI |
| COSCLI | 1.0.8 | Apache-2.0 | 外部发布工具，不打包进 APK |

本功能没有新增第三方运行时依赖，复用了项目已有组件。

## 10. 官方参考

- [Android FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [Android 网络安全配置](https://developer.android.com/privacy-and-security/security-config)
- [腾讯云 COSCLI 下载与配置](https://cloud.tencent.com/document/product/436/63144)
- [COSCLI cp 命令](https://cloud.tencent.com/document/product/436/63669)
- [EdgeOne 对象存储源站与私有访问授权](https://cloud.tencent.com/document/product/1552/122800)
- [EdgeOne 套餐费用](https://cloud.tencent.com/document/product/1552/94158)
- [COS 成本优化与刊例示例](https://cloud.tencent.com/document/product/436/50201)
- [EdgeOne 关联 COS 与日志费用说明](https://cloud.tencent.com/document/product/1552/94163)
