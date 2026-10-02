# A-BridgeFS 当前状态

更新时间：2026-10-02

## 项目定位

A-BridgeFS 当前作为 **AI 协作移动端工作台 + 本地执行桥** 进行真实验证。

核心方向：

**双 AI 协作 + 工作区 + GitHub + BridgeFS 本地执行**

核心模型：

**两个 AI 共享读取能力；施工权限独立管理，同一 Repository / Branch 同时最多一个 AI 持有修改权。**

Decision AI / Worker AI 只作为任务阶段角色标签，不是固定身份。

## 当前状态

| 部分 | 状态 | 说明 |
| --- | --- | --- |
| Android 基础工程 | 已实现 | 当前工程可继续施工 |
| API / 对话 | 已实现 / 待统一验证 | API 可配置、可聊天；Chat → API 持久绑定已实现 |
| 本地指令 / BridgeFS | 已实现 | 保留真实本地执行能力 |
| GitHub 连接基础 | 已实现 / 待真机验证 | PAT、Keystore、账号、Repository、Branch 基础能力已落地 |
| 工作区 | 开发中 | Workspace → Conversations 数据结构已落地；V021 已接入 Workspace 切换 / 新建 / 重命名，以及 Conversation 切换 / 新建的基础 UI；仍需真机验证与继续清理兼容 facade |
| 双 AI 协作协议 | 已确认设计 / 尚未完整实现 | 共享读取、施工权独占且可转移 |
| 协作消息 | 开发中 | 当前仍偏旧单轮协作链；聊天消息尚未保存并展示实际使用 API 的名称与头像 |
| 施工锁 | 已设计未实现 | Repository / Branch 级独占写权限 |
| 协作回执 | 已设计未完全实现 | Receipt 与消息时间线仍需统一 |
| 协作日志 | 已设计未完全实现 | 已有分类日志目录，产品级协作日志仍需补齐 |
| GitHub 实际写入链 | 开发中 | 当前已有权限边界，但尚未形成完整写入 → Verify 链 |
| Verify 实链 | 开发中 | 需要 Commit → Run / Check Run → Job → Result |
| 复杂调度 / 多 AI | 未开始 | 当前明确不做 |

## 当前设计基线

### AI

工作区包含两个协作 AI。

两者默认拥有工作区允许范围内的读取能力，可以同时读取同一项目，也可以同时分析、沟通和检查结果。

不因 AI 身份天然获得修改权。

### 施工权

施工权属于具体：

Workspace + Repository + Branch

同一 Repository / Branch：

- 同时最多一个 AI 持有施工权
- 施工权可以转移
- 施工权不等于 API Profile
- 施工权不等于 GitHub Token 本身权限
- GitHub Token 真实权限仍需独立检查

典型生命周期：

申请 → 授权 → 施工 → Commit → Verify → 释放 / 转移

## 2026-10-02 追加：工作区 / GitHub 第一批施工

### 已落地

- `BridgeProject` 已增加工作区级 `localFileModifyEnabled`，并持久化保存。
- V021 工作区页已出现“本地文件”权限卡；该开关目前表达**工作区意图**，尚未接入 `PermissionPolicy` 的最终执行判定。
- GitHub 低层 `GitHubApiClient.updateFile()` 已支持 Contents API 的文件更新。
- `GitHubWorkspaceService.updateFile()` 已作为工作区级写入边界，先检查 GitHub read/write 权限，再调用低层客户端。

### 尚未完成

- 本地文件修改开关尚未真正阻断 WRITE / EDIT。
- Conversation 级本地文件权限覆盖尚未实现；目标是 `Conversation override ?: Workspace permission`。
- GitHub 文件写入尚未连接 AI 施工流程、Commit / PR / Actions / Verify。
- Workspace → Conversation 数据结构已落地，V021 对话切换/新建已开始使用工作区内 Conversation；仍保留兼容 facade，后续继续清理旧调用。
- API 页面仍需从固定 Decision / Worker 配置迁移为 AI 成员 + API Profile 资源模型。

### 本批次状态

| 项目 | 状态 |
| --- | --- |
| 工作区本地修改权限数据 | 已实现 |
| 工作区本地修改权限 UI | 已实现 |
| 本地修改权限实际执行拦截 | 已设计未实现 |
| GitHub 低层文件更新 | 已实现 |
| GitHub Workspace 写入边界 | 已实现 |
| GitHub → Commit → Verify | 开发中 |

## 当前代码事实

- V021Activity 是当前 Launcher。
- MainActivity 仍存在，且包含尚未迁移的历史功能，暂不能直接删除。
- BridgeFS 本地执行链真实存在。
- GitHub Workspace 边界已有基础实现。
- V021 已移除 API Profile 的“允许修改”UI/模型字段；历史持久化中的 `write` 字段仅作兼容读取，不再作为权限来源。
- Receipt 仍存在 executions / pending_receipt / messages / input 四处分散状态。
- V021 启动时尚未恢复 pending receipt。
- Workspace 与 Conversation 的基础数据结构和当前 UI 已分离，但兼容 facade 仍存在，尚未完成彻底迁移。
- 当前聊天页已有 Workspace / Conversation 操作入口，但尚未完成 APK 真机验证。
- AI 消息目前未按消息保存实际使用的 API 名称与头像；该问题已建立 Issue #30。
- 当前协作实现仍偏单轮协议链，尚未形成双 AI 连续协作循环。
- GitHub 实际写入尚未形成完整 GitHub → Verify 链。

此前关于“协作 API Key 明文写入 SharedPreferences”的判断已撤回；当前代码已有 SecretStore / Android Keystore 迁移逻辑。

## 当前施工优先级

1. 完成 Workspace / Conversation UI 真机验证，并继续清理兼容 facade。
2. 修复聊天消息 API 身份显示：消息保存实际使用的 API 名称与头像（Issue #30）。
3. 把工作区 / Conversation 权限真正接入 PermissionPolicy。
4. 把 API Profile 与 AI 成员、实际权限边界分离清楚。
5. 统一 Receipt 状态链。
6. 实现 Repository / Branch 施工锁。
7. 连接真实 GitHub 写入与 Verify。
8. 实现双 AI 连续协作循环。
9. 清理旧 MainActivity / 旧日志路径等历史实现。

## 2026-10-02 追加确认：API 页面与对话执行链

### API 页面
当前 V021 的「配置协作 API」仍使用旧版 **Decision AI / Worker** 双角色配置界面。

这不是当前正式架构。后续应改为：
- 两个 AI 成员 / API 资源可独立配置
- AI 身份与 API Profile 解耦
- 不再用 Decision AI / Worker 作为固定配置槽位
- 施工权由 Workspace + Repository + Branch 管理

### 对话 → BridgeFS
当前对话已经存在真实的自动执行链：

`用户消息 → API 对话 → AI 回复 → [bridgefs] 区块识别 → CommandParser → PermissionPolicy → BridgeFS 执行 → Receipt`

因此「AI 对话会触发 A-BridgeFS / BridgeFS 执行回执」属于**当前已存在的实现能力**，不是待设计功能。

当前缺口不是是否执行，而是执行链与新双 AI 协作模型尚未统一：
- Receipt 尚未统一进入对话消息时间线
- pending receipt 启动恢复尚未完成
- API Profile 的 write 开关尚未成为实际执行权限边界
- 双 AI 协作尚未接入同一套施工权 / Receipt / Verify 链
