# 开发指南

## 环境

| 工具 | 要求 |
| --- | --- |
| JDK | 17 或更高；CI 使用 17 |
| Android SDK | 36.1 |
| Android NDK | 28.2.13676358 |
| CMake | 3.22.1 |

具体版本以 `app/build.gradle.kts`、`.github/workflows/android-ci.yml` 和 Gradle Wrapper 配置为准。

## 本地配置

高德地图 Key 可写入不提交版本库的 `local.properties`：

```properties
AMAP_API_KEY=your-key
```

也可在命令行通过 Gradle 属性传入：

```powershell
.\gradlew.bat assembleDebug -PAMAP_API_KEY=your-key
```

Release 签名使用根目录 `keystore.properties`。从 `keystore.properties.example` 复制字段并改为本机
路径和凭据；签名文件与真实凭据不得提交。未配置完整签名时仍可生成未签名 Release APK。

生产服务端固定由 `AppConfig.BASE_URL` 定义，应用内不提供服务端地址输入框。修改该值属于服务端
契约变更，需按 [服务端契约](../architecture/SERVER_CONTRACT.md) 复核。

## 常用命令

以下命令均从仓库根目录执行。

```powershell
# 快速编译
.\gradlew.bat assembleDebug

# JVM 测试
.\gradlew.bat testDebugUnitTest

# 静态检查
.\gradlew.bat spotlessCheck detektDebug lintDebug

# 截图验证
.\gradlew.bat validateDebugScreenshotTest

# 仪器测试 APK 编译
.\gradlew.bat assembleDebugAndroidTest

# 与 CI 对齐的本地门禁
.\gradlew.bat spotlessCheck detektDebug
.\gradlew.bat testDebugUnitTest validateDebugScreenshotTest lintDebug assembleDebug assembleDebugAndroidTest
```

连接设备或模拟器后，可执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

截图基线更新、Baseline Profile、Macrobenchmark 和 Release 命令分别见对应专题文档。

## 测试分层

| 类型 | 位置 | 适合验证 |
| --- | --- | --- |
| JVM 单元测试 | `app/src/test` | 状态机、Controller、DTO/Mapper、缓存算法和协议边界 |
| 仪器测试 | `app/src/androidTest` | SQLite、Compose 交互、字体缩放和 Android 框架行为 |
| 截图测试 | `app/src/screenshotTest` | 页面布局、主题、状态和长文本回归 |
| Baseline Profile / Macrobenchmark | `baselineprofile/src/main` | 启动路径、帧耗时和内存 |

测试应放在能最低成本覆盖真实风险的层级。涉及权限、BLE、定位、地图 native SDK、前台服务、
后台麦克风、弱网和音频设备的行为，最终仍需真机验证。

## 代码质量

- Spotless 使用 ktlint，并默认只检查相对 `origin/main` 变化的 Kotlin/Gradle 文件。
- Detekt 使用 `config/detekt/detekt.yml` 和存量基线；新代码不应扩大基线。
- Compose 截图只在视觉变化确认后更新，普通开发和 CI 运行验证任务。
- 第三方 RNNoise 源码位于 `app/src/main/cpp/third_party/rnnoise`，不纳入自研代码格式与规模判断。

CI 的实际命令以 `.github/workflows/android-ci.yml` 为准。修改任务名或质量门禁时，应同步更新本页。

## 常见问题

### 地图不可用

先检查 `AMAP_API_KEY`，再检查设备 ABI 与系统页大小。当前高德 native SDK 仅覆盖
`armeabi-v7a` 和 `arm64-v8a`，并存在 16K 页兼容限制；应用会在不兼容环境中显示不可用提示。

### Release 没有签名

确认 `keystore.properties` 的 `storeFile`、`storePassword`、`keyAlias` 和 `keyPassword` 均非空，
且 `storeFile` 能从 `app` 模块目录正确解析。完整流程见 [Release 指南](../operations/RELEASE_OPTIMIZATION.md)。

### 性能测试拒绝模拟器

这是预期行为。Profile 规则可以在兼容模拟器上生成，但 Macrobenchmark 性能数字必须在物理设备上采集。
