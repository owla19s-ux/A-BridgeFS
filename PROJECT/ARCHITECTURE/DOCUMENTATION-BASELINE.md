# APS 文档基线与整理说明

更新时间：2026-10-07

## 一、当前产品定义

第一阶段统一采用：

> **APS = AI 与外部能力之间的连接器 + 调度分配器。**

项目页是 Project 的主要人机工作面；UI 中的项目、AI、状态、记录等主要用于人类查看和操作。

## 二、文档分层

| 层级 | 目录 | 当前作用 |
|---|---|---|
| 正式产品定义 | `PROJECT/SPEC/` | 定义当前产品行为 |
| 正式架构 | `PROJECT/ARCHITECTURE/` | 定义当前架构边界 |
| 正式 UI | `PROJECT/UI/` | 定义项目页等 UI |
| 状态 / 任务 / 专项 | `docs/` | 当前工作、审查、专项说明 |
| 施工现场 | `AI_WORKSPACE/` | AI 工作过程 |
| 历史资料 | `docs/bridgefs-assets/`、明确标记的历史文件 | 仅用于追溯 |

## 三、已统一的关键概念

### Space / Context / Connection

Space 是 Connection；Context 是 Space 内的工作上下文；Connection 是 APS 统一的外部能力抽象。

项目页只是当前对 Context 的一种用户界面表达，不是 APS 顶层架构对象。不要把任何页面直接设计成完整 Agent Runtime。

### Connection 与 Resource

第一阶段 Connection 类型：

```text
Connection
├── Space
├── AI
├── GitHub
├── Local
├── File
├── Device
├── API / Service
├── Plugin
└── 未来其他连接
```

典型关系：

- GitHub Connection → Repository / Branch / File Resource → GitHub Connector；
- Local Connection → Android 本地目录 / 文件 Resource → Local Connector。

Resource 是 Connection 实际指向或操作的外部资源。

Local 不删除；当前 Local 操作模块较弱，后续按 Local Connector 重构。

### Dispatcher

Dispatcher 是轻量调度分配层，不是完整 Agent Runtime。

第一阶段任务阶段固定为：**拆分 → 做 → 实际执行结果 → 审查**；审查不通过返回“做”。

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

## 五、当前需要继续审查的文档

以下类型的文档仍可能存在旧语义：

- GitHub Workspace / A-BridgeFS；
- BridgeFS 当前架构描述；
- 复杂 Dispatcher / Agent Runtime；
- 自动多 AI 讨论；
- 固定 AI 角色；
- 将项目页描述为执行引擎。

后续以本文件和正式 PROJECT 资料为基线逐项清理，不一次性为了“整齐”改动无关历史资料。

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
当前 PROJECT
 ↓
当前 docs
 ↓
历史资料
```

历史资料可以解释演进，但不能证明当前实现。
