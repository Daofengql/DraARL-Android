# DraARL Android 项目概览

审计日期：2026-08-09
代码基线：`2.0.0-alpha1`（versionCode 8）

## 规模结论

这是一个中等规模、功能面较宽的单模块 Android 客户端。自研生产 Kotlin 约 2.83 万有效代码行，已经覆盖账号、设备、群组、实时通信、地图、APRS 和多种业余无线电工具；复杂度主要来自通信状态、音频生命周期、后台服务和硬件/系统权限，而不是 Gradle 模块数量。

| 范围 | 文件数 | 代码行数 | 说明 |
| --- | ---: | ---: | --- |
| 生产 Kotlin | 188 | 28,266 | 不含空行、生成目录和第三方源码 |
| JVM 单元测试 Kotlin | 71 | 6,331 | 285 个测试用例 |
| Android 仪器测试 Kotlin | 3 | 94 | 主要覆盖底部导航和 SQLite |
| Compose 截图测试 Kotlin | 5 | 853 | 26 张壳层、页面、状态和组件参考图 |
| 主资源 XML | 18 | 292 | Manifest、网络安全、主题等 |
| 自研 C++ 接入 | 1 | 121 | RNNoise JNI/CMake 桥接 |
| 第三方 RNNoise C/H | 32 | 281,091 | 约 30.2 MB，绝大部分为模型权重数据 |

RNNoise 会显著放大仓库行数和体积，评估自研规模时应将 `app/src/main/cpp/third_party/rnnoise` 单独统计。

## 代码分布

生产 Kotlin 文件主要分布如下：

| 包 | 文件数 | 职责 |
| --- | ---: | --- |
| `ui` | 60 | Compose 页面、导航和组件 |
| `radio` | 42 | 消息状态与同步、会话、UDP、音频、重连、缓存和前台通信服务 |
| `data` | 17 | 模型、本地存储、消息对账和路由 |
| `tools` | 11 | BLE、中继、通联日志和预设 |
| `maps` / `aprs` | 13 | 地图、坐标换算、网格和 APRS-IS |
| `session` | 4 | 登录、恢复、远端会话变化和退出清理 |
| `settings` | 4 | 设置状态、持久化和缓存清理协调 |
| `network` | 21 | HTTP 传输、认证会话、领域 API 契约/实现、响应 DTO 和 Mapper |
| 其他 | 16 | 通用并发、账号、设备、群组、资料、协议和更新 |

当前最大的生产 Kotlin 文件是：

| 文件 | 行数 |
| --- | ---: |
| `ui/screens/DevicesScreen.kt` | 1,044 |
| `radio/UdpRadioClient.kt` | 951 |
| `ui/screens/GroupsScreen.kt` | 899 |
| `AppController.kt` | 875 |
| `ui/screens/LocationMapScreen.kt` | 683 |

## 架构边界

- UI 使用 Jetpack Compose，一级导航固定为设备、群组、PTT、工具、我的。
- `AppController` 仍是跨功能状态协调中心；设备、群组、资料、工具、设置、APRS、电台消息、电台会话、应用更新和登录会话已下沉到各自 Controller，PTT 播放协调和导航仍集中于此。
- `SessionController` 独占登录、持久会话恢复、当前用户和退出状态；`ApiSessionManager` 独占 Token 刷新、会话持久化和认证请求重试，旧登录、旧恢复和旧资料结果不会覆盖替换后的会话。
- `AprsController` 独占按用户隔离的配置、手动发送状态和后台 Service 协调；设置页只接收不可变 `AprsUiState` 与事件回调。
- `RadioMessageController` 独占消息列表、缓存写入、游标分页、服务端对账和公开资料预加载；消息列表读取不可变 `RadioMessageUiState`，旧账户和旧群组结果不会覆盖当前状态。
- `RadioSessionController` 独占节点发现、连接准备、频道路由、连接状态和 Service Binder 生命周期；电台页面读取不可变 `RadioSessionUiState`，账户切换会取消旧连接与路由结果。
- 设备、群组、资料、公共认证和工具 Controller 的普通阻塞任务由共享 `ControllerTaskRunner` 承载；每个 Controller 持有挂接到 `viewModelScope` 的子 `SupervisorJob`，阻塞调用显式切到 IO dispatcher，重置或关闭后取消任务并丢弃迟到结果；公共认证和工具按可并行领域保留独立任务槽，工具草稿缓存写入同一生命周期内的串行 IO 队列。
- `AppDataRefresher` 使用结构化 `async(ioDispatcher)` 并行聚合设备、群组、默认群组、通信统计、趋势和用户六路请求；`AppController` 持有刷新 Job，并继续通过 `RefreshCoordinator` 合并刷新风暴，退出或销毁会同时取消 Job 与代次。
- `DashboardCacheController` 按账户持有缓存读写任务；SharedPreferences 读取、JSON 解析和写入均切到 IO dispatcher，退出账户会丢弃迟到缓存，网络新快照会使仍在读取的旧缓存失效。
- 群组在线计数和当前频道在线设备查询由 `AppController` 的子任务 scope 持有；频道切换和账户退出直接取消旧任务，在线设备刷新继续通过 `RefreshCoordinator` 合并运行期间的重复请求。
- `AppUpdateController` 独占更新检查、下载进度、安装权限恢复和 UI 状态；检查与下载使用独立 Controller 子任务，账户退出后取消旧请求并过滤迟到进度，`AppController` 只保留兼容 getter 和命令转发。
- 设备、群组、工具和个人页已拆出只接收页面数据与回调的内容层，可脱离 `AppController` 生成稳定截图。
- HTTP 连接、超时、Header、响应体和 multipart 集中在 `HttpTransport`；`ApiSessionManager` 在传输层之上合并并发 401、刷新 Token 并持久化会话；149 行的 `ApiClient.kt` 只保留公共异常/URL 规则与 auth、devices、groups、radio、profile、tools、updates 七组窄接口组合。实时通信由 `UdpRadioClient` 和 `RadioConnectionService` 承担；连接代次、`RadioStatus` 与认证身份由 `UdpSessionStateContext` 在单一事务边界内管理，`UdpConnectionStateMachine` 只保留状态转换规则，状态通知在锁外按序发布；认证响应解析与总超时读取已形成可独立测试的边界，Socket 创建、端口复用、超时和数据报收发由可注入的 `UdpTransport` 隔离，客户端时间决策统一读取 `RadioClock`，心跳、服务器静默判断和周期任务由 `UdpSessionMonitor` 持有，PTT 录音、超时、尾音与发送缓存由 `UdpPttCoordinator` 编排，接收语音流、播放队列、容量淘汰与超时结算由 `IncomingVoiceAssembler` 持有，重连与一次性任务句柄由 `UdpSessionTaskCoordinator` 管理，录音、实时播放、历史回放与缓存通过 `RadioAudioRuntime` 注入。
- 设备、群组、资料、工具、更新和电台 DataSource/Controller 只依赖对应领域 API，不再依赖完整 `ApiClient`。
- SQLite/SharedPreferences 分别保存消息历史、仪表盘/工具缓存、会话和客户端设置。
- 原生层仅承担 RNNoise；Opus 编解码主要通过 Concentus 在 JVM 层实现。
- 节点时延探测使用 `coroutineScope`、`async` 和 4 个 permit 的信号量继承电台会话取消；历史录音的 HTTPS/文件读取使用随 `OpusAudioEngine` 释放的 IO 协程作用域。仍保留的专用执行上下文仅用于 BLE RPC 超时、Opus/AudioTrack 串行播放、UDP 阻塞握手与接收以及电台定时任务，这些资源都由对应硬件或实时组件在 `close` / `release` 时关闭。

## 维护重点

1. `DevicesScreen` 仍超过 1,000 行，`UdpRadioClient` 为 951 行，`AppController` 为 875 行。UDP 状态、认证、Socket 传输、心跳监测、PTT 编排、接收语音组装、任务调度和音频设备边界已经独立，普通 Controller、全量刷新、缓存、节点探测和历史音频 IO 已建立结构化任务所有权；后续代码重点转向大型页面和 Compose 状态订阅。
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
- 设备、群组、资料、公共认证和工具 Controller 已移除各自的 `Executor`、`Handler`、原子关闭标记和代次模板，统一通过 `ControllerTaskRunner` 管理 loading、IO 调度、取消和主 scope 回投；工具缓存读写也已移出主线程，群组加入与退出不再依赖 `AppController` 的通用线程池。11 个 JVM 用例覆盖 dispatcher、迟到结果、关闭、验证码替换、流程取消、独立任务槽、串行缓存写入和群组成员关系操作。
- 全量应用数据刷新已移除 `CompletableFuture` 与共享 Executor，六路请求在注入的 IO dispatcher 上并行执行并保留逐项 fallback；2 个 JVM 用例覆盖单项失败和并行启动，`RefreshCoordinator` 的 2 个用例继续覆盖合并刷新与旧结果丢弃。
- 仪表盘缓存读写已从 `AppController` 主线程迁到按账户隔离的生命周期任务；3 个 JVM 用例覆盖 IO dispatcher、网络新快照优先级和账户切换时的迟到缓存。
- 群组计数和当前频道在线设备同步已移除原子 busy/pending/generation 模板，改由 `ControllerTaskRunner` 与 `RefreshCoordinator` 组合管理取消和 trailing refresh；账户或频道上下文变化后不再接受旧响应。
- 更新检查、下载、进度与安装权限恢复已从 `AppController` 下沉到 `AppUpdateController`，移除共享 Executor、Handler 回投和原子 busy 标记；4 个 JVM 用例覆盖重置竞态、迟到进度、安装权限恢复和旧服务端自动检查。
- 节点时延探测已移除临时固定线程池，使用电台会话的结构化并发、并发上限和单节点预算；历史录音下载也不再持有独立 Executor。3 个节点选择用例覆盖最低时延、优先级回退和单点异常隔离，既有音频生命周期用例继续覆盖释放后的迟到下载。
- HTTP 连接、超时、HTTPS、Header、空响应、异常 JSON、multipart 和主动取消已集中到可注入的 `HttpTransport`，并由 9 个 MockWebServer 用例覆盖。
- Token 刷新、会话持久化、认证请求重试和旧认证/资料结果丢弃已集中到 `ApiSessionManager`，并由 8 个 JVM 用例覆盖并发 401 合并、刷新失败、过期边界、Token 刷新和 Session 替换竞态。
- UDP 连接、认证、在线、重连、错误、主动断开与关闭已建模为显式状态和事件，连接代次、`RadioStatus` 和认证身份收敛到单一串行状态上下文；48 个新增 JVM 用例覆盖状态顺序、陈旧事件、重复重连、认证响应、总超时、Transport 收发/关闭、心跳与静默边界、调度任务所有权、PTT 音频边界、接收流乱序/淘汰/结算及并发状态发布，资源所有权与关闭顺序记录在 `docs/UDP_CONNECTION_LIFECYCLE.md`。
- `ApiClient` 已成为兼容门面；Auth、Profile、Devices、Groups、Radio、Tools、Updates 及 Token 刷新响应先进入类型化 DTO，再由独立 Mapper 转成业务模型，映射异常携带请求方法、路径与失败阶段。17 个领域 API 用例和 3 个工具 DTO 用例覆盖代表性路径、兼容字段、异常字段、响应阶段和 Session 替换竞态，原类的 38 条 Detekt 历史豁免已删除。
- 设置、账号安全和存储页已统一为细边框设置组、方形图标位和紧凑数据行，不再用默认 `Card` 叠加页面分区；危险清理使用统一弹窗与等宽动作区。
- APRS 设置页已拆出不依赖定位权限和运行时 Controller 的内容层；链路、服务器、自动上报和测试区统一使用细边框设置组、命令按钮与状态提示，不再用默认 `Card` 叠加页面分区。
- BLE 配网页的设备类型、连接状态、认证、Wi-Fi 与服务配置已统一为设置组、状态指示和命令按钮；设备类型弹窗使用可访问的单选行，不再在弹窗或页面分区中嵌套默认 `Card`。APRS 与 BLE 重写同时清理了 30 条已失效的 Detekt 行长豁免。
- 图标命令统一由 `DraarlIconButton`、`CommandIconButton` 和 `DraarlTooltip` 承载；41 个原生图标按钮、20 个设备式命令图标按钮及 2 个地图浮动图标共用可见 tooltip 与 `contentDescription`，页面不再直接使用 Material `IconButton`。
- 标准确认、表单和选择弹窗已统一到 `DraarlDialog` 外壳和动作区，危险确认使用统一语义；双操作按钮在 1.75 倍及以上字体下纵向排列，避免命令文案被截断。设备与群组的全屏管理覆盖层保留专用全屏 `Dialog`。
- CW 与位置选择 Bottom Sheet 已统一到 `DraarlSheet` 外壳、拖拽把手与动作区。
- 设备与群组页的页面级加载/空态已统一到 `PageFeedback`；工具类可恢复错误和蓝牙权限拒绝通过可关闭 `InlineNotice` 统一反馈层级。
- 通联日志、电台预设和中继查询的加载/空态已统一到 `PageFeedback`；预设页复用工具标题栏，中继查询复用命令按钮与状态指示，预设拖拽计算从页面函数拆出并继续读取当前排序。
- 应用壳层、五个一级页面、设置行、APRS、BLE 配网、存储页、工具子页、弹窗、Bottom Sheet 和首批页面反馈已有 26 张可重复生成的浅色/深色参考图，覆盖窄屏、常规手机、横屏、长中文以及 1.3/1.5/2.0 倍字体。
- GitHub Actions、Spotless/ktlint、Detekt 存量基线和 Markdown 链接检查已接入，RNNoise 第三方目录被显式排除。
- Android 仪器测试 APK 已在本次基线编译通过，但尚未连接设备执行；Release 构建和签名也需在发布候选版本上重新验证。
