# A-BridgeFS 阶段工作日志

## 2026-10-02 — 双 AI 协作设计校正

### 已确认

本阶段不采用“固定 Decision AI + 固定 Worker AI”作为正式身份模型。

正式方向：

**两个 AI 共享读取能力；施工权限属于 Workspace + Repository + Branch，同一 Repository / Branch 同时最多一个 AI 持有修改权，施工权可以转移。**

Decision / Worker 可以作为任务阶段角色，但不是永久身份。

### 当前实验

A-BridgeFS 作为移动端 AI 协作工作台：

AI A / AI B
→ Workspace
→ GitHub / BridgeFS
→ Commit / Verify
→ Receipt

A-177 仍可作为真实施工实验项目，但它不是系统架构中的固定 Worker 身份。

### 当前已发现的问题

1. 当前代码仍存在旧的单轮协作链。
2. Worker / Decision 相关命名会让实现误以为两 AI 是固定角色。
3. 施工权尚未形成 Repository / Branch 级锁。
4. GitHub 实际写入与 Verify 仍未完全接通。
5. Receipt 仍分散在 executions / pending_receipt / messages / input。
6. Workspace 与 Conversation 数据模型仍需拆分。

### 下一阶段

先完成设计文档统一，再按统一架构施工。

不要先按旧 Decision AI / Worker 模型继续扩展代码。
