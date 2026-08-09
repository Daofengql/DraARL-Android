# DraARL UI 视觉基线

更新时间：2026-08-09

## 自动截图范围

参考图由 Android 官方 Compose Preview Screenshot Testing 生成，保存在
`app/src/screenshotTestDebug/reference`。测试直接组合生产代码中的壳层和页面内容，不创建或启动
`AppController`，因此不会触发会话恢复、服务绑定、网络请求或本地存储写入。

当前 24 张参考图在 Windows 本地通过 JVM/Layoutlib 渲染，不依赖模拟器、真机或远程服务。

| 场景 | 浅色 | 深色 | 额外覆盖 |
| --- | --- | --- | --- |
| 应用壳层 | 360 x 800 dp | 411 x 891 dp | 五入口底栏与突出 PTT 入口 |
| 设备 | 360 x 800 dp | 411 x 891 dp | 长设备名、在线/离线、受限状态，以及 1.3 倍字体加载态 |
| 群组 | 360 x 800 dp | 411 x 891 dp | 公开/私有分区、长中文群组名和空态 |
| PTT | 411 x 891 dp | 360 x 800 dp | 状态条、频道、空日志和发送控制 |
| 工具 | 800 x 360 / 360 x 800 dp | 411 x 891 dp | 横屏、账号审核，以及 1.5 倍字体错误反馈 |
| 个人 | 411 x 891 dp | 360 x 800 dp | 长中文资料和 1.5 倍字体 |
| 设置行 | 360 x 520 dp | 360 x 620 dp | 长说明、危险操作和 1.5 倍字体 |
| APRS 设置 | 360 x 800 dp | - | 服务器表单、自动上报入口和 1.3 倍字体 |
| BLE 配网 | - | 360 x 800 dp | Wi-Fi 表单、写入命令和 1.5 倍字体 |
| 存储与弹窗 | 411 x 520 dp | 411 x 891 / 320 x 620 dp | 典型缓存数据、危险操作，以及 2.0 倍字体的地图确认 |
| Bottom Sheet | 360 x 640 dp | 320 x 700 dp | 1.3 倍位置选择与 2.0 倍 CW 数据/双操作按钮 |

PTT 基线使用生产代码中的通联日志模式。高德地图由运行时 `AndroidView` 和地图 SDK 渲染，不纳入
Layoutlib 像素基线，后续仍需在有 Key 的真机上检查地图页。

## 当前视觉语言

- 交互强调色使用低饱和青绿色；连接、等待、发射、接收和错误继续使用独立通信状态色，统计数据以
  青绿、琥珀、铁锈红和低饱和紫区分，避免用同一种品牌色承载全部语义。
- 普通容器使用 3-6 dp 圆角；8-10 dp 只保留给较大的独立对象和弹层，不使用默认悬浮胶囊作为页面层级。
- 呼号、SSID、群组 ID、无线电标识、在线计数和时长使用带表格数字的等宽样式。
- 底栏固定为 64 dp，使用短选中线；PTT 是带边框的设备控制入口，不使用实心主按钮形态。
- 设备和群组使用方形设备标识与点状状态，工具页使用两组紧凑列表，个人页使用无框身份区和分隔行。
- 标准确认、表单和选择弹窗统一使用带细边框的 `DraarlDialog`；双操作按钮在常规字体下并排，在 1.75 倍及以上字体下纵向排列，避免长命令被截断。设备与群组的全屏管理覆盖层继续使用无默认宽度的专用 `Dialog`。
- CW 与位置选择统一使用低圆角、无色调抬升的 `DraarlSheet`，标题、分隔线、拖拽把手和动作区与弹窗共享同一层级规则。
- 页面级空态、加载、错误和权限反馈使用统一的 `PageFeedback` 语义、居中层级与状态色；可恢复的工具错误使用可关闭 `InlineNotice`，按钮内进度保持紧凑反馈。
- APRS 设置使用细边框设置组组织链路、登录参数、后台上报和链路测试；状态反馈复用 `StatusTone`，不再以默认 Card 和裸状态文本建立层级。
- BLE 配网使用相同设置组组织设备类型、认证和配置表单；扫描、断开与写入使用命令按钮，连接阶段使用 `StatusIndicator`，设备类型弹窗使用单选行而非嵌套 Card。

浅色和深色主题的 12 组关键前景/背景组合均按 WCAG 对比度公式检查，最低结果为 5.98:1。

生成和验证命令：

```powershell
.\gradlew.bat app:updateDebugScreenshotTest
.\gradlew.bat app:validateDebugScreenshotTest
```

只有确认视觉变化符合预期时才运行更新命令。普通开发和 CI 使用验证命令，报告位于
`app/build/reports/screenshotTest/preview/debug/index.html`。

## 当前控件清单

| 类型 | 主要位置 | 当前用途 | 后续规则 |
| --- | --- | --- | --- |
| `Card` | `RadioMessageComponents.kt`、`LogbookScreen.kt`、`RadioPresetsScreen.kt`、`RelaySearchScreen.kt` | 消息、日志、预设和中继结果对象 | 只保留独立或可比较的数据对象；页面分区不使用 Card |
| `Surface` | `DraarlBottomBar.kt`、`DraarlComponents.kt`、`DraarlContainers.kt`、`DraarlSettings.kt`、`DraarlSegmentedControl.kt`、`RadioStatusStrip.kt`、`CommunicationTrendChart.kt` 和 `ProfileOverview.kt`，以及设备、群组、地图、消息、登录和工具页面 | 背景、状态容器、可点击行、统计对象、弹层和控制面 | 保留承载语义、状态色、细边框或点击反馈的 Surface；纯页面分区不增加浮层和阴影 |
| 圆角按钮 | 认证、设备/群组管理、地图、日志编辑、资料与设置页面中的 `Button`、`OutlinedButton`、`TextButton` | 明确提交、确认、取消和危险操作 | 命令按钮保留；图标已有通用语义时使用图标按钮；普通行入口不使用胶囊按钮 |
| 分段控件 | `DraarlSegmentedControl.kt`、`AprsMapPanel.kt`、`SystemSettingsScreen.kt` | PTT 地图/日志模式与设置选项 | 只用于同级互斥模式，保持紧凑 0-6 dp 圆角，不扩展为页面导航 |
| 状态色 | `Theme.kt`、`DraarlComponents.kt`、`RadioStatusStrip.kt`、设备/群组/PTT/APRS/设置页面 | 连接、接收、发射、在线、等待、离线和错误 | 所有业务状态从 `appColors` 或 `StatusTone` 获取；页面不得直接发明新的成功/警告色 |

## 评审约束

- UI 改造前后必须使用相同 Preview 名称、尺寸、主题和固定样本数据比较。
- 参考图需要检查非空渲染、底栏与内容遮挡、长文本换行和状态色可辨识度。
- 像素基线不能替代真机 Insets、软键盘、权限弹窗、地图和触摸目标检查。
- 本文证明一级页面及首批设置行、APRS、BLE 配网、存储页、错误提示、弹窗和 Bottom Sheet 静态基线已建立；设备加载、群组空态和工具错误已有首批页面状态基线，剩余状态矩阵仍由
  `TODO.md` 中“增加 UI 回归测试”跟踪。
