# APS 产品需求与行为规范

更新时间：2026-10-07

> 本文件是 APS 第一阶段当前正式产品定义。状态必须区分：已确认、已设计未实现、开发中、已实现、已验证、待设计、废弃。

## 一、产品定位

APS 是运行在本地设备上的 **AI 外部能力连接与调度客户端**。

第一阶段核心：

> **连接器 + 调度分配器。**

APS 不以完整 Agent Runtime、复杂工作流引擎或自主执行平台为产品目标。

## 二、项目页

对象是 APS 的核心业务上下文；当前 UI 仍可使用“项目页”作为面向用户的页面名称，但页面不应反向限定底层对象只能用于软件开发。

项目页主要展示和操作：

- Project 基本信息；
- 对象及其 Resource；
- Default AI；
- Project Members；
- 项目主要对话；
- 请求 AI 协助；
- 当前任务、施工、Commit、Verify 等状态。

项目页本身主要承担人机交互和状态展示，不等于底层执行引擎。

## 三、对象与 Resource

第一阶段不再把一个对象限定为单一 Project Address。

一个 APS 对象可以关联多个 Resource，例如：

```
对象
├── Local Resource
├── GitHub Resource
├── Cloud / Other Resource
└── State
```

Resource 的访问方式由 Connector 决定：

```
Local → Local Connector
GitHub → GitHub Connector
Other → 对应 Connector
```

云端不是本地副本，本地也不是云端缓存。APS 不默认建立多端同步。

State 是对象持续工作的权威状态，可以存放在对象指定的任一可持久化 Resource 上，例如 GitHub 的 `.aps/` 或本地目录。

因此：

> **对象描述“正在持续维护的东西”，Resource 描述“它涉及的外部资源”，State 描述“当前做到哪里”。**

## 四、AI 与 API

| 对象 | 作用 |
|---|---|
| API Profile | AI API 连接资源 |
| Project Member | AI 在项目中的成员关系 |
| Default AI | 当前项目默认 Member |
| AI Connector | 实际连接 AI API 的能力边界 |

一个对象可以有多个 AI Member。

## 五、Dispatcher

Dispatcher 是轻量调度分配层。

```
请求
 ↓
Dispatcher
 ↓
Connector / AI Member
 ↓
结果
 ↓
返回或继续
```

Dispatcher 的职责是分配与协调，不默认承担：

- 完整 Agent Runtime；
- 无限循环；
- 复杂状态机；
- 大型 Workflow Engine；
- 自主规划系统。

## 六、多 AI 协作施工：当前只定义边界

多 AI 是 Project 的可选能力，不是默认运行模式。

当前确认：

```
项目页
 ↓
当前 AI
 ↓
请求 AI 协助
 ↓
其他 AI Member / 临时 AI
 ↓
协助结果
 ↓
当前 AI 继续
```

当前**尚未确定**：

1. 多 AI 如何领取 / 转交同一个施工任务；
2. 一个 AI 的上下文如何交给另一个 AI；
3. 代码修改权如何在多个 AI 之间转移；
4. 如何形成可靠的结果回执；
5. Dispatcher 需要保存哪些最小协作状态；
6. 多 AI 并行是否必要，以及如何避免同时修改同一 Repository / Branch。

因此这一部分标记为：

> **待设计，不以旧 Decision / Worker 模型替代。**

## 七、连续施工

单个 AI 获得施工权限后，可以连续完成：

```
理解 → 读取 → 修改 → Commit → Verify → 必要时继续修复
```

多 AI 协作的具体连续施工机制另行设计。

## 八、独立对话页

对话页与项目页独立。

主要用途：

- 单 AI 对话；
- 查询；
- 分析；
- 测试；
- 允许范围内读取外部资源。

对话页不会因为连接 API / GitHub 自动获得项目施工权限。

## 九、权限

读取与修改分开：

```
读取
 ↓
AI 分析

修改
 ↓
施工权限
 ↓
Commit
 ↓
Verify
```

ConstructionLock 只属于修改阶段的权限控制，不是普通读取前置条件。

## 十、状态分类

### 已确认

- APS = Connector + Dispatcher；
- Project 是核心对象；
- 项目页是 Project 的主要 UI 工作面；
- Local / GitHub 都是 Project Address；
- GitHub 项目使用远程 Repository；
- AI Member 与 API Profile 分离；
- 多 AI 是按需能力；
- 多 AI 施工协作机制尚未定型。

### 待设计

- 多 AI 施工任务交接；
- 上下文 / 工作状态共享；
- 回执与结果确认；
- 并行施工与冲突控制；
- Dispatcher 所需的最小协作状态。

### 明确废弃

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 默认双 AI 讨论循环；
- Workspace-centered 产品模型；
- BridgeFS 作为当前产品模块。

### 第一阶段不做

- 完整 Agent Runtime；
- 无限 Agent Loop；
- 复杂 Workflow Engine；
- 完整 Git 客户端；
- Secretary / Orchestrator 作为既定架构。


## 十一、对象 / Resource / State / Task / Receipt

第一阶段正式采用以下关系：

- **对象**：APS 持续管理的独立上下文，不限定具体业务类型；
- **Resource**：对象实际涉及的本地、GitHub、云端或其他资源；
- **State**：对象持续工作的权威状态；
- **Task**：可分配的施工单元；
- **Receipt**：AI / 工具提交的结果及确认依据。

Task 不等于 AI，AI 可以被替换；多 AI 交接以 Task 与 Receipt 为基本单位，而不是共享完整长上下文。

详细模型见 `PROJECT/ARCHITECTURE/APS-OBJECT-RESOURCE-MODEL.md`。
