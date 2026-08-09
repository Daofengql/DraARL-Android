# DraARL Android 工程优化 TODO

更新时间：2026-08-09

## 目标与边界

本清单只处理代码质量、架构边界、性能、测试、文档和视觉一致性，不新增或改变业务功能。

- 保留 Kotlin、Jetpack Compose、Material 3、现有服务端 API、UDP 协议和本地数据格式。
- Material 3 继续作为无障碍和交互基础，但不直接决定 DraARL 的成品视觉。
- 暂不拆 Gradle 多模块；先在单 `app` 模块内建立清晰的包和状态所有权。
- UDP、录音、播放和 BLE 可以保留专用线程；普通 HTTP、SQLite 和文件任务优先使用结构化并发。
- 重构前补关键行为测试，每个批次独立提交，不把架构、视觉和依赖升级混在一起。
- 只有通过静态检查、单元测试、构建和对应真机回归的项目才可勾选完成。

## 当前基线

以已提交的 `main` 为基线：

- 单 `app` 模块，版本 `2.0.0-alpha1`（versionCode 8）。
- 生产 Kotlin 184 个文件，约 2.79 万有效代码行；JVM 测试 63 个文件、259 个用例。
- Compose 截图测试 4 个文件、24 张参考图，覆盖应用壳层、五个一级页面、设置行、APRS、BLE 配网、存储页、弹窗、Bottom Sheet 和首批页面反馈状态。
- 复杂度主要集中在 `UdpRadioClient`、`AppController` 和大型 Compose 页面。
- README、项目概览、构建环境和服务端契约目前与代码匹配；后续结构变更需要同步刷新。
- Spotless、Detekt、单元测试、截图验证、Lint、Debug 构建和 Markdown 链接检查已进入 CI，不再列为待办。

## P1：代码边界与状态所有权

- [ ] **明确 `UdpRadioClient` 状态机**

- 尽量在单一串行执行上下文修改会话状态，减少 `Volatile`、`Atomic` 和锁的混用。

验收：认证、超时、重连、乱序包和主动关闭可以通过确定性 JVM 测试验证。

- [ ] **统一普通异步任务模型**

- HTTP、SQLite、文件和普通 Controller 操作统一使用结构化并发和明确的 dispatcher。
- 以页面或 Controller 生命周期作为 Job 所有者，取消后不得继续写状态或弹出错误。
- 合并重复的 loading/success/error、generation 和主线程回投模板。
- 移除 Executor 内嵌 `runBlocking` 的混合路径；专用实时线程需写明保留原因。

验收：关闭页面、退出账号和重新发起请求时，旧任务不会覆盖新状态，也不会泄漏线程或资源。

## P1：UI 去模板化

目标是形成克制、清晰、偏专业通信设备的 DraARL 视觉语言，而不是重做交互或更换 UI 框架。

- [ ] **统一状态、反馈与容器**

- 使用现有 DraARL 组件统一连接状态、在线状态、延迟、录音、播放、警告和失败反馈。
- 空状态、加载、错误和权限拒绝统一标题、反馈层级与操作位置。
- 动效只表达连接、发送、接收和状态切换，不添加装饰性背景或无意义持续动画。

验收：同一状态在不同页面具有一致的颜色、图标、文案层级和交互位置。

- [ ] **增加 UI 回归测试**

- 继续补齐五个一级页面尚缺的空、错和加载状态组合。
- 扩大非默认字体缩放对五个一级页面的覆盖。
- 真机验证全面屏手势、三键导航、横屏、软键盘和权限弹窗返回路径。

验收：关键视觉回归可在 CI 或本地自动发现，真机不存在遮挡、溢出或不可点击区域。

## P2：性能与构建

- [ ] **缩小 Compose 重组范围**

- 将音频电平、连接瞬态、播放位置和下载进度限制在最小 Composable 范围。
- 页面订阅细粒度 `UiState`，列表项使用稳定 key 和不可变模型。
- 对高频 Canvas/动画数据使用绘制层状态，避免引起整个页面重组。
- 使用 Layout Inspector 或 Compose compiler metrics 记录改造前后结果。

验收：PTT、播放、下载和周期刷新时，非相关页面和列表项不持续重组。

- [ ] **优化 Release 构建**

- 启用并验证 R8 与资源收缩，维护高德、JNI 和序列化所需 keep rules。
- 记录 APK 体积、冷启动、首帧和运行内存的优化前后数据。
- 增加 Baseline Profile，覆盖启动、登录态恢复、进入 PTT 和设备列表。
- Release 回归覆盖 RNNoise、地图、更新安装和前台服务。

验收：Release 行为与 Debug 一致，体积或关键启动指标有可复现的改善。

## P2：测试与维护成本

- [ ] **补核心边界测试**

- `AppController` 拆出的 reducer、协调器和退出清理。
- 各领域 DTO/Mapper 的异常字段、缺失字段和兼容字段。
- `UdpRadioClient` 状态转换、超时、重连、乱序包与资源释放。
- `RadioConnectionService` 缓冲、绑定/解绑和前台状态。
- `RadioMessageStore` 迁移、事务、分页与并发访问。
- 所有 Controller 的取消、旧响应丢弃和关闭后不回调。

验收：核心类重构主要由自动化测试保护，而不是依赖真机发现回归。

- [ ] **分批升级依赖**

- AndroidX Core、Lifecycle、Activity、Compose、JUnit 和 Espresso 按组升级。
- 每组升级单独提交并运行完整门禁，不与 UI 或架构重构混合。
- 删除未使用依赖，业务代码不得依赖其他库偶然带入的传递依赖。

验收：依赖来源、版本组合和升级影响可追踪，构建可复现。

## 文档同步

- [ ] **每个结构批次同步文档**

- 控制器或包边界变化后更新 `README.md` 的工程结构。
- 每个较大批次重新统计 `docs/PROJECT_OVERVIEW.md` 的文件数、有效行数、测试数和最大文件。
- 构建工具、SDK/NDK、签名、地图 Key 或 CI 命令变化时同步 README。
- API、UDP 或更新清单的内部重构不得改写现有契约说明；若实现与说明不一致，先修实现或单独确认契约变更。
- 文档中的相对链接必须通过 Markdown 链接检查。

验收：README 用于新开发者构建和定位代码，项目概览反映最新已提交代码，而不是工作区中间状态。

## 推荐执行顺序

1. 分两到三个小批次改造壳层、列表/设置页和反馈容器。
2. 继续分离 `UdpRadioClient` 的传输与音频职责，统一普通异步任务并补确定性测试。
3. 处理 Compose 重组、Release/R8 和 Baseline Profile。
4. 补齐核心边界测试并分批升级依赖。
5. 每个批次同步规模数据与文档，最后执行完整真机回归。

## 每批验证

静态检查与单元测试：

```powershell
.\gradlew.bat spotlessCheck detektDebug testDebugUnitTest
```

完整工程门禁：

```powershell
.\gradlew.bat spotlessCheck detektDebug testDebugUnitTest validateDebugScreenshotTest lintDebug assembleDebug assembleDebugAndroidTest
```

涉及 UI、音频、网络、服务或 Release 的批次还需完成对应截图、真机或性能验证，并在提交说明中记录环境与结果。

## 完成定义

- 所有现有业务行为、服务端契约和本地数据均保持兼容。
- CI 与当前批次对应的真机检查全部通过，不扩充 Detekt 基线来掩盖新增问题。
- 状态和异步任务有明确所有者、取消边界和自动化测试。
- `AppController`、`ApiClient`、`UdpRadioClient` 的职责和体积得到实质性下降。
- UI 保留 Android 的可用性，但不再呈现默认 Material 示例应用的视觉气质。
- README、项目概览和实际代码结构一致。
