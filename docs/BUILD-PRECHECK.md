# APS 构建前检查清单

更新时间：2026-10-07

## 目的

每次准备生成新的 APK 前，先完成一次“代码事实 + 功能要求 + 已知问题”预检。只有预检通过，才进入 Actions 构建和真机验证。

核心原则：

> 代码里存在，不等于用户功能已经完成；UI 有入口，也不等于底层链路已经接通。

## 一、检查来源

1. `PROJECT/SPEC/APS-PRODUCT-SPEC.md`：用户确认的功能要求。
2. `docs/STATUS.md`：当前实现状态。
3. GitHub Issue #29 及其子任务：当前问题与施工顺序。
4. 当前施工分支代码：确认实际调用链和 UI 行为。
5. Commit / Actions / 真机结果：作为最终事实。

## 二、构建前固定检查

### A. 功能表

逐项确认：
- Project 是否能被用户实际进入和管理。
- Project Conversation 是否已经与 Project 模型正确连接。
- 是否能在 Project 内独立新建 / 切换 Conversation。
- Project Conversation 是否使用正确的 Default AI / Project Member / API Profile 关系。
- AI 消息是否显示对应 API 名称与头像，而不只是页面顶部显示。
- API Profile 是否只承担连接资源角色，不承担施工权。
- 本地文件修改权限是否真正进入 PermissionPolicy / BridgeFS 执行链。
- GitHub 读取 / 修改边界是否真正生效。
- **普通对话是否可以读取真实 GitHub Repository / Branch / 文件；不能依赖旧 Workspace GitHub 模型。**
- Project 是否按 Default AI / Project Member / Request AI Assistance 的正式模型工作，而不是旧 Decision / Worker 或固定双 AI 模型。
- Receipt 是否能恢复并与消息 / 执行 / Verify 状态对应。
- 日志是否能从 App 内实际访问并按类型查看。

### B. 代码检查

重点检查：
- Launcher 与实际页面入口。
- Project / Project Conversation 数据模型及所有调用方。
- 旧 BridgeProject / Workspace 兼容字段是否仍被业务代码使用；如存在，只作为兼容残留检查，不得作为当前业务模型。
- API Profile 与权限代码是否存在错误耦合。
- PermissionPolicy 是否读取正确的 Project / Conversation 有效权限。
- 普通对话 GitHub 读取是否经过统一 GitHub 访问边界。
- GitHub 写入是否经过 Project Address + Repository + Branch 施工边界。
- 是否存在旧 Decision / Worker 协议残留。
- 是否存在旧日志路径和重复日志入口。
- MainActivity 是否仍承载新功能。
- 本轮修改是否留下 UI 有入口但功能没有接通的路径。

### C. UI 打通检查

每个本轮涉及的功能都必须逐项检查：

| 检查项 | 必须回答 |
| --- | --- |
| 入口 | 用户能否在 APK 中找到入口？ |
| 操作 | 用户能否完整执行操作？ |
| 调用 | UI 是否调用正确的业务逻辑，而不是只修改显示状态？ |
| 保存 | 操作结果是否持久化？ |
| 恢复 | 重新进入页面 / 重启 App 后是否恢复？ |
| 生效 | 保存的状态是否真正影响后续业务链？ |
| 验证 | 能否在真机上完成一次完整操作？ |

任一项为“否”，不得标记为“已验证”。

### D. 已知问题回归

至少检查 Issue #29 当前子任务，以及上一轮确认的问题：
- 消息宽度：当前已解决，回归确认即可。
- API 名称：当前消息中未显示，待修复。
- API 头像：当前消息中未显示，待修复。
- Project 主页面 / 数据结构：当前 App 尚未完全收口。
- Project 内独立新建 Conversation：当前 App 尚未完全验证。

## 三、构建后验证
> **APK 来源规则：** `android-verify.yml` 生成的 Debug APK 仅用于 CI 编译/打包验证，不作为设备安装测试包。设备安装测试必须使用 `android-build.yml` 生成的 `release/APS.apk`，该 APK 使用仓库固定的官方签名密钥。不要混用两种签名 APK。


Actions 构建完成后必须记录：
- 构建 Commit SHA。
- Workflow / Job 结果。
- APK 对应版本 / 构建号。
- APK SHA-256。
- 真机安装结果。
- 本轮功能实际验证结果。

未经真实验证，不把“已实现”改为“已验证”。

## 四、问题处理规则

新发现的 Bug / 遗漏需求：

发现 → 建 Issue → 加入 Issue #29 → 排施工顺序 → 修改 → Commit → 验证 → 更新状态。

如果是普通实现问题，施工可以继续，不需要用户反复输入“继续”。

如果涉及产品、架构、权限边界或用户授权的新决策，则暂停并报告。


## 2026-10-04 新增：普通对话 GitHub 读取预检

本项优先于 GitHub 修改施工。

必须确认：
- 普通对话不进入 Project 工作面也能发起 GitHub 读取；
- GitHub 全局访问关闭时不会调用 GitHub API；
- 授权无效 / Repository 不可访问时显示真实错误；
- AI 能读取真实 Repository / Branch / 文件；
- 读取结果确实进入 AI 请求，而不是只在 UI 显示“已连接”；
- 普通对话读取不获得修改权，不绕过 ConstructionLock。

未完成以上真机链路前：普通对话 GitHub = 开发中 / 待验证。
不要标记为“GitHub 已完全打通”。