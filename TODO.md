# DraARL Android 优化 TODO

更新时间：2026-08-08

## 范围

本轮只处理代码质量、运行效率、可测试性和 UI 视觉系统，不增加或改变业务功能。

实施约束：

- 保留 Kotlin、Jetpack Compose 和 Material 3；Material 3 只作为底层组件，不直接决定成品视觉。
- 暂不拆 Gradle 多模块，先在现有 `app` 模块内明确边界。
- UDP、录音和播放可以保留专用线程，不为统一写法强行迁移到协程。
- 每项优化单独提交，避免把视觉改造、架构重构和行为修复混在一个大 PR。
- 重构前先补关键测试，重构过程中保持现有服务端和本地数据契约不变。

## 当前基线

- 版本：`2.0.0-alpha1`（versionCode 8）。
- 生产 Kotlin：130 个文件，约 2.30 万行。
- JVM 测试：140 个通过；Android 仪器测试 APK 已编译，尚未连接设备执行。
- 复杂度集中在 `AppController`、`ApiClient`、`UdpRadioClient` 和大型 Compose 页面。
- UI 使用约 36 处 `Card`、59 处 `Surface`、15 处 `AlertDialog`；已有 7 个设计系统 Preview，尚无截图回归测试。

## P1：DraARL UI 设计系统

### [ ] 真机验收应用壳层与底栏

位置：`ui/DraarlApp.kt`

- 真机确认系统手势区、三键导航和横竖屏 Insets。

验收：底栏仍保持五入口和现有交互，但视觉上不再像 Material 示例应用。

### [ ] 收敛其他页面的布局语言

- 设备、群组和工具页优先使用紧凑列表、分区标题和细分隔线。
- 个人页取消作为整页容器的 Card，改为无框身份区、数据区和设置列表。
- 统计 Card 仅保留在确实需要并列比较的独立数据项上。
- 全屏详情、弹窗和 Bottom Sheet 统一标题、返回、确认和危险操作位置。
- 空状态、错误、加载和权限状态统一组件，不在页面内重复实现。

验收：不同一级页面具有一致的信息密度、层级和操作位置，同时保留通信工具的专业感。

### [ ] 增加 UI 回归基线

- 为底栏、通信页、设备列表、群组列表、个人页建立截图测试。
- 覆盖浅色、深色、窄屏、常规屏、长中文、大字体和空/错/加载状态。
- 视觉修改必须附带基线对比，不依赖人工记忆判断是否回归。

验收：关键 UI 改动可以在没有真机的情况下发现尺寸、颜色和文本溢出问题。

## P1：状态与并发模型

### [ ] 统一普通异步操作

- HTTP、SQLite、文件和普通 Controller 操作迁移到结构化并发。
- 使用 `viewModelScope`、子 Job 和明确的取消边界替代重复的 Executor + Handler + generation 模板。
- 抽取统一的 operation runner 或 `UiResult`，集中处理 loading、success、error 和取消。
- 移除 Executor 线程中再调用 `runBlocking` 的混合写法。
- UDP、AudioRecord、AudioTrack 和 BLE 回调仍保留适合其时序要求的执行模型。

验收：Device、Group、Profile、Tools Controller 不再各自复制相同的 launch/post/generation 代码。

### [ ] 拆分 `AppController`

建议按以下顺序迁移：

1. `SettingsController`：主题、显示缩放、音频偏好和存储。
2. `AprsController`：配置、手动发送和后台服务协调。
3. `RadioMessageController`：缓存加载、历史分页、同步、对账和公开资料预加载。
4. `RadioSessionController`：节点发现、连接、路由和 Service Binder。
5. `SessionController`：登录态恢复、刷新和退出清理。

要求：

- 每个 Controller 公开不可变 `UiState` 和有限事件接口。
- UI 不再接收完整 `AppController`，只接收页面所需状态和回调。
- 共享数据只有一个所有者，不在多个 Controller 中复制用户、群组或连接状态。
- 每迁移一块就删除旧字段和旧路径，不长期保留双重实现。

验收：`AppController` 最终只负责应用级组合、导航和跨域协调，核心页面可独立 Preview 和测试。

### [ ] 拆分 `ApiClient`

- 抽取 `HttpTransport`，统一超时、HTTPS、Header、错误解析、重试和上传。
- 将 Token 刷新与 Session 持久化放入独立认证层。
- 按 auth、devices、groups、radio、profile、tools、updates 划分 API。
- 将 `JSONObject` 映射移到独立 DTO/Mapper，逐步采用类型化序列化。
- 为上传、401 并发刷新、错误体、空响应和异常 JSON 建立 Mock Web Server 测试。

验收：领域 API 不直接创建 `HttpURLConnection`，解析错误能定位到具体 DTO/字段。

### [ ] 收敛 UDP 状态机

位置：`radio/UdpRadioClient.kt`

- 将连接/认证/在线/重连/断开建模为显式状态机。
- 将 Socket 传输、心跳看门狗、PTT 发送和接收语音组装拆成独立对象。
- 尽量在单一串行执行上下文修改会话状态，减少 Volatile、Atomic 和锁的组合。
- 注入 Clock、Scheduler、Socket/Transport 和 Audio 接口，支持确定性测试。
- 明确所有任务、Socket、音频流和重连计划的所有权与取消顺序。

验收：连接和重连状态转换可以用纯 JVM 测试覆盖，不依赖真实网络或 Android Service。

## P2：Compose 与数据处理性能

### [ ] 缓存派生列表和索引

- 设备、群组和频道过滤使用 `remember` / `derivedStateOf`，只在输入变化时重新计算。
- 预先构建 `groupId -> groupName` 索引，避免列表项逐个执行 `firstOrNull`。
- 评估 `unplayedVoiceCount` 等派生值，避免高频重组时重复扫描消息列表。
- 保持 Lazy 列表 key 稳定，避免使用会随标题或状态变化的复合 key。

验收：搜索输入之外的无关状态变化不会重新过滤或遍历完整列表。

### [ ] 减少高频 Compose 状态传播

- 将音频电平、下载进度和连接瞬态限制在最小 Composable 范围。
- 页面只订阅自身需要的 UiState，不因完整 Controller 的其他字段变化而重组。
- 对高频 Canvas 数据使用绘制层状态或动画状态，避免整页重组。
- 使用 Compose Layout Inspector 或 compiler metrics 验证改造前后差异。

验收：PTT、播放、下载和周期刷新时，非相关页面与列表项不发生持续重组。

### [ ] 优化 Release 构建

- 在 Release 启用 R8 和资源收缩，维护必要的高德、JNI、JSON/序列化 keep rules。
- 比较优化前后的 APK 大小、冷启动、首帧和运行内存。
- 增加 Baseline Profile，覆盖启动、登录态恢复、进入 PTT 和打开设备列表。
- Release 验证必须包含 RNNoise、地图、更新安装和前台服务。

验收：优化后的 Release 行为一致，APK 体积和关键启动指标有可记录的改善。

## P2：测试与工程质量

### [ ] 补核心边界测试

- `AppController` 拆出的状态 reducer 和协调器。
- `ApiClient` 传输、解析和并发 Token 刷新。
- `UdpRadioClient` 状态机、超时、重连、乱序包和资源释放。
- `RadioConnectionService` 缓冲、绑定/解绑和前台状态。
- Controller 取消、旧响应丢弃和关闭后不回调。
- `RadioMessageStore` 迁移、事务、分页和并发访问。

验收：核心类重构不再主要依赖真机回归发现问题。

### [ ] 建立静态质量门禁

- 引入并配置 ktlint 或 Spotless，先只格式化改动文件，避免一次性全仓库噪声。
- 引入 Detekt 或等价规则，重点检查复杂函数、过长类、吞异常和线程资源。
- CI 执行单元测试、Lint、Debug 构建、仪器测试 APK 编译和 Markdown 链接检查。
- 对第三方 RNNoise 目录设置排除，避免格式化和静态分析模型源码。

验收：新增代码不能继续扩大已识别的重复异步模板、硬编码视觉值和超长核心类。

### [ ] 分批更新依赖

- 对 AndroidX Core、Lifecycle、Activity、JUnit、Espresso 等依赖逐组升级。
- 每组升级单独提交并运行完整门禁，不与 UI 或架构重构混合。
- 删除不再使用的依赖，业务代码不得依赖 Coil 等库的传递依赖。

验收：依赖版本组合明确、可复现，无一次性大版本升级造成的定位困难。

## 每阶段验证

自动化：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

UI 检查：

- 浅色、深色。
- 360 dp 窄屏、常规手机、横屏。
- 系统字体 1.0、1.3、1.5、2.0。
- 全面屏手势和传统三键导航。
- 空数据、长文本、加载、错误、禁用和权限拒绝状态。

性能检查：

- 隐藏地图是否暂停。
- 音频电平主线程投递频率。
- PTT/播放时 Compose 重组范围。
- 冷启动、首帧、内存和 Release APK 大小。

真机检查：

- 前后台切换与前台服务。
- PTT 录音、实时播放和历史播放。
- BLE 扫描/连接/断开。
- 定位与地图生命周期。
- 弱网、断网、自动重连和退出清理。

## 完成定义

- 所有现有业务行为保持不变。
- 自动化门禁全部通过，真机关键链路完成记录。
- UI 不再直接依赖默认 Material 视觉，核心页面具有一致的 DraARL 语言。
- 系统字体缩放、浅深色、窄屏和长文本均可用。
- 核心异步任务具备明确的所有者、取消边界和测试。
- `AppController`、`ApiClient`、`UdpRadioClient` 的职责和体积均得到实质性下降。
