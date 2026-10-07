# APS

APS 是运行在本地 Android 设备上的 **AI 外部能力连接与调度客户端**。

第一阶段产品定位：

> **APS = Connection + Dispatcher。**

## Connection

第一阶段正式 Connection 类型：

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

Space、AI、GitHub、本地、文件、设备、API / Service、Plugin 都属于统一 Connection 体系，但 Space 是其中承载连接与资源的基础工作底座。

Connection 不等于 API，也不限定具体业务用途。

## Space / Address / Resource

Space 是 APS 中承载连接与资源的基础工作底座。

```
Space
├── Address      ← 定位
├── Resource     ← 实际内容
└── 可组合能力
    ├── Connection
    ├── AI
    ├── Task
    ├── Conversation
    ├── Device
    ├── Plugin
    └── ...
```

**固定的是定位与实际内容；能力是可组合的。**

Address 回答“在哪里”，Resource 表示“实际有什么”。一个 Space / Context 可以存在多个 Address；每个 Address 至少包含名称、地址、备注。

## Context

Context 是 Space 内的具体工作上下文，不固定承载 Task、对话、State、工作记录等全部能力，而是根据使用场景组合所需能力。

“项目”只是 Context 的一种使用方式或用户自定义名称，不是 APS 顶层固定业务对象。

## Resource / Connector

Connection 提供访问、调用或管理能力；Address 用于定位 Resource；Resource 是实际内容；Connector 是具体 Connection 的实现边界。

```
Address
   ↓
Resource
   ↓
Connection / Connector
   ↓
实际读取 / 修改 / 调用
```

第一阶段至少明确 AI Connector、GitHub Connector、Local Connector。未来 File、Device、API / Service、Plugin 等 Connection 可以建立对应 Connector。

## Dispatcher

Dispatcher 是核心调度能力。需要任务调度的 Context 才建立 Task。第一阶段任务链：

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

AI 不采用固定 Decision AI / Worker AI / 主 AI 等角色。

Context 内 AI 可以分别获得拆分、做、审查三个独立权限。

Dispatcher 必须根据实际执行结果推进，不把 AI 口头“完成”直接当作事实。

## 对话页

对话页是独立 AI 使用入口，可以选择 / 切换 AI Connection；可以不关联 Context；可以按授权调用其他 Connection；关联 Context 后遵守 Context 权限。

## 设置页

设置页采用点击展开 / 收起：连接与服务、权限、验证、系统。

## 第一阶段边界

### 已确定

- APS = Connection + Dispatcher；
- Space 是承载连接与资源的基础工作底座，并属于 Connection 体系；
- Address 与 Resource 是 Space 的固定基础；
- Context 是可组合能力的具体工作上下文；
- AI、GitHub、Local、File、Device、API / Service、Plugin 属于统一 Connection 体系；
- Connector 是具体 Connection 的实现边界；
- Dispatcher 使用“拆分 → 做 → 审查”；
- AI 无固定职责角色。

### 待设计

- 多 AI 任务交接；
- Context 工作状态共享；
- Receipt / Evidence 正式协议；
- 并行 Task 与资源冲突控制；
- Dispatcher 更复杂的协作状态；
- 更多 Connection 类型的具体实现。

### 明确废弃

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 默认双 AI 循环；
- Project / Workspace-centered 顶层模型；
- Project Address 作为顶层模型；
- BridgeFS 作为当前产品模块。

代码中仍可能存在 Project、Project Address、Project Member 等历史命名；这些属于迁移兼容，不代表当前产品模型。
