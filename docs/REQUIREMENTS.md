# A-BridgeFS 当前需求板

更新时间：2026-10-04

## A. 核心产品模型

### 1. Project 是核心对象

Project 是 A-BridgeFS 的主要工作单元。

一个 Project 至少包含：

- 基本信息
- Project Address
- Default AI
- Project Members
- Project Conversation
- Request AI Assistance

### 2. Project Address

Project Address 是统一的项目资源地址概念。

V0.1 至少支持：

- Local
- GitHub

未来可扩展其他存储。

**GitHub 不再与 Project 并列成为另一套工作区业务模型。**

### 3. Default AI / Project Member / API Profile

三者必须保持分层：

| 对象 | 作用 |
|---|---|
| API Profile | API 连接资源：Base URL、Key、Model 等 |
| Project Member | AI 在项目中的成员关系，可管理 |
| Default AI | 当前 Project 默认使用的 Member |

要求：

- Project 可以拥有一个或多个 Member；
- Member 可以新增、移除、修改；
- Member 可以更换 API Profile；
- Default AI 可以更换；
- API Profile 不直接等同于 Project Member；
- 不使用固定 AI A / AI B 作为产品模型；
- 不使用固定 Decision AI / Worker AI 作为产品模型。

### 4. Project Conversation

进入 Project 后，Conversation 是主要工作入口。

正常行为：

```
用户提问
 ↓
当前 Project
 ↓
Project Address
 ↓
Default AI
 ↓
读取 / 分析 / 工作
 ↓
回答 / 完成
```

普通项目问题不得因为存在多个 Project Member 而自动触发多 AI 讨论。

### 5. Request AI Assistance

多 AI 协助是一个动作，而不是 Project 的默认模式。

```
Default AI
 ↓
Request AI Assistance
 ↓
其他 Project Member / 临时 AI
 ↓
协助
 ↓
Default AI 继续
```

协助对象不以 A/B 身份命名。

## B. 连续工作

Default AI 在获得相应施工权限后，可以连续完成一次任务：

```
理解 → 读取 → 修改 → Commit → Verify → 必要时继续修复 → 完成
```

用户不需要在每一步点击“继续”。

仅在以下情况暂停：

- 需要用户决策；
- 权限 / 资源不足；
- 明确阻塞；
- 达到合理的失败 / 重试边界。

## C. 独立「对话」页

「对话」页与 Project 独立。

主要用途：

- 选择 API Profile；
- 与单个 AI 直接交流；
- 进行普通问答、分析；
- 在允许情况下读取外部资源。

默认定位为**只读 / 查询型能力**。

它不得：

- 自动进入 Project；
- 自动使用 Project Default AI；
- 自动启动多 AI 协作；
- 因 API 连接而获得 Project 施工权限；
- 绕过 ConstructionLock 修改项目。

当前对话页底层架构基本保留，主要进行 UI 优化。

## D. 权限边界

### 读取

读取 Project Address 中的资源属于正常 AI 工作能力。

GitHub 读取不需要 ConstructionLock。

### 修改

修改 / Commit 属于施工能力，需要明确的施工权限。

ConstructionLock 只控制修改阶段，不作为普通读取前置条件。

## E. 当前已确认 / 待施工

### 已确认

- Project 是核心对象。
- Project Address 统一 Local / GitHub / future storage。
- Default AI 是 Project 的主要 AI。
- Project Members 是可管理集合，不是固定 A/B。
- API Profile 与 Project Member 分离。
- Project Conversation 是项目主要入口。
- Request AI Assistance 是按需动作。
- 普通「对话」独立于 Project。
- 普通「对话」默认只读 / 查询定位。
- 多项目 Secretary / Orchestrator 暂不施工。

### 待施工

- Project Address 数据模型统一；
- Project Default Member 数据模型；
- Project Member 增删改与 API Profile 更换；
- Project 创建 / 编辑 / 删除；
- Project Conversation 与新 Project 模型统一；
- Request AI Assistance 新入口；
- ConstructionLock 从旧 Workspace 语义迁移到 Project；
- 清理旧 A/B、Decision / Worker、独立 Conversation GitHub 配置；
- Project UI 重新收口。

## F. 暂不做

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 普通项目提问自动多 AI 讨论；
- 复杂 Project Dashboard；
- Secretary / Orchestrator；
- 完整 Git 客户端；
- 多层自动 Agent 调度。

## G. 验收原则

```
代码
 ↓
UI 入口
 ↓
用户操作
 ↓
状态保存 / 恢复
 ↓
真实业务生效
 ↓
APK
 ↓
真机验证
```

代码存在不等于功能完成。

状态必须明确区分：

- 已设计未实现
- 开发中
- 已实现
- 已验证
- 阻塞
- 废弃
