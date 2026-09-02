# DraARL Android

DraARL 麟链的 Android 通信客户端，面向普通用户提供设备与群组管理、实时 PTT、消息、地图、
APRS 和业余无线电工具。管理后台与服务端不在本仓库内。

## 主要能力

- 图片验证码登录、加密会话存储和 access token 自动刷新
- 设备、群组、PTT、工具、我的五个一级入口
- DraARLv1 UDP 认证、节点优选、多频道收听、心跳和自动重连
- 16 kHz Opus 语音、文本与位置消息，本地 SQLite 缓存和增量同步
- 高德地图选点、位置预览、距离与方向计算
- 蓝牙配网、中继查询、通联日志、电台预设和梅登海德网格工具
- APRS-IS 手动及后台位置上报
- 前台通信服务、PTT 悬浮窗和应用内更新

## 快速开始

要求：JDK 17、Android SDK 36.1、Android NDK 28.2.13676358、CMake 3.22.1。项目使用仓库内的
Gradle Wrapper，不需要单独安装 Gradle。

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

地图功能需要在 `local.properties` 或 Gradle 属性中配置 `AMAP_API_KEY`：

```properties
AMAP_API_KEY=your-key
```

未配置 Key 时仍可构建和使用基础通信功能，但地图选点与预览不可用。完整环境、测试、截图和
常用命令见 [开发指南](docs/guides/DEVELOPMENT.md)。

## 工程入口

仓库包含两个 Gradle 模块：

| 模块 | 用途 |
| --- | --- |
| `app` | Android 应用、JVM 测试、仪器测试和 Compose 截图测试 |
| `baselineprofile` | Baseline Profile 生成与 Macrobenchmark |

生产代码位于 `app/src/main/java/cn/silverdragon/draarl`。重要边界如下：

| 目录 | 职责 |
| --- | --- |
| `ui` | Compose 导航、页面和公共组件 |
| `session`、`network` | 登录会话、HTTP 传输、Token 刷新和领域 API |
| `radio`、`protocol` | UDP 会话、消息、PTT 音频、前台服务和 DraARL 协议 |
| `data` | 领域模型、本地缓存、消息对账和路由模型 |
| `devices`、`groups`、`profile` | 对应业务领域的 Controller 与数据协调 |
| `maps`、`aprs`、`tools` | 地图、APRS、BLE 和无线电工具 |
| `settings`、`update` | 设置持久化、缓存清理和应用更新 |

详细依赖方向、状态所有权和维护风险见 [项目结构](docs/architecture/PROJECT_OVERVIEW.md)。

## 文档

文档从 [docs/README.md](docs/README.md) 进入：

- [开发指南](docs/guides/DEVELOPMENT.md)：环境、配置、构建和测试
- [项目结构](docs/architecture/PROJECT_OVERVIEW.md)：模块、运行时组件和状态所有权
- [服务端契约](docs/architecture/SERVER_CONTRACT.md)：HTTP、UDP 和更新清单的客户端边界
- [UDP 连接生命周期](docs/architecture/UDP_CONNECTION_LIFECYCLE.md)：实时链路状态与资源关闭顺序
- [UI 视觉基线](docs/engineering/UI_VISUAL_BASELINE.md)：截图测试、视觉约束和评审清单
- [Compose 性能](docs/engineering/COMPOSE_PERFORMANCE.md)：状态订阅原则与性能验证
- [Release 指南](docs/operations/RELEASE_OPTIMIZATION.md)：签名、ABI、R8、Profile 和产物验收
- [依赖升级](docs/operations/DEPENDENCY_UPGRADES.md)：SDK 约束与升级流程
- [待办事项](TODO.md)：尚未完成的真机或发布验证

第三方数据与许可说明见 [Third-Party Notices](docs/legal/THIRD_PARTY_NOTICES.md)。
