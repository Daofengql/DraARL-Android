# UI 视觉基线

Compose Preview Screenshot Testing 用于保护可重复渲染的页面、状态和组件。参考图位于
`app/src/screenshotTestDebug/reference`，测试源码位于 `app/src/screenshotTest`。

截图测试直接组合生产内容组件，不创建 `AppController`，也不触发会话恢复、Service 绑定、网络请求
或本地写入。高德地图由 `AndroidView` 和 native SDK 渲染，不纳入 Layoutlib 像素基线。

## 命令

```powershell
# 验证现有基线
.\gradlew.bat :app:validateDebugScreenshotTest

# 仅在视觉变化已确认时更新基线
.\gradlew.bat :app:updateDebugScreenshotTest
```

验证报告位于 `app/build/reports/screenshotTest/preview/debug/index.html`。更新参考图后必须再次运行验证，
并在 diff 中人工检查变化区域。

## 覆盖策略

新增或大改页面时，按风险选择以下维度，不追求所有维度的笛卡尔积：

| 维度 | 最低要求 |
| --- | --- |
| 主题 | 关键页面至少覆盖浅色和深色 |
| 宽度 | 一级页面覆盖窄屏或常规手机；横向布局变化时增加横屏 |
| 字体 | 长标题、弹窗和密集操作覆盖 1.5 倍以上字体 |
| 状态 | 覆盖正常、空、加载、错误、禁用或成功中实际存在的高风险状态 |
| 文本 | 使用接近上限的中文、呼号、频率、凭据或错误信息 |
| 弹层 | 重要 Dialog、Bottom Sheet 和全屏覆盖层单独建立基线 |

固定样本数据必须确定、无网络依赖，时间和随机值不能导致每次输出不同。

## 视觉规则

- 页面使用低装饰、工作型的信息密度，分区依赖留白、细分隔线和排版，不堆叠 Card。
- 页面与重复项不形成卡片嵌套；只有实体项、弹窗和确需边界的工具使用容器。
- 圆角保持克制；常规容器不超过 8 dp，按钮和状态标签按既有组件实现。
- 返回、搜索、复制、编辑、删除、播放和地图控制优先使用标准图标按钮，并提供 tooltip 与
  `contentDescription`。
- 二元设置使用开关或复选框，互斥模式使用分段控件，数字设置使用 Slider/Stepper/Input。
- 业务状态色从 `appColors` 或 `StatusTone` 获取，页面不直接发明新的成功、警告或错误色。
- 非地图二级页面复用 `DraarlScreenHeader`；确认弹窗复用 `DraarlDialog`；Bottom Sheet 复用
  `DraarlSheet`。
- 空、加载和错误状态优先复用 `PageFeedback`、`InlineNotice`、`StatusIndicator`、`ToolError` 或
  `AppUpdateFeedback`。
- 按钮、导航项、标题和长文本在窄屏和大字体下不得截断、重叠或遮挡相邻内容。

## 公共组件

| 类型 | 首选组件/位置 | 使用原则 |
| --- | --- | --- |
| 页面标题 | `DraarlScreenHeader` | 统一安全区、返回、双行标题和尾部动作 |
| 弹窗与 Sheet | `DraarlDialog`、`DraarlSheet` | 统一边框、标题、操作区与大字体布局 |
| 命令按钮 | `CommandButton`、图标按钮 | 支持 busy；通用命令优先图标 |
| 模式切换 | `DraarlSegmentedControl` | 只用于同级互斥模式，不充当页面导航 |
| 状态反馈 | `PageFeedback`、`InlineNotice`、`StatusIndicator` | 按页面级、行内和紧凑状态选择 |
| 业务状态色 | `appColors`、`StatusTone` | 连接、收发、在线、等待和错误统一来源 |

新增公共组件前先搜索已有实现。只有能统一多个真实场景或消除重复复杂度时才抽象。

## 评审清单

- 使用相同 Preview 名称、尺寸、主题和样本数据比较改动前后。
- 检查截图非空、内容与底栏不互相遮挡、长文本能换行、状态色可辨识。
- 确认动态列表 key 稳定，加载或状态变化不会使布局整体跳动。
- 确认触摸命令仍有可访问名称；截图通过不代表语义树正确。
- 地图、Insets、软键盘、权限弹窗、系统字体、触摸目标和真实滚动必须另做仪器或真机检查。

尚未完成的设备与状态矩阵统一记录在 [TODO.md](../../TODO.md)。
