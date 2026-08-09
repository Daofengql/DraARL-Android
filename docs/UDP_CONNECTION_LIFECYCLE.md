# UDP 连接生命周期

本文记录 Android 客户端实时电台链路的状态所有权、执行上下文和资源关闭顺序。协议字段和服务端行为仍以 DraARL Server 的协议文档与实现为准。

## 状态边界

- `RadioSessionController` 负责节点发现、连接参数准备、频道路由和 Service Binder 生命周期，并向 UI 发布 `RadioConnectionPhase`。
- `RadioConnectionService` 持有唯一 `UdpRadioClient`，Service 销毁时调用 `release` 完成最终清理。
- `UdpConnectionStateMachine` 持有 UDP 会话代次、目标配置和内部阶段。阶段包括断开、连接、认证、在线、等待重连、重连、错误和关闭。
- 每次新连接、重连调度、主动断开或关闭都会推进代次。Transport、调度任务和音频回调只有在代次仍匹配时才能更新连接状态。
- `CLOSED` 是终态；释放后的客户端不能由延迟任务或旧回调重新连接。
- `RadioClock` 是所有会话时间判断的来源；生产实现读取系统时间，测试可注入确定性时钟。

## 资源所有权

| 资源 | 所有者 | 约束 |
| --- | --- | --- |
| 目标节点、Token、路由与会话代次 | `UdpConnectionStateMachine` | 更新形成新的不可变状态快照 |
| Socket 创建、端口复用和连接 | `DatagramUdpTransportFactory` | 绑定首选端口失败时回退到系统分配端口 |
| 数据报收发、读取超时和 Socket 关闭 | `UdpTransport` | `UdpRadioClient` 不直接依赖 `java.net` Socket 类型 |
| 认证握手 | `draarl-udp-connect` 单线程执行器 | 仅当前代次可以安装和激活 Transport |
| UDP 阻塞接收 | 每次在线会话的 `draarl-udp-receiver` | 关闭对应 Transport 或代次失效后退出 |
| 心跳、服务器静默监测和周期语音清理 tick | `UdpSessionMonitor` | 独占收发时间戳与两个周期任务，通过 `RadioClock` 做确定性时间判断 |
| PTT 录音、超时、尾音与发送缓存 | `UdpPttCoordinator` | 仅成功发出的 Opus 包进入本地录音，停止和取消均为幂等操作 |
| 重连与一次性任务句柄 | `UdpSessionTaskCoordinator` | 通过可注入的 `RadioScheduler` 创建，按任务类型统一替换和取消 |
| 录音、实时播放和录音回放 | `RadioAudioCapture` / `RadioAudioPlayback` | Android 实现委托 `OpusAudioEngine`；连接替换、重连、断开和释放均先停止活动音频 |
| 录音缓存 | `RadioAudioStore` | Android 实现使用文件缓存，JVM 测试可使用内存存储 |
| 接收语音流与播放队列 | `UdpRadioClient` | 在 `voiceSessionLock` 下更新，清理时结算为消息 |
| CW 发送缓存 | `UdpRadioClient` | 在 `cwVoiceLock` 下更新，发射结束后一次性交接 |

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
- `UdpAuthenticationTest` 验证认证成功字段、服务端拒绝、异常响应、客户端实例匹配、非认证包忽略和总超时。
- `UdpTransportTest` 通过本地回环验证双向数据报、读取超时、首选端口回退和幂等关闭。
- `UdpSessionMonitorTest` 使用 fake clock 和 scheduler 验证心跳频率、静默超时边界、服务端包刷新、发送时间记录与周期任务取消。
- `UdpSessionTaskCoordinatorTest` 使用 fake scheduler 验证 PTT 超时替换、重连保留条件、取消与最终关闭。
- `UdpPttCoordinatorTest` 使用 fake Audio、Clock、Scheduler 和回调验证捕获拒绝、成功包缓存、超时替换、尾音结算与取消后旧任务丢弃。
- `UdpRadioClientPttTest` 组合 fake Transport、Scheduler、Clock、Audio 和 Store，验证完整认证后的 PTT 发包、本地消息结算、捕获拒绝、陈旧错误丢弃和幂等释放。
- `RadioReconnectPolicyTest` 验证服务端旧会话过期前的安全重试间隔。

真实 Socket 中断、弱网乱序、前后台切换和音频资源释放仍需仪器测试或真机回归。
