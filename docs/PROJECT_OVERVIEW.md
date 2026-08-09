# DraARL Android 项目概览

审计日期：2026-08-09
代码基线：`2.0.0-alpha1`（versionCode 8）

## 规模结论

这是一个中等规模、功能面较宽的单模块 Android 客户端。自研生产 Kotlin 约 2.67 万有效代码行，已经覆盖账号、设备、群组、实时通信、地图、APRS 和多种业余无线电工具；复杂度主要来自通信状态、音频生命周期、后台服务和硬件/系统权限，而不是 Gradle 模块数量。

| 范围 | 文件数 | 代码行数 | 说明 |
| --- | ---: | ---: | --- |
| 生产 Kotlin | 171 | 26,803 | 不含空行、生成目录和第三方源码 |
| JVM 单元测试 Kotlin | 55 | 4,050 | 215 个测试用例 |
| Android 仪器测试 Kotlin | 3 | 94 | 主要覆盖底部导航和 SQLite |
| Compose 截图测试 Kotlin | 4 | 725 | 22 张壳层、页面、状态和组件参考图 |
| 主资源 XML | 18 | 292 | Manifest、网络安全、主题等 |
| 自研 C++ 接入 | 1 | 121 | RNNoise JNI/CMake 桥接 |
| 第三方 RNNoise C/H | 32 | 281,091 | 约 30.2 MB，绝大部分为模型权重数据 |

RNNoise 会显著放大仓库行数和体积，评估自研规模时应将 `app/src/main/cpp/third_party/rnnoise` 单独统计。

## 代码分布

生产 Kotlin 文件主要分布如下：

| 包 | 文件数 | 职责 |
| --- | ---: | --- |
| `ui` | 57 | Compose 页面、导航和组件 |
| `radio` | 31 | 消息状态与同步、会话、UDP、音频、重连、缓存和前台通信服务 |
| `data` | 16 | 模型、本地存储、消息对账和路由 |
| `tools` | 11 | BLE、中继、通联日志和预设 |
| `maps` / `aprs` | 13 | 地图、坐标换算、网格和 APRS-IS |
| `session` | 4 | 登录、恢复、远端会话变化和退出清理 |
| `settings` | 4 | 设置状态、持久化和缓存清理协调 |
| `network` | 21 | HTTP 传输、认证会话、领域 API 契约/实现、响应 DTO 和 Mapper |
| 其他 | 14 | 账号、设备、群组、资料、协议和更新 |

当前最大的生产 Kotlin 文件是：

| 文件 | 行数 |
| --- | ---: |
| `radio/UdpRadioClient.kt` | 1,094 |
| `ui/screens/DevicesScreen.kt` | 1,022 |
| `AppController.kt` | 988 |
| `ui/screens/GroupsScreen.kt` | 874 |
| `ui/screens/LocationMapScreen.kt` | 674 |

## 架构边界

- UI 使用 Jetpack Compose，一级导航固定为设备、群组、PTT、工具、我的。
- `AppController` 仍是跨功能状态协调中心；设备、群组、资料、工具、设置、APRS、电台消息、电台会话和登录会话已下沉到各自 Controller，PTT 播放协调、应用更新和导航仍集中于此。
- `SessionController` 独占登录、持久会话恢复、当前用户和退出状态；`ApiSessionManager` 独占 Token 刷新、会话持久化和认证请求重试，旧登录、旧恢复和旧资料结果不会覆盖替换后的会话。
- `AprsController` 独占按用户隔离的配置、手动发送状态和后台 Service 协调；设置页只接收不可变 `AprsUiState` 与事件回调。
- `RadioMessageController` 独占消息列表、缓存写入、游标分页、服务端对账和公开资料预加载；消息列表读取不可变 `RadioMessageUiState`，旧账户和旧群组结果不会覆盖当前状态。
- `RadioSessionController` 独占节点发现、连接准备、频道路由、连接状态和 Service Binder 生命周期；电台页面读取不可变 `RadioSessionUiState`，账户切换会取消旧连接与路由结果。
- 设备、群组、工具和个人页已拆出只接收页面数据与回调的内容层，可脱离 `AppController` 生成稳定截图。
- HTTP 连接、超时、Header、响应体和 multipart 集中在 `HttpTransport`；`ApiSessionManager` 在传输层之上合并并发 401、刷新 Token 并持久化会话；149 行的 `ApiClient.kt` 只保留公共异常/URL 规则与 auth、devices、groups、radio、profile、tools、updates 七组窄接口组合。实时通信由 `UdpRadioClient` 和 `RadioConnectionService` 承担。
- 设备、群组、资料、工具、更新和电台 DataSource/Controller 只依赖对应领域 API，不再依赖完整 `ApiClient`。
- SQLite/SharedPreferences 分别保存消息历史、仪表盘/工具缓存、会话和客户端设置。
- 原生层仅承担 RNNoise；Opus 编解码主要通过 Concentus 在 JVM 层实现。

## 维护重点

1. `UdpRadioClient` 和 `DevicesScreen` 仍超过 1,000 行，是当前修改冲突和回归风险最集中的位置。`AppController` 已降至 988 行，后续重点转向 UDP 状态机、异步任务所有权和大型页面。
2. 自动化测试以 JVM 测试为主，仪器测试只有 3 个文件。BLE、定位、前台服务、弱网重连、后台麦克风和系统权限仍需要真机覆盖。
3. CI 已固定 Android SDK 36.1、NDK 28.2 和 CMake 3.22，并执行静态检查、截图验证与 Debug 构建门禁；地图运行验收仍依赖注入高德 Key，Release 签名仍需发布环境显式配置。
4. Android 客户端依赖同仓库之外的 DraARL Server API 与 UDP 协议文档。服务端契约变更时，应同时检查 README 的“服务端契约”、`DraarlProtocol`、`ApiClient` 和更新清单校验。

## 文档适配状态

- `README.md` 已覆盖当前 2.0 alpha 的导航、PTT/多频道、地图、APRS 和工具能力，并记录实际构建依赖。
- `TODO.md` 只保留尚未完成的实施与真机验收清单；已落地的静态质量门禁不再列为待办。
- 设置状态、持久化、音频偏好同步和缓存清理已集中到 `SettingsController`；设置入口、系统设置与存储页面不再接收完整 `AppController`。
- APRS 配置、手动发送、重复发送抑制和后台上报协调已集中到 `AprsController`，并由 8 个 JVM 用例覆盖归一化、失败、取消和用户切换竞态。
- 电台消息缓存、最新页同步、历史游标、实时去重、已播放状态和资料预加载已集中到 `RadioMessageController`，并由 7 个 JVM 用例覆盖失败与上下文切换竞态。
- 电台节点、连接、路由和 Service Binder 已集中到 `RadioSessionController`，并由 8 个 JVM 用例覆盖路由恢复、审核限制、连接参数、服务重连和账户切换竞态。
- 登录、持久会话恢复、用户更新、会话失效和退出清理已集中到 `SessionController`，并由 9 个 JVM 用例覆盖失败、远端失效和退出竞态。
- HTTP 连接、超时、HTTPS、Header、空响应、异常 JSON、multipart 和主动取消已集中到可注入的 `HttpTransport`，并由 9 个 MockWebServer 用例覆盖。
- Token 刷新、会话持久化、认证请求重试和旧认证/资料结果丢弃已集中到 `ApiSessionManager`，并由 8 个 JVM 用例覆盖并发 401 合并、刷新失败、过期边界、Token 刷新和 Session 替换竞态。
- `ApiClient` 已成为兼容门面；Auth、Profile、Devices、Groups、Radio、Tools、Updates 及 Token 刷新响应先进入类型化 DTO，再由独立 Mapper 转成业务模型，映射异常携带请求方法、路径与失败阶段。17 个领域 API 用例和 3 个工具 DTO 用例覆盖代表性路径、兼容字段、异常字段、响应阶段和 Session 替换竞态，原类的 38 条 Detekt 历史豁免已删除。
- 设置、账号安全和存储页已统一为细边框设置组、方形图标位和紧凑数据行，不再用默认 `Card` 叠加页面分区；危险清理使用统一弹窗与等宽动作区。
- 标准确认、表单和选择弹窗已统一到 `DraarlDialog` 外壳和动作区，危险确认使用统一语义；双操作按钮在 1.75 倍及以上字体下纵向排列，避免命令文案被截断。设备与群组的全屏管理覆盖层保留专用全屏 `Dialog`。
- CW 与位置选择 Bottom Sheet 已统一到 `DraarlSheet` 外壳、拖拽把手与动作区。
- 设备与群组页的页面级加载/空态已统一到 `PageFeedback`；工具类可恢复错误和蓝牙权限拒绝通过可关闭 `InlineNotice` 统一反馈层级。
- 应用壳层、五个一级页面、设置行、存储页、弹窗、Bottom Sheet 和首批页面反馈已有 22 张可重复生成的浅色/深色参考图，覆盖窄屏、常规手机、横屏、长中文以及 1.3/1.5/2.0 倍字体。
- GitHub Actions、Spotless/ktlint、Detekt 存量基线和 Markdown 链接检查已接入，RNNoise 第三方目录被显式排除。
- Android 仪器测试 APK 已在本次基线编译通过，但尚未连接设备执行；Release 构建和签名也需在发布候选版本上重新验证。
