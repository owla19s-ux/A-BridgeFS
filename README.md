# APS

APS 是运行在本地 Android 设备上的 **AI 外部能力连接器 + 调度分配器**。

第一阶段重点不是建立复杂 Agent Runtime，而是先把 **Connection、Space / Context、Resource、Dispatcher、Execution、GitHub / Verify** 这些基础能力收口，并通过真实代码与验证结果推进。

## 核心模型

### Connection

APS 使用统一 Connection 体系连接外部能力：

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

Space 属于 Connection 体系，但承担承载连接与资源的基础工作底座职责。

Connection 不等于 API，也不限定具体业务用途。

### Space / Address / Resource

```
Space
├── Address      ← 定位
├── Resource     ← 实际内容
└── 可组合能力
    ├── Connection
    ├── Task
    ├── Conversation
    ├── State
    ├── Records
    ├── Verification
    └── ...
```

**固定的是定位与实际内容；能力是可组合的。**

Address 回答“在哪里”，Resource 表示“实际有什么”。一个 Space / Context 可以存在多个 Address。

### Context

Context 是 Space 内的具体工作上下文。

Context 不固定承载 Task、Conversation、State、工作记录、验证等全部能力，而是根据使用场景组合所需能力。

“项目”可以是 Context 的一种使用方式或用户自定义名称，不是 APS 顶层固定业务模型。

一个 Context 可以使用多个 Connection，并按实际需要关联 Address、Resource、AI、Task 等能力。

## Dispatcher

Dispatcher 是 APS 的调度能力。

需要任务调度的 Context 才建立 Task。当前第一阶段采用：

```
用户任务
   ↓
【拆分】
   ↓
执行任务
   ↓
【做】
   ↓
实际执行结果
   ↓
【审查】
   ↓
通过 → 完成
   │
   └─ 不通过 → 【做】
```

AI 不采用固定 Decision AI / Worker AI 等角色。

Context 内的 AI Connection 可以分别获得 **拆分、做、审查** 三项独立能力权限。

Dispatcher 必须根据实际执行结果推进，不能把 AI 口头“完成”直接当作事实。

## GitHub / Verify

GitHub 是正式 Connection 类型之一，远程 Repository 是其 Resource 来源。

当前主线持续收口：

```
GitHub Connection
      ↓
Repository / Branch
      ↓
文件读取 / 修改
      ↓
Commit
      ↓
Actions / Verify
      ↓
实际验证结果
```

自动化流程不能仅凭“提交成功”或状态对象推断已经执行；必须确认实际 Workflow / Verify 结果。

## UI

第一阶段主要工作面：

- **项目页**：Space / Context 的主要人机工作面；
- **对话页**：独立 AI 对话、查询与测试，也可以关联 Context；
- **设置页**：API、GitHub 等连接与系统配置。

项目页中的项目、AI、状态、操作记录主要用于人类查看和操作。

## 第一阶段当前状态

当前主线正在继续收口：

- Space / Context 工作面；
- Context Conversation；
- 软件开发结构（按需能力）；
- Task / Dispatcher / Execution；
- GitHub / Commit / Verify；
- Local Connector；
- 云端测试与主链验证；
- 独立对话 / 设置。

当前尚未进入正式实现阶段的主要能力：

- 多 AI 协作施工协议；
- Context 工作状态共享的完整机制；
- Receipt / Evidence 正式协议；
- 并行 Task 与资源冲突控制；
- 更复杂的 Dispatcher 协作状态；
- 完整 Agent Runtime / Workflow Engine。

## 明确废弃

以下内容不属于当前产品模型：

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 默认双 AI 循环；
- Project / Workspace-centered 顶层模型；
- Project Address 作为顶层模型；
- BridgeFS 作为当前产品模块；
- Secretary / Orchestrator 作为既定架构。

代码中仍可能存在 Project、Project Address、Project Member 等历史命名；这些属于迁移兼容，不代表当前产品模型。

## 文档与事实依据

项目正式架构、UI 与规格以 `PROJECT/` 下的当前文档为准；状态、任务和仓库操作规则见 `docs/`。

当前事实优先级：

**代码 → Commit → Actions / Verify → APK / 真机结果 → 当前正式文档 → 历史资料。**

README 只保留产品定位、核心模型和当前边界；详细设计与施工记录不在 README 中重复维护。
