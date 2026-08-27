# 闪电课表更新功能新手操作手册

这份文档假设你从来没有配置过 Android 签名、对象存储或 CDN。你不需要一次学会所有名词，只要按照章节顺序操作即可。

如果你现在只想确认“检查更新能不能工作”，只做第 1 至第 4 章。这个阶段不需要购买腾讯云服务，不需要域名，也不需要 Release 签名密码。

## 1. 最终要实现什么

完成后，用户使用闪电课表时会经历下面的过程：

1. 应用启动时，每 24 小时最多检查一次更新。
2. 用户也可以在设置页点击“检查更新”。
3. 有新版本时，应用显示版本号、安装包大小和更新说明。
4. 用户点击下载后，应用校验安装包是否完整、包名是否正确、版本是否正确。
5. 校验通过后，Android 系统安装器询问用户是否安装。

应用不能静默安装，也不会强制用户升级。最后一步始终由用户在 Android 系统安装器中确认。

## 2. 先认识几个名词

| 名词 | 可以把它理解成什么 | 在本项目中的作用 |
|---|---|---|
| APK | Android 安装包 | 用户最终下载并安装的文件 |
| Debug | 开发测试版 | 用于你自己的手机测试，不发给正式用户 |
| Release | 正式发布版 | 使用固定密钥签名，提供给正式用户 |
| versionCode | 只能递增的整数版本号 | Android 判断新包能否覆盖旧包，例如 100、101 |
| versionName | 给用户看的版本名称 | 例如 `1.0.0`、`1.0.1` |
| 签名密钥 | 应用的身份证和印章 | 新旧 APK 必须使用同一密钥，否则不能覆盖安装 |
| `latest.json` | 一张“最新版本说明单” | 告诉应用 APK 地址、版本、大小和 SHA-256 |
| SHA-256 | 文件指纹 | APK 被修改或下载损坏时，指纹会不同 |
| COS | 腾讯云文件仓库 | 保存 APK 和 `latest.json` |
| EdgeOne | 下载加速入口 | 用户通过它下载，COS 可以继续保持私有 |
| 域名 | 用户访问服务使用的地址 | 例如 `update.example.com` |
| CNAME | 域名解析方式之一 | 把更新域名指向 EdgeOne |
| COSCLI | 腾讯云命令行上传工具 | 发布脚本通过它上传 APK 和清单 |
| `adb reverse` | 手机到电脑的端口映射 | 让真机通过 `127.0.0.1` 访问电脑的测试服务器 |

最重要的两个规则：

1. `versionCode` 每次正式发布都必须比上一次大。
2. 所有正式 APK 必须一直使用同一份签名密钥。

## 3. 在正确的目录打开 PowerShell

项目目录是：

```text
E:\code\kechengbiao-android-native
```

最简单的打开方法：

1. 使用 Windows 文件资源管理器打开这个目录。
2. 点击顶部地址栏。
3. 输入 `powershell`，然后按 Enter。
4. 新窗口打开后执行 `Get-Location`。

你应该看到：

```text
Path
----
E:\code\kechengbiao-android-native
```

后续命令默认都在这个目录执行。如果终端显示的目录不同，先运行：

```powershell
Set-Location E:\code\kechengbiao-android-native
```

## 4. 第一次只做本地升级测试

这一章的目的，是先证明 Android 客户端的完整升级流程可以工作。此时安装包只在你的电脑和手机之间传输，不会上传腾讯云。

### 4.1 检查电脑工具

依次执行：

```powershell
java -version
adb version
python --version
```

预期结果：

- `java -version` 中包含 `17`。
- `adb version` 能显示 Android Debug Bridge 版本。
- `python --version` 能显示 Python 3 的版本。

如果 `python` 找不到，也可以尝试：

```powershell
py --version
```

常见问题：

| 错误 | 原因 | 处理方法 |
|---|---|---|
| `java 不是内部或外部命令` | JDK 未安装或未加入 PATH | 在 Android Studio 中确认 Gradle JDK 为 17，或安装 JDK 17 |
| `adb 无法识别` | Android SDK platform-tools 未加入 PATH | 在 Android Studio 的 SDK Manager 安装 Platform-Tools |
| `python 无法识别` | Python 未安装或未加入 PATH | 安装 Python 3，安装时勾选 Add Python to PATH |

### 4.2 连接 Android 手机

在手机上完成：

1. 打开“设置 > 关于手机”。
2. 连续点击“版本号”约 7 次，直到提示已进入开发者模式。
3. 返回设置，找到“开发者选项”。不同品牌的位置可能不同。
4. 开启“USB 调试”。
5. 用可以传输数据的 USB 线连接电脑。
6. 手机上出现“是否允许 USB 调试”时选择允许。

电脑执行：

```powershell
adb devices
```

成功时类似：

```text
List of devices attached
ABC123456    device
```

如果状态是 `unauthorized`，解锁手机并同意 USB 调试弹窗，然后重新执行 `adb devices`。

如果列表为空，优先检查 USB 线、USB 模式和手机驱动。把手机 USB 模式改成“文件传输”通常更容易识别。

### 4.3 清理旧测试包

为防止旧 Debug 签名或更高版本号影响测试，先执行：

```powershell
adb uninstall com.shisan.campuspro.debug
```

如果显示 `Unknown package` 或 `not installed`，表示手机上本来就没有，可以继续。

### 4.4 一键准备两个测试版本

执行：

```powershell
.\update-server\prepare-local-test.ps1
```

脚本会自动做六件事：

1. 构建 `1.0.0-test`，versionCode 为 100。
2. 把 `1.0.0-test` 安装到手机。
3. 构建 `1.0.1-test`，versionCode 为 101。
4. 把新 APK 放到 `update-server/dist/test/releases/`。
5. 生成对应的 `latest.json`。
6. 执行 `adb reverse tcp:8080 tcp:8080`。

成功时，命令最后会显示：

```text
本地升级测试材料已准备完成。
在仓库根目录启动：python -m http.server 8080
然后在手机设置页点击“检查更新”。
```

不要手动修改应用源码中的版本号。脚本通过 Gradle 参数临时构建两个版本，不会污染正式版本配置。

### 4.5 启动本地文件服务器

保持当前终端不动，再打开第二个 PowerShell，并进入项目目录。

在第二个终端执行：

```powershell
python -m http.server 8080
```

如果你的电脑只能使用 `py`，执行：

```powershell
py -m http.server 8080
```

成功时会显示类似：

```text
Serving HTTP on 0.0.0.0 port 8080 ...
```

这个终端必须保持打开。关闭终端或按 Ctrl+C 后，手机就无法下载测试文件。

### 4.6 在手机上完成升级

1. 打开手机上的“闪电课表 Debug”。
2. 进入应用设置页。
3. 确认版本信息显示 `1.0.0-test`。
4. 点击“检查更新”。
5. 应看到 `1.0.1-test` 的更新弹窗。
6. 点击“下载更新”，观察下载进度。
7. 下载完成后点击“安装”。
8. 首次测试会进入“允许来自此来源的应用”页面。
9. 开启允许安装，然后返回应用。
10. 系统安装器打开后点击“更新”或“安装”。
11. 安装完成后重新打开闪电课表。
12. 设置页版本应变成 `1.0.1-test`。
13. 再次点击“检查更新”，应显示“已是最新版本”。

如果手机提示“无法安装此应用”，先看第 13 章的故障表，不要反复修改签名文件。

### 4.7 测试结束后清理

在运行文件服务器的终端按 Ctrl+C，然后执行：

```powershell
adb reverse --remove tcp:8080
adb uninstall com.shisan.campuspro.debug
```

完成这一章，说明客户端的检查、下载、校验、授权和安装流程已经跑通。接下来才需要考虑腾讯云。

## 5. 上云之前必须准备什么

正式分发需要以下内容：

| 项目 | 是否必需 | 从哪里获得 |
|---|---|---|
| 腾讯云账号并完成实名认证 | 必需 | 腾讯云控制台 |
| 一个中国大陆 COS 存储桶 | 必需 | 腾讯云 COS 控制台 |
| 一个已备案的域名 | 中国大陆加速必需 | 域名注册商及备案系统 |
| EdgeOne 套餐 | 必需 | 腾讯云 EdgeOne 控制台 |
| Release 签名密码和别名 | 必需 | 现有 `keys/kebiao` 的创建者或备份记录 |
| COSCLI | 必需 | 腾讯云官方工具下载页 |

如果你暂时没有已备案域名，可以继续做本地 Debug 测试，但不要填写虚假的正式域名，也不要发布 Release 给用户。

## 6. 配置固定的 Release 签名

### 6.1 为什么签名不能换

Android 只有在新 APK 与旧 APK 使用同一签名时，才允许覆盖安装。即使包名和版本号都正确，换了密钥也会安装失败。

项目已有密钥文件：

```text
keys/kebiao
```

这个文件已被 Git 忽略。不要把密钥、密码、聊天截图或 `keystore.properties` 上传到 GitHub、网盘公开链接或群聊。

### 6.2 查找密钥别名

执行：

```powershell
keytool -list -v -keystore .\keys\kebiao
```

终端会要求输入密钥库密码。输入时屏幕可能没有任何字符，这是正常现象，输入完成后按 Enter。

找到输出中的：

```text
Alias name: 某个别名
```

中文 JDK 也可能显示“别名”。记下这个值，但不要记录或分享密码。

### 6.3 创建本机签名配置

执行：

```powershell
Copy-Item .\keystore.properties.example .\keystore.properties
notepad .\keystore.properties
```

文件内容如下：

```properties
storeFile=keys/kebiao
storePassword=你的密钥库密码
keyAlias=上一步查到的别名
keyPassword=你的密钥密码
```

每个字段的含义：

| 字段 | 填什么 |
|---|---|
| `storeFile` | 密钥文件位置，保持 `keys/kebiao` |
| `storePassword` | 打开密钥库使用的密码 |
| `keyAlias` | `keytool` 输出中的 Alias name |
| `keyPassword` | 该别名对应私钥的密码，有时与 storePassword 相同 |

保存后不要执行 `git add keystore.properties`。项目已经通过 `.gitignore` 阻止它进入 Git。

## 7. 创建 COS 存储桶

### 7.1 找到 APPID

登录腾讯云控制台，在账号信息中找到 APPID。APPID 通常是一串数字，例如 `1250000000`。

APPID 不是 SecretId，也不是密码，可以用于组成桶名。

### 7.2 创建桶

进入“对象存储 COS > 存储桶列表 > 创建存储桶”，建议填写：

| 控制台字段 | 建议值 |
|---|---|
| 名称 | `campuspro-update` |
| 所属地域 | 广州 `ap-guangzhou` |
| 访问权限 | 私有读写 |
| 存储类型 | 标准存储 |
| 多 AZ | 用户量较小时可不开启，按你的可靠性要求选择 |
| 版本控制 | 初期可不开启，但发布时必须保留版本化 APK 文件 |

创建后，完整桶名会自动带 APPID，例如：

```text
campuspro-update-1250000000
```

不要把桶设置成公共读。后面由 EdgeOne 获得私有回源权限，用户只通过 EdgeOne 下载。

## 8. 配置 COSCLI

### 8.1 下载并安装

打开腾讯云官方 [COSCLI 下载与安装配置](https://cloud.tencent.com/document/product/436/63144)，下载 Windows amd64 版本。

把文件重命名为 `coscli.exe`。简单做法是把它放到你的用户目录，然后把该目录加入 Windows PATH。

重新打开 PowerShell，执行：

```powershell
coscli --version
```

预期看到类似：

```text
coscli version v1.0.8
```

### 8.2 准备上传账号

推荐在腾讯云“访问管理 CAM”中创建专门的子用户，不要直接把主账号永久密钥保存在电脑上。

如果你暂时不熟悉 CAM，请让账号管理员按照技术文档中的最小权限 JSON 创建发布子用户。不要为了省事给子用户 `AdministratorAccess`。

详细最小权限策略见 [技术配置文档 2.3 节](UPDATE_SERVICE_CONFIGURATION.md#23-coscli-v108)。

### 8.3 初始化 COSCLI

执行：

```powershell
coscli config init
```

它会依次询问参数。按下面填写：

| 提示 | 填写内容 |
|---|---|
| Secret ID | 发布子用户的 SecretId |
| Secret Key | 发布子用户的 SecretKey |
| Session Token | 使用临时密钥时填写；永久子用户密钥通常留空 |
| APPID | 腾讯云账号 APPID |
| Bucket Name | 完整桶名，如 `campuspro-update-1250000000` |
| Bucket Endpoint/Region | 广州，`cos.ap-guangzhou.myqcloud.com` / `ap-guangzhou` |
| Bucket Alias | `campuspro-update` |

配置完成后执行：

```powershell
coscli config show
coscli ls cos://campuspro-update/
```

`ls` 能列出桶内容或返回空列表，表示连接成功。出现 `AccessDenied` 时，检查 CAM 权限、桶名、地域和密钥所属账号。

## 9. 配置 EdgeOne 和域名

### 9.1 添加站点

进入腾讯云“EdgeOne > 站点列表 > 添加站点”。填写你的已备案主域名，例如 `example.com`。

控制台会提供接入方式。新手按控制台向导选择 CNAME 接入通常更直观。

### 9.2 添加更新域名

在站点下进入“域名服务 > 域名管理 > 添加域名”，填写子域名，例如：

```text
update.example.com
```

源站类型选择：

```text
对象存储源站 > 腾讯云 COS
```

选择刚才创建的 `campuspro-update-APPID` 桶。

### 9.3 开启私有回源

找到“私有访问授权”并开启。按控制台提示授权 EdgeOne 读取该 COS 桶。

开启后，COS 保持私有读写，但 EdgeOne 可以执行 `GetObject`、`HeadObject` 和 `OptionsObject`。

### 9.4 配置 HTTPS

在更新域名的 HTTPS 配置中申请或部署证书。可以使用 EdgeOne 支持的免费单域名托管证书。

最终更新地址必须能够通过下面的形式访问：

```text
https://update.example.com/releases/latest.json
```

正式应用拒绝 HTTP 地址。

### 9.5 配置 CNAME

EdgeOne 会给出一个 CNAME 目标值。进入你的 DNS 服务商，为 `update` 主机记录添加 CNAME。

示例只是说明格式，实际目标必须复制控制台给出的值：

```text
记录类型：CNAME
主机记录：update
记录值：EdgeOne 控制台提供的目标
```

DNS 生效可能需要几分钟。不要把记录值写成 COS 原始域名。

### 9.6 配置缓存规则

在“EdgeOne > 规则引擎”中增加两条规则：

规则一，更新清单：

```text
匹配路径：/releases/latest.json
缓存时间：60 秒
```

规则二，APK：

```text
匹配后缀：.apk
缓存时间：365 天
```

APK 文件名包含版本号，所以可以长期缓存。`latest.json` 每次发布都会覆盖，因此只能短时间缓存。

## 10. 填写 update.properties

复制示例：

```powershell
Copy-Item .\update.properties.example .\update.properties
notepad .\update.properties
```

假设你的 APPID 是 `1250000000`，域名是 `update.example.com`，填写：

```properties
manifestUrl=https://update.example.com/releases/latest.json
publicBaseUrl=https://update.example.com/releases
cosBucket=campuspro-update-1250000000
cosRegion=ap-guangzhou
cosAlias=campuspro-update
debugManifestUrl=http://127.0.0.1:8080/update-server/dist/test/releases/latest.json
```

逐项解释：

| 字段 | 用途 | 最容易犯的错误 |
|---|---|---|
| `manifestUrl` | App 检查更新的完整清单地址 | 漏写 `/releases/latest.json` |
| `publicBaseUrl` | 生成 APK 公网地址的目录 | 错写成 COS 私有源站地址 |
| `cosBucket` | 完整 COS 桶名 | 忘记末尾 APPID |
| `cosRegion` | COS 地域 | 与实际桶地域不一致 |
| `cosAlias` | COSCLI 中配置的桶别名 | 与 `config init` 输入的别名不同 |
| `debugManifestUrl` | 本地真机测试地址 | 不要改成局域网 IP，Debug 只放行 127.0.0.1 |

`update.properties` 已被 Git 忽略，不要提交，因为不同环境可能使用不同域名和桶。

## 11. 第一次正式发布

### 11.1 理解首次发布的前提

用户手机里的旧版本必须同时满足：

1. 已经包含本次更新检查代码。
2. `BuildConfig.UPDATE_MANIFEST_URL` 已编译成真实 HTTPS 地址。
3. 使用与新版本相同的 Release 密钥签名。

如果用户安装的是没有更新功能的历史 APK，它不会凭空获得检查更新能力。你需要先通过原来的渠道分发一个包含更新功能的基础版本。

### 11.2 先只生成，不上传

执行：

```powershell
.\update-server\publish-update.ps1 `
  -VersionCode 102 `
  -VersionName "1.0.2" `
  -ReleaseNotes "首次接入应用内更新" `
  -DryRun
```

如果成功，会生成：

```text
update-server/dist/releases/campuspro-1.0.2.apk
update-server/dist/releases/latest.json
```

查看清单：

```powershell
Get-Content .\update-server\dist\releases\latest.json
```

重点检查：

- `packageName` 是 `com.shisan.campuspro`。
- `versionCode` 是 102。
- `versionName` 是 `1.0.2`。
- `apkUrl` 使用 EdgeOne HTTPS 域名。
- `sha256` 是 64 位十六进制字符串。

### 11.3 正式上传

确认 DryRun 结果正确后，去掉 `-DryRun`：

```powershell
.\update-server\publish-update.ps1 `
  -VersionCode 102 `
  -VersionName "1.0.2" `
  -ReleaseNotes "首次接入应用内更新"
```

脚本先上传版本化 APK，成功后最后上传 `latest.json`。如果 APK 上传失败，线上清单不会指向不存在的新文件。

### 11.4 自动检查线上文件

执行：

```powershell
.\update-server\test-update-service.ps1 `
  -ManifestUrl "https://update.example.com/releases/latest.json"
```

成功时最后显示：

```text
更新服务检查通过。
```

脚本会检查 HTTPS、状态码、Content-Type、JSON Schema、APK Content-Length、实际大小和 SHA-256。

### 11.5 真机验收

不要直接拿同一个版本测试。应先在手机安装一个使用同一 Release 密钥签名、但 versionCode 更低的版本。

例如：

```text
手机旧版本：versionCode 101，versionName 1.0.1
线上新版本：versionCode 102，versionName 1.0.2
```

然后打开设置页点击“检查更新”，完整走一遍下载和安装流程。

## 12. 以后每次发版怎么做

以后不需要重复创建 COS、域名和 EdgeOne。每次发版只做：

1. 确认代码已经测试通过。
2. 选择一个比线上更大的 versionCode。
3. 先执行 `publish-update.ps1 -DryRun`。
4. 检查生成的 `latest.json`。
5. 去掉 `-DryRun` 正式上传。
6. 执行 `test-update-service.ps1`。
7. 用旧版本真机检查一次更新。
8. 在 EdgeOne 指标中观察状态码和流量。

不要覆盖旧 APK。版本化 APK 文件名不同，保留旧文件可以在清单出错时快速回滚。

## 13. 常见错误怎么处理

| 你看到的现象 | 最可能的原因 | 先做什么 |
|---|---|---|
| `adb devices` 列表为空 | USB 调试、线材或驱动问题 | 换数据线，将 USB 模式改成文件传输 |
| `unauthorized` | 手机未授权电脑 | 解锁手机并允许 USB 调试 |
| 本地检查更新显示网络错误 | Python 服务器未启动或 adb reverse 丢失 | 重新启动服务器并执行 `adb reverse tcp:8080 tcp:8080` |
| 本地清单 404 | 启动服务器时不在仓库根目录 | `Get-Location` 确认目录后重启服务器 |
| 显示更新但下载失败 | APK 路径、大小或 SHA 不一致 | 重新运行 `prepare-local-test.ps1` |
| Android 提示签名不一致 | 手机旧包和新包使用不同密钥 | 卸载 Debug 测试包；正式用户包不能靠卸载解决数据保留问题 |
| Android 提示版本过低 | 新包 versionCode 没有增加 | 使用更大的 versionCode 重新构建 |
| Release 构建提示缺少配置 | 没有创建本机两个 properties 文件 | 按第 6 章和第 10 章创建配置 |
| COSCLI `AccessDenied` | CAM 权限不足或资源范围错误 | 检查子用户策略、桶名、APPID 和地域 |
| EdgeOne 返回 403 | 私有回源授权未生效 | 重新检查 EdgeOne 私有访问授权和桶策略 |
| EdgeOne 返回 404 | 对象路径错误 | COS 中确认存在 `releases/latest.json` |
| 发布后仍看到旧清单 | EdgeOne 仍有短期缓存 | 等待 60 秒或按 URL 清除 `latest.json` 缓存 |
| 检查更新一直显示最新 | 清单 versionCode 不高于当前版本 | 查看 App 当前版本和线上清单数字 |

调试日志：

```powershell
adb logcat -c
adb logcat | Tee-Object .\update-test-log.txt
```

停止日志时按 Ctrl+C。日志可能包含设备和应用运行信息，不要直接公开上传完整日志。

## 14. 新手最关心的三个问题

### 14.1 需要自己写服务端程序吗

不需要。这个方案没有一直运行的 Java、Node.js 或 Python 服务端。

COS 保存静态文件，EdgeOne 分发文件。PowerShell 脚本只在你发布新版本时运行。

### 14.2 需要 KV 或数据库吗

不需要。最新版本信息只有一个 `latest.json`，直接作为静态文件保存即可。

只有未来需要灰度发布、用户分组、强制策略或管理后台时，才需要考虑动态 API 或 KV。

### 14.3 需要开启日志存储吗

初期不需要。先使用 EdgeOne 控制台的请求数、状态码、下行流量和缓存命中率指标。

实时日志投递到 CLS 会产生额外费用。遇到需要逐请求审计或长期排障的场景，再开启日志存储。

## 15. 大概需要多少钱

假设保留 10 个 30 MB APK：

```text
总存储量 = 10 × 30 MB ÷ 1024 ≈ 0.293 GB
COS 月存储费 = 0.293 × 0.118 ≈ 0.035 元
EdgeOne 个人版 = 29.9 元/月
合计固定费用约 = 29.935 元/月
```

这只是小规模估算，不包含超出套餐的下行流量、少量 COS 回源请求和可能启用的日志费用。正式购买前以腾讯云价格计算器为准。

## 16. 最终检查清单

本地测试：

- [ ] `adb devices` 能看到状态为 `device` 的手机。
- [ ] `prepare-local-test.ps1` 执行成功。
- [ ] 手机从 `1.0.0-test` 升级到 `1.0.1-test`。
- [ ] 升级后再次检查显示“已是最新版本”。

正式配置：

- [ ] 已备份 `keys/kebiao` 和密码。
- [ ] 已创建但未提交 `keystore.properties`。
- [ ] COS 桶是私有读写。
- [ ] COSCLI 桶别名是 `campuspro-update`。
- [ ] 更新域名已备案、CNAME 已生效、HTTPS 正常。
- [ ] EdgeOne 已开启 COS 私有访问授权。
- [ ] `update.properties` 中没有示例域名和示例 APPID。

每次发版：

- [ ] versionCode 大于线上版本。
- [ ] 先执行 DryRun 并检查 `latest.json`。
- [ ] 正式发布脚本没有报错。
- [ ] 线上服务检测脚本通过。
- [ ] 使用旧版 Release 在真机完成升级。

更完整的 U01 至 U20 异常测试、CAM 最小权限 JSON、故障回滚和自动化命令见 [技术配置与自测文档](UPDATE_SERVICE_CONFIGURATION.md)。
