# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**闪电课表**（CampusProAndroid）是一款面向吉首大学学生的 Android 课表管理工具。它通过模拟浏览器登录教务系统（JWXT）抓取 HTML 页面并用 Jsoup 解析，获取课表、成绩、考试等数据。

- **技术栈**：Kotlin 2.0.21 + Jetpack Compose + Material 3 + 多模块 Gradle
- **包名**：`com.shisan.campuspro`
- **compileSdk / targetSdk / minSdk**：36 / 34 / 24
- **JVM 目标**：统一 Java 17
- **APP 标签**：闪电课表

## 构建与运行命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 直接安装到设备/模拟器
./gradlew installDebug

# 构建 Release APK（需要 keystore.properties 和 update.properties）
./gradlew assembleRelease

# 运行所有单元测试（JVM 测试，不需要设备）
./gradlew test

# 运行单个模块的所有测试
./gradlew :core:network:test
./gradlew :core:data:test
./gradlew :feature:schedule:test

# 运行单个测试类
./gradlew :core:network:test --tests "*JwxtSessionTest*"

# 运行单个测试方法
./gradlew :core:network:test --tests "*JwxtSessionTest.testAutoLogin*"

# 清理构建
./gradlew clean

# 查看依赖树
./gradlew :app:dependencies

# 检查 Release APK 是否包含 Debug 标记（打包后自动执行，也可手动触发）
./gradlew verifyReleaseDebugIsolation
```

## 构建要求

- **Debug 构建**：直接运行，无需额外配置
- **Release 构建**：
  - 根目录需存在 `keystore.properties`（参照 `keystore.properties.example`）
  - 根目录需存在 `update.properties`（定义 `manifestUrl`，必须 HTTPS）
- **JDK**：需要 JDK 17+
- **Gradle 仓库**：已配置腾讯云和阿里云镜像加速（`settings.gradle.kts`）

## 项目架构

### 模块结构（14 个 Gradle 模块）

```
app/                          # 应用入口：Application、MainActivity、导航、通知运行时
core/model/                   # 纯 Kotlin 数据模型（无 Android 依赖）
core/common/                  # 通用工具（User-Agent 等）
core/network/                 # Ktor 网络客户端 + Jsoup HTML 解析（教务系统爬虫、CAS 认证）
core/database/                # Room 数据库（Entity/DAO/迁移）
core/datastore/               # DataStore 偏好 + 加密凭据存储（AndroidX Security Crypto）
core/data/                    # Repository 接口定义 + 同步协调 + 课表合并
core/designsystem/            # 主题（CampusTheme）、图标、颜色、通用控件
core/ui/                      # 共享 Compose 组件（课表卡片、列表、自适应布局）
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

1. **手动依赖注入**：不使用 Hilt/Koin。`AndroidAppContainer`（`CampusApplication.kt`）是**纯组装器**——只负责创建和暴露对象图，业务逻辑已提取到各协调器（`NotificationCoordinator`、`PrivacySessionManager`）和 core 层 Repository。通过 `CampusApp` Composable 参数传递给各 Route。

2. **导航**：单 Activity 架构，Jetpack Navigation Compose。4 个底部 Tab（课表/成绩/考试/我的）使用 `HorizontalPager` 实现，配合 `NavigationBar`（手机）或 `NavigationRail`（平板/横屏）切换。使用 `CampusAdaptiveProvider` 控制布局自适应。

3. **两种登录模式**：
   - `PORTAL`：通过学校统一身份认证（CAS）登录，支持第二课堂和可信电子凭证
   - `JWXT_DIRECT`：直接登录教务系统，仅能使用课表/成绩/考试功能

4. **数据流**：
   ```
   教务系统 HTML → Ktor 客户端获取 → Jsoup 解析 → Room DB → Flow → ViewModel/State → Compose UI
   ```
   所有数据解析在 `Dispatchers.Default` 线程池中执行，解析后的数据通过 Repository 存入 Room。

5. **认证与会话**（两套独立系统，各自管理生命周期，使用 Mutex 保证并发安全）：
   - `JwxtDirectSession`（`core/network`）：JWXT 直连登录 + 页面抓取（课表/成绩/考试）
   - `PortalSession`（`core/network`）：门户 CAS 统一认证 + `castgc()`（供可信电子凭证建立 SSO 会话）
   - 共享底层：`JwxtClient` + `ResettableCookieStorage` + `CredentialsProvider` + `SessionFlagStore`；结果类型集中在 `JwxtSessionTypes.kt`
   - `JwxtAuthRepository`（`core/data`）按 `currentLoginMode()` 分发给对应会话；`JwxtSyncRepository`（`core/data`）同法协调同步
   - 自动登录探测（`probeSession()`）检查 Cookie 有效性，失效时使用加密存储的凭据重新登录
   - `sessionFlagStore` 追踪会话标记和上次登录模式

6. **后台通知**：
   - WorkManager `PeriodicWorkRequest`（每6小时）同步检查
   - AlarmManager 精确闹钟实现课程提醒（`SCHEDULE_EXACT_ALARM` 权限）
   - `NotificationWorkScheduler` 协调 WorkManager 任务的启停

7. **应用内更新**：
   - Release 构建：从 HTTPS manifest URL 检查更新
   - Debug 构建：支持 `http://127.0.0.1:8080` 本地更新服务器
   - APK 下载后校验完整性，通过 FileProvider 发起安装

8. **构建变体扩展**：`BuildVariantExtension` 接口分离 Debug/Release 行为：
   - Debug：包含开发者工具入口（DeveloperToolsRoute）
   - Release：空实现，并有验证任务确保无 Debug 代码泄露

9. **隐私合规**：
   - 首次启动需同意隐私政策后才能使用在线功能
   - `PrivacyConsentStore` 管理同意状态
   - 拒绝/撤销同意时清除所有在线数据

### 核心文件地图

| 文件 | 职责 |
|---|---|
| `app/.../CampusApplication.kt` | 纯组装器 `AndroidAppContainer`，只创建对象图 |
| `app/.../MainActivity.kt` | 单 Activity，Navigation + HorizontalPager + 状态管理 |
| `app/.../notification/NotificationRuntime.kt` | 通知渠道、Worker、Alarm 接收器 |
| `app/.../notification/NotificationCoordinator.kt` | 通知协调器：偏好读写 + WorkManager 调度绑定 |
| `app/.../PrivacySessionManager.kt` | 隐私合规协调器：同意状态 + 登出 + 通知清理 |
| `core/network/.../JwxtClient.kt` | Ktor HTTP 客户端封装 + 共享 Cookie 存储 |
| `core/network/.../HttpClientFactory.kt` | 4 种预配置 HttpClient 工厂（jwxt/casAuth/jwxtOAuth/credential） |
| `core/network/.../JwxtDirectSession.kt` | JWXT 直连会话：登录/自动登录/页面抓取 |
| `core/network/.../PortalSession.kt` | 门户 CAS 会话：登录/自动登录/castgc() |
| `core/network/.../JwxtSessionTypes.kt` | 共享类型：LoginResult/AutoLoginResult/CredentialsProvider/SessionFlagStore |
| `core/network/.../JwxtHtmlParser.kt` | Jsoup HTML 解析入口 |
| `core/network/.../CredentialClient.kt` | 可信电子凭证 API 客户端 |
| `core/network/.../PortalCasSession.kt` | 门户 CAS 统一认证流程 |
| `core/data/.../Repositories.kt` | Repository 接口 + InMemory 实现 |
| `core/data/.../JwxtAuthRepository.kt` | AuthRepository 实现：按登录模式分发到两套会话 |
| `core/data/.../JwxtSyncRepository.kt` | SyncRepository 实现：抓取/解析/落库 + 会话过期重试 |
| `core/data/.../ReminderAwareScheduleRepository.kt` | 课表写操作装饰器（触发提醒重建） |
| `core/database/.../RoomRepositories.kt` | Room 仓库实现（Schedule/Grades/Exams） |
| `core/data/.../ScheduleMerger.kt` | 远程课表与本地手动课程合并 |
| `core/data/.../StartupSyncCoordinator.kt` | 启动同步协调（12小时 TTL） |
| `core/database/.../CampusDatabase.kt` | Room 数据库定义 + DAO + 迁移（v1→v4） |
| `core/datastore/.../EncryptedCredentialsStore.kt` | AndroidX Security Crypto 加密凭据存储 |
| `core/datastore/.../UserPreferencesDataSource.kt` | DataStore 偏好存储 |
| `core/ui/.../CampusAdaptiveLayout.kt` | 自适应布局（Compact/Medium/Expanded） |
| `core/update/.../DefaultAppUpdateManager.kt` | 应用更新管理器 |

## 关键依赖版本

| 依赖 | 版本 | 用途 |
|---|---|---|
| AGP | 8.13.2 | Android Gradle 插件 |
| Kotlin | 2.0.21 | 语言 |
| Compose BOM | 2025.06.00 | Compose 组件 |
| Room | 2.8.4 | 本地数据库 |
| Ktor | 3.2.3 | HTTP 客户端（OkHttp 引擎） |
| Jsoup | 1.22.2 | HTML 解析 |
| Navigation | 2.9.5 | 页面导航 |
| Lifecycle | 2.10.0 | ViewModel + Runtime Compose |
| WorkManager | 2.11.2 | 后台同步任务 |
| Coroutines | 1.10.2 | 协程 |
| DataStore | 1.1.7 | 偏好存储 |
| Security Crypto | 1.1.0-alpha07 | 凭据加密 |
| kotlinx-serialization | 1.7.3 | JSON 序列化 |

全部版本声明集中在 `gradle/libs.versions.toml`（版本目录），各模块引用版本变量而非硬编码。

## 测试规范

- 所有单元测试为 **JVM 测试**（不需要 Android 设备或模拟器）
- 使用 JUnit 4 + kotlinx-coroutines-test + Turbine（测试 Flow）
- 网络模块测试使用 `ktor-client-mock` 模拟 HTTP 响应
- **不支持 `./gradlew testRelease` 等变体限定命令**，统一使用 `./gradlew test`

### 关键测试文件

**core:network**（HTML 解析 + 会话 + 凭证 API）：
- `JwxtDirectSessionTest.kt` — 直连会话测试（登录/自动登录/页面抓取）
- `PortalSessionTest.kt` — 门户会话测试（含 castgc 刷新与并发安全）
- `JwxtParserTest.kt` — Jsoup HTML 表格解析
- `PortalHtmlDumpTest.kt` — 真实 HTML dump 的解析验证
- `PortalJwxtIntegrationTest.kt` — 门户→教务系统集成测试
- `CredentialClientSessionTest.kt` — 凭证 API 客户端测试

**core:data**（Repository + 同步 + 合并 + 状态映射）：
- `ScheduleMergeTest.kt` — 远程/本地课程合并算法
- `StartupSyncCoordinatorTest.kt` — 启动同步策略
- `AcademicUpdateDetectorTest.kt` — 教务更新差异检测（从 app 迁入）
- `AuthSessionStatusTest.kt` — autoLogin 结果到会话状态的映射（从 app 迁入）

**feature 模块**（ViewModel + UI 状态）：
- `feature:auth` → `AuthViewModelTest.kt`
- `feature:schedule` → `ScheduleViewModelTest.kt`
- `feature:profile` → `ScheduleManagementViewModelTest.kt`, `CredentialAccessTest.kt`

## 代码风格

- 使用 `kotlin.code.style=official`（Google Kotlin 风格）
- 注释解释"为什么"而非"做了什么"
- 包名与模块命名空间一致（如 `com.shisan.campuspro.core.data`）
- 多模块间的依赖方向：`feature → core/data → core/{model,network,datastore}`，`core/database → core/data`（Room 仓库实现引用 Repository 接口契约）

## 注意事项与踩坑记录

- **JVM 目标统一**：根 `build.gradle.kts` 通过 `allprojects` 强制所有模块 `compileOptions` 和 Kotlin `jvmTarget` 统一为 Java 17
- **KSP 非 KAPT**：Room 编译期代码生成使用 KSP，非 KAPT
- **ProGuard**：Release 构建 `isMinifyEnabled = true`，混淆规则在 `app/proguard-rules.pro`
- **Release 验证**：构建完成后自动运行 `verifyReleaseDebugIsolation` 和 `verifyNoVendorPushDependencies` 验证任务
- **HTTP 安全**：`network_security_config.xml` 禁止明文流量，Debug 更新服务器只允许 `https://` 或 `http://127.0.0.1`
- **镜像仓库**：使用腾讯云 + 阿里云镜像加速 Gradle 依赖下载
- **REASONIX.md** 已弃用并从仓库中删除
- 项目的 `.claude/settings.local.json` 已配置 `./gradlew assembleDebug` 和 `git show` 白名单权限
