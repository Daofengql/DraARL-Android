# UDP 连接生命周期

本文记录 Android 客户端实时电台链路的状态所有权、执行上下文和资源关闭顺序。协议字段和服务端行为仍以 DraARL Server 的协议文档与实现为准。

## 状态边界

- `RadioSessionController` 负责节点发现、连接参数准备、频道路由和 Service Binder 生命周期，并向 UI 发布 `RadioConnectionPhase`。
- `RadioConnectionService` 持有唯一 `UdpRadioClient`，Service 销毁时调用 `release` 完成最终清理；活动连接的端点和频道写入恢复存储，Service 重建时使用现有加密会话刷新 Token 并恢复 UDP。
- `RadioServiceStatePolicy` 只计算各连接阶段的前台动作与通知标题，Android `Service` 保留实际的通知、前台类型和停止调用。
- Service 只监听默认网络的丢失；网络切换或 Wi-Fi/蜂窝短暂抖动不会主动打断现有 UDP，会在默认网络持续不可用超过短暂宽限后触发恢复。连接/重连阶段持有 CPU `PARTIAL_WAKE_LOCK` 和可用时的 Wi-Fi lock，显式断开、会话失效和销毁时全部释放。
- `UdpSessionStateContext` 串行持有 UDP 会话代次、目标配置、内部阶段、`RadioStatus` 和认证身份；`UdpConnectionStateMachine` 只计算状态转换。阶段包括断开、连接、认证、在线、等待重连、重连、错误和关闭。
- 状态通知在上下文锁外由唯一发布者按序发送；阻塞或重入 listener 不会持有状态锁，也不会倒序覆盖新状态。
- 每次新连接、重连调度、主动断开或关闭都会推进代次。Transport、调度任务和音频回调只有在代次仍匹配时才能更新连接状态。
- `CLOSED` 是终态；释放后的客户端不能由延迟任务或旧回调重新连接。
- `RadioClock` 是所有会话时间判断的来源；生产实现读取系统时间，测试可注入确定性时钟。

## 资源所有权

| 资源 | 所有者 | 约束 |
| --- | --- | --- |
| 目标节点、Token、路由、会话代次、状态与认证身份 | `UdpSessionStateContext` | 在单一事务边界内更新不可变快照；状态机自身不再维护并发锁 |
| Socket 创建、端口复用和连接 | `DatagramUdpTransportFactory` | 绑定首选端口失败时回退到系统分配端口 |
| 数据报收发、读取超时和 Socket 关闭 | `UdpTransport` | `UdpRadioClient` 不直接依赖 `java.net` Socket 类型 |
| 认证握手 | `draarl-udp-connect` 单线程执行器 | 仅当前代次可以安装和激活 Transport |
| UDP 阻塞接收 | 每次在线会话的 `draarl-udp-receiver` | 关闭对应 Transport 或代次失效后退出 |
| 心跳、服务器静默监测和周期语音清理 tick | `UdpSessionMonitor` | 独占收发时间戳与两个周期任务，通过 `RadioClock` 做确定性时间判断 |
| Service 重建、网络切换和连接恢复 | `RadioConnectionService` / `RadioConnectionRecoveryStore` | 只持久化非敏感端点参数；Token 继续由 `SecureSessionStore` 加密保存；显式断开清除恢复意图 |
| PTT 录音、超时、尾音与发送缓存 | `UdpPttCoordinator` | 仅成功发出的 Opus 包进入本地录音，停止和取消均为幂等操作 |
| 重连与一次性任务句柄 | `UdpSessionTaskCoordinator` | 通过可注入的 `RadioScheduler` 创建，按任务类型统一替换和取消 |
| 录音、实时播放和录音回放 | `RadioAudioCapture` / `RadioAudioPlayback` | Android 实现委托 `OpusAudioEngine`；连接替换、重连、断开和释放均先停止活动音频；历史录音下载 Job 在新回放、停止回放和引擎释放时取消 |
| 录音缓存 | `RadioAudioStore` | Android 实现使用文件缓存，JVM 测试可使用内存存储 |
| 接收语音流与播放队列 | `IncomingVoiceAssembler` | 串行持有流状态、容量淘汰与超时结算，只向客户端返回有序播放/完成动作 |
| CW 发送缓存 | `UdpRadioClient` | 在 `cwVoiceLock` 下更新，发射结束后一次性交接 |
| 前台动作与通知标题 | `RadioServiceStatePolicy` | 连接、认证、在线和重连保持前台；断开时仅悬浮 PTT 可以继续保活 |

## 关闭顺序

主动断开按以下顺序执行：

1. 状态机处理 `Disconnect`，推进代次并清除目标配置，使旧回调立即失效。
2. 取消待执行的重连、心跳、看门狗和 PTT 超时任务。
3. 重置 CW/PTT 状态并停止录音、录音回放和实时播放。
4. 结算所有接收中的语音流，清空播放队列。
5. 清除会话标识并关闭 Transport，使阻塞接收线程退出。
6. 发布 `DISCONNECTED` 状态。

`release` 先执行完整主动断开，再将状态机置为 `CLOSED`，随后释放音频引擎并关闭连接执行器和调度器。

自动重连会先处理 `ReconnectScheduled` 并推进代次，然后执行与断开相同的活动资源清理。延迟到期后只有当前代次可以处理 `ReconnectStarted`，且会读取等待期间更新后的 Token 和发送频道。

## 自动化边界

- `UdpConnectionStateMachineTest` 验证合法顺序、乱序/陈旧事件、重复连接、单次重连调度、等待期间配置更新、认证失败、主动断开和关闭终态。
- `UdpSessionStateContextTest` 验证认证状态/身份原子提交、陈旧代次丢弃、锁外顺序通知和 listener 失败恢复。
- `UdpAuthenticationTest` 验证认证成功字段、服务端拒绝、异常响应、客户端实例匹配、非认证包忽略和总超时。
- `UdpTransportTest` 通过本地回环验证双向数据报、读取超时、首选端口回退和幂等关闭。
- `UdpSessionMonitorTest` 使用 fake clock 和 scheduler 验证心跳频率、静默超时边界、服务端包刷新、发送时间记录与周期任务取消。
- `UdpSessionTaskCoordinatorTest` 使用 fake scheduler 验证 PTT 超时替换、重连保留条件、取消与最终关闭。
- `UdpPttCoordinatorTest` 使用 fake Audio、Clock、Scheduler 和回调验证捕获拒绝、成功包缓存、超时替换、尾音结算与取消后旧任务丢弃。
- `IncomingVoiceAssemblerTest` 验证单活动流、待播积压、动作顺序、容量淘汰、时间戳回退、身份归一化和缓存上限。
- `UdpRadioClientPttTest` 组合 fake Transport、Scheduler、Clock、Audio 和 Store，验证完整认证后的 PTT 发包、本地消息结算、捕获拒绝、陈旧错误丢弃、接收语音播放/结算、在线接收异常后的重连登记和幂等释放。
- `OpusAudioEngineLifecycleTest` 验证引擎重复释放、释放后的迟到下载，以及停止回放会实际中断历史录音下载且不触发回调或缓存写入。
- `RadioMessageBufferTest`、`RadioMessageDispatcherTest` 与 `RadioServiceStatePolicyTest` 验证 Service 待绑定消息容量、绑定/解绑投递、各连接阶段前台动作及通知标题优先级。
- `RadioReconnectPolicyTest` 验证服务端旧会话过期前的安全重试间隔。

真实 Socket 中断、弱网乱序、前后台切换和音频资源释放仍需仪器测试或真机回归。
