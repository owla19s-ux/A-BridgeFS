# A-BridgeFS 当前任务总表（2026-10-04）

> 本文用于给施工提供一个“现在到底先做什么”的单一阅读入口。
> Issue #29 继续作为长期任务索引；本文件负责把当前任务重新归集成可执行顺序。

## 一、当前产品原则

第一版只有：

**两个 AI + 用户 + 实际施工。**

同时：

- 普通对话也必须能够读取 GitHub；
- Workspace 对话也能够读取 GitHub；
- GitHub 读取不需要 ConstructionLock；
- GitHub 修改 / Commit 才需要施工权；
- AI A / AI B 不绑定固定 Decision / Worker 身份；
- 不继续扩大复杂 CollaborationProtocol；
- 功能必须真正从 UI 进入并可验证。

## 二、当前施工顺序

### P0 — 普通对话 GitHub 只读链
**状态：阻塞 / 待施工**

目标：

`普通对话 → API → GitHub 授权 → Repository / Branch → 文件读取 → AI 回答`

必须验证：

1. 普通对话不进入 Workspace 也能读取 GitHub；
2. API 请求能够获得真实 GitHub 文件内容；
3. AI 能根据真实仓库内容回答；
4. GitHub 关闭 / 授权失效时出现真实错误；
5. 读取不会获得修改权限。

**当前不做 GitHub 修改。**

### P1 — Workspace / Conversation UI 闭环
**状态：开发中 / 待真机验证**

逐项确认：

- Workspace 创建 / 切换；
- Conversation 创建 / 切换；
- API 选择；
- 消息发送 / 保存 / 恢复；
- API 名称与头像；
- GitHub 配置回显；
- 权限状态回显；
- Receipt 回显。

### P2 — 双 AI 自然协作
**状态：开发中**

目标：

`AI A ↔ AI B ↔ 用户`

不要恢复固定 Decision AI / Worker AI。

当前要清理：

- 固定 A → B 路由；
- 旧 Decision / Worker 提示词；
- 强制 AI 输出协议 JSON；
- 为 AI 自动补 id / ts 等协议字段的错误方向。

AI 正常讨论应该使用普通语言；App 自己维护内部状态。

### P3 — 用户选择施工 AI
**状态：已实现 / 待完整验证**

已经存在 ConstructionLock 和用户选择入口。

继续验证：

- 用户选择 AI A / AI B；
- 施工权正确持久化；
- 同一 Repository / Branch 同时只有一个 holder；
- 非 holder 不能修改。

### P4 — GitHub 修改 → Commit → Verify
**状态：开发中**

仅在普通 GitHub 读取链稳定后继续。

目标：

`施工 AI → ConstructionLock → updateFile → Commit → Actions → Verify`

### P5 — Receipt / 日志 / 历史整理
**状态：开发中**

包括：

- Receipt 状态统一；
- 日志分类与入口；
- MainActivity / legacy facade；
- 旧日志路径；
- 历史协议文件整理。

## 三、明确废弃 / 不再作为施工依据

以下旧任务描述不得继续指导新施工：

- 固定 Decision AI → Worker AI；
- “Decision AI 决定、Worker AI 永久施工”的身份模型；
- 要求 AI 普通聊天必须生成完整 CollaborationProtocol JSON；
- 为了修复 AI JSON 缺少 id 而继续给 AI 输出补系统字段；
- 先建设复杂 Agent 调度，再补基础链路。

## 四、任务状态定义

| 状态 | 含义 |
|---|---|
| 已确认 | 用户和架构已确认方向 |
| 开发中 | 代码正在施工 |
| 已实现 | 代码层已存在 |
| 待验证 | 已实现但没有完整 APK / 真机证据 |
| 已验证 | 完整链路已有真实验证证据 |
| 阻塞 | 当前问题不解决会影响后续链路 |
| 废弃 | 不再作为当前施工依据 |

## 五、总验收原则

任何功能都必须经过：

**代码 → UI 入口 → 用户操作 → 状态保存 / 恢复 → 真实业务生效 → APK → 真机验证**

尤其是 GitHub：

**显示“已连接”不等于 GitHub 已打通。**

必须先证明普通对话真的能读取仓库，再继续证明 AI 能修改仓库。
