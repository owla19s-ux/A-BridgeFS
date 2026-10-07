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

Connection 不等于 API，也不限定具体业务用途。具体资源、权限、认证、能力和执行方式由对应 Connection / Connector 决定。

## 三、Space

Space 是 Connection 的一种，不是普通 UI 文件夹、项目目录或个人资料区。

Space 表示可以被 APS 连接、进入、组织和使用的环境。Space 可以来自手机本地、PC、其他设备、云端、NAS 或未来插件提供的其他空间。

Space 内可以按用户需要组织多个 Context。

## 四、Context

Context 是 APS 的内部语义 / 数据概念，表示一个可以独立承载状态、内容、任务、对话和工作的具体上下文。

Context 不限定为软件开发项目。“项目”只是 Context 的一种使用方式或用户自定义名称，不是 APS 顶层固定业务对象。

Context 可以使用一个或多个 Connection，也可以没有 AI Connection。

## 五、Resource 与 Connector

Resource 是 Connection 实际指向或操作的外部资源。

```
Connection
   ↓
Resource
   ↓
Connector
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

State 是 Context 持续工作所需的权威状态；Task 是可执行的工作单元；Receipt / Evidence 是实际结果及确认依据。

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
- Space 是 Connection；
- AI、GitHub、Local、File、Device、API / Service、Plugin 均属于 Connection 体系；
- Context 是独立工作上下文；
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

新设计、新代码和新文档应优先使用 Connection、Space、Context、Resource、Task、Receipt、Dispatcher 等当前概念。