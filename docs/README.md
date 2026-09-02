# 文档索引

本文档目录按主题组织，`README.md` 是唯一入口。配置值和依赖版本以 Gradle 文件为准，文档只解释
用途、约束和维护方式。

## 目录结构

| 目录 | 内容 |
| --- | --- |
| `guides/` | 本地开发、构建与测试入口 |
| `architecture/` | 模块边界、服务端契约与实时连接生命周期 |
| `engineering/` | Compose 性能和 UI 截图基线 |
| `operations/` | 依赖升级与 Release 产物验收 |
| `legal/` | 第三方数据与许可声明 |
| `brand/` | 项目标识与预览素材 |

## 从哪里开始

| 目标 | 文档 |
| --- | --- |
| 第一次构建或运行测试 | [开发指南](guides/DEVELOPMENT.md) |
| 理解模块、Controller 和数据流 | [项目结构](architecture/PROJECT_OVERVIEW.md) |
| 修改 HTTP、认证、UDP 或更新协议 | [服务端契约](architecture/SERVER_CONTRACT.md) |
| 修改 UDP 会话、前台服务或音频生命周期 | [UDP 连接生命周期](architecture/UDP_CONNECTION_LIFECYCLE.md) |
| 修改 Compose 页面或公共组件 | [UI 视觉基线](engineering/UI_VISUAL_BASELINE.md) 与 [Compose 性能](engineering/COMPOSE_PERFORMANCE.md) |
| 升级依赖或 Android SDK | [依赖升级](operations/DEPENDENCY_UPGRADES.md) |
| 构建和验收 Release APK | [Release 指南](operations/RELEASE_OPTIMIZATION.md) |
| 查看仍需完成的人工验证 | [待办事项](../TODO.md) |
| 核对第三方数据许可 | [Third-Party Notices](legal/THIRD_PARTY_NOTICES.md) |

## 单一事实来源

| 信息 | 事实来源 |
| --- | --- |
| 应用版本、SDK、NDK、CMake、ABI 与构建任务 | `app/build.gradle.kts` |
| 插件与依赖版本 | `gradle/libs.versions.toml` |
| Gradle Wrapper 版本 | `gradle/wrapper/gradle-wrapper.properties` |
| CI 执行的门禁 | `.github/workflows/android-ci.yml` |
| 生产服务端地址 | `app/src/main/java/cn/silverdragon/draarl/AppConfig.kt` |
| Android 权限和组件 | `app/src/main/AndroidManifest.xml` |
| R8/JNI 保留规则 | `app/src/main/keepRules/release.keep` |
| 未完成的人工验证 | `TODO.md` |

文档不要复制可直接从上述文件读出的完整版本清单、代码行数、测试数量或文件排行。这些值变化频繁，
只有在解释兼容限制或发布基线时才记录，并注明复核日期。

## 维护规则

- 行为、命令或配置改变时，在同一改动中更新对应文档。
- 新增专题前先确定唯一所有者；不要在 README、项目概览和专题文档重复同一段细节。
- 已完成事项从 `TODO.md` 删除。一次性调查过程放在提交或 Issue 中，不长期堆积在项目概览。
- 命令应从仓库根目录可直接执行，并优先使用 Gradle Wrapper。
- 文档链接必须使用仓库相对路径，并通过 CI 的离线 Markdown 链接检查。
- 涉及服务端契约时，同时核对 DTO/Mapper、协议实现、兼容错误提示和测试。
