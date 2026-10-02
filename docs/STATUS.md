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
| 工作区 | 开发中 | 当前仍需从 BridgeProject 继续演化 |
| 双 AI 协作协议 | 已确认设计 / 尚未完整实现 | 共享读取、施工权独占且可转移 |
| 协作消息 | 开发中 | 当前实现仍偏旧单轮协作链 |
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

## 当前代码事实

- V021Activity 是当前 Launcher。
- MainActivity 仍存在，且包含尚未迁移的历史功能，暂不能直接删除。
- BridgeFS 本地执行链真实存在。
- GitHub Workspace 边界已有基础实现。
- API Profile 的 write 字段目前尚未接入统一 PermissionPolicy。
- Receipt 仍存在 executions / pending_receipt / messages / input 四处分散状态。
- V021 启动时尚未恢复 pending receipt。
- Workspace 与 Conversation 仍未完全分离。
- 当前协作实现仍偏单轮协议链，尚未形成双 AI 连续协作循环。
- GitHub 实际写入尚未形成完整 GitHub → Verify 链。

此前关于“协作 API Key 明文写入 SharedPreferences”的判断已撤回；当前代码已有 SecretStore / Android Keystore 迁移逻辑。

## 当前施工优先级

1. 统一正式架构文档：双 AI、共享读取、Repository / Branch 施工权独占且可转移。
2. 统一 Receipt 状态链。
3. 把 API Profile 与实际权限边界分离清楚。
4. 逐步拆分 Workspace / Conversation 数据模型。
5. 实现 Repository / Branch 施工锁。
6. 连接真实 GitHub 写入与 Verify。
7. 实现双 AI 连续协作循环。
8. 清理旧 MainActivity / 旧日志路径等历史实现。

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
