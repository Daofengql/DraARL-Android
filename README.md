# DraARL Android

DraARL 麟链的 Android 通信客户端。客户端只包含普通用户功能，不包含服务端管理员后台。

## 当前功能

- 图片验证码 + HTTP JWT 登录、加密会话存储、access token 自动刷新
- 通过 `GET /api/access-points` 获取公开中心/边缘 UDP 入口
- 并行探测边缘节点时延、自动选择最快可达节点并连接，探测不可用时按服务端优先级回退
- DraARLv1 现代 UDP 幽灵认证，使用安装范围随机 `client_instance_id` 和服务端签发的 Session
- 同账号多台 Android 可同时在线；2 秒心跳、连接看门狗和重新认证式自动重连
- 每个 Session 单一发送频道、多频道收听，路由通过 Session API 原子更新
- 16 kHz Opus PTT 语音收发，按来源频道和发送者隔离接收流并串行播放
- 按账号和群组隔离的本地 SQLite 消息缓存，并与频道消息游标 API 增量对账
- 通过客户端资源 manifest 检查 APK 更新，并二次验证最低服务端版本、幽灵协议版本和能力集合
- 前台通信服务，支持应用退到后台后保持 UDP 会话
- 普通用户仪表盘、设备、群组、通信记录和个人资料页面

## 构建

环境要求：Android SDK 36.1、JDK 21 或更高版本。

```powershell
.\gradlew.bat test lintDebug assembleDebug
```

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
