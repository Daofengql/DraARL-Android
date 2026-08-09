# 依赖升级记录

更新时间：2026-08-10

依赖按可独立回滚和验证的版本组升级。每组执行静态检查、JVM 测试、截图验证、Lint、Debug APK 和
AndroidTest APK 构建，不与业务或 UI 重构放在同一提交。

## AndroidX Core

| 依赖 | 升级前 | 当前 | Maven 最新稳定版 |
| --- | --- | --- | --- |
| `androidx.core:core-ktx` | 1.10.1 | 1.18.0 | 1.19.0 |
| `androidx.core:core-splashscreen` | 1.0.1 | 1.2.0 | 1.2.0 |

Core 1.19.0 的 AAR 元数据声明 `minCompileSdk=37`，项目当前使用 Android 36.1，因此本批选择仍声明
`minCompileSdk=36` 的最高稳定版 1.18.0。此次不为单个依赖扩大 compile/target SDK 迁移范围。

本地门禁通过 288 个 JVM 测试、38 张 Compose 截图、Debug Lint、Detekt、Spotless、Debug APK 和
AndroidTest APK 构建。升级未要求生产代码或资源适配。

## Lifecycle 与 Activity

| 依赖 | 升级前 | 当前 | Maven 最新稳定版 |
| --- | --- | --- | --- |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.1 | 2.10.0 | 2.11.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.6.1 | 2.10.0 | 2.11.0 |
| `androidx.activity:activity-compose` | 1.8.0 | 1.13.0 | 1.13.0 |

Lifecycle Runtime Compose 2.11.0 的 AAR 元数据声明 `minCompileSdk=37`，因此 Lifecycle 组统一选择仍支持
Android 36.1 的 2.10.0，避免同一产品线混用版本。Activity Compose 1.13.0 声明 `minCompileSdk=36`，可使用
最新稳定版。

完整本地门禁继续通过 288 个 JVM 测试和 38 张 Compose 截图，未要求生产代码、Manifest 或资源适配。

## Compose

| 依赖 | 升级前 | 当前 | Google Maven 最新稳定版 |
| --- | --- | --- | --- |
| `androidx.compose:compose-bom` | 2026.02.01 | 2026.06.01 | 2026.06.01 |

Compose UI Test 在该版本中弃用 `androidx.compose.ui.test.junit4.createComposeRule`，底栏仪器测试已迁移到
`androidx.compose.ui.test.junit4.v2.createComposeRule`。v2 规则使用标准测试调度语义，现有测试通过显式节点查询、
点击和断言等待空闲，不依赖旧规则的立即执行行为。

完整本地门禁通过 288 个 JVM 测试和 38 张 Compose 截图；迁移后的 AndroidTest Kotlin 强制重新编译且无新增
弃用警告，Debug Lint、APK 和 AndroidTest APK 构建继续通过。

后续仍需升级 Android JUnit 与 Espresso 测试库，并检查是否存在可删除的未使用直接依赖。
