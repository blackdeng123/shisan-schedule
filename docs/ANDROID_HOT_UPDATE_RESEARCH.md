# 闪电课表 Android 热更新方案调研

> 调研日期：2026-07-11。本文是技术与分发风险调研，不是实施方案；仅记录本次调研结论，不处理工作区中既有的其他改动。

## 结论先行

不建议为“闪电课表”实现传统 Android 热更新（在线替换 Dex、`.so`、资源或插件代码）。当前项目已经具备的“检查新版本、下载完整 APK、由系统安装器升级”是更适合中国大陆直发场景的方案，应继续作为唯一的**代码更新**路径。

可以增加的“快速调整”能力，应限定为服务端配置和内容：公告、开关、灰度入口、课程数据规则或可远程关闭的非关键功能；它们不得下载或执行 Dex、JAR、`.so`、APK 或可调用 Android API 的脚本。出现代码缺陷时，仍发布一个使用同一签名密钥、递增 `versionCode` 的完整 APK。

## 1. 先区分三个容易混淆的概念

| 名称 | 更新的对象 | 是否改变客户端代码 | 是否适合本项目的大陆直发 |
| --- | --- | --- | --- |
| APK 自更新 | 新签名 APK | 是，经过系统安装器升级 | **适合，当前方案** |
| 热更新 / 动态代码加载 | Dex、JAR、`.so`、补丁或插件 APK | 是，在已安装应用内加载 | **不建议** |
| 服务端配置 / 内容下发 | JSON、文案、开关、规则数据 | 否 | **适合，但须有明确边界** |
| Dynamic Feature Modules | App Bundle 内预先构建的功能模块 | 是，但由 Google Play 交付 | **不适合替代大陆 APK 直发** |

Android 的 APK 签名机制要求新 APK 与已安装应用使用匹配证书才能原位升级；APK v2 及以上签名会覆盖几乎整个 APK，文件被修改会导致签名失效。完整 APK 升级因此具有明确的系统信任边界。[Android 官方签名说明](https://developer.android.com/studio/publish/app-signing) 与 [AOSP APK 签名机制说明](https://source.android.com/docs/security/features/apksigning) 都确认了这一点。

## 2. Android 原生动态代码加载：技术上存在，不等于适合使用

Android 提供了 `DexClassLoader`，可从含 `classes.dex` 的 JAR 或 APK 加载类。这是平台 API，并非被移除；但官方明确建议把优化后的代码缓存放在应用私有目录或 Android 10+ 的分区存储中，不能放外部存储，以避免代码注入风险。[DexClassLoader 官方 API](https://developer.android.com/reference/dalvik/system/DexClassLoader)

Android 安全文档把动态代码加载列为安全风险：远程来源的代码可能被篡改或替换，进而导致数据泄露、任意代码执行或应用不可用；文档还直接指出，很多远程动态加载形式会违反 Google Play 政策。[Android 动态代码加载风险指南](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)

对本项目而言，除了安全问题，还有以下工程风险：

1. 补丁需要同时适配不同 Android 版本、CPU 架构、厂商 ROM、R8 混淆结果和启动顺序，排查成本远高于一次完整 APK 升级。
2. 出错补丁常发生在进程启动或类加载阶段，可能造成应用无法进入，从而失去在线撤回能力；完整 APK 仍可通过版本化发布和用户安装修复。
3. 资源、Manifest、四大组件、权限、数据库迁移及原生库并不都能安全地热替换。尤其是 Manifest 和组件变动本就要求重新安装 APK。
4. 一旦补丁服务器、CDN、签名私钥或下载链路被攻破，攻击面比完整 APK 更新更大。仅校验哈希不足以代替完整的补丁签名、密钥轮换、回滚和审计体系。

因此，本项目不应直接使用 `DexClassLoader`、反射替换 `BaseDexClassLoader` 路径、下载 `.dex/.jar/.so`，也不应把远程 JavaScript/Lua 等脚本设计成可间接调用 Android API 的“代码通道”。

## 3. Dynamic Feature Modules：不是传统热更新，也不适合当前分发渠道

Dynamic Feature Module 是把功能在构建期拆进 Android App Bundle 的模块，再由 **Google Play Feature Delivery** 在安装时、按需或按条件交付。它并不是让应用从自有 COS 下载一段新 Dex 后执行。官方文档说明其建立在 Android App Bundle 和 Google Play 的应用服务模型之上，按需交付也由 Play Feature Delivery 完成。[Play Feature Delivery 概览](https://developer.android.com/guide/playcore/feature-delivery)

该机制的优点是模块由商店签名、交付和更新，能降低首包体积；限制也很明确：按需下载需要 Android 5.0+，应用还需处理 SplitCompat、模块是否已安装及模块数量等约束。[按需交付官方指南](https://developer.android.com/guide/playcore/feature-delivery/on-demand)

闪电课表目前面向中国大陆、采用 COS + EdgeOne 直发 APK，未通过 Google Play 的 App Bundle 分发链路。因此：

- Dynamic Feature Modules 不能替代当前 APK 自更新服务。
- 即便本地可用 `bundletool` 测试 split APK，也不能得到 Google Play 的按需线上托管、签名和模块更新能力。
- 只有未来明确上架 Google Play，且某些独立、低频、非核心功能确实需要缩小首包时，才值得单独评估模块化，不应把它当成紧急修复工具。

## 4. Google Play 的限制：若未来上架，传统热更新不可共存

Google Play 的“设备和网络滥用”政策明确规定：通过 Google Play 分发的应用不能用 Google Play 以外的机制修改、替换或更新自身，也不能从 Google Play 以外下载可执行代码，例如 Dex、JAR、`.so`。这直接覆盖了传统热更新和自建插件代码下载。[Google Play Device and Network Abuse 政策](https://support.google.com/googleplay/android-developer/answer/16559646?hl=zh-Hans)

此外，`REQUEST_INSTALL_PACKAGES` 被 Google Play 作为高风险权限限制；应用必须以“发送/接收应用包并支持用户主动安装”为核心功能，且要在 Play Console 提交权限声明。课表应用的核心功能不是安装应用包，若将来进入 Google Play，应移除这项权限并改用 Play 的更新机制。[REQUEST_INSTALL_PACKAGES 官方政策](https://support.google.com/googleplay/android-developer/answer/12085295?hl=zh-Hans)

所以应把渠道策略固定为：

| 发布渠道 | 代码更新方式 | 热更新 / 远程可执行代码 |
| --- | --- | --- |
| 当前中国大陆官网/COS/EdgeOne APK 直发 | 用户确认后的完整 APK 升级 | 不采用 |
| 未来 Google Play | Play 商店更新或 Play In-App Updates / Feature Delivery | 不采用 |

这不是法律意见；政策会变化，上架前必须再次阅读上述 Google Play 政策并在 Play Console 中核验权限要求。

## 5. 中国大陆 APK 直发场景的技术风险

中国大陆直发 APK 没有 Google Play 为你承担交付和更新的一致性管理，重点风险如下。第 1 至第 4 项是本项目应优先治理的工程风险；第 5 项是长期生态变化，需要持续关注。

| 风险 | 具体表现 | 建议控制措施 |
| --- | --- | --- |
| 签名密钥丢失或更换 | 系统会把新证书视为不同应用，已安装用户无法原位升级 | 固定使用现有发布密钥；离线备份；只在受控电脑上签名；绝不把 `keystore.properties`、密钥库或密码提交 Git |
| 下载被篡改或发布错包 | CDN、对象存储权限或发布电脑出问题时，用户可能拿到错误文件 | COS 私有源站 + EdgeOne HTTPS；先上传版本化 APK、校验后最后更新 `latest.json`；校验 APK 大小、SHA-256、包名、`versionCode` 和系统 APK 签名 |
| 厂商 ROM 与用户操作差异 | 用户需授权“允许来自此来源的应用”；系统安装器、下载目录和安全扫描的界面因机型而异 | 始终走系统安装器；不尝试静默安装；在真机覆盖小米、华为/鸿蒙、OPPO/vivo、三星和原生 Android；提供可理解的失败提示 |
| 网络与缓存不一致 | `latest.json` 被缓存、APK 尚未同步、弱网或中断导致下载失败 | 清单短缓存，版本化 APK 长缓存；上传顺序为 APK 再清单；下载使用临时文件；完成前不安装；支持重试与服务器健康检查 |
| Android 开发者验证演进 | Android 已公布 2026 年起先在部分国家实施、2027 年及以后全球扩展的开发者验证计划；旁加载仍可行，但应用包名和签名密钥将需与已验证开发者关联 | 即使当前只服务中国大陆，也应尽早保管签名身份、登记包名归属，并持续关注官方时间表；不要依赖“永远可以无验证旁加载”的假设 |

最后一项来自 [Android 开发者验证中文指南](https://developer.android.com/developer-verification/guides?hl=zh-cn)。截至本报告日期，首批强制地区为巴西、印度尼西亚、新加坡和泰国，尚非中国大陆；但官方已说明 2027 年及以后会全球推广，因此这里属于前瞻性风险，而非当前大陆用户的已生效限制。

## 6. 常见开源方案的官方仓库核验

以下状态仅根据各项目的官方 GitHub 仓库、仓库许可证文件及 GitHub API 在 2026-07-11 的可见信息判断；“未归档”不代表适合现代 Android 项目。

| 方案 | 机制与官方自述 | 官方维护证据 | 许可证 | 对本项目的判断 |
| --- | --- | --- | --- | --- |
| Tinker（腾讯） | 支持 Dex、原生库和资源补丁；其 README 自己列出不能动态更新 Manifest/新增组件，并明确提示受 Google Play 协议限制不能动态更新 APK | 仓库未归档；官方 Releases 页面显示最新发布为 `v1.9.15.2`（2025-07-07）；仍有开发分支提交 | BSD 3-Clause，仓库 LICENSE 明确写明二进制采用 BSD 3-Clause | 不引入。维护并非完全停止，但集成模型偏旧，且与“远程动态加载不做”的安全边界冲突 |
| AndFix（阿里/支付宝） | 通过 native hook 替换方法体；README 标注支持 Android 2.3–7.0 | 仓库未归档，但官方仓库最后推送为 2020-11-17，远早于本项目 `targetSdk 34`/现代 Android 环境 | Apache-2.0 | 不引入。官方支持范围到 Android 7.0，不能作为现代设备与新 target SDK 的可靠基础 |
| RePlugin（360） | Android 插件框架，不是只修复方法体的热修复库；会加载和管理独立插件 APK | 仓库未归档；官方仓库默认 `dev` 分支最近推送为 2025-09-16，仍有开放议题 | Apache-2.0 | 不引入。它解决的是插件化产品架构，复杂度和远程可执行代码风险均超出课表应用需求 |

官方仓库与许可证原始链接：

- [Tencent/Tinker 仓库](https://github.com/Tencent/tinker)、[Tinker LICENSE](https://github.com/Tencent/tinker/blob/dev/LICENSE)、[Tinker Releases](https://github.com/Tencent/tinker/releases)
- [alibaba/AndFix 仓库](https://github.com/alibaba/AndFix)、[AndFix LICENSE](https://github.com/alibaba/AndFix/blob/master/LICENSE)、[AndFix 提交历史](https://github.com/alibaba/AndFix/commits/master)
- [Qihoo360/RePlugin 仓库](https://github.com/Qihoo360/RePlugin)、[RePlugin LICENSE](https://github.com/Qihoo360/RePlugin/blob/dev/LICENSE)、[RePlugin 提交历史](https://github.com/Qihoo360/RePlugin/commits/dev)
- [GitHub API：Tinker 元数据](https://api.github.com/repos/Tencent/tinker)、[AndFix 元数据](https://api.github.com/repos/alibaba/AndFix)、[RePlugin 元数据](https://api.github.com/repos/Qihoo360/RePlugin)

许可证角度，三者都属于宽松许可：Tinker 为 BSD 3-Clause，AndFix 与 RePlugin 为 Apache-2.0。它们在许可上通常可用于商业项目，但许可证兼容不等于其技术风险、维护成本或商店政策风险可接受；而且若真正引入，还需在发布时保留对应许可证与 NOTICE。

## 7. 给闪电课表的具体建议

1. **维持现有完整 APK 更新，不做热更新。** 这是风险最低、最容易测试和回滚的代码发布路径。
2. **把“快速修复”拆开处理。** 文案、公告、开关、服务端可配置参数可远程下发；课程解析逻辑、数据库结构、权限、UI 业务代码和原生库一律通过新 APK 发布。
3. **把更新服务当作发布系统维护。** 保持同一发布签名、递增 `versionCode`、HTTPS、私有 COS 源站、版本化 APK 地址、清单短缓存及 APK 完整性校验。现有 [更新服务配置文档](UPDATE_SERVICE_CONFIGURATION.md) 已覆盖这些操作。
4. **预留紧急止损能力，而非补丁执行能力。** 可为非关键功能做服务端开关；出现严重故障时先关闭入口或降级到本地安全路径，再发布更高版本号的修复 APK。
5. **若未来上架 Google Play，按渠道拆分。** Play 版本移除自建 APK 安装权限和下载更新路径，使用 Play 更新能力；不要尝试一套传统热更新同时兼容官网直发与 Google Play。
6. **将签名与开发者身份视为长期资产。** 当前签名密钥必须备份，包名归属要稳定；同时关注 Android 开发者验证在全球的后续实施。

## 8. 本报告未建议实施的内容

- 不在项目中接入 Tinker、AndFix、RePlugin 或任意 Dex/JAR/`.so` 远程加载库。
- 不新增补丁服务器、补丁签名、插件市场或动态脚本执行器。
- 不改变现有 COS/EdgeOne APK 更新架构。

这些不是遗漏，而是基于 Android 官方安全指南、未来 Google Play 分发兼容性和本项目大陆直发定位做出的明确取舍。
