# 第三方组件许可证清单

本项目的运行时依赖通过 Gradle/Maven 集成，并由 AboutLibraries 在构建期生成应用内许可证数据。`sample/` 下的外部参考项目被 Git 忽略且不参与 APK 构建；主项目的部分课表界面、交互方式与功能设计参考了 WakeUp课程表，相关归属在应用内和源码中单独声明。

## 应用运行时组件

| 组件 | 声明版本 | 许可证 | 用途 |
|---|---:|---|---|
| Kotlin stdlib | Gradle 解析 | Apache-2.0 | Kotlin 运行时 |
| AndroidX Core | 1.17.0 | Apache-2.0 | Android 通用扩展与通知兼容 |
| AndroidX Compose BOM | 2025.06.00 | Apache-2.0 | Compose 依赖版本对齐 |
| AndroidX Compose UI / Foundation / Material 3 / Material Icons | BOM 管理 | Apache-2.0 | 原生声明式 UI 与图标 |
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 | Compose Activity 集成 |
| AndroidX Browser | 1.10.0 | Apache-2.0 | 校外访问时通过 Custom Tabs 安全降级到学校 WebVPN |
| AndroidX Lifecycle Runtime Compose / ViewModel | 2.10.0 | Apache-2.0 | 生命周期感知状态与 ViewModel |
| AndroidX Navigation Compose | 2.9.5 | Apache-2.0 | 应用导航 |
| AndroidX Room | 2.8.4 | Apache-2.0 | 本地数据库 |
| AndroidX DataStore Preferences | 1.1.7 | Apache-2.0 | 偏好与会话标记存储 |
| AndroidX Security Crypto | 1.1.0-alpha07 | Apache-2.0 | 教务凭据加密存储 |
| AndroidX WorkManager | 2.11.0 | Apache-2.0 | 课程提醒后台任务 |
| AndroidX ProfileInstaller | 1.3.1（运行时解析为 1.4.0） | Apache-2.0 | Baseline Profile 安装 |
| kotlinx-coroutines | 1.10.2 | Apache-2.0 | 结构化并发与 Flow |
| kotlinx-serialization-json | 1.7.3（运行时由 Gradle 解析） | Apache-2.0 | 读取构建生成的许可证数据 |
| Ktor Client + OkHttp engine | 3.2.3 | Apache-2.0 | 教务系统 HTTP、Cookie、超时与重试 |
| jsoup | 1.22.2 | MIT | 教务系统 HTML 解析 |

应用内“开源许可”页面展示 Gradle 最终解析的完整传递依赖、准确版本和许可证全文；上表用于概括项目主动引入的主要运行时组件。

## 构建与测试工具

| 组件 | 版本 | 许可证 | 用途 |
|---|---:|---|---|
| Android Gradle Plugin | 8.13.2 | Apache-2.0 | Android 构建系统 |
| Kotlin Gradle Plugin / Compose Compiler Plugin | 2.0.21 | Apache-2.0 | Kotlin 与 Compose 编译 |
| Kotlin Symbol Processing (KSP) | 2.0.21-1.0.27 | Apache-2.0 | Room 代码生成 |
| AboutLibraries Android Gradle Plugin | 15.0.3 | Apache-2.0 | 构建期收集并校验第三方许可证 |
| JUnit | 4.13.2 | EPL-1.0 | JVM 单元测试 |

## 设计与功能参考项目

| 项目 | 参考版本 | 许可证 | 作者 / 用途 |
|---|---:|---|---|
| [WakeUp课程表（WakeupSchedule_Kotlin）](https://github.com/YZune/WakeupSchedule_Kotlin) | 3.612 | Apache-2.0 | YZune（杨增）及贡献者；课表界面、交互方式与部分功能设计参考 |

WakeUp课程表原项目声明允许借鉴，并希望相关 App 对项目作出说明。闪电课表已在“设置 → 开源许可”中提供特别致谢、原项目链接和 Apache-2.0 许可证全文入口。

## 文档专用组件

| 组件 | 版本 | 许可证 | 用途 |
|---|---:|---|---|
| Mermaid | 11.16.0 | MIT | 在 `docs/project-development-map.html` 中渲染架构和数据流图 |
