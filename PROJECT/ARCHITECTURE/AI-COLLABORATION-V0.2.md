# 双 AI 协作协议 v0.2

状态：设计基线 / 已确认方向 / 施工链开发中

## 1. 核心原则

A-BridgeFS 采用两个 AI 协作模型，而不是固定的 Decision AI + Worker AI 身份模型。

两个 AI：
- 默认共享工作区允许范围内的读取能力
- 可以同时分析、沟通、检查结果
- 可以在不同任务中承担不同角色
- 同一 Repository / Branch 同时最多一个 AI 持有施工权
- 施工权可以授予、释放、转移

Decision AI / Worker AI 只能作为阶段角色标签，不是永久身份。

核心原则：

**读权限共享，施工权限独占且可转移。**

## 2. 协作角色

角色属于任务或阶段：

- PLANNER：目标拆解、方案分析
- BUILDER：当前持有施工权并负责修改
- REVIEWER：检查与验证
- OBSERVER：读取与分析

同一个 AI 可以在不同阶段承担不同角色。

## 3. 权限模型

权限属于 Workspace + Repository + Branch，而不是 API Profile。

读取能力可以同时授予两个 AI。

施工能力可细分为：
- read
- edit
- create
- delete
- run_test
- commit
- push
- create_pr
- merge

施工锁：

ConstructionLock(Workspace, Repository, Branch)

状态：
- FREE
- HELD_BY_AI_A
- HELD_BY_AI_B

建议锁覆盖：

申请 → 修改 → 测试 → Commit → Verify

完成、释放、授权撤销或任务阻塞后才能转移。

## 4. 协作消息

第一阶段消息类型：

PROPOSAL / QUESTION / DECISION / TASK / PROGRESS / REQUEST_WRITE / GRANT_WRITE / RELEASE_WRITE / FILE_CHANGE_REQUEST / COMMIT / VERIFY / BLOCKED / COMPLETE / ESCALATE

当前代码协议仍保留 `DECISION_AI / WORKER` 作为任务阶段路由；文件修改协议正在施工中，不能把 Worker 普通文本或普通 `PROGRESS` 直接视为写入请求。

消息协议不规定固定的 AI → AI 方向。

## 5. 典型流程

AI A / AI B 读取
→ 讨论 / 分析
→ 形成任务
→ 申请施工权
→ Workspace 授权检查
→ 取得 Repository / Branch 施工锁
→ 持有施工权的 AI 连续施工
→ 测试 / Commit / Verify
→ 释放或转移施工权

施工期间另一 AI 可以读取、分析、沟通、提出建议，但不能直接写入被锁定的 Repository / Branch。

## 6. 决策

决策不是某个固定 AI 的专属能力。

任一 AI 都可以提出 QUESTION / PROPOSAL。

形成协作结论后记录 DECISION。

两 AI 无法解决，或涉及用户授权时：

ESCALATE → Human

## 7. 连续施工

明确授权后，施工 AI 应能够在边界内连续完成：

调查 → 修改 → 测试 → Commit → Verify

普通实现问题不要求用户逐次输入“继续”。

只有改变已确认设计、超出任务范围、新增施工权限、高风险操作、外部阻塞、无法形成决定或需要用户授权时才暂停。

## 8. Verify

代码或仓库状态变化后：

Commit SHA → Actions / Check Run / Job → 真实结果

VERIFY 只报告事实，不代表自动批准。

## 9. 当前范围

第一阶段只支持两个协作 AI。

暂不做：
- 多于两个 AI
- 复杂 Agent 调度
- 永久角色绑定
- 全局单一施工锁
- 复杂投票
- 无限自动循环
- 绕过用户授权

## 10. 与其他模块关系

A-BridgeFS 负责 Workspace、AI 成员、API Profile、权限、施工锁、协作消息、状态与回执。

GitHub 负责代码和工程事实。

BridgeFS 负责 Android 本地真实执行。

API Profile 是连接资源，不等于施工权。

## 11. 当前实现差距

### 11.1 工作区权限落地进度

工作区已经开始承载本地文件修改权限：

- `BridgeProject.localFileModifyEnabled`：工作区级开关，已持久化。
- `BridgeProject.conversations`：工作区内独立 Conversation 列表，已持久化。
- `activeConversationId`：当前对话指针已持久化；旧扁平消息数据可自动迁移到默认 Conversation。
- V021 工作区页提供开关 UI。
- `PermissionPolicy.authorization(workspace, conversation)` 已读取 Conversation 覆盖与 Workspace 默认值。
- `FileBridgeService` 在真实本地执行入口再次使用有效授权，因此该权限已进入实际执行拦截；仍待新 APK / 真机验证。

目标模型为：

`effectiveLocalFileModify = chatOverride ?: workspace.localFileModifyEnabled`

### 11.2 GitHub 写入边界

GitHub 写入采用分层边界：

1. `GitHubWorkspaceService`：Workspace 级 GitHub 访问与写入边界。
2. `ConstructionLockStore`：Workspace + Repository + Branch 的独占施工权。
3. `GitHubApiClient`：GitHub HTTP / Contents API 实现。

当前已经具备 Workspace-scoped `updateFile()`，实际写入入口要求调用者提供 AI Member，并通过 ConstructionLock 检查该 AI 是否持有当前 Repository / Branch 的施工权。

`CollaborationCoordinator.requestConstruction()` 已能把持久化任务推进到 `CONSTRUCTING` 并取得施工锁；`updateFile()` 已能完成真实 Contents API 更新并保存 Commit SHA。

当前 ConstructionLock 已具备：
- acquire：申请 / 持有
- release：释放
- transfer：转移
- requireHolder：实际写入前检查

协作任务状态已开始持久化：
- CollaborationTaskStore 保存 Workspace + Conversation + Task 状态。
- 任务只有显式 requestConstruction() 才进入施工阶段，不会因普通协作分析自动抢占施工锁。
- 施工任务通过 GitHubWorkspaceService.updateFile() 进入真实 Contents API 写入边界。
- Contents API 返回的 Commit SHA 会保存到任务状态，并将任务推进到 WAITING_VERIFY。

Verify 实链第一版已经接入：
- 以任务保存的 Commit SHA 查询 GitHub Actions。
- 只接受 head_sha 与任务 Commit SHA 完全一致的 Run。
- queued / in_progress 等状态保持 WAITING_VERIFY。
- completed + success 才进入 COMPLETE，并释放 ConstructionLock。
- completed + 非 success 进入 FAILED，保留施工锁以允许继续修复。

尚未完成：
- 新 Actions Run / APK / 真机验证。
- 更细的 Job / Step 结果汇总与 UI 展示。

### 11.3 下一阶段

继续完成：
- Worker 输出 → FILE_CHANGE_REQUEST → `updateFile()` 的安全协议接线
- AI 自主施工 → Commit 的完整运行链
- 新 Actions / APK / 真机实证
- Receipt 与协作消息时间线统一
- 双 AI 连续协作循环
- 施工失败后的恢复 / 转移策略

已完成基础设施但仍需真实验证：
- Workspace / Conversation 数据拆分与权限执行拦截
- AI 成员与 API Profile 解耦
- Repository / Branch ConstructionLock
- GitHub 写入 → Commit → Actions / Verify 基础链

当前最关键断点：`CollaborationCoordinator.updateFile()` 已可真实写入，但 `runObjective()` 尚未把 Worker 的文件修改意图映射为该调用；因此“AI 自主施工 → Commit”仍不可达。

旧 Decision AI ↔ Worker 文档仅作为历史参考，不再作为当前正式身份模型。
