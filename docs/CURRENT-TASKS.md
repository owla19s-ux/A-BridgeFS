# A-BridgeFS 当前任务总表

更新时间：2026-10-04

> 本轮目标：按已确认的 Project 模型与 Project UI 收口。普通「对话」不承担 Project 工作职责。

## 一、当前架构基线

### Project UI 已确认

```text
项目
├─ 项目主要对话 ★
├─ ↑ / ↓ 屏幕显示切换
└─ 项目配置
    └─ 独立点击展开 / 收起
```

↑ / ↓ 只改变屏幕显示区域，不控制项目配置展开状态。

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

### 已废弃 / 已拆除

- 固定 AI A / AI B 默认成员；
- Decision AI / Worker AI；
- CollaborationProtocol / CollaborationTransport / CollaborationTaskStore / CollaborationVerifyService；
- 普通对话独立 GitHub 地址绑定；
- Workspace GitHub 与 Conversation GitHub 两套长期业务概念；
- 普通项目提问自动多 AI 讨论；
- 复杂 Project Dashboard。

### 暂不施工

- Secretary / Orchestrator；
- 多项目统一调度；
- 更复杂的 Agent 自动规划系统。

## 七、当前施工顺序（2026-10-04 重建）

> 旧 P0 / P1 / P2 任务链停止作为施工依据。本轮以最新 Project UI 为唯一入口重新排链。
> 普通「对话」页与「配置」页暂缓，不阻塞 Project 主线。

### T0 — 架构基线与旧链清理
**状态：基本完成 / 持续审查**
- Project 作为核心对象；
- Project Address；
- Default AI / Project Member / API Profile 边界；
- 固定 Decision / Worker / A / B 已废弃；
- Request AI Assistance 改为按需动作；
- ConstructionLock 保留为 Project 施工权限基础；
- 旧 Collaboration 运行链已拆除；
- 文档已开始按新模型收口。

### T1 — Project 主页面收口
**当前施工重点**

目标：让代码真正符合已经确认的 Project UI，而不是继续在旧 Workspace 页面上叠功能。

正式结构：
项目 → 项目主要对话 ★ → ↑ / ↓ 屏幕显示切换 → 项目配置（独立展开 / 收起）

重点：
1. 移除 Project 页面中的旧 Workspace 页面结构；
2. Project Conversation 成为首页主要内容，而不是独立的第二级“工作区对话页”；
3. 当前 Project 的切换 / 新建 / 重命名保留，但改成 Project 语义；
4. ↑ / ↓ 与配置展开状态完全解耦；
5. Project 配置折叠后必须让主要对话获得空间；
6. 不在首页堆 GitHub / API / 权限 / Verify 等内部卡片。

### T2 — Project 配置闭环
**依赖 T1**
- Project 名称；
- Project Address（Local / GitHub）；
- Default AI；
- Project Members；
- Member → API Profile；
- 必要的本地修改权限。
验收重点：UI 修改 → 保存 → 离开 Project → 重新进入 → 状态保持。

### T3 — Project Conversation 闭环
**依赖 T1 / T2**
正常项目提问必须形成：Project → Project Address → Default AI → 读取 / 分析 → 回答
- 一个正常问题只走 Default AI；
- 不自动召唤其他 Member；
- 不恢复 Decision / Worker；
- 消息、API 身份、历史记录正常保存与恢复。

### T4 — Project 连续施工
**依赖 T3**
理解 → 读取 → 修改 → ConstructionLock → Commit → Verify → 必要时自动继续修复 → 完成
暂停条件只包括完成、用户决策、权限 / 资源问题或达到明确的迭代边界。

### T5 — Request AI Assistance
**依赖 T3 / T4**
- Default AI 主导；
- 明确请求后才加入其他 Project Member / 临时 AI；
- 协助完成后回到 Default AI；
- 不形成固定 AI A / B 状态机。

### T6 — 独立「对话」与「配置」
**暂缓**
本轮不作为 Project 主线阻塞项。后续只做必要 UI / 可用性修正，不重新设计底层架构。

### T7 — 全链路验证
**依赖 T1～T5**
代码 → UI → 用户操作 → 状态保存 / 恢复 → 真实行为 → 正式签名 APK → 真机
每项 Verify 必须记录类型、编号、Run ID、相关 Commit；没有真实验证不得标记“已验证”。

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


## 2026-10-04 拆除结果

旧的双 AI 协作运行链已从当前代码路径拆除：不再存在固定 AI A / AI B 对话入口、Decision / Worker 协作入口或旧 CollaborationTransport 任务链。`ConstructionLock` 保留，作为未来 Project 连续施工的权限基础，而不是旧协作模式的入口。
