# DraARL Android 工程优化 TODO

更新时间：2026-08-10

## 目标与边界

本清单只处理代码质量、架构边界、性能、测试、文档和视觉一致性，不新增或改变业务功能。

- 保留 Kotlin、Jetpack Compose、Material 3、现有服务端 API、UDP 协议和本地数据格式。
- Material 3 继续作为无障碍和交互基础，但不直接决定 DraARL 的成品视觉。
- 暂不拆分生产业务模块；生产代码保持在 `app`，`baselineprofile` 只承载测试与性能采集。
- UDP、录音、播放和 BLE 可以保留专用线程；普通 HTTP、SQLite 和文件任务优先使用结构化并发。
- 重构前补关键行为测试，每个批次独立提交，不把架构、视觉和依赖升级混在一起。
- 只有通过静态检查、单元测试、构建和对应真机回归的项目才可勾选完成。

## 当前基线

以已提交的 `main` 为基线：

- 生产 `app` 模块和测试专用 `baselineprofile` 模块，版本 `2.0.0-alpha1`（versionCode 8）。
- 生产 Kotlin 198 个文件，约 2.89 万有效代码行；JVM 测试 93 个文件、360 个用例。
- Compose 截图测试 8 个文件、50 张参考图，覆盖启动与应用壳层、五个一级页面、页面顶部栏、趋势空态、更新反馈、认证与账号安全反馈、密码重置成功、邮箱修改失败及设备绑定成功、设置行、APRS、BLE 配网及设备选择空态、存储页及清理反馈、工具子页、中继查询无结果与结果列表、通联日志筛选空态、电台预设空态、PTT 历史分页错误、弹窗、Bottom Sheet、强制更新下载态以及设备和群组筛选空态。
- 复杂度主要集中在 `UdpRadioClient`、`AppController` 和大型 Compose 页面。
- README、项目概览、构建环境和服务端契约目前与代码匹配；后续结构变更需要同步刷新。
- Spotless、Detekt、单元测试、截图验证、Lint、Debug 构建和 Markdown 链接检查已进入 CI，不再列为待办。
- Release 已通过 AGP 9.3 优化接口启用 R8 与资源收缩，arm64 APK 从 41.91 MiB 降至 30.54 MiB；前后数据、keep rules 和本地 `verifyReleaseArtifact` 静态门禁记录在 `docs/RELEASE_OPTIMIZATION.md`。

## P1：代码边界与状态所有权

- [ ] **真机验证异步生命周期**

- 在实际 Android 设备上覆盖登录态恢复后立即退出、群组加入后退出账号、节点探测中切换账号和历史录音下载中断。
- 连续重新发起请求，确认旧状态、旧错误和旧缓存不会覆盖当前账户或当前页面。
- 观察退出页面、退出账号和关闭前台通信服务后的线程与网络活动，确认没有资源泄漏。

验收：对应 JVM 竞态用例和完整本地门禁保持通过，真机上无迟到状态、错误、缓存或线程泄漏。

## P1：UI 去模板化

目标是形成克制、清晰、偏专业通信设备的 DraARL 视觉语言，而不是重做交互或更换 UI 框架。

- [ ] **增加 UI 回归测试**

- 真机验证全面屏手势、三键导航、横屏、软键盘和权限弹窗返回路径。

验收：关键视觉回归可在 CI 或本地自动发现，真机不存在遮挡、溢出或不可点击区域。

## P2：性能与构建

- [ ] **缩小 Compose 重组范围**

- 使用 Layout Inspector 在真机记录剩余热点改造前后的重组结果。

验收：PTT、播放、下载和周期刷新时，非相关页面和列表项不持续重组。

- [ ] **优化 Release 构建**

- 记录冷启动、首帧和运行内存的优化前后数据。
- 在可提供测试账号的真机扩展现有 Baseline Profile，覆盖登录态恢复、进入 PTT 和设备列表。
- Release 回归覆盖 RNNoise、地图、更新安装和前台服务。

验收：Release 行为与 Debug 一致，体积或关键启动指标有可复现的改善。

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
2. 使用真机 Layout Inspector 验证 Compose 重组，并记录 Release 真机指标、扩展已登录 Baseline Profile 路径。
3. 每个批次同步规模数据与文档，最后执行完整真机回归。

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
