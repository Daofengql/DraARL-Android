# 服务端契约

Android 客户端依赖 DraARL Server 的 HTTP API、DraARLv1 UDP 协议和客户端资源清单。本页只记录
客户端依赖的边界；字段定义和服务端行为以服务端仓库的 API/协议文档及实现为准。

## 入口

生产 HTTP 地址由 `AppConfig.BASE_URL` 定义。客户端不允许用户切换服务端。

主要链路：

1. `GET /api/captcha` 获取图片验证码。
2. `POST /api/auth/login` 获取 access token 和 refresh token。
3. `GET /api/access-points` 获取可用中心/边缘 UDP 入口。
4. 客户端并行探测边缘节点，选择最快可达节点；探测失败时按服务端优先级回退。
5. 客户端发送 DraARLv1 `Type=1` JSON，携带 JWT、安装实例 ID 和能力集合。
6. 认证成功后保存 `session_id` 与 `session_tag`，后续上行包在 Reserved 字段回传 tag。
7. `PUT /api/radio/sessions/:session_id/routing` 原子更新发送频道和收听频道。
8. `GET /api/groups/:id/messages` 按频道 ACL 和游标同步消息历史。
9. `GET /api/public/client-resources/manifest` 获取 APK 更新及最低服务端/协议/能力要求。

## 会话边界

- `DraarlApplication` 在进程内共享唯一 `SecureSessionStore` 和 `ApiClient`。
- `ApiSessionManager` 负责 Token 持久化、刷新、并发 401 合并和认证请求重试。
- 页面 Controller 与 `RadioConnectionService` 使用同一份内存会话，避免重复刷新或继续使用旧 Token。
- 账号切换、退出或远端失效后，旧请求和旧 UDP 回调不得覆盖新会话。
- 持久会话恢复有总时限；失败后清理通信资源并返回登录流程。

## 实时消息

- UDP 下行 `Type=4/5` 从 Reserved 字段读取真实 `source_group_id`。
- 语音接收状态按来源频道和发送者隔离，避免跨频道或跨用户拼接。
- 在线消息先写本地缓存，再通过频道消息 API 增量对账。
- 服务端记录按 `server_record_id` 以及来源、发送者、类型、内容和时间窗口与本地记录合并。
- 本地新消息保留短期保护窗口，避免历史同步覆盖尚未落入服务端的实时消息。

详细 UDP 状态和资源生命周期见 [UDP 连接生命周期](UDP_CONNECTION_LIFECYCLE.md)。

## 更新兼容性

客户端资源 manifest 的发布项可以声明最低服务端版本、UDP 协议版本和所需能力。客户端展示更新前
会再次验证这些要求，避免下载与当前服务端不兼容的 APK。

修改更新清单 schema 或能力名称时，应同时检查：

- `data/ClientResources.kt`
- `network/UpdateApiDtos.kt`
- `update/AppUpdateManager.kt`
- 对应 DTO、Mapper 和兼容性测试

## 变更检查清单

服务端契约变化时，在同一改动中完成以下检查：

- 更新对应领域 API 接口、DTO 和 Mapper，不在 UI 中直接解析 JSON。
- 为必填字段、兼容默认值、错误响应和旧字段别名增加测试。
- 检查 `ApiSessionManager` 的认证、刷新和旧结果丢弃语义。
- 检查 `DraarlProtocol` 的包类型、Reserved 字段、版本和能力集合。
- 检查消息缓存对账键与服务端游标语义。
- 检查应用更新 manifest 的协议与能力校验。
- 更新本页和相关错误提示；协议状态变化还需更新 UDP 生命周期文档。
