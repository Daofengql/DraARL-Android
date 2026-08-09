# 依赖升级记录

更新时间：2026-08-09

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

后续仍按 Lifecycle/Activity、Compose 和 Android 测试库分组升级；每组完成后在此追加版本、兼容限制和验证结果。
