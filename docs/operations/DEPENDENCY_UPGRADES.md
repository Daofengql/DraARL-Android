# 依赖升级

所有插件和依赖版本集中在 `gradle/libs.versions.toml`，Gradle 本身由 Wrapper 管理。本页只记录升级
策略与当前已知兼容边界，不重复完整版本目录。

## 当前兼容边界

| 范围 | 约束 |
| --- | --- |
| Android SDK | 应用使用 `compileSdk 36.1`、`targetSdk 36`、`minSdk 24` |
| AndroidX Core | 1.19.0 要求 `compileSdk 37+`，当前保持仍支持 SDK 36 的版本 |
| AndroidX Lifecycle | 2.11.0 要求 `compileSdk 37+`，同组依赖保持一致版本 |
| Compose | 使用 BOM 管理 Compose 组件版本，不为单个 Compose artifact 单独指定版本 |
| Compose UI Test | 使用 `junit4.v2` 规则 API；测试运行时显式使用 Espresso 3.7.0 |
| Baseline Profile | 插件、Macrobenchmark 与相关测试库在 `baselineprofile` 模块内成组升级 |
| 高德地图 | native SDK 仅覆盖 `armeabi-v7a` 与 `arm64-v8a`，并存在 16K 页兼容限制 |
| Kotlin / AGP / Gradle | 三者需要按官方兼容矩阵一起评估，不单独跳版本 |

依赖的当前版本以 `gradle/libs.versions.toml` 为准；SDK、NDK、CMake 和 ABI 以
`app/build.gradle.kts` 为准。

## 升级分组

每次只升级可以独立回滚和验证的一组：

1. Gradle、AGP、Kotlin 与构建插件。
2. AndroidX Core、Lifecycle 与 Activity。
3. Compose BOM、Compose 测试与 Screenshot 插件。
4. 网络和图片库，如 OkHttp、Coil。
5. 音频、地图和其他带运行时或 native 风险的库。
6. Baseline Profile、Macrobenchmark、UIAutomator 与 Android 测试库。

不要把依赖升级与大范围业务重构放在同一提交。若某个版本要求提升 `compileSdk`、`targetSdk`、JDK、
NDK 或最低 Android 版本，应将平台迁移作为独立工作评估。

## 升级流程

1. 阅读目标版本的 release notes、AAR metadata 和迁移说明。
2. 更新 `gradle/libs.versions.toml` 或 Wrapper；必要时同步 Gradle 配置。
3. 运行依赖解析，确认没有意外降级、重复版本或测试运行时冲突。
4. 编译受影响源码并处理弃用，不通过 suppress 隐藏需要迁移的 API。
5. 执行与风险相称的自动化门禁。
6. 对地图、音频、BLE、权限和前台服务等行为执行真机验证。
7. 仅在出现新的长期兼容限制时更新本页；普通版本号变化不写升级流水账。

## 验证命令

基础门禁：

```powershell
.\gradlew.bat spotlessCheck detektDebug
.\gradlew.bat testDebugUnitTest validateDebugScreenshotTest lintDebug assembleDebug assembleDebugAndroidTest
```

构建工具、R8、native 或 Profile 相关升级还需执行：

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat :app:verifyReleaseArtifact
.\gradlew.bat :app:generateReleaseBaselineProfile
```

Android 测试库升级应在目标 API 的设备或模拟器运行 `connectedDebugAndroidTest`。性能库升级后的数字
必须在物理设备采集。

## 回滚标准

出现以下情况且无法在当前升级范围内明确修复时，应回滚该组升级：

- 要求未计划的 SDK、JDK、NDK 或最低系统版本迁移。
- Release 产物丢失 ABI、native 库、R8 keep 边界或 Baseline Profile。
- 测试库在目标 Android 版本启动前崩溃，或只能通过降低覆盖度绕过。
- 地图、音频、BLE、前台服务或权限行为出现无法解释的真机回归。
