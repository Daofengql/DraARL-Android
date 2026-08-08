# DraARL Android 项目概览

审计日期：2026-08-08
代码基线：`2.0.0-alpha1`（versionCode 8）

## 规模结论

这是一个中等规模、功能面较宽的单模块 Android 客户端。自研生产 Kotlin 约 2.36 万有效代码行，已经覆盖账号、设备、群组、实时通信、地图、APRS 和多种业余无线电工具；复杂度主要来自通信状态、音频生命周期、后台服务和硬件/系统权限，而不是 Gradle 模块数量。

| 范围 | 文件数 | 代码行数 | 说明 |
| --- | ---: | ---: | --- |
| 生产 Kotlin | 136 | 23,665 | 不含空行、生成目录和第三方源码 |
| JVM 单元测试 Kotlin | 48 | 2,069 | 149 个测试用例 |
| Android 仪器测试 Kotlin | 3 | 94 | 主要覆盖底部导航和 SQLite |
| Compose 截图测试 Kotlin | 2 | 366 | 12 张壳层和一级页面参考图 |
| 主资源 XML | 18 | 292 | Manifest、网络安全、主题等 |
| 自研 C++ 接入 | 1 | 121 | RNNoise JNI/CMake 桥接 |
| 第三方 RNNoise C/H | 32 | 281,091 | 约 30.2 MB，绝大部分为模型权重数据 |

RNNoise 会显著放大仓库行数和体积，评估自研规模时应将 `app/src/main/cpp/third_party/rnnoise` 单独统计。

## 代码分布

生产 Kotlin 文件主要分布如下：

| 包 | 文件数 | 职责 |
| --- | ---: | --- |
| `ui` | 56 | Compose 页面、导航和组件 |
| `radio` | 21 | UDP、音频、重连、缓存和前台通信服务 |
| `data` | 16 | 模型、本地存储、消息对账和路由 |
| `tools` | 12 | BLE、中继、通联日志和预设 |
| `maps` / `aprs` | 12 | 地图、坐标换算、网格和 APRS-IS |
| `settings` | 4 | 设置状态、持久化和缓存清理协调 |
| 其他 | 15 | 账号、设备、群组、网络、资料、协议和更新 |

当前最大的生产 Kotlin 文件是：

| 文件 | 行数 |
| --- | ---: |
| `AppController.kt` | 1,819 |
| `network/ApiClient.kt` | 1,184 |
| `radio/UdpRadioClient.kt` | 1,094 |
| `ui/screens/DevicesScreen.kt` | 1,003 |
| `ui/screens/GroupsScreen.kt` | 844 |

## 架构边界

- UI 使用 Jetpack Compose，一级导航固定为设备、群组、PTT、工具、我的。
- `AppController` 仍是跨功能状态协调中心；设备、群组、资料、工具和设置已下沉到各自 Controller，但会话、刷新、PTT、APRS 和导航仍集中于此。
- 设备、群组、工具和个人页已拆出只接收页面数据与回调的内容层，可脱离 `AppController` 生成稳定截图。
- HTTP 契约集中在 `ApiClient`，实时通信由 `UdpRadioClient` 和 `RadioConnectionService` 承担。
- SQLite/SharedPreferences 分别保存消息历史、仪表盘/工具缓存、会话和客户端设置。
- 原生层仅承担 RNNoise；Opus 编解码主要通过 Concentus 在 JVM 层实现。

## 维护重点

1. `AppController`、`ApiClient`、`UdpRadioClient` 都超过 1,000 行，是当前修改冲突和回归风险最集中的位置。后续应按会话、路由、消息同步和 APRS 继续拆分，同时保持单一状态来源。
2. 自动化测试以 JVM 测试为主，仪器测试只有 3 个文件。BLE、定位、前台服务、弱网重连、后台麦克风和系统权限仍需要真机覆盖。
3. CI 已固定 Android SDK 36.1、NDK 28.2 和 CMake 3.22，并执行静态检查、截图验证与 Debug 构建门禁；地图运行验收仍依赖注入高德 Key，Release 签名仍需发布环境显式配置。
4. Android 客户端依赖同仓库之外的 DraARL Server API 与 UDP 协议文档。服务端契约变更时，应同时检查 README 的“服务端契约”、`DraarlProtocol`、`ApiClient` 和更新清单校验。

## 文档适配状态

- `README.md` 已覆盖当前 2.0 alpha 的导航、PTT/多频道、地图、APRS 和工具能力，并记录实际构建依赖。
- `TODO.md` 只保留尚未完成的实施与真机验收清单；已落地的静态质量门禁不再列为待办。
- 设置状态、持久化、音频偏好同步和缓存清理已集中到 `SettingsController`；设置入口、系统设置与存储页面不再接收完整 `AppController`。
- 应用壳层和五个一级页面已有 12 张可重复生成的浅色/深色参考图，覆盖窄屏、常规手机、横屏、长中文和 1.5 倍字体。
- GitHub Actions、Spotless/ktlint、Detekt 存量基线和 Markdown 链接检查已接入，RNNoise 第三方目录被显式排除。
- Android 仪器测试 APK 已在本次基线编译通过，但尚未连接设备执行；Release 构建和签名也需在发布候选版本上重新验证。
