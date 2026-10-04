# A-BridgeFS 当前任务总表

更新时间：2026-10-04

> 本轮目标：先把 Project 基础模型理清，再施工。普通「对话」不再承担 Project 工作区职责。

## 一、当前架构基线

### Project 是核心

```
项目
│
├─ 项目基本信息
├─ 项目地址
│   ├─ 本地
│   ├─ GitHub
│   └─ future other storage
├─ 默认 AI
├─ AI 成员
├─ 请求 AI 协助
└─ 对话 ★
```

进入项目后的主要行为是：

**查看项目 → 提问 → 默认 AI 读取 / 分析 / 工作。**

### 独立「对话」是额外工具

```
对话
├─ 选择 API
├─ 普通问答
├─ 读取 / 分析
└─ 不默认拥有项目施工权
```

它与 Project 独立，目前不需要大改架构，主要优化 UI。

## 二、Project 成员模型

当前目标模型：

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

### API Profile

API Profile 是连接资源：

```
API Profile
├─ Base URL
├─ API Key
├─ Model
└─ ...
```

### Project Member

Project Member 是项目关系：

- 可以添加；
- 可以删除；
- 可以修改名称；
- 可以更换 API Profile；
- 可以成为 Default AI。

**不要再把 AI A / AI B 当作成员模型。**

## 三、Project 工作链

正常：

```
用户
 ↓
Project
 ↓
Project Address
 ↓
Default AI
 ↓
读取 / 分析 / 工作
 ↓
完成
```

连续施工：

```
理解
 ↓
读取
 ↓
修改
 ↓
Commit
 ↓
Verify
 ↓
必要时继续修复
 ↓
完成
```

“继续”不是正常步骤，只用于暂停、阻塞或需要用户决策的情况。

## 四、Request AI Assistance

这是 Project 中的一个动作：

```
Default AI
 ↓
需要协助
 ↓
请求 AI 协助
 ↓
其他 Project Member / 临时 AI
 ↓
协助完成
 ↓
Default AI 继续
```

不再设计成固定的 AI A / AI B 对话循环。

## 五、独立「对话」页

当前判断：

**架构基本没问题，主要做 UI 优化。**

保留：

- 独立 API 选择；
- 独立消息历史；
- 普通 AI 对话；
- 允许的只读资源访问。

不承担：

- Project 主工作流；
- Project Default AI；
- Project Member 协作状态；
- 自动多 AI 讨论；
- Project ConstructionLock；
- Project Commit / Verify。

## 六、旧架构审查范围

### 重构

- BridgeProject / Workspace 语义；
- Project Address；
- Default AI 数据绑定；
- Conversation 与 Project 的关系；
- ConstructionLock 的 Workspace → Project 语义；
- Project UI。

### 保留语义、重构实现

- `aiMembers` → Project Members；
- API Profile；
- Conversation；
- Receipt / Log；
- GitHub 读取；
- GitHub 修改 / Commit / Verify；
- PermissionPolicy。

### 废弃候选

- 固定 AI A / AI B 默认成员；
- Decision AI / Worker AI；
- CollaborationProtocol；
- 普通对话独立 GitHub 地址绑定；
- Workspace GitHub 与 Conversation GitHub 两套长期业务概念；
- 普通项目提问自动多 AI 讨论；
- 复杂 Project Dashboard。

### 暂不施工

- Secretary / Orchestrator；
- 多项目统一调度；
- 更复杂的 Agent 自动规划系统。

## 七、当前施工顺序

### P0 — Project 基础模型

1. 统一 Project Address；
2. 增加 / 迁移 Default Member；
3. 把 Project Members 从 A/B 默认值改为可管理集合；
4. Project 创建 / 编辑 / 删除；
5. Project Conversation 接入 Project；
6. 保存并恢复完整 Project 状态。

### P1 — Project 工作

1. Default AI 读取 Project Address；
2. Project Conversation 成为主要入口；
3. 连续施工链；
4. ConstructionLock 迁移到 Project 语义；
5. Commit / Verify 接入 Project 工作链。

### P2 — Request AI Assistance

1. Project 内增加明确入口；
2. 选择其他 Member / 临时 AI；
3. 协助完成后回到 Default AI；
4. 不形成固定 A/B 状态机。

### P3 — 独立「对话」UI

只做必要 UI 优化，不重新设计底层架构。

## 八、总验收

```
代码
→ UI
→ 用户操作
→ 状态保存 / 恢复
→ 真实行为
→ APK
→ 真机
```

每个功能都必须经过完整链路，不能以“代码存在”代替已完成。

## 九、未来

多项目、多 API 真正形成并行工作需求后，再考虑：

```
Secretary / Orchestrator
        │
 ┌──────┼──────┐
 ↓      ↓      ↓
项目 A 项目 B 项目 C
```

它属于未来调度层，不改变 Project 本身的核心模型。
