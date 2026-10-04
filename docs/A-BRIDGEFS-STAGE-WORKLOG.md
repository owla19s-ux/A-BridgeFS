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


## 2026-10-04 — 协作架构术语再次校正

### 已确认

上一轮施工记录中仍使用了“Decision AI → Worker AI → Verify → Decision AI”的固定循环描述。该描述属于旧实现模型，**不再代表当前正式架构**。

当前正式模型：

```text
AI A / AI B
  ↓
共享读取 / 分析 / 沟通
  ↓
形成任务与阶段角色
  ↓
当前需要施工的一方取得 ConstructionLock
  ↓
BUILDER：调查 → 修改 → 测试 → Commit
  ↓
WAITING_VERIFY
  ↓
GitHub Actions / Check Run
  ↓
真实 Verify
  ↓
释放 / 转移施工权
  ↓
AI A / AI B 继续协作
```

### 术语边界

- 不存在固定的 Decision AI。
- 不存在固定的 Worker AI。
- PLANNER / BUILDER / REVIEWER / OBSERVER 是任务阶段角色。
- 同一个 AI 可以在不同阶段承担不同角色。
- `ConstructionLock` 决定当前哪个 AI 可以对指定 Workspace + Repository + Branch 施工。
- `COMMIT → VERIFY`、Verify 后释放/转移施工权等底层机制继续保留。

### 当前代码迁移状态

代码中仍可发现 `DECISION_AI / WORKER`、旧提示词及固定路由方法；这些属于**旧实现残留 / 迁移对象**，不能继续作为新功能设计依据。

后续施工必须以 `PROJECT/ARCHITECTURE/AI-COLLABORATION-V0.2.md` 为正式架构基线，先统一协作状态与角色模型，再继续补齐连续施工链。


## 2026-10-04 — 普通对话 GitHub 读取能力重新定义

### 用户确认

GitHub 读取不能只属于 Workspace。

普通「对话」页也必须能够读取 GitHub，否则：

- 无法确认 API 与 GitHub 是否真正连通；
- 无法让普通 AI 根据真实仓库回答问题；
- 用户只能看到“已配置”，却无法验证“实际可读”。

### 当前正确分层

```text
普通对话
  ↓
API
  ↓
GitHub 全局访问 / 授权
  ↓
Repository / Branch
  ↓
文件读取
  ↓
AI
```

Workspace 对话也可以读取 Workspace 绑定的 GitHub 资源。

修改则是另一条链：

```text
施工 AI
  ↓
ConstructionLock
  ↓
GitHub updateFile
  ↓
Commit
```

本阶段先不碰后者。

### 当前任务

**任务 A：打通普通对话 GitHub 只读链。**

验收条件：普通对话能够读取真实 Repository / Branch / 文件，并让 AI 根据真实内容回答；关闭 GitHub 访问或授权失效时，能够得到真实错误状态。

状态：**开发中 / 阻塞后续 GitHub 施工链验证**。

### 任务文档整理原则

旧任务中出现的“Worker 文件修改结果 → updateFile()”“固定 Decision AI → Worker”不再作为当前第一优先级。它们保留为历史施工记录，不得覆盖当前双 AI + 普通对话 GitHub 读取的正式要求。
