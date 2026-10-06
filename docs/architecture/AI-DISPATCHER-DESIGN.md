# APS AI Dispatcher Design / APS AI 调度层设计

> Status / 状态：已设计未实现 / Designed, Not Implemented
>
> Type / 类型：External Architecture Study → APS Proposal
>
> Source / 研究对象：AiCode `jieapi/AiCode`
>
> Source Commit / 源码 Commit：`6c615e38b8c8d423a110e327d4be172ae6e0d030`
>
> Purpose / 目的：借鉴 AiCode Agent Runtime 的调度层设计，为 APS 建立项目级 AI Dispatcher（AI 调度器）的架构基础；不复制 AiCode 的本地 Linux/PRoot 执行环境。

---

## 1. 结论 / Conclusion

AiCode 最值得 APS 借鉴的不是 Android 本地 Coding Environment，而是它的 **Agent Runtime 调度模型 / Agent Runtime orchestration model**：

```
Action
  ↓
Reducer
  ↓
State + Side Effect
  ↓
Side Effect Execution
  ↓
Event / Result
  ↓
Action
  ↓
...
```

APS 应在此基础上形成自己的：

```
AI Proposal
    ↓
APS Dispatcher
    ↓
State / Policy / Dependency Check
    ↓
Execution
    ↓
Event / Result
    ↓
State Update
    ↓
Next Dispatch
```

**核心原则 / Core Principle：**

> AI 负责推理和提出行动；APS Dispatcher 负责项目级调度、状态、权限、依赖和执行顺序。

The AI reasons and proposes actions; the APS Dispatcher owns project-level orchestration, state, policy, dependencies, and execution order.

---

## 2. 为什么需要 Dispatcher / Why APS Needs a Dispatcher

APS 不是单 AI 聊天应用。

APS 需要协调：

- Multiple AI providers / 多个 AI Provider
- Multiple AI roles or agents / 多个 AI 节点
- GitHub repository / GitHub 仓库
- Repository branches / 分支
- GitHub Actions / CI
- Verify / 验证
- Tool calls / 工具调用
- Permission / 权限
- Recovery / 恢复

如果没有独立调度层，逻辑容易退化成：

```
AI → Tool → GitHub → AI → Tool → GitHub
```

这会让 AI 直接承担项目状态管理。

正确方向是：

```
                 APS Dispatcher
                       │
       ┌───────────────┼────────────────┐
       ↓               ↓                ↓
     AI Node        Tool Layer       GitHub
       │               │                │
       └───────────────┼────────────────┘
                       ↓
                 Actions / Verify
                       ↓
                    Events
                       ↓
                    State
                       ↓
                 Dispatcher
```

---

## 3. AiCode 已确认的调度结构 / Confirmed AiCode Structure

AiCode 的 `StatefulAgentWorkflow` 明确采用：

- `AgentSessionState`
- `AgentAction`
- `AgentSideEffect`
- `reduce(state, action)`
- `ArrayDeque<AgentAction>`
- Event Flow

其核心循环可以概括为：

```
InitRequest
    ↓
CallLlm
    ↓
LlmResponse
    ↓
PermissionEvaluated
    ↓
ExecuteToolBatch
    ↓
ToolBatchFinished
    ↓
CallLlm
    ↓
...
```

The important point is that external execution is not mixed directly into the state reducer.

---

## 4. APS Dispatcher 基本模型 / APS Dispatcher Model

APS 建议采用四层：

### 4.1 State / 状态

描述“现在项目处于什么状态”。

Possible state examples:

```
ProjectState
├── activeTask
├── taskStatus
├── currentBranch
├── activeAgents
├── pendingActions
├── runningTools
├── permissions
├── latestCommit
├── latestVerify
├── errors
└── recoveryPoint
```

状态不是 UI 状态。

State is runtime/project state, not merely UI state.

---

### 4.2 Action / 动作

描述“系统接下来需要处理什么”。

Examples:

```
StartTask
AssignAgent
CallAI
RequestTool
EvaluatePermission
ExecuteTool
CreateCommit
RunVerify
HandleVerifyResult
RecoverTask
CancelTask
CompleteTask
```

Action 不直接执行现实世界操作。

An Action represents intent; it does not itself perform external side effects.

---

### 4.3 Reducer / 状态转移

Reducer 根据：

```
Current State + Action
        ↓
New State + Side Effects
```

决定下一步。

Reducer should remain deterministic and should not directly perform network, GitHub, filesystem, or UI operations.

---

### 4.4 Side Effect / 执行副作用

Side Effect 才真正访问外部系统：

```
CallAI
GitHub API
GitHub Actions
Tool
Persistence
Permission UI
```

执行完成后产生 Event / Result，再重新进入 Dispatcher。

---

## 5. APS 调度闭环 / APS Dispatch Loop

建议最终形成：

```
┌──────────────────────┐
│     Current State    │
└──────────┬───────────┘
           ↓
        Action
           ↓
┌──────────────────────┐
│     Dispatcher       │
│                      │
│ State / Policy /     │
│ Dependency / Queue   │
└──────────┬───────────┘
           ↓
     Side Effect
           ↓
┌──────────────────────┐
│ AI / Tool / GitHub / │
│ Actions / Verify     │
└──────────┬───────────┘
           ↓
        Event
           ↓
┌──────────────────────┐
│    State Update      │
└──────────┬───────────┘
           │
           └────────→ Next Action
```

这是 APS 后续多 AI 协作的基础骨架。

---

## 6. Tool Batch / 工具批次

AiCode 的一个重要设计是：一次 LLM Response 可以包含多个独立 Tool Calls。

```
LLM
 ├── Tool A
 ├── Tool B
 └── Tool C
        ↓
   Permission
        ↓
   Parallel Run
        ↓
   Ordered Results
```

执行可以并行，但结果必须按原始 Tool Call 顺序重新组装。

### APS 借鉴原则 / APS Principle

未来多个独立 AI/Tool 操作也可以进入一个 Dispatch Batch：

```
Batch
├── inspect repository
├── inspect CI
├── inspect issue
└── inspect code
```

但 **并行不是默认规则**。

Dispatcher 必须先判断依赖：

```
A ──→ B
```

存在依赖时必须串行。

---

## 7. Permission / 权限

AiCode 把 Policy 和 User Approval 分开。

推荐 APS 同样采用：

```
Policy Engine
     ↓
┌────┼────┐
↓    ↓    ↓
ALLOW ASK DENY
```

### ALLOW / 允许

直接执行。

### ASK / 请求确认

交给用户确认。

### DENY / 拒绝

直接生成结构化失败结果，不应该让整个 Dispatcher 崩溃。

---

## 8. AccessPolicy 演进 / AccessPolicy Evolution

APS 当前已有访问开关和 AccessPolicy。

未来可以逐步演进成 capability-based policy：

```
GitHub
├── READ_REPOSITORY
├── WRITE_REPOSITORY
├── CREATE_BRANCH
├── CREATE_COMMIT
├── CREATE_PR
├── RUN_ACTIONS
└── READ_VERIFY
```

AI Provider 也可以有：

```
AI
├── CALL_MODEL
├── USE_TOOL
├── SPAWN_AGENT
└── ACCESS_PROJECT_STATE
```

但这些属于后续设计，不在当前架构重建阶段直接全部实现。

---

## 9. Multi-Agent / 多 AI

AiCode 的 Sub-Agent 是一等运行对象。

APS 可以借鉴其生命周期思想：

```
Main Agent
├── Agent A
├── Agent B
└── Agent C
```

每个 Agent 应具有：

- provider
- model
- capability
- allowed tools
- task
- state
- lifecycle
- result

### Recursion Control / 递归控制

AiCode 明确限制子 Agent 再继续创建子 Agent。

APS 也应避免无限：

```
A → B → C → D → ...
```

建议由 Dispatcher 控制最大深度和并发数量。

---

## 10. Event Bus / 事件机制

AiCode 使用事件流把 Runtime 与 UI 解耦。

APS 建议未来建立统一 Project Event：

```
TaskStarted
AIStarted
AICompleted
ToolStarted
ToolFinished
CommitCreated
ActionStarted
ActionFinished
VerifyStarted
VerifyFinished
AgentStarted
AgentFinished
TaskFailed
TaskCancelled
TaskCompleted
```

UI 只消费事件。

UI should observe runtime events rather than become the owner of runtime state.

---

## 11. Background Notification / 后台任务通知

AiCode 有一个值得直接借鉴的细节：

后台任务完成时，如果 Agent 正在运行，不立即额外唤醒 LLM。

而是：

```
Background Event
      ↓
Pending Notification Queue
      ↓
下一批 Tool Result
      ↓
一起送给 Agent
```

这样可以减少额外 LLM round trips。

APS 可以用于：

- GitHub Actions 完成
- Verify 完成
- 子 AI 完成
- PR 状态变化
- 外部 Tool 完成

---

## 12. Checkpoint / Recovery

AiCode 的 Checkpoint 是执行前恢复点。

APS 不应简单复制文件快照，而应结合 GitHub：

```
Task
 ↓
Execution
 ↓
Commit
 ↓
Verify
 ↓
Failure
 ↓
Recovery Decision
```

未来可以形成：

```
Task Snapshot
+
Commit SHA
+
Branch
+
Verify Run
+
Dispatcher State
```

从而实现可恢复任务。

---

## 13. Cancellation / 取消

AiCode 对“用户拒绝”和“系统失败”进行了区分。

APS 也必须保持：

```
CANCELLED ≠ FAILED
```

例如：

- 用户主动停止 → `CANCELLED`
- API 超时 → `FAILED`
- Verify 失败 → `VERIFY_FAILED`
- 权限拒绝 → `DENIED`
- 成功 → `COMPLETED`

否则后续 AI 很容易把用户主动停止误认为系统故障并自行重试。

---

## 14. Failure / Retry / 失败与重试

Dispatcher 应区分：

### Retryable / 可重试

例如：

- 网络暂时失败
- Provider 暂时不可用
- GitHub API 临时错误

### Non-Retryable / 不应重试

例如：

- 权限拒绝
- 参数错误
- 工具不存在
- 分支冲突需要人工决策
- Verify 明确失败且需要修改代码

因此：

```
Error
 ↓
Classify
 ↓
Retry / Repair / Ask / Stop
```

而不是所有 Error 都：

```
Error → Retry
```

---

## 15. APS 与 AiCode 的边界 / Architectural Boundary

### 借鉴 / Borrow

- Action Queue
- State Machine
- Reducer
- Side Effects
- Tool Batch
- Permission Policy
- Capability
- Event Bus
- Sub-Agent Lifecycle
- Notification Queue
- Cancellation Semantics
- Recovery Concept

### 不复制 / Do Not Copy

- Alpine
- PRoot
- Local Linux environment
- Local shell as primary execution infrastructure
- Local Git as primary project authority
- Local Android build as primary CI

APS 的执行基础仍然是：

```
Android
  ↓
AI / Dispatcher
  ↓
GitHub
  ↓
GitHub Actions
  ↓
Verify
```

---

## 16. APS 的目标形态 / Target Architecture

```
                         ┌───────────────┐
                         │      User     │
                         └───────┬───────┘
                                 ↓
                         ┌───────────────┐
                         │      APS      │
                         │     UI/API    │
                         └───────┬───────┘
                                 ↓
                    ┌────────────────────────┐
                    │   Project Dispatcher   │
                    │                        │
                    │ State / Action / Queue │
                    │ Policy / Dependency    │
                    │ Lifecycle / Recovery   │
                    └───────────┬────────────┘
                                │
             ┌──────────────────┼──────────────────┐
             ↓                  ↓                  ↓
        ┌─────────┐       ┌─────────┐       ┌─────────┐
        │ AI Node │       │ Tool    │       │ GitHub  │
        │ Runtime │       │ Layer   │       │ Layer   │
        └────┬────┘       └────┬────┘       └────┬────┘
             │                  │                  │
             └──────────────────┼──────────────────┘
                                ↓
                         GitHub Actions
                                ↓
                             Verify
                                ↓
                              Event
                                ↓
                           Dispatcher
```

---

## 17. Implementation Boundary / 实现边界

当前状态：

**已设计未实现 / Designed, Not Implemented**

本文件不要求当前立即建立完整 Dispatcher。

当前 architecture-rebuild 阶段优先：

1. 保持现有 APS 架构重建链路可构建。
2. 建立清晰的模块边界。
3. 不让 UI 直接承担项目调度职责。
4. 不让单个 AI 调用链承担全部项目状态。
5. 为后续 Dispatcher 保留独立入口。
6. 后续再按最小链路逐步实现。

建议第一版只实现：

```
Task
 ↓
Dispatcher
 ↓
AI Call
 ↓
Tool
 ↓
GitHub
 ↓
Result
 ↓
Dispatcher
 ↓
Next Action
```

不要一开始实现完整 Multi-Agent、复杂 Recovery、自动策略学习等高级能力。

---

## 18. Source Traceability / 来源追踪

本设计主要参考 AiCode：

```
Repository:
jieapi/AiCode

Commit:
6c615e38b8c8d423a110e327d4be172ae6e0d030

Primary source:
StatefulAgentWorkflow.kt

Related sources:
AgentWorkflow.kt
AgentNotificationCenter.kt
```

关键外部设计均应视为“研究结论”，不是 APS 已实现功能。

---

## 19. Status Labels / 状态标签

| Item | Status |
|---|---|
| AiCode 调度层研究 | 已确认 / Confirmed |
| Action + State + Side Effect 模型 | 已确认外部实现 / Confirmed External Pattern |
| APS Dispatcher | 已设计未实现 / Designed, Not Implemented |
| APS Capability Policy | 已设计未实现 / Designed, Not Implemented |
| APS Multi-Agent Runtime | 已设计未实现 / Designed, Not Implemented |
| APS Event Bus | 已设计未实现 / Designed, Not Implemented |
| APS Recovery Runtime | 已设计未实现 / Designed, Not Implemented |
| AiCode 本地 Linux/PRoot | 不采用 / Not Adopted |

---

## 20. Final Principle / 最终原则

**APS 不复制 AiCode。**

APS borrows the runtime orchestration principles from AiCode, but adapts them to a GitHub-centered project execution model.

核心关系：

```
AI = Reasoning / Proposal
Tool = Capability
Policy = Permission
Dispatcher = Orchestration
GitHub = Project Authority
Actions = Execution
Verify = Evidence
State = Recovery
```

最终目标不是让一个 AI “自己干完整个项目”，而是：

> **让 APS 能够管理多个 AI、工具和远程执行环境，让它们在明确状态和规则下协作，并且任何关键步骤都可以追踪、验证和恢复。**
