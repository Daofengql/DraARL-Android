# Compose 性能记录

记录日期：2026-08-10

## 音频电平重组边界

改造前，`ConnectionPanel` 在组装 `RadioConnectionPanelState` 时直接读取
`AppController.playbackLevel` 和 `AppController.transmitLevel`。两个高频状态每次更新都会使连接面板的父级
重组作用域失效。电平动画也在 Composable 主体中读取动画值，每个动画帧都会重组电平组件。

改造后：

- `RadioConnectionPanelState` 只保留低频连接与显示状态。
- `ControllerAudioLevelMeter` 形成独立、可跳过的重组作用域，并在该作用域内读取两个电平状态。
- `RadioAudioLevelMeter` 只在输入目标变化时参与组合；动画值由 Canvas 绘制阶段读取，动画帧只使绘制失效。

## 编译器指标

使用 Kotlin 2.2.10 Compose compiler，在同一 Debug 编译环境中执行：

```powershell
.\gradlew.bat compileDebugKotlin -PenableComposeCompilerReports=true --rerun-tasks
```

报告默认生成到 `app/build/compose_compiler`，该目录属于构建产物，不提交仓库。普通构建未提供
`enableComposeCompilerReports` 属性时不会生成报告或增加编译器报告开销。

| 指标 | 改造前 | 改造后 | 变化 |
| --- | ---: | ---: | ---: |
| 总 Composable | 888 | 889 | +1 |
| 可重启 Composable | 880 | 881 | +1 |
| 可跳过 Composable | 599 | 600 | +1 |
| 已知稳定参数 | 13,291 | 13,295 | +4 |
| 已知不稳定参数 | 212 | 213 | +1 |

新增的 1 个 Composable 即 `ControllerAudioLevelMeter`。编译器报告确认它是独立、可重启且可跳过的作用域；
`RadioConnectionPanel` 和 `RadioStatusStrip` 继续保持可跳过，参数也保持稳定。指标总量本身不代表运行时帧性能，
本次收益来自状态读取位置和绘制阶段读取边界的变化。

## 下载进度状态边界

改造前，`AppUpdateController` 将下载进度与状态、更新信息和消息一起存放在单个
`mutableStateOf(AppUpdateUiState)` 中。每个下载进度回调都会复制整个状态对象，使所有只关心状态或消息的订阅者
失效；全局更新弹窗还会在 `AppUpdateHost` 中直接读取进度值。

改造后，下载进度由独立 `mutableFloatStateOf` 持有，`AppUpdateUiState` 只在状态、更新信息或消息变化时替换。
系统设置页和全局更新弹窗都将稳定的 `() -> Float` 直接交给 `LinearProgressIndicator`，进度读取限制在指示器内部。
控制器测试同时断言进度从 0 更新到 0.25 时 `AppUpdateUiState` 保持同一对象引用。

Compose compiler 总量保持 889 个 Composable、881 个可重启和 600 个可跳过；`AppUpdateDialog.progress`
从稳定 `Float` 参数变为稳定 `Function0<Float>` 参数，没有增加额外组合层级。

## 消息播放状态边界

改造前，`RadioScreen` 在自动连播滚动 effect 的 key 和每个消息项模型中直接读取 `playingMessageId`。播放开始、
停止或切换时会使页面根作用域失效；所有可见消息项也订阅同一个状态，即使绝大多数项目始终为未播放状态。

改造后：

- `AutoPlayMessageScrollEffect` 独立读取播放 ID 和连播状态，播放切换只重启滚动 effect。
- `MessageItemState` 只保留消息的低频展示数据，`playing` 作为独立参数传入消息组件。
- 每个 `ControllerMessageItem` 使用带 `structuralEqualityPolicy()` 的 `derivedStateOf` 订阅播放 ID；只有旧播放项和
  新播放项的布尔值实际变化，其他可见项保持 `false` 且不进入重组。

Compose compiler 总量从 889 个 Composable、881 个可重启和 600 个可跳过，变为 891、883 和 602；新增的
`AutoPlayMessageScrollEffect` 与 `ControllerMessageItem` 均为可重启、可跳过作用域，`RadioScreen` 本身继续可跳过。

## 连接瞬态状态边界

改造前，`RadioScreen` 根作用域读取完整 `RadioSessionUiState` 和 `RadioStatus`，连接阶段、收发状态、在线设备或
弹窗数据变化都会使整个页面失效。连接面板也由页面向下传递完整会话状态。

改造后：

- 所选群组使用带结构相等策略的派生状态，其他会话字段变化不会使页面根作用域失效。
- 连接刷新和自动连接副作用移入 `RadioConnectionEffects`，保持原有 effect key 和触发语义。
- PTT、RX/TX 与 CW 可用性由不可变 `RadioTransmissionState` 派生，并限制在编辑器、展开面板或 CW Sheet 的
  独立作用域。
- 在线设备列表、连接面板和三个选择弹窗分别读取所需状态；派生展示模型会过滤不影响显示的复合状态更新。

强制全量编译后，Compose compiler 总量从 891 个 Composable、883 个可重启和 602 个可跳过，变为
900、892 和 611；新增的命名作用域及 `ConnectionPanel` 均为可重启、可跳过。

## 消息 UI 状态边界

改造前，`RadioScreen` 根作用域直接读取完整 `RadioMessageUiState`。历史加载、同步错误、公开资料预加载或未播放计数
的任何变化都会使页面先读取完整状态对象，即使消息列表本身没有变化。

改造后，页面分别通过带 `structuralEqualityPolicy()` 的 `derivedStateOf` 读取消息列表、历史加载、同步错误、公开资料和
未播放数量。消息列表继续使用消息 ID 作为稳定 key；与当前视图无关的消息控制器字段不会替换页面根作用域使用的派生值。
本次没有增加 Composable 层级，编译器报告仍为 900 个 Composable、892 个可重启和 611 个可跳过。

## 内容快照稳定性

`DevicesContentState`、`GroupsContentState` 和 `RelaySearchContentState` 都只包含只读页面快照，现已显式标记
为 `@Immutable`。设备、群组和中继内容层的状态参数因此被 Compose 编译器识别为稳定，列表仍使用 ID 作为
稳定 key；过滤结果和分组映射继续由 `remember` 按输入快照复用。

本地强制编译报告（`compileDebugKotlin -PenableComposeCompilerReports=true --rerun-tasks`）确认三个状态类为
`stable`，总计 900 个 Composable、892 个可重启、611 个可跳过，已知稳定参数为 13,323 个。稳定性标记只
约束重组跳过条件，不替代真机 Layout Inspector 对运行时重组次数和帧时间的验证。

## 群组名称索引边界

群组在线人数和总人数会周期刷新，但 PTT 页面根作用域与连接面板只需要群组 ID、名称和是否存在可选群组。原实现以
完整 `Group` 列表作为 `remember` key，计数变化会重新创建名称索引并使相关作用域失效。

现在两个作用域都通过带 `structuralEqualityPolicy()` 的 `derivedStateOf` 读取 `id -> name` 映射。在线人数和总人数
变化时映射结构保持相等，不再向下游发布新值；名称或群组集合变化时仍会正常更新。群组选择和接收路由弹窗只在显示时
读取完整列表，因此不牺牲弹窗中的实时计数。JVM 用例覆盖“仅计数变化时索引相等、名称变化时索引变化”，强制全量编译
报告仍为 900 个 Composable、892 个可重启、611 个可跳过，`RadioScreen` 与 `ConnectionPanel` 均保持可跳过。

## 验证边界

本批已通过 Debug Kotlin 编译和 Compose compiler 报告对比。本轮本地模拟器只执行消息缓存仪器测试，尚未使用真机
Layout Inspector 验证 PTT 收发期间的实际重组次数，也未记录帧时间；其余页面的细粒度订阅和真机运行时证据仍由
`TODO.md` 跟踪。
