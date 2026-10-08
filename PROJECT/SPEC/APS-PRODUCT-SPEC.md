# APS 产品需求与行为规范

更新时间：2026-10-07
> 本文件是 APS 第一阶段当前正式产品定义。状态必须区分：已确认、已设计未实现、开发中、已实现、已验证、待设计、废弃。

## 一、产品定位

APS 是运行在本地 Android 设备上的 **AI 外部能力连接与调度客户端**。

第一阶段核心：
> **Connection + Dispatcher。**

APS 负责把外部能力以统一 Connection 形式接入，并根据 Context、任务、权限和实际结果进行调度。

## 二、Connection

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

Connection 不等于 API，也不限定具体业务用途。具体资源、权限、认证、能力和执行方式由对应 Connection / Connector 决定。

## 三、Space

Space 是 APS 中承载连接与资源的基础工作底座。

Space 属于 Connection 体系，但不是普通 Connection 的简单同类。它的固定基础是 Address 与 Resource；其他能力按使用场景组合。

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

Address 至少包含名称、地址、备注。一个 Space / Context 可以存在多个 Address。

Space 不是普通 UI 文件夹、项目目录或个人资料区。

## 四、Context

Context 是 APS 的内部语义 / 数据概念，表示 Space 内的具体工作上下文。

Context 不固定承载 Task、Conversation、State、内容或工作记录的全部组合；它根据具体场景组合所需能力。

Context 可以使用一个或多个 Connection，也可以没有 AI Connection。Task 也不是所有 Context 的必需组成部分。

## 五、软件开发结构

“软件开发结构”是软件开发类 Context 的一种可组合能力，用于把产品设计、功能、架构、施工和验证连接到同一结构模型中。

它不是新的顶层对象，也不替代 Context、Task 或 Resource。

```text
软件开发结构
├── 目标
├── UI / 交互
├── 功能
├── 架构
├── 施工点
├── 施工卡
├── 代码 / 产物
└── 验证
```

核心关系为：

```text
UI / 目标 → 功能 → 架构 → 施工点 → 施工卡 → 代码 / 产物 → 验证
```

关系允许一对多或多对多。施工卡是具体施工交接单位，可以关联一个或多个代码 / 产物；验证负责记录实际结果和证据。

UI / 交互还必须保留独立的**用户确认状态**：AI / APS 可以提出界面方案，但不得把 AI 输出直接视为用户确认。至少区分“待用户确认、已确认、已变更 / 待重新确认”。该状态与代码 Verify 独立；第一阶段可以暂不实现完整确认流程，但数据模型和 UI 入口必须预留。

软件开发结构不是文件目录或 Git 分支结构，而是描述软件从设计到施工再到验证的语义关系。

软件开发 Context 可以组合软件开发结构、Resource / Connection、AI、Task / Dispatcher、State 和 Receipt / Evidence / Verification。普通 Context 不要求具有软件开发结构。

## 五、Resource 与 Connector

Resource 是 APS 实际可使用、读取或操作的内容；Address 用于定位 Resource，两者不等同。

Connection 提供访问、调用或管理能力，Connector 将具体 Connection 落到真实能力。

```
Address
   ↓
Resource
   ↓
Connection / Connector
   ↓
读取 / 修改 / 查询 / 执行
```

第一阶段至少包含 AI Connector、GitHub Connector、Local Connector；未来可为其他 Connection 建立对应 Connector。

## 六、Dispatcher

Dispatcher 是 APS 的核心调度能力。第一阶段正式任务模型：

```
用户任务
   ↓
【拆分】
   ↓
产生执行任务
   ↓
【做】
   ↓
实际执行结果
   ↓
【审查】
   ↓
通过 → 完成
   │
   └─ 不通过 → 返回【做】
```

- 拆分：理解任务、拆成可执行事项、确定执行顺序；
- 做：调用 Connection 并进行实际执行或修改；
- 审查：依据实际结果检查是否满足任务要求。

AI 是否能够承担某一阶段由 Context 内用户授予的权限决定，不由固定 AI 角色决定。

## 七、AI 与权限

AI 是 Connection，不是固定的 Decision AI、Worker AI、主 AI 或永久角色。

一个 Context 可以有多个 AI Connection / AI 成员。

第一阶段 Context 为 AI 提供三个独立能力权限：拆分、做、审查。

同一个 AI 可以拥有一个、多个或全部权限；多个 AI 也可以成为同一阶段的候选。

同一任务同一时间只能由一个具有“做”权限的 AI 进行实际修改。

## 八、对话页

对话页是独立 AI 使用入口，可以直接选择 / 切换 AI Connection，也可以不关联 Context。

对话页可以按授权调用其他 Connection；关联 Context 后遵守 Context 的资源与权限，但不会自动启动三阶段任务调度。

## 九、设置页

设置页负责全局配置，不承载具体 Context 工作过程。第一阶段一级设置：连接与服务、权限、验证、系统。

## 十、状态与事实

State 是 Context 持续工作时需要的权威状态；Task 是可执行的工作单元，但不是所有 Context 的固定组成部分；Receipt / Evidence 是实际结果及确认依据。

```
AI / Connection 返回
      ↓
实际执行结果
      ↓
审查 / 验证
      ↓
State 更新
```

“已完成”“已验证”等状态必须能够追溯到实际结果或证据。

## 十一、第一阶段边界

### 已确认
- APS = Connection + Dispatcher；
- Space 是承载连接与资源的基础工作底座，并属于 Connection 体系；
- Address 与 Resource 是 Space 的固定基础；
- AI、GitHub、Local、File、Device、API / Service、Plugin 均属于 Connection 体系；
- Context 是可组合能力的具体工作上下文；
- “项目”只是 Context 的一种使用方式；
- Dispatcher 使用“拆分 → 做 → 审查”；
- AI 不采用固定职责角色。

### 待设计
- 多 AI 具体交接协议；
- Context 状态共享的最小结构；
- Receipt / Evidence 正式协议；
- 并行 Task 隔离与冲突控制；
- Dispatcher 更复杂的协作状态；
- 未来 Connection 类型的具体实现。

### 明确废弃
- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 默认双 AI 讨论循环；
- Workspace-centered 产品模型；
- Project Address 作为顶层模型；
- BridgeFS 作为当前产品模块。

## 十二、兼容说明

当前代码中仍可能存在 Project、Project Address、Project Member、API Profile 等历史实现命名。这些属于迁移过程中的实现兼容，不代表当前产品语义重新回到 Project-centered 模型。

新设计、新代码和新文档应优先使用 Connection、Space、Context、Address、Resource、Task、Receipt、Dispatcher 等当前概念。