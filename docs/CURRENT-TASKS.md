# A-BridgeFS 当前任务总表（2026-10-04）

> 当前阶段先收口基础模型，再审查旧架构残留，不继续在旧 Collaboration 模型上叠加功能。

## 一、当前产品核心模型

A-BridgeFS 当前核心对象是 **Project（项目）**。

一个项目先明确三件核心事情：

```
Project
├─ Project Address（项目地址）
│  ├─ Local
│  ├─ GitHub
│  └─ future other storage
│
├─ Default AI / API（默认 AI / API）
│
└─ Conversation（项目对话）★
```

核心原则：

- 用户进入项目，主要目的就是查看项目情况、向项目提问、让默认 AI 工作。
- **Project Address 属于项目本身**，不是独立的“Workspace GitHub”或“Conversation GitHub”。
- GitHub 只是项目地址的一种类型；Local 同样是一等地址类型。
- **Default AI 是项目的主要 AI**，不是固定的 Decision AI / Worker AI。
- 普通项目问题默认由 Default AI 直接回答。
- 不因为一次普通提问自动启动多个 AI 的讨论。
- 其他 AI 只有在确有需要时，通过“请求 AI 协助”临时加入。
- 多项目、多 API 的秘书 / 管家 / 调度层属于未来能力，当前不进入 V0.1。

## 二、V0.1 当前施工目标

### P0 — Project / Address / Default AI 基础链

**状态：已确认 / 审查中**

```
Project
 ↓
Project Address
 ↓
Default AI / API
 ↓
Project Conversation
 ↓
读取 / 分析 / 工作
 ↓
完成
```

最终必须验证：

1. 项目可以创建、打开、切换；
2. 项目地址由项目自身持有；
3. 项目地址可以区分 Local / GitHub 等类型；
4. 项目可以绑定 Default AI / API；
5. 进入项目后默认使用该 AI；
6. 项目对话直接围绕当前项目工作；
7. GitHub 项目可以由默认 AI 读取真实仓库内容；
8. 不需要用户手工转发仓库内容；
9. 项目状态能够保存并恢复。

### P1 — 项目连续工作

**状态：已确认 / 待架构审查**

默认 AI 获得施工权限后，可以在一次任务中自行：

```
理解 → 读取 → 修改 → Commit → Verify → 必要时继续修复 → 完成
```

“继续”只用于暂停、阻塞或需要用户决策的情况，不应成为正常施工步骤。

### P2 — 请求 AI 协助

**状态：已确认 / 待重构**

协助不是固定的 AI A / AI B 模式，而是项目中的一次动作：

```
Default AI
 ↓
需要协助
 ↓
Request AI Assistance
 ↓
临时 AI 加入
 ↓
完成协助
 ↓
Default AI 继续
```

普通项目对话不得因为“存在多个 API”而自动进入多 AI 讨论。

## 三、当前重点：旧架构审查

**状态：当前施工重点**

修改代码前，逐项审查现有：

- Project / Workspace 模型；
- Conversation 模型；
- GitHub 配置与 GitHubConversationConfigStore；
- Repository / Branch 绑定；
- API Profile / Default AI 绑定；
- CollaborationCoordinator / CollaborationTransport；
- AI Members；
- ConstructionLock；
- Receipt / Log；
- MainActivity / legacy facade；
- 其他旧的 Decision / Worker / A / B 逻辑。

审查目标不是立即删除代码，而是给每项标记：

| 状态 | 含义 |
|---|---|
| 保留 | 符合 Project 模型，可继续使用 |
| 合并 | 功能正确，但应归入 Project |
| 重构 | 方向正确，但当前结构不符合新模型 |
| 废弃候选 | 属于旧架构，待确认后移除 |
| 已废弃 | 不再作为施工依据 |
| 待验证 | 需要代码 / UI / APK 证据 |

## 四、明确不再作为当前架构依据

- ❌ Decision AI / Worker AI 固定身份；
- ❌ 固定 AI A / AI B；
- ❌ 普通项目提问自动触发多 AI 讨论；
- ❌ CollaborationProtocol JSON 作为普通 AI 对话协议；
- ❌ Workspace GitHub 与 Conversation GitHub 长期分裂；
- ❌ 把 GitHub Repository / Branch 当成项目之外的独立业务核心；
- ❌ 用复杂 Project Dashboard 替代项目对话；
- ❌ 当前阶段建设秘书 / 管家 / 多项目调度系统。

## 五、当前页面方向

```
项目
│
├─ 项目地址
│   ├─ 本地
│   ├─ GitHub
│   └─ future
│
├─ 默认 AI
│
├─ 请求 AI 协助
│
└─ 对话 ★
```

**对话是项目页面主体。**

暂不要求最近 Commit、Verify 统计、任务 Dashboard、复杂文件树等内容成为首页核心。

## 六、GitHub 原则

GitHub 是 Project Address 的一种实现：

- GitHub 读取属于项目正常工作能力；
- GitHub 读取不需要 ConstructionLock；
- GitHub 修改 / Commit 才需要施工权限；
- GitHub Repository / Branch 信息最终由 Project Address 统一管理；
- 不再维护长期独立的“普通对话 GitHub 地址”和“Workspace GitHub 地址”两套业务概念。

## 七、总验收原则

**代码 → UI 入口 → 用户操作 → 状态保存 / 恢复 → 真实业务生效 → APK → 真机验证**

“代码存在”不等于“功能完成”。

尤其是 Project：

**必须真正做到进入项目 → 使用项目地址 → 使用默认 AI → 围绕项目对话 / 工作。**

## 八、后续架构方向（暂不施工）

未来多项目、多 API 并行后，再增加：

```
Secretary / Orchestrator
        │
 ┌──────┼──────┐
 ↓      ↓      ↓
项目 A 项目 B 项目 C
```

该层负责跨项目调度，而不是改变 Project 自身的核心模型。
