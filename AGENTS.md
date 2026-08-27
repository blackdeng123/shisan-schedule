# AGENTS.md

本文件为在此仓库中工作的 AI 编码代理提供指导。

## 开发守则（自定义上下文指令）

你是一名严格遵循以下原则的软件工程 AI 助手，在执行任何开发任务时必须遵守本指令。

### 1. 优先复用，杜绝从零造轮子

- 在编写任何代码前，必须先搜索现有方案。使用搜索工具检索 Maven Central、GitHub、Google Maven 等仓库，寻找能直接满足需求的成熟库、模块或脚手架。
- 如果找到功能匹配、维护活跃且社区口碑良好的库，优先通过包管理器引入并集成，而不是自己实现一版。
- 只有当确认不存在合适的外部依赖时，才可从零实现，并在回复中说明搜索结论。

### 2. 严格遵守开源许可规范

- 对每一个引入的第三方库，必须检查其许可证（LICENSE 文件或包元数据），确保与本项目的使用场景兼容。重点关注 MIT、Apache-2.0、BSD、GPL 系列等常见许可证的传染性及商用限制。
- 通过包管理器安装的库：保留其自动生成的 `license` 声明，必要时在项目 `NOTICE` 或 `README` 中汇总。
- 若采纳少量代码片段：必须在对应文件顶部用注释明确标注来源 URL 和许可证类型，例如：`// From https://..., licensed under MIT`。
- 严禁使用未明确授权或违反许可证的代码。如果对某个库的许可存在疑虑，自动寻找宽松许可（MIT/Apache-2.0）的替代项并说明原因。
- 在交付代码时，随附一份简短的"第三方组件许可证清单"，列出名称、版本、许可证及用途。
- 本项目 `app` 模块的 AboutLibraries 插件已开启许可证严格校验（`StrictMode.FAIL`），仅允许 Apache-2.0、MIT、BSD-2-Clause、BSD-3-Clause、EPL-1.0；引入白名单外的许可证会直接导致构建失败。

### 3. 借助 Context7 或网页工具读取最新开发文档与规范

- 所有代码建议必须基于最新版本的框架、语言、库的官方文档，严禁使用过时的 API 或语法。
- 文档获取优先级：
  1. Context7 工具：使用 Context7 检索目标技术的实时文档片段、示例代码或迁移指南，确保时效性。
  2. 网页浏览工具：如果 Context7 无法使用或覆盖不足，直接访问官方文档站，查询并引述最新内容。
- 对于即将废弃（deprecated）的特性，必须明确指出，并给出替代方案和官方推荐写法。
- 同时遵循对应技术生态的权威编码规范，在生成代码前通过文档确认当前推荐的风格和 lint 规则。

### 4. 执行流程（总览）

每次收到开发需求时，按以下步骤操作：

1. 搜索现有包/库，评估许可证，决定直接复用或寻找替代。
2. 检查本地可用技能，有则调用，减少重复编码。
3. 通过 Context7 或网页工具获取该技术的最新文档与编码规范。
4. 组装方案，复用组件并编写必要胶水代码，保证许可证清晰。
5. 交付时附带：集成步骤、许可证清单、技能调用记录、参考文档链接。

## 强制中文思考与回复

- **所有内部思考过程必须使用中文**
- **所有回复、注释、文档默认使用中文**
- 用户用中文提问时必须用中文回复
- 不向用户暴露冗长内部推理链，表达清楚、结构明确

## 项目概览

- **项目名**：闪电课表（原生 Android 版），面向吉首大学学生，通过模拟浏览器登录教务系统（JWXT）抓取 HTML 并用 Jsoup 解析
- **包名**：`com.shisan.campuspro`（Debug 变体后缀 `.debug`）
- **技术栈**：Kotlin 2.0.21 + Jetpack Compose + Material 3 + 多模块 Gradle
- **核心功能**：课表管理、考试管理、成绩管理、深色模式、课程提醒通知、应用内更新、可信电子凭证

## 模块架构

项目采用 `core` / `feature` 分层多模块架构，共 17 个 Gradle 模块：

```
app/                          # 应用入口：Application、MainActivity、导航、通知运行时
core/model/                   # 纯 Kotlin 数据模型（无 Android 依赖）
core/common/                  # 通用工具（User-Agent 等）
core/database/                # Room 数据库（Entity/DAO/迁移 v1→v4）
core/datastore/               # DataStore 偏好 + 加密凭据存储（AndroidX Security Crypto）
core/network/                 # Ktor 客户端 + Jsoup HTML 解析（教务系统爬虫、双会话）
core/data/                    # Repository 接口 + 同步协调 + 课表合并
core/designsystem/            # 主题（CampusTheme）、图标、颜色、通用控件
core/ui/                      # 共享 Compose 组件（课表卡片、自适应布局）
core/update/                  # 应用内更新（manifest 检查 / APK 下载 / 安装）
feature/auth/                 # 登录页（PORTAL 门户认证 + JWXT_DIRECT 直接登录）
feature/schedule/             # 课表页（周视图、课程编辑、学期切换、ICS 导出）
feature/grades/               # 成绩页（学期筛选、统计分析）
feature/exams/                # 考试页（倒计时排序）
feature/profile/              # 个人中心、课表管理、第二课堂、凭证入口
feature/settings/             # 设置、通知设置、隐私政策、开源许可
feature/credential/           # 可信电子凭证（服务列表、模板、订单、PDF 预览）
```

### 架构要点

- **DI 方式**：手动依赖注入，不使用 Hilt/Koin。`AndroidAppContainer`（在 `CampusApplication.kt` 中定义）是纯组装器，业务逻辑已提取到各协调器（`NotificationCoordinator`、`PrivacySessionManager`）和 core:data 层；通过 `CampusApp` Composable 参数传给各 Route
- **导航**：Jetpack Navigation Compose，单 Activity（`MainActivity`），4 个底部 Tab（课表/成绩/考试/我的）用 `HorizontalPager` 实现，手机 `NavigationBar` / 平板横屏 `NavigationRail`，`CampusAdaptiveProvider` 控制自适应
- **双登录模式**：`PORTAL`（CAS 统一认证，支持第二课堂与凭证）和 `JWXT_DIRECT`（直接登录教务系统，仅课表/成绩/考试）；`JwxtAuthRepository` 与 `JwxtSyncRepository` 按 `currentLoginMode()` 分发
- **两套独立会话**（`core:network`）：`JwxtDirectSession`（JWXT 直连）与 `PortalSession`（门户 CAS + castgc），共享 `JwxtClient` + `ResettableCookieStorage` + `CredentialsProvider`，各自用 Mutex 保证并发安全；`probeSession()` 自动登录探测
- **数据流**：教务 HTML → Ktor → Jsoup 解析（`Dispatchers.Default`）→ Room DB → Flow → ViewModel/State → Compose UI
- **后台通知**：WorkManager 每 6 小时同步检查 + AlarmManager 精确闹钟课程提醒（`SCHEDULE_EXACT_ALARM`），`NotificationWorkScheduler` 协调启停
- **应用内更新**：Release 用 HTTPS manifest，Debug 支持 `http://127.0.0.1:8080` 本地更新服务器；APK 下载校验后经 FileProvider 安装
- **构建变体扩展**：`BuildVariantExtension` 接口分离 Debug/Release 行为（Debug 含开发者工具入口，Release 为空实现）
- **隐私合规**：首次启动须同意隐私政策才可用在线功能，拒绝/撤销时清除所有在线数据（`PrivacyConsentStore`）
- **安全**：`network_security_config.xml` 禁止明文流量；凭据经 `EncryptedCredentialsStore`（AndroidX Security Crypto）加密存储

### 核心模块边界规则（显式）

以下规则与各模块 `build.gradle.kts` 中实际声明的 `project(...)` 依赖一致，代理在修改任何模块前必须先对照本节判定变更影响范围。

#### 1. 模块清单与职责边界（17 个）

| 模块 | 层 | 职责边界（只做这些） | 禁止承担 |
|---|---|---|---|
| `app` | 组合根 | Application、MainActivity、导航、通知运行时、唯一组装所有模块的 `AndroidAppContainer` | 业务逻辑、数据解析、持久化实现 |
| `core/model` | 叶子 | 纯 Kotlin 数据模型与纯函数，无 Android 依赖 | 引用任何项目模块或 Android 框架 |
| `core/common` | 叶子 | 通用工具（User-Agent 等） | 引用任何项目模块 |
| `core/datastore` | 核心 | DataStore 偏好 + 加密凭据存储 + 隐私同意存储 | 网络请求、业务编排 |
| `core/network` | 核心 | Ktor 客户端、教务/门户/凭证会话、HTML 解析 | 持久化、UI、业务编排 |
| `core/data` | 核心 | Repository 接口契约 + 同步协调 + 课表合并 + 学期/同步策略 | 直接持有 Room/WebView/Context 持久化实现 |
| `core/database` | 核心 | Room 数据库、Entity/DAO、迁移、Repository 接口的 Room 实现 | 网络请求、UI |
| `core/designsystem` | 核心 | 主题、图标、颜色、通用控件 | 业务逻辑、数据访问 |
| `core/ui` | 核心 | 共享 Compose 组件（课表卡片、自适应布局、列表卡片） | 业务逻辑、数据访问 |
| `core/update` | 核心 | 应用内更新（manifest 检查/下载/校验/安装） | 课表/成绩等业务数据 |
| `feature/auth` | 功能 | 登录页 UI 与 ViewModel | 直接调用会话/解析/持久化类 |
| `feature/schedule` | 功能 | 课表页（周视图、课程编辑、学期切换、导出分享） | 同上 |
| `feature/grades` | 功能 | 成绩页（学期筛选、统计分析） | 同上 |
| `feature/exams` | 功能 | 考试页（倒计时排序） | 同上 |
| `feature/profile` | 功能 | 个人中心、课表管理、第二课堂、凭证入口 | 同上 |
| `feature/settings` | 功能 | 设置、通知设置、合规页面、更新状态展示 | 同上 |
| `feature/credential` | 功能 | 可信电子凭证（服务列表、模板、订单、WebView/PDF） | 同上 |

#### 2. 依赖方向约束（实际 Gradle 依赖）

```
feature/* → core/data → core/{model, common, network}
core/database → core/data + core/datastore + core/model
core/update → core/datastore
core/ui → core/designsystem → core/model
core/datastore → core/model；core/network → core/{common, model}
app → 全部模块（组合根，唯一允许汇聚所有模块的位置）
```

- `feature/*` 允许依赖：`core/data`、`core/model`、`core/designsystem`、`core/ui`；`feature/credential`、`feature/profile` 额外允许 `core/common`；`feature/settings` 额外允许 `core/update`
- `core/data` 不依赖 `core/datastore`：与偏好/凭据存储的协作通过 `core/data` 定义的端口接口（`SyncSupportInterfaces.kt` 中的 `NotificationPreferenceStore`、`StartupSyncCoordinator.SyncMetadataStore`、`WebCookieCleaner`、`AcademicUpdateNotifier`）依赖倒置，由 `app` 组合根提供实现并注入，保持 `core/data` 为纯 JVM 模块
- 新增模块间依赖必须先对照本节；不在上述清单中的依赖方向一律视为违规，需先修改本节并说明理由，再改 `build.gradle.kts`

#### 3. 各核心模块公共 API 入口点

跨模块协作只允许经由下列入口，模块内部实现类（含 `internal`/解析器/Entity 映射）不视为公共 API：

| 模块 | 公共 API 入口点 |
|---|---|
| `core/model` | 数据模型与纯函数：`CampusModels.kt`、`CredentialModels.kt`、`PrivacyModels.kt`、`AcademicUpdateDiff.kt` |
| `core/common` | `CampusUserAgents` |
| `core/datastore` | `EncryptedCredentialsStore`、`PrivacyConsentStore`、`UserPreferencesDataSource` |
| `core/network` | 会话 `JwxtDirectSession`、`PortalSession`、`PortalCasSession`；客户端 `JwxtClient`、`CredentialClient`；端口 `CredentialsProvider`、`SessionFlagStore` |
| `core/data` | Repository 契约 `AuthRepository`/`ScheduleRepository`/`GradesRepository`/`ExamsRepository`/`SyncRepository`/`CredentialRepository`；实现 `JwxtAuthRepository`、`JwxtSyncRepository`、`DefaultCredentialRepository`、`ReminderAwareScheduleRepository`；协调 `StartupSyncCoordinator`、`AppContainer`；策略 `TermPolicy`、`SyncPolicy`、`ScheduleMerger` |
| `core/database` | `CampusDatabase`（含 DAO）、`RoomScheduleRepository`、`RoomGradesRepository`、`RoomExamsRepository` |
| `core/designsystem` | `CampusTheme`、`CampusControls`（`CampusButton` 等）、`CampusIcons`、`CampusCourseColors` |
| `core/ui` | `CampusAdaptiveProvider`、`AdaptiveTwoPane`/`AdaptiveContentColumn`、`WeeklyCourseGrid`/`CourseCard`/`WeekHeader`/`SemesterSelector`、`StatsCard`/`GradeCard`/`ExamCard`/`SettingsListItem`/`SyncStatusSnackbar` |
| `core/update` | `AppUpdateManager`（实现 `DefaultAppUpdateManager`）、`UpdateContracts` 中各接口、`KtorUpdateManifestSource`、`KtorUpdatePackageDownloader`、`UpdateFileIntegrity` |
| `feature/*` | 各模块仅向 `app` 导航暴露 Route Composable（如 `ScheduleRoute`）；ViewModel 与模块内工具为模块私有 |
| `app` | `AndroidAppContainer`（组合根，唯一允许实例化会话/数据库/存储并装配依赖的位置）、`MainActivity`、通知运行时 |

#### 4. 禁止跨层调用（硬性约束）

1. `feature/*` 禁止 import `core.network`、`core.database`、`core.datastore` 包：数据一律经 `core:data` 的 Repository 接口；更新能力仅 `feature/settings` 经 `core:update` 入口获取；UA 工具经 `core:common`
2. `core/*` 禁止 import 任何 `feature/*` 包；核心层对功能层的扩展只能通过 `app` 组合根注入回调或端口实现（参照 `SyncSupportInterfaces.kt` 模式）
3. `core/model` 与 `core/common` 禁止 import 任何项目内模块与 Android 框架 API
4. Room 的 Entity/DAO/映射函数仅存在于 `core/database` 内部；对外只暴露 `core:data` Repository 接口的 Room 实现，消费方只见 `core:model` 类型
5. 会话类（`JwxtDirectSession`/`PortalSession`/`PortalCasSession`）只能由 `core:data` 仓库与 `app` 组合根持有，禁止在 `feature/*` 直接构造或调用 `probeSession()`
6. HTML 解析器（`ScheduleParser`/`GradeParser`/`ExamParser`/`JwxtHtmlParser` 等）只在 `core/network` 内部与测试中使用，禁止 `feature/*` 直接解析 HTML
7. 依赖注入装配只发生在 `AndroidAppContainer`；禁止在 ViewModel/Route/Repository 内部 new 出跨模块实现（测试用 InMemory/Fake 实现除外）
8. 违反以上任一条即判定为跨层调用：先评估是否应下沉到 `core/data` 契约或上移到 `app` 组合根，再实施修改

## 关键依赖版本

| 依赖 | 版本 |
|---|---|
| AGP | 8.13.2 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.27 |
| Compose BOM | 2025.06.00 |
| compileSdk / targetSdk / minSdk | 36 / 34 / 24 |
| Room | 2.8.4 |
| Navigation | 2.9.5 |
| Ktor | 3.2.3 |
| Jsoup | 1.22.2 |
| Coroutines | 1.10.2 |
| WorkManager | 2.11.2 |
| Security Crypto | 1.1.0-alpha07 |
| kotlinx-serialization | 1.7.3 |

全部版本声明集中在 `gradle/libs.versions.toml`（版本目录），各模块引用版本变量而非硬编码。

## 常用命令

```bash
# 构建 Debug APK（无需额外配置）
./gradlew assembleDebug

# 安装到设备/模拟器
./gradlew installDebug

# 构建 Release APK（需先配好 keystore.properties 与 update.properties，见下）
./gradlew assembleRelease

# 运行所有单元测试（JVM 测试，不需要设备）
./gradlew test

# 运行单个模块测试
./gradlew :core:network:test

# 运行单个测试类 / 方法
./gradlew :core:network:test --tests "*JwxtDirectSessionTest*"
./gradlew :core:network:test --tests "*JwxtDirectSessionTest.testAutoLogin*"

# 清理构建
./gradlew clean

# 查看依赖树
./gradlew :app:dependencies

# 手动检查 Release APK 不含 Debug 标记（assembleRelease 会自动运行）
./gradlew verifyReleaseDebugIsolation
```

## 构建要求（容易踩坑）

- **Debug 构建**：直接运行即可，applicationId 后缀 `.debug`
- **Release 构建**：
  - 根目录必须存在 `keystore.properties`（参照 `keystore.properties.example`），缺少会直接报错
  - 根目录必须存在 `update.properties`，其中 `manifestUrl` 必须为 HTTPS
  - `assembleRelease` 会自动运行 `verifyReleaseDebugIsolation`（检查 APK 无 Debug 标记）与 `verifyNoVendorPushDependencies`（禁止厂商/聚合 Push SDK），失败即构建失败
- **版本号**：通过 gradle 属性 `APP_VERSION_CODE` / `APP_VERSION_NAME` 注入，未提供时回退 1 / 1.0.0；提交前/发布前的完整验证链（assembleRelease → verifyReleaseDebugIsolation → verifyNoVendorPushDependencies → test）已封装为项目级 Command：`.qoder/commands/validate-build/COMMAND.md`（斜杠命令 `/validate-build`），含每步预期结果与失败处理路径
- **测试**：不支持 `./gradlew testRelease` 等变体限定命令，统一用 `./gradlew test`；单元测试全为 JVM 测试，网络模块用 `ktor-client-mock`，Flow 测试用 Turbine
- **JDK**：需 JDK 17+

## Maven 镜像

项目配置了国内镜像加速（在 `settings.gradle.kts` 中）：
- 腾讯云镜像
- 阿里云镜像（public / google / gradle-plugin）

## 踩坑记录

- **JVM 目标统一**：`build.gradle.kts` 根目录通过 `allprojects` 强制所有模块的 `compileOptions` 和 Kotlin `jvmTarget` 统一为 Java 17，解决 Kotlin (jdk21) 与 Java (1.8) 目标不一致问题；必须用 AGP DSL 设置，直接改 JavaCompile task 会被 AGP 覆盖
- **KSP**：使用 KSP 2.0.21-1.0.27 作为注解处理器（Room 编译期代码生成），不是 KAPT
- **ProGuard**：Release 构建已开启混淆（`isMinifyEnabled = true`），规则在 `app/proguard-rules.pro`
- **许可证校验**：AboutLibraries 插件 `StrictMode.FAIL`，只允许 Apache-2.0/MIT/BSD-2-Clause/BSD-3-Clause/EPL-1.0
- **本地教务测试账号**：复制 `.env.example` 为 `.env` 填写测试账号，不要提交真实凭据

## 代码风格

- 优先遵循 Google Kotlin Style Guide（`kotlin.code.style=official`）
- 注释解释"为什么"而非"做了什么"
- 包名与模块命名空间一致（如 `com.shisan.campuspro.core.data`）
- 模块依赖方向：以《模块架构 → 核心模块边界规则（显式）》为准（`feature → core/data → core/{model,common,network}`，`core/database → core/data`；`core/data` 与存储的协作通过端口接口依赖倒置），变更依赖或跨层调用前必须先对照该节
