# DraARL Android

DraARL 麟链的 Android 通信客户端。客户端只包含普通用户功能，不包含服务端管理员后台。

## 当前功能

- 图片验证码 + HTTP JWT 登录、加密会话存储、access token 自动刷新
- 通过 `GET /api/access-points` 获取公开中心/边缘 UDP 入口
- 并行探测边缘节点时延、自动选择最快可达节点并连接，探测不可用时按服务端优先级回退
- DraARLv1 UDP JWT 认证，Android `DevModel/SSID = 101`
- 2 秒心跳、连接看门狗、自动重连和幽灵设备冲突等待
- 16 kHz Opus PTT 语音收发、文本与 PTT 共存发送器、群组切换和在线设备列表
- 按账号和群组隔离的本地 SQLite 消息缓存，并与服务端通信记录增量对账
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
3. 向优选入口发送 DraARLv1 `Type=1`，JWT 放在 `DATA` 区域。
4. 认证成功后发送 `Type=2` 心跳、`Type=4` 文本和 `Type=5` Opus 语音。
5. `PUT /api/radio/group` 携带 `dev_model=101` 同步 Android 客户端群组。

生产服务端地址集中定义在 `AppConfig.BASE_URL`，固定为 `https://ptt.4l2.cn`，客户端不再提供地址输入框。

在线收发以 UDP 实时消息和本地缓存作为最近数据源。服务端通信记录经过 3 分钟沉淀窗口后，才按
`server_record_id`、发送方、消息类型、内容和时间窗口与本地记录合并，避免服务端记录尚未稳定时覆盖新消息。
