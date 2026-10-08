# APS 当前任务总表

更新时间：2026-10-07

> 本文件只维护当前仍需施工 / 验收 / 设计的任务。产品定义、架构原则、UI 正式设计和仓库操作规则分别由 PROJECT 与对应 docs 负责。

## 1. 当前第一阶段主线

### T1 — Space → 目录 → Context 工作面
**状态：开发中**

验收重点：
- 空间页进入 Space，按目录展示 Context；
- Context 主要对话是主体；
- Context 使用的 Address / Resource、Connection、AI 权限以及当前存在的任务 / 状态能力可查看；
- Context 的连接与配置独立展开 / 收起；
- 不把内部服务对象全部平铺成复杂 Dashboard。

### T2 — Context Conversation
**状态：开发中**

目标：

```
Space / Context
 ↓
Connection / Resource
 ↓
AI Connection
 ↓
Context 对话
 ↓
真实读取 / 工作
```

### T3 — Task / Dispatcher / Execution
**状态：开发中**

继续验证：
- Task 生命周期；
- 拆分 / 做 / 审查；
- 三项 AI 权限；
- Dispatcher；
- ConstructionLock；
- Execution；
- Commit 前置条件。

### T4 — GitHub / Commit / Verify
**状态：开发中**

继续验证：
- GitHub Connector；
- Repository / Branch；
- 文件读取 / 修改；
- Commit；
- Actions / Verify；
- 远程 Repository 作为 GitHub Connection 的 Resource 来源。

### T5 — Local Connector
**状态：待重构**

本地项目能力保留。

当前问题：现有本地操作模块较弱，历史 BridgeFS 执行语义较重。

后续目标：
- 保留 Android Local Connection 及其目录 Resource；
- 将本地目录 / 文件能力收口为 Local Connection + Local Connector；
- 不恢复 BridgeFS 产品模型；
- 不在没有实际需求前建立复杂本地执行引擎。

### T6 — 云端测试与主链验证
**状态：开发中**

建立：
- 模块测试；
- 契约测试；
- Context 主链测试；
- Commit → Actions → Verify 验证。

### T7 — 独立对话 / 设置
**状态：UI 已确认 / 开发中**

### T8 — 软件开发结构
**状态：已设计 / 待实现**

基础组织前提：Space → 目录（分组）→ Context（项目）。每个 Context 具有名称，并可维护多个 Address（名称 / 地址 / 备注）。

目标：在软件开发 Context 中建立统一的软件开发结构能力，连接：

```text
目标 / UI → 功能 → 架构 → 施工点 → 施工卡 → 代码 / 产物 → 验证
```

原则：不新增 Project 顶层模型；不做独立 Dashboard；底层关系允许一对多 / 多对多；Task 负责施工交接，结构负责语义关系，Receipt / Evidence / Verify 负责事实依据。

其中 UI / 交互必须预留“待用户确认 → 已确认 → 已变更 / 待重新确认”的状态；用户确认是设计认可，不等同于代码 Verify。第一版可以暂不实现完整确认交互。

### T9 — 多 AI 协作施工
**状态：待设计**

这是当前尚未解决的重要问题。

需要单独确定：

1. 一个 Context 内多个 AI Connection 如何分工；
2. 任务如何交接；
3. 上下文 / 工作状态如何共享；
4. AI 如何确认前一个 AI 的结果；
5. Repository / Branch 施工权如何交接；
6. 并行施工如何避免冲突；
7. Dispatcher 需要保存哪些最小状态。

**当前只保留“请求 AI 协助”的入口和边界，不提前确定具体协作协议。**

### T10 — 正式 APK / 真机验收
**状态：待主链收口后验收**

范围：
- 安装 / 启动；
- 空间页 / Context 工作面操作；
- 生命周期；
- 系统权限；
- 实际 API / GitHub；
- 最终用户体验。

## 2. 当前优先级

Space → 目录 → Context → Address / Resource → 软件开发结构（按需） → Connection → 对话 → 可选 Task / Dispatcher → Execution → GitHub / Verify → Local Connection → 云端测试 → 独立对话 / 设置 → 多 AI 协作设计 → 真机验收

多 AI 协作虽然是重要能力，但在协议未确定前不进入实现阶段。

## 3. 明确不做

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 普通 Context 问题自动多 AI；
- 复杂 Context Dashboard；
- 无限 Agent Loop；
- GitHub 第二套 Workspace 模型；
- BridgeFS 作为当前产品模块；
- Secretary / Orchestrator 作为既定架构；
- ProjectContinuationCoordinator 旧 continuation 运行链。

## 4. 任务维护规则

1. 新任务先判断是否属于当前第一阶段主线。
2. 架构决策写入 PROJECT，不在本表形成第二套规格。
3. 历史过程写入 STATUS 或专项历史文档。
4. 未确定的能力标记“待设计”，不能写成“已设计”。
5. 每次施工后只更新实际发生变化的任务状态。
