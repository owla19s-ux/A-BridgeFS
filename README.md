# APS

APS 是面向 **AI 项目工作与真实执行环境** 的 Android 工作台。

当前阶段先把最基础的产品模型理清：**Project（项目）是核心对象**。普通「对话」是独立的 AI 对话工具，不与 Project 的工作模型混为一谈。

## 当前产品模型

```
APS
│
├─ 项目 ★核心
│   ├─ 项目基本信息
│   ├─ 项目地址
│   │   ├─ Local
│   │   ├─ GitHub
│   │   └─ future other storage
│   ├─ 默认 AI
│   ├─ AI 成员
│   ├─ 请求 AI 协助
│   └─ 对话 ★主要入口
│
├─ 对话 ★独立工具
│   └─ API / 读取 / 普通问答
│
└─ 配置
    ├─ API Profiles
    ├─ GitHub
    └─ 全局权限
```

### Project

Project 负责真正的项目工作。

核心关系：

```
Project
├─ id
├─ name
├─ address
├─ defaultMemberId
└─ members[]
      ├─ id
      ├─ name
      └─ apiProfileId
```

**Project Member 与 API Profile 是两个不同层级的对象。**

- API Profile：API 连接资源，例如 Base URL、API Key、Model。
- Project Member：项目中的 AI 成员关系。
- 一个项目可以有一个或多个 AI Member。
- Member 可以新增、移除、修改名称。
- Member 可以更换所使用的 API Profile。
- Default AI 可以随时更换。
- Project 本身可以创建、编辑、删除。

因此不再把 AI A / AI B 当成产品身份，也不把 API 永久绑定到 Project。

### Project Address

Project Address 是项目资源地址的统一概念。

GitHub 只是其中一种实现，Local 同样是一等地址类型，未来可以增加其他存储。

读取与修改严格分开：

- 读取项目资源：正常项目工作能力。
- 修改 / Commit：需要对应的施工权限。
- GitHub Repository / Branch 不再作为独立于 Project 的长期业务核心。

### Project Conversation

进入项目后，用户主要是：

**看项目 → 提问 → 让默认 AI 读取、分析、工作。**

普通项目问题由 Default AI 直接处理，不因为项目存在多个 AI Member 就自动启动多 AI 对话。

默认工作链：

```
用户
 ↓
当前项目
 ↓
项目地址
 ↓
默认 AI
 ↓
读取 / 分析 / 工作
 ↓
完成
```

默认 AI 在一次任务中可以连续推进：

```
理解 → 读取 → 修改 → Commit → Verify → 必要时继续修复 → 完成
```

只有真正需要用户决策、权限或资源时才暂停；“继续”不是正常施工步骤。

### 请求 AI 协助

多 AI 是**按需能力**，不是 Project 的默认运行模式。

```
默认 AI
 ↓
需要协助
 ↓
请求 AI 协助
 ↓
其他 Project Member / 临时 AI
 ↓
完成协助
 ↓
默认 AI 继续
```

不再使用固定 Decision AI / Worker AI，也不以固定 AI A / AI B 表达成员身份。

## 独立「对话」页

底部「对话」是独立工具，不是 Project 的第二套工作区。

它主要用于：

- 选择一个 API Profile；
- 与 AI 直接交流；
- 读取允许访问的外部资源；
- 进行普通问答、分析和测试。

默认定位是**读取型能力**。它不因为连接了 API 或 GitHub 就自动获得 Project 施工权限，也不会自动启动多 AI 协作。

因此当前「对话」页架构基本保留，后续主要进行 UI 优化。

## 权限原则

```
读取
 ↓
可以回答 / 分析

修改
 ↓
需要施工权限
 ↓
Commit
 ↓
Verify
```

ConstructionLock 属于修改阶段，不是普通读取的前置条件。

## 当前阶段

当前优先级不是继续扩展旧协作系统，而是：

1. 完成 Project 数据模型与 Project Address 统一；
2. 明确 Default AI / Project Member / API Profile 三者边界；
3. 把 Project Conversation 作为主要项目入口；
4. 把“请求 AI 协助”改造成按需动作；
5. 清理旧 Decision / Worker / A/B / Workspace GitHub / Conversation GitHub 等架构残留；
6. 普通「对话」页保持独立，主要做 UI 优化。

多项目、多 API 并行后的 Secretary / Orchestrator 属于未来能力，当前不施工。

## 构建与发布

```
main
 ↓
GitHub Actions
 ↓
正式 Release 签名构建
 ↓
Artifact
 ↓
latest Release
 ↓
APS.apk
```

当前尚无公开发行版本，因此内部验证构建统一使用正式签名身份，不再人为区分“测试签名”和“正式签名”。

## 项目资料

- `AGENTS.md`：施工、验证与 AI 协作规则
- `PROJECT/ARCHITECTURE/`：当前正式架构
- `PROJECT/SPEC/`：正式项目规格
- `PROJECT/UI/`：正式 UI 资料
- `PROJECT/STATUS/`：当前状态与审计快照
- `docs/CURRENT-TASKS.md`：当前任务与架构审查范围
- `docs/STATUS.md`：当前实现状态
- `docs/REQUIREMENTS.md`：当前需求与验收
- `docs/BUILD-PRECHECK.md`：构建前检查
- `docs/architecture/`：专项架构与历史资料

**代码事实、GitHub Commit / Actions / APK 验证结果优先于旧文档描述。**
