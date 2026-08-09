# Compose 性能记录

记录日期：2026-08-09

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

## 验证边界

本批已通过 Debug Kotlin 编译和 Compose compiler 报告对比。当前没有连接 Android 设备，因此尚未使用 Layout
Inspector 验证 PTT 收发期间的实际重组次数，也未记录帧时间；连接瞬态和播放位置仍由 `TODO.md` 跟踪。
