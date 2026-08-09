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
- 生产 Kotlin 191 个文件，约 2.83 万有效代码行；JVM 测试 71 个文件、285 个用例。
- Compose 截图测试 6 个文件、37 张参考图，覆盖启动与应用壳层、五个一级页面、页面顶部栏、趋势空态、更新反馈、认证与账号安全反馈、设置行、APRS、BLE 配网、存储页、工具子页、弹窗、Bottom Sheet 和首批页面反馈状态。
- 复杂度主要集中在 `UdpRadioClient`、`AppController` 和大型 Compose 页面。
- README、项目概览、构建环境和服务端契约目前与代码匹配；后续结构变更需要同步刷新。
- Spotless、Detekt、单元测试、截图验证、Lint、Debug 构建和 Markdown 链接检查已进入 CI，不再列为待办。

## P1：代码边界与状态所有权

- [ ] **真机验证异步生命周期**

- 在实际 Android 设备上覆盖登录态恢复后立即退出、群组加入后退出账号、节点探测中切换账号和历史录音下载中断。
- 连续重新发起请求，确认旧状态、旧错误和旧缓存不会覆盖当前账户或当前页面。
- 观察退出页面、退出账号和关闭前台通信服务后的线程与网络活动，确认没有资源泄漏。

验收：对应 JVM 竞态用例和完整本地门禁保持通过，真机上无迟到状态、错误、缓存或线程泄漏。

## P1：UI 去模板化

目标是形成克制、清晰、偏专业通信设备的 DraARL 视觉语言，而不是重做交互或更换 UI 框架。

- [ ] **增加 UI 回归测试**

- 五个一级页面当前可达的页面级空态、加载态和 PTT 同步错误态已有首批基线；继续补齐筛选空态、操作后反馈等组合。
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

1. 在真机完成异步生命周期和 UI Insets、导航、横屏、软键盘与权限返回路径验证。
2. 处理 Compose 重组、Release/R8 和 Baseline Profile，并记录可复现指标。
3. 补齐核心边界测试，继续缩小 `AppController` 与 `UdpRadioClient` 的职责。
4. 按依赖组独立升级并执行完整门禁。
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
