# Release 指南

本页说明 Release APK 的签名、ABI、R8、Baseline Profile 和产物验收。配置事实以
`app/build.gradle.kts` 与 `app/src/main/keepRules/release.keep` 为准。

## 发布配置

Release 使用 Android Gradle Plugin 的优化接口执行 R8 代码压缩、混淆、优化和资源收缩。

主要 keep 边界：

- 高德地图和搜索 SDK 的反射/JNI 类型。
- `RnnoiseNative` 的类名与四个 JNI 方法。
- Android Manifest 中由系统创建的 Activity、Service、Application 和 Provider。

业务 DTO 使用手写 `JSONObject` 映射，不依赖反射序列化规则。新增反射、JNI 或运行时类名查找时，
必须同时更新 keep rules 和 `verifyReleaseArtifact` 可自动验证的部分。

## 签名

在仓库根目录创建不提交版本库的 `keystore.properties`：

```properties
storeFile=../path/to/release.jks
storePassword=replace-with-local-password
keyAlias=draarl
keyPassword=replace-with-local-password
```

四个字段均有效时 Release 使用该签名；否则仍可生成未签名 APK。发布前必须用 Android 工具确认
签名证书与已发布版本一致，不能只依赖构建成功。

## ABI

当前发布矩阵只支持 `armeabi-v7a` 和 `arm64-v8a`，由高德地图与 RNNoise 的 native 库共同约束。
x86/x86_64 不属于发布目标。

| 目标 | 命令 | 产物 |
| --- | --- | --- |
| 默认 ARM64 | `.\gradlew.bat :app:assembleRelease` | `app-release.apk` |
| ARMv7 | `.\gradlew.bat :app:assembleRelease -Pabi=v7` | `app-release.apk` |
| 双 ABI 单包 | `.\gradlew.bat :app:assembleReleaseUniversal` | `app-release.apk` |
| 双 ABI 拆分加 universal | `.\gradlew.bat :app:assembleReleaseAll` | 两个 ABI APK 与 `app-universal-release.apk` |

也可使用 `-Pabi=arm64-v8a`、`-Pabi=armeabi-v7a`、逗号分隔 ABI、`universal` 或 `all`。产物位于
`app/build/outputs/apk/release`。

## 构建与静态验收

```powershell
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:verifyReleaseArtifact
```

`verifyReleaseArtifact` 依赖当前 ABI 配置的 `assembleRelease`，检查：

- 预期 APK 文件存在。
- APK 包含 Manifest、DEX 和编译后的 Baseline Profile。
- APK 只包含所选 ABI，并包含对应 RNNoise 与高德 native 库。
- `mapping.txt`、`seeds.txt`、`usage.txt`、`resources.txt`、`configuration.txt` 存在且非空。
- R8 未重命名 RNNoise JNI owner 类和高德 `MapView` 边界。
- seeds 保留 RNNoise 的四个 native 方法。
- 源 Baseline/Startup Profile 存在并达到最低规则数量。

R8 报告位于 `app/build/outputs/mapping/release`。该任务不替代签名检查、Manifest 组件行为和真机回归。

## Baseline Profile

生成 Profile：

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile
```

规则写入 `app/src/release/generated/baselineProfiles` 并随 Release APK 编译到 `assets/dexopt`。当前无账号
路径覆盖冷启动、进入注册和返回登录；增加登录态路径时必须确保测试账号、服务端和设备条件可重复。

运行 Macrobenchmark：

```powershell
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

Profile 可以在兼容模拟器上生成，但 AndroidX 会拒绝模拟器性能指标。冷启动、帧耗时和内存对照必须
在同一台物理设备、相同系统和相同热状态下采集。

## 发布检查清单

- 更新 `versionCode` 和 `versionName`，并确认服务端资源 manifest 的版本与兼容要求。
- 使用发布环境的 `AMAP_API_KEY` 与签名配置构建目标 ABI。
- 运行静态质量门禁、JVM 测试、截图验证、Lint 和 Release 产物验收。
- 检查 APK 签名、文件名、ABI、版本、SHA-256 和安装升级路径。
- 在目标设备验证登录恢复、PTT、前台服务、RNNoise、地图、APRS 和应用内更新。
- 若重新采集 Profile，确认源码规则与 APK 中的编译产物均存在。
- 将仍未完成的人工项保留在 [TODO.md](../../TODO.md)，不要把临时结果写成长期基线。

## 体积分析

当前体积主要由高德地图和 RNNoise native 库决定。需要比较优化效果时，必须使用相同源码、依赖、
ABI、签名状态和地图 Key 分别构建对照组，再记录 APK、DEX、资源、assets 和 native 分类。一次性字节数
与哈希应保存在发布记录或 CI 产物中，而不是长期写死在本页。
