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
Space → 目录 → Context
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

第一阶段施工顺序以当前 UI / 功能地图为索引，按“先打通用户高频入口和可复用连接能力，再接组织页与调度结构”推进：

1. **Conversation 基础闭环**：对话独立创建 / 切换 / 管理、自动保存与恢复、错误处理；明确当前 UI 占位响应，不把它视为真实 AI 已接通。
2. **AI / API / Model**：多 Profile 持久化、默认连接、旧配置迁移和连接管理 UI 已提交并通过对应工作流；当前统一 Connector Factory / Registry 已提交并通过 Verify #178、Android Tests #422、Build and Release #731。已修复 Profile Store 不能因某个密钥解密失败而丢失其他 Profile 元数据的问题；对话级连接选择和模型 ID 覆盖已接入首版并持久化；对话内模型目录拉取与选择已接入首版；后续补真实聊天连接测试、流式输出与运行中取消测试。
3. **Local / File Connector**：本地目录授权、读取 / 修改 / 保存、失败处理；可由普通对话页和 Context 工作面按授权复用。
4. **GitHub 接入 UI 与真实读取**：复用已有凭证、Connector、API、并发读取和写入守卫；优先完成认证、仓库 / 分支 / 文件选择，以及普通对话真实读取链路。
5. **Local ↔ GitHub 协作**：在两侧连接能力稳定后，再明确同步方向、覆盖 / 冲突处理和验证规则。
6. **Space → 目录 → Context UI**：把组织管理页接到已经独立稳定的 Conversation、Local 和 GitHub 能力；不把 Space 提前变成开发阻塞项。
7. **软件开发结构 / Task / Dispatcher / Verify**：按实际需要逐步建立结构、施工交接和证据链，不把设计文档当成已经运行的机制。
8. **多 AI 协作协议与真机整体验收**：在单连接主链可靠后再设计多 AI 交接；最后执行 APK 安装、真实 API / GitHub 和端到端验收。

这是施工优先级，不代表低优先级项目不重要，也不要求前一项的所有边缘功能完美后才能开始后一项。发现共享机制或验证阻塞时，可以按依赖调整顺序。

功能状态和跨模块依赖见 `PROJECT/MAP/README.md`。该地图是持续维护的索引，不是开发前置审批表；每次只更新本轮受影响的行。
 
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
