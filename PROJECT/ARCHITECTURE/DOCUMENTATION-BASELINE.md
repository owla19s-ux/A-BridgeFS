# APS 文档基线与整理说明

更新时间：2026-10-08

## 一、当前产品定义

第一阶段统一采用：

> **APS = AI 与外部能力之间的连接器 + 调度分配器。**

当前正式 UI 定义优先作为语义基准：

> **Space 是 APS 中负责组织目录与 Context 的基础工作空间。**

**Space 负责组织；Context 负责项目；Address 负责定位；Resource 负责实际内容；能力按 Context 组合。**

## 二、文档分层

| 层级 | 目录 | 当前作用 |
|---|---|---|
| 正式产品定义 | `PROJECT/SPEC/` | 定义当前产品行为 |
| 正式架构 | `PROJECT/ARCHITECTURE/` | 定义当前架构边界 |
| 正式 UI | `PROJECT/UI/` | 定义当前 UI 与核心语义 |
| 状态 / 任务 / 专项 | `docs/` | 当前工作、审查、专项说明 |
| 施工现场 | `AI_WORKSPACE/` | AI 工作过程 |
| 历史资料 | `docs/bridgefs-assets/`、明确标记的历史文件 | 仅用于追溯 |

## 三、已统一的关键概念

### Space / Address / Resource / Context

Space 属于 Connection 体系，但主要承担目录与 Context 的组织职责，不是普通 Connection 的简单同类。

Space 的固定基础：

```
Space
├── 名称
└── 目录
    ├── Context（项目）
    │   ├── 名称
    │   ├── Address[]（名称 / 地址 / 备注）
    │   └── 可组合能力
    └── ...
    ├── Connection
    ├── AI
    ├── Task
    ├── Conversation
    ├── Device
    ├── Plugin
    └── ...
```

**Space 负责组织；Context 负责项目；Address 负责定位；Resource 负责实际内容；能力按 Context 组合。**

Address 回答“在哪里”，Resource 表示“实际有什么”。一个 Context 可以存在多个 Address；每个 Address 至少包含名称、地址、备注。

Context 是 Space 内的具体工作上下文，不固定承载 Task、Conversation、State、内容或工作记录的全部组合，而是按场景组合能力。

### Connection 与 Connector

第一阶段 Connection 类型：

```
Connection
├── Space      ← 特殊：连接与资源底座
├── AI
├── GitHub
├── Local
├── File
├── Device
├── API / Service
├── Plugin
└── 未来其他连接
```

Connection 提供连接、访问、调用或管理能力；Connector 是具体实现边界。

### Dispatcher

Dispatcher 是轻量调度分配层，不是完整 Agent Runtime。

第一阶段任务阶段固定为：**拆分 → 做 → 实际执行结果 → 审查**；审查不通过返回“做”。

Task 是按需建立的工作交接单位，不是所有 Context 的固定组成部分。

### 多 AI

一个 Context 可以使用多个 AI Connection。

当前已经确定 AI 的拆分 / 做 / 审查权限和三阶段任务边界；具体任务交接、状态共享、并行施工和冲突处理仍待设计。

因此相关文档必须使用“待设计 / 开放问题”表述，不能用旧 Decision / Worker、Secretary / Orchestrator 或复杂 Agent Runtime 代替结论。

## 四、旧内容处理原则

### BridgeFS

BridgeFS 已不属于当前 APS 产品。

`docs/bridgefs-assets/` 保留为历史资料，不应继续作为当前架构依据。

代码中的旧 BridgeFS 命名属于遗留实现；本地能力如果继续存在，统一向 Local Connector 演进。

### Workspace / Project 历史模型

GitHub 不再建立独立 Workspace 或 Project-centered 业务模型。Project Address 也不再作为当前顶层抽象。

### Decision / Worker / AI A / AI B

均为旧模型，不作为当前产品角色。

## 五、文档同步原则

UI 文档当前是语义起点。其他正式文档、架构计划、README 和状态文档必须与 UI 已确认定义一致。

反查时优先检查：
- Space 是否仍被定义成普通 Connection；
- Address 是否缺失；
- Resource 是否仍被写成 Connection 的附属目标而没有固定基础语义；
- Context 是否被写成固定包含 Task / Conversation / State / Records；
- Task 是否被误写成所有 Context 的必需组成；
- 是否重新引入 Project Address / Workspace-centered 模型。

不一次性为了“整齐”改动无关历史资料。

## 六、文档事实优先级

```
代码实现
 ↓
Commit
 ↓
Actions / Verify
 ↓
APK / 真机
 ↓
当前 PROJECT / UI 语义定义
 ↓
当前 PROJECT 其他正式文档
 ↓
当前 docs
 ↓
历史资料
```

UI 负责确认当前产品语义；代码与实际验证负责确认当前实现状态。历史资料可以解释演进，但不能证明当前实现。
