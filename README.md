# DraARL Android

DraARL 麟链的 Android 通信客户端。客户端只包含普通用户功能，不包含服务端管理员后台。

## 当前功能

- 图片验证码 + HTTP JWT 登录、加密会话存储、access token 自动刷新
- “设备、群组、PTT、工具、我的”五入口导航，覆盖设备/群组管理、资料与账号安全
- 通过 `GET /api/access-points` 获取公开中心/边缘 UDP 入口
- 并行探测边缘节点时延、自动选择最快可达节点并连接，探测不可用时按服务端优先级回退
- DraARLv1 现代 UDP 幽灵认证，使用安装范围随机 `client_instance_id` 和服务端签发的 Session
- 同账号多台 Android 可同时在线；2 秒心跳、连接看门狗和重新认证式自动重连
- 发送/日志频道统一切换，多频道收听路由通过 Session API 原子更新
- 16 kHz Opus PTT 语音收发、录音与可选 RNNoise 播放降噪，按来源频道和发送者隔离接收流并串行播放
- 文本、语音和位置消息；高德地图选点、位置预览、距离与方向计算
- 按账号和群组隔离的本地 SQLite 消息缓存，并与频道消息游标 API 增量对账
- 蓝牙设备配网、中继台查询、通联日志、电台预设和梅登海德网格工具
- APRS-IS 手动/后台位置上报，以及主题、显示缩放、存储和 PTT 悬浮窗设置
- 通过客户端资源 manifest 检查 APK 更新，并二次验证最低服务端版本、幽灵协议版本和能力集合
- 前台通信服务，支持应用退到后台后保持 UDP 会话

## 工程结构

项目是单 `app` 模块的 Kotlin + Jetpack Compose Android 应用：

- `app/src/main/java/cn/silverdragon/draarl/ui`：Compose 导航、页面和公共组件
- `app/src/main/java/cn/silverdragon/draarl/data`、`network`：本地状态、缓存、数据映射和 HTTP API
- `app/src/main/java/cn/silverdragon/draarl/settings`：设置状态、持久化和缓存清理协调
- `app/src/main/java/cn/silverdragon/draarl/radio`、`protocol`：UDP 会话、PTT 音频和 DraARL 协议
- `app/src/main/java/cn/silverdragon/draarl/aprs`、`tools`、`maps`：APRS、原生工具和地图能力
- `app/src/main/cpp`：RNNoise 的 JNI/CMake 接入及第三方源码

当前规模、代码分布和维护重点见 [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md)，自动截图范围与
控件收敛规则见 [`docs/UI_VISUAL_BASELINE.md`](docs/UI_VISUAL_BASELINE.md)。

## 构建

环境要求：Android SDK 36.1、JDK 17 或更高版本（CI 使用 JDK 17，本地已在 JDK 24 验证）、Android NDK 28.2.13676358 和 CMake 3.22.1。项目 Wrapper 使用 Gradle 9.5.0，Android Gradle Plugin 为 9.3.0。

地图相关功能从 Gradle 属性或 `local.properties` 读取 `AMAP_API_KEY`；未配置时不影响基础通信功能和 Debug 构建，但地图选点与预览不可用。

```powershell
.\gradlew.bat spotlessCheck detektDebug
.\gradlew.bat testDebugUnitTest validateDebugScreenshotTest lintDebug assembleDebug assembleDebugAndroidTest
```

Spotless 只检查相对 `origin/main` 新增或修改的 Kotlin/Gradle 文件；Detekt 使用存量基线，并阻止新增的复杂方法、超长类、吞异常和无明确生命周期的通用线程池。GitHub Actions 对 `main` 和 Pull Request 执行同一组静态检查、截图验证、构建门禁和 Markdown 链接检查。

调试 APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

## 服务端契约

实现参考 `DraARL-Server/docs/api`、`docs/Protocol.md`、`internal/udphub/jwt_auth.go` 和 Web 普通用户页面。关键链路为：

1. `GET /api/captcha` 获取图片验证码，再通过 `POST /api/auth/login` 获取 access/refresh token。
2. `GET /api/access-points` 获取健康 UDP 入口。
3. 向优选入口发送 DraARLv1 `Type=1` 版本化 JSON，包含 JWT、稳定的安装实例 UUID 和多收能力。
4. 认证成功后保存 `session_id` 与 `session_tag`；后续 `Type=2/4/5` 上行在 Reserved 回传 tag。
5. 下行 `Type=4/5` 从 Reserved 读取真实 `source_group_id`，语音按来源频道和发送者隔离状态。
6. `PUT /api/radio/sessions/:session_id/routing` 更新唯一发送频道和多个收听频道。
7. `GET /api/groups/:id/messages` 按频道 ACL 和游标同步消息历史；通信记录接口不再作为聊天历史。
8. `GET /api/public/client-resources/manifest` 获取 APK 更新，schema 1 清单携带当前服务端契约；客户端在展示更新前再次校验发布所需的最低服务端版本、协议版本和能力。

生产服务端地址集中定义在 `AppConfig.BASE_URL`，固定为 `https://ptt.4l2.cn`，客户端不再提供地址输入框。

在线收发以 UDP 实时消息和本地缓存作为最近数据源。服务端已完成的频道消息按
`server_record_id`、来源频道、发送方、消息类型、内容和时间窗口与本地记录合并；本地新消息仍保留
3 分钟保护窗口，避免历史同步覆盖尚未写入服务端的实时消息。
