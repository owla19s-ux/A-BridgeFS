# APS

APS 是运行在本地设备上的 **AI 外部能力连接与调度客户端**。

第一阶段产品定位：

> **APS = 连接器 + 调度分配器。**

AI、GitHub、本地目录等外部能力通过 Connector 接入；Dispatcher 负责把请求分配给合适的能力。APS 的 UI 主要是给人看的**项目页、对话页、配置页和状态面板**，不是独立的 Agent Runtime。

## 当前产品模型

```
APS
├─ 项目页 ★核心
│  ├─ Project
│  ├─ Project Address
│  │  ├─ Local
│  │  └─ GitHub
│  ├─ Default AI
│  ├─ AI Members
│  ├─ 项目主要对话
│  ├─ 请求 AI 协助
│  └─ 状态 / 操作记录
├─ 对话页
│  └─ 独立 AI 对话、查询、测试
└─ 配置页
   ├─ API Profiles
   ├─ GitHub
   └─ 全局配置
```

## 项目页

**项目页是 APS 的主要人机工作面。**

它主要让用户查看和操作：

- 当前项目；
- 项目地址；
- 默认 AI / AI 成员；
- 项目主要对话；
- AI 协助；
- 施工、Commit、Verify 等结果。

项目页主要是**面板展示和操作入口**，真正的外部能力由 Connector 提供。

## Project Address

```
Project
├─ Local Project
│  └─ Local Connector → Android 本地目录
└─ GitHub Project
   └─ GitHub Connector → GitHub API → 远程 Repository
```

Local 与 GitHub 是两种并列的项目地址。

GitHub 项目不需要本地 Clone 作为业务前提；远程 Repository 是 GitHub 项目的资源来源。

## Connector

第一阶段至少明确：

- **AI Connector**：连接不同 AI API / Model；
- **GitHub Connector**：访问 Repository、Branch、文件、Commit、Actions 等；
- **Local Connector**：访问 Android 本地项目目录。

Connector 是能力边界，不负责把 APS 变成大型执行平台。

## Dispatcher

Dispatcher 是轻量的**调度分配层**：

```
请求
 ↓
Dispatcher
 ↓
选择 / 分配 Connector 或 AI Member
 ↓
获得结果
 ↓
返回 / 继续下一步
```

第一阶段不把 Dispatcher 扩展成完整 Agent Runtime、复杂 Workflow Engine、无限 Agent Loop 或大型事件状态机。

## 多 AI 协作施工

一个 Project 可以拥有多个 AI Member。

当前已经确认：

- 多 AI 不是普通项目问题的默认运行模式；
- 当前 AI 可以按需请求其他 AI 协助；
- AI Member 不采用固定 AI A / AI B 身份；
- 多 AI 协作的**具体施工调度方式尚未最终确定**。

尚未解决的核心问题包括：

- 多个 AI 如何分工和交接；
- 上下文如何共享；
- 如何确认前一个 AI 的结果；
- 施工权如何交接；
- 多 AI 并行如何避免冲突；
- Dispatcher 需要保存哪些最小协作状态。

当前只定义边界：

```
项目页
 ↓
当前 AI
 ↓
请求 AI 协助
 ↓
其他 AI Member / 临时 AI
 ↓
返回协助结果
 ↓
当前 AI 继续
```

**不要在未确定前引入固定 Decision AI / Worker AI、Secretary / Orchestrator 或复杂 Agent Runtime 作为既定实现。**

## 当前第一阶段边界

### 已确定

- APS 是连接器 + 调度分配器；
- 项目页是核心 UI 工作面；
- Project 可以使用 Local 或 GitHub 地址；
- GitHub 项目以远程 Repository 为资源来源；
- AI、GitHub、本地目录分别通过 Connector 接入；
- Project Member 与 API Profile 分离；
- Default AI 是当前项目默认 AI Member；
- 多 AI 协助是按需能力；
- 多 AI 协作施工的具体调度机制暂未定型。

### 待设计

- 多 AI 施工任务交接；
- 上下文 / 工作状态共享；
- 回执与结果确认；
- 并行施工与冲突控制；
- Dispatcher 所需的最小协作状态。

### 明确不作为第一阶段目标

- 完整 Agent Runtime；
- 无限 Agent Loop；
- 复杂 Workflow Engine；
- 固定 Decision / Worker；
- 固定 AI A / AI B；
- 把 GitHub 做成独立 Workspace；
- 把项目页做成复杂 Dashboard。

## 文档优先级

- `PROJECT/SPEC/`：当前产品定义；
- `PROJECT/ARCHITECTURE/`：当前架构边界；
- `PROJECT/UI/`：当前 UI；
- `docs/`：状态、任务、专项、过渡和历史资料；
- `docs/bridgefs-assets/`：历史 BridgeFS 资料，不属于当前产品架构。

代码、Commit、Actions / Verify、APK 与真机结果优先于文档。
