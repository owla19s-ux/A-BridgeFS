
# A-BridgeFS 跨项目资产盘点 V0.1

状态：已审计 / 资产整理中  
日期：2026-09-28  
范围：BridgeFS、AI-V1、Weijia

## 1. 结论

A-BridgeFS 不从零设计。三个项目分别提供不同层级的成熟资产：

- BridgeFS：Android 本地执行能力，属于第一版最核心的代码资产。
- AI-V1：API、Role、Task、Permission、Runtime、Work Context 等 AI+ 设计资产。
- Weijia：Identity、Account、Credential、Connection、Authorization、Policy、Mapping、工作状态等基础语义资产。

复用原则：代码优先复用已验证实现，设计优先复用已经形成稳定边界的语义；不把三个项目整体拼接。

## 2. BridgeFS 已确认资产

### CommandParser

旧 BridgeFS 已有统一 Command 类型与解析器，当前包含：

list、read、write、edit、search、grep、path、copy-path、mkdir。

支持一次输入解析多条指令，并按原始位置排序。

结论：P0，直接迁移或轻量封装。

### CommandExecutor

已经把 Command 映射到本地执行，并通过 PathSecurity.safe 进行路径保护。

结论：P0，核心执行资产，重构为 A-BridgeFS Executor。

### PathSecurity

已有根目录限制、绝对路径拒绝、.. 拒绝、canonical path 检查。

结论：P0，直接复用设计与实现；后续叠加授权范围。

### FileBridgeService

已经验证 Foreground Service、Overlay、根目录、CommandParser、CommandExecutor、结果展示、日志、Clipboard、目录浏览。

结论：
- Service 宿主：P0
- Clipboard：P1
- 目录浏览：P1
- 旧悬浮 UI：P2，参考重写

### Android Manifest / 权限

已经有 Overlay、外部存储、Foreground Service specialUse 等实际工程经验。

结论：P0/P1，作为 Android 宿主迁移依据，不直接复制全部旧 UI 配置。

## 3. AI-V1 已确认资产

### Role Collaboration Model

核心原则：

- Role 不是固定模型。
- 一个 Account 可以承担多个 Role。
- 一个 Role 可以由不同 Account/Subject 承担。
- Role 由 Role、Capability、Permission、Collaboration Relation、Interaction 等机制组合。
- Intelligence 与 Permission 分离。

结论：P0。第一版直接采用这个语义，但裁剪为 Name、System Prompt、Provider/Model Binding、Enabled。

### Identity / Permission

AI-V1 已形成：

Subject → Identity → ExternalAccount → Connection → CredentialRef

以及：

Subject + Capability + ResourceRef → Permission

Task / ExecutionRecord 可以关联 Executor Subject、Account、Connection。

结论：P0/P1。第一版保留 Account、Connection、Permission 的核心语义，复杂 Identity 可暂时隐藏。

### Unified Interaction / Execution

已有统一链路：

Conversation → Task → Capability → Permission/Policy → Tool → Runtime → Execution → Result/Evidence → Verification → Task State

结论：P0。A-BridgeFS 第一版裁剪为：

Chat/Role → Task → Command → Permission → Executor → Receipt。

### Work Context

AI-V1 已明确区分 Conversation Record、Task Record、Work Record、Project State、Current Work Brief、Work Plan、Work Context。

结论：P1/P2。第一版不实现完整 Work Context，但必须保持这些概念不要互相污染。

## 4. Weijia 已确认资产

### 身份与连接

Weijia 明确保持：

Subject ≠ Identity ≠ Account ≠ Credential

Connection ≠ Account

并把 Connection 看成具有状态、生命周期、来源、范围和失效条件的独立对象。

结论：P0/P1，用于 Provider / Connection 语义。

### Authorization / Permission / Policy

Weijia 的候选施工路线：

Identity / Account → Credential → Connection → Control Domain → Authorization / Permission / Policy → Relation → Capability / Resource / Service → Mapping / Context → Interaction → Task / Execution → Result / Verification

结论：P0。抽取最小授权模型；Control Domain 等高级语义暂不完整实现。

### account-minimal

Weijia 已存在 SQLite 最小账号模型，包含：

subject、identity、account、credential、role、permission、grant、resource、decision_log。

结论：参考重写。A-BridgeFS 不直接复制数据库，而是按 Android-first 和第一版需求裁剪。

### Mapping / Context

Weijia 已把 Mapping 定义为把真实存在的能力、资源、关系、权限映射到当前用户、上下文、角色、空间和场景。

结论：P2，保留语义，第一版只预留接口。

## 5. 暂不复用

- AI-V1 完整 UI / Floating UI
- 完整 Project Engine
- NeuroMesh 内部认知实现
- Weijia 完整 Project Engine
- 完整语义字典
- 多用户组织模型
- 浏览器系统
- 云同步
- 自动调度
- 多 Agent 协作循环

## 6. 优先级

P0：Command、Parser、Executor、PathSecurity、Android Service、Role、Provider/Connection、Permission、Task/Execution 基础链。

P1：Clipboard/Share Adapter、ExecutionRecord、API Connection 状态、最小 Work Context。

P2：Mapping、Project Context、Multi-Agent、Scheduler、NeuroMesh、Browser、Cloud Sync。

## 7. 复用策略

只允许五种：

- 直接迁移
- 重构迁移
- 参考重写
- 废弃
- 暂缓
