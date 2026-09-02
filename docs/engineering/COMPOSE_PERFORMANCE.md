# Compose 性能

本页记录 Compose 状态订阅和性能验证的长期规则。编译器汇总数字会随组件数量变化，不作为固定
项目指标；需要分析时从当前分支重新生成报告。

## 核心原则

- 页面根作用域只读取决定页面结构的状态，不订阅高频或仅局部展示的字段。
- 音频电平、下载进度、播放 ID、Slider 数值等高频状态下沉到最小 Composable。
- 能在绘制阶段读取的动画值通过 `Canvas` 或 provider 延后读取，避免每帧重组父级。
- 内容组件接收不可变快照和事件回调，不直接读取整个 `AppController`。
- 弹窗、Bottom Sheet 和重型面板只在显示时订阅其所需状态。
- `LazyColumn`/`LazyRow` 的动态业务项必须使用稳定、唯一且无歧义的 key。
- `remember`、`derivedStateOf` 和状态投影用于缩小失效范围，不用于掩盖错误的数据所有权。

## 已建立的边界

| 场景 | 订阅边界 |
| --- | --- |
| PTT 音频电平 | 独立电平组件读取，动画值在绘制阶段读取 |
| 应用更新下载 | 对话框通过稳定 provider 读取进度，不替换完整更新状态 |
| 消息播放 | 可见消息项按消息 ID 派生播放状态，自动滚动使用独立 effect |
| 电台连接 | 连接副作用、收发能力、在线设备和连接面板使用独立状态投影 |
| 节点与路由弹窗 | 仅在显示时读取各自不可变最小状态 |
| 群组名称 | PTT 页面读取名称索引；实时人数变化不替换名称投影 |
| 应用入口 | 路由只读取初始化与认证所需的 `SessionGateState` |
| 地图 PTT | 浮动 PTT 控件独立于包含高德 `AndroidView` 的父作用域 |
| 系统设置 | 根状态与两个高频 Slider 状态分离 |
| 结果列表 | 中继等列表读取独立结果快照，并使用稳定实体 key |

这些边界应通过相应的状态投影或 Snapshot JVM 测试保护。重构时优先扩展现有投影，不要让页面重新
订阅完整 Controller 状态。

## 编译器报告

运行以下命令生成当前分支的 Compose compiler metrics 与 reports：

```powershell
.\gradlew.bat compileDebugKotlin -PenableComposeCompilerReports=true --rerun-tasks
```

输出位于 `app/build/compose_compiler`，属于构建产物，不提交仓库。普通构建不设置
`enableComposeCompilerReports`，不会生成报告。

分析时重点检查：

- 修改的 Composable 是否仍可重启、可跳过。
- 新增参数为何被判定为 unstable，能否改为不可变值、稳定接口或事件回调。
- 是否因为读取完整状态对象而扩大了失效范围。
- 是否新增了不必要的组合层级或在列表项中创建不稳定对象。

汇总的 Composable、restartable、skippable 数量只能用于同一源码附近的诊断，不能直接证明运行时
性能改善。

## 修改检查清单

- 为新增页面提供纯内容层，输入为稳定状态和回调。
- 高频字段在最小使用点读取；父级不为转发一个值而订阅它。
- 列表 key 来自稳定实体 ID；复合 key 必须避免分隔符歧义和重复结果。
- effect 的 key 完整表达生命周期，不使用无关大对象触发重启。
- 对无关字段变化不应触发的区域，增加状态投影或 Snapshot 测试。
- 涉及布局变化时同步运行 [UI 视觉基线](UI_VISUAL_BASELINE.md) 的截图验证。

## 验证

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat validateDebugScreenshotTest
.\gradlew.bat compileDebugKotlin -PenableComposeCompilerReports=true --rerun-tasks
```

编译器报告和 Snapshot 测试不能替代运行时证据。PTT 收发、地图 `AndroidView`、长列表滚动、系统设置
Slider 和更新下载等高频场景，仍应按 [TODO.md](../../TODO.md) 使用真机 Layout Inspector、帧时间和
Macrobenchmark 验证。
