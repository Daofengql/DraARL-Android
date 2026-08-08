# DraARL UI 视觉基线

更新时间：2026-08-08

## 自动截图范围

参考图由 Android 官方 Compose Preview Screenshot Testing 生成，保存在
`app/src/screenshotTestDebug/reference`。测试直接组合生产代码中的壳层和页面内容，不创建或启动
`AppController`，因此不会触发会话恢复、服务绑定、网络请求或本地存储写入。

| 场景 | 浅色 | 深色 | 额外覆盖 |
| --- | --- | --- | --- |
| 应用壳层 | 360 x 800 dp | 411 x 891 dp | 五入口底栏与突出 PTT 入口 |
| 设备 | 360 x 800 dp | 411 x 891 dp | 长设备名、在线/离线和受限状态 |
| 群组 | 360 x 800 dp | 411 x 891 dp | 公开/私有分区和长中文群组名 |
| PTT | 411 x 891 dp | 360 x 800 dp | 状态条、频道、空日志和发送控制 |
| 工具 | 800 x 360 dp | 411 x 891 dp | 横屏与账号审核状态 |
| 个人 | 411 x 891 dp | 360 x 800 dp | 长中文资料和 1.5 倍字体 |

PTT 基线使用生产代码中的通联日志模式。高德地图由运行时 `AndroidView` 和地图 SDK 渲染，不纳入
Layoutlib 像素基线，后续仍需在有 Key 的真机上检查地图页。

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
| `Card` | `CommunicationTrendChart.kt`、`ProfileHeader.kt`、`ProfileOverview.kt`、`RadioMessageComponents.kt`，以及账号、APRS、BLE、日志、预设、设置和存储页 | 身份区、统计、消息和部分页面分区 | 只保留独立或可比较的数据对象；个人页身份区、设置分区和说明区优先改为无框布局或细分隔线 |
| `Surface` | `DraarlBottomBar.kt`、`DraarlComponents.kt`、`DraarlContainers.kt`、`DraarlSegmentedControl.kt`、`RadioStatusStrip.kt`，以及设备、群组、地图、消息、登录和工具页面 | 背景、状态容器、可点击行、弹层和控制面 | 保留承载语义、状态色或点击反馈的 Surface；纯页面分区不增加浮层和阴影 |
| 圆角按钮 | 认证、设备/群组管理、地图、日志编辑、资料与设置页面中的 `Button`、`OutlinedButton`、`TextButton` | 明确提交、确认、取消和危险操作 | 命令按钮保留；图标已有通用语义时使用图标按钮；普通行入口不使用胶囊按钮 |
| 分段控件 | `DraarlSegmentedControl.kt`、`AprsMapPanel.kt`、`SystemSettingsScreen.kt` | PTT 地图/日志模式与设置选项 | 只用于同级互斥模式，保持紧凑 0-6 dp 圆角，不扩展为页面导航 |
| 状态色 | `Theme.kt`、`DraarlComponents.kt`、`RadioStatusStrip.kt`、设备/群组/PTT/APRS/设置页面 | 连接、接收、发射、在线、等待、离线和错误 | 所有业务状态从 `appColors` 或 `StatusTone` 获取；页面不得直接发明新的成功/警告色 |

## 评审约束

- UI 改造前后必须使用相同 Preview 名称、尺寸、主题和固定样本数据比较。
- 参考图需要检查非空渲染、底栏与内容遮挡、长文本换行和状态色可辨识度。
- 像素基线不能替代真机 Insets、软键盘、权限弹窗、地图和触摸目标检查。
- 本文只证明一级页面静态基线已建立；空、错、加载、弹窗和 Bottom Sheet 的完整状态矩阵仍由
  `TODO.md` 中“增加 UI 回归测试”跟踪。
