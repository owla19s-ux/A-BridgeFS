# APS 功能地图与关联表

> 本文件是持续维护的开发索引，不是开发前置审批表。当前内容是依据 `main` 分支现有 UI 规范与可见源码建立的第一版基线；随着实际检查和施工继续补齐。**未查清的内容标为“待核实”，没有实现或验证证据的内容不标为已完成。**

## 1. 使用规则

### 1.1 本表解决什么问题

- UI 表说明用户能看到什么、入口在哪里。
- 功能表说明系统应该执行什么业务行为。
- 机制表记录这些行为依赖的存储、连接、权限、调度、验证等底层能力。
- 关系表将 UI、功能、机制连起来，帮助估算新增、修改、删除的影响范围。

四张表不要求一次填全。发现新功能或底层机制时再加行即可。

### 1.2 ID 与状态规则

1. 每个对象使用稳定 ID；改名称、调整位置、改变顺序时不更换 ID。
2. 树形归属只由 `parent_id` 确定；跨模块关联只通过关系表表达。禁止按 ID 前缀猜测父子关系或依赖关系。
3. 功能被移除时，优先标记为“已停用”，保留历史关系和验证记录；只有确认没有历史引用且确实需要清理时才物理删除。
4. 每条信息区分**设计状态**与**实现状态**。设计已确认，不代表代码已经实现；代码存在，不代表已经接入 UI；Build 通过，不等于业务功能已经验证。
5. 此处“未发现实现”表示在当前可见的 `main` 源码范围中没有找到对应实现，不代表已经穷尽所有隐藏路径。后续发现证据时直接更新。
6. 增删 UI 时优先复用已有功能和机制，不为每个按钮新造一套底层；只有功能语义或权限边界确实不同才拆分。
7. 本表不要求每次普通 UI 调整都跑完整测试。共享数据模型、连接调用、文件写入、权限、Task 状态或 Verify 语义有变化时，才扩大影响检查与验证范围。

### 1.3 状态词表

| 状态类型 | 允许值 | 含义 |
|---|---|---|
| 设计状态 | 已确认 / 需细化 / 待确认 / 已停用 | 产品/UI 约定的成熟度 |
| 实现状态 | 未实现 / 部分实现 / 代码存在未接 UI / 已实现待验证 / 已验证 / 阻塞 / 待核实 | 当前代码及实际验证证据 |
| 关系类型 | exposes / implements_through / depends_on / persists_with / guards / verifies_with | UI 暴露功能、功能使用机制、依赖、持久化、权限保护、验证覆盖 |

---

## 2. 表一：UI 页面与区域表

本表列正式设计的 UI 节点，也记录当前源码接入情况。当前正式 UI 仍以 `PROJECT/UI/README.md` 为准。

| UI ID | parent_id | 页面 / 区域 | 主要可见功能 | 关联功能 ID | 设计状态 | 当前实现状态 |
|---|---|---|---|---|---|---|
| UI-001 | — | APS 主入口 | 进入空间、普通对话、设置 | FN-001,FN-005,FN-021,FN-022 | 已确认 | 部分实现：当前 Manifest 的 Launcher 指向 ConversationActivity；空间/设置入口未发现 |
| UI-002 | UI-001 | 空间页 | 选择与管理 Space，进入目录和 Context | FN-001,FN-002 | 已确认 | 未发现页面实现 |
| UI-003 | UI-002 | Space 选择与管理 | 查看、创建、重命名、删除或切换 Space | FN-001 | 需细化 | 未发现页面/完整模型实现 |
| UI-004 | UI-002 | 目录与 Context 列表 | 分组展示 Context，进入指定 Context | FN-001,FN-002 | 已确认 | 未发现 UI；Context 模型/Store 有初步实现 |
| UI-005 | UI-004 | 新建 Context / 模板 | 自定义创建、从模板创建、编辑/复制/保存模板 | FN-002,FN-003 | 已确认 | 未发现模板模型与 UI 实现 |
| UI-006 | UI-004 | Context 工作面 | 显示 Context 信息及按需启用的能力 | FN-002,FN-004,FN-011,FN-017,FN-018,FN-019 | 已确认 | 未发现页面实现 |
| UI-007 | UI-006 | Context 基本信息与管理 | 名称、备注；创建、查看、编辑、删除、移动、复制、归档 | FN-002 | 已确认 | 部分实现：Context 数据模型和 SharedPreferences Store 存在；统一 CRUD UI/完整动作未发现 |
| UI-008 | UI-006 | Address / Resource 区域 | 管理多个地址；选择资源；区分位置与实际内容 | FN-004,FN-011 | 已确认 | 部分实现：Context 目前可关联 GitHub ResourceRef；通用 Address 与 Local/File Resource 尚未发现 |
| UI-009 | UI-006 | 连接与权限区域 | 选择 Context 可用连接并管理资源操作授权 | FN-022,FN-011,FN-020 | 已确认 | 部分实现：GitHub Connection 装配/写权限边界有代码；通用授权与 UI 未发现 |
| UI-010 | UI-006 | 软件开发结构 | 结构树、施工表、验证视图；用户确认 UI 方案 | FN-017,FN-018,FN-019 | 已确认 | 未发现对应结构数据模型和 UI 实现 |
| UI-011 | UI-001 | 普通对话页 | 独立对话，不强制关联 Context | FN-005,FN-006,FN-007,FN-008,FN-009 | 已确认 | 部分实现：ConversationActivity 存在并作为 Launcher；调用仍使用固定占位 Connector |
| UI-012 | UI-011 | 对话分组 | 新建、重命名、删除分组；移动对话 | FN-006 | 已确认 | 已实现待验证：Activity 已接入分组筛选、新建/重命名/删除分组及移动对话入口；CI 已通过，仍需设备交互验收 |
| UI-013 | UI-011 | 对话管理列表 | 新建、查看、切换、重命名、删除对话 | FN-005,FN-007 | 已确认 | 已实现待验证：Activity 已接入新建/切换/重命名/删除；保存失败边界与设备交互仍待验收 |
| UI-014 | UI-011 | 消息与输入区 | 展示消息、输入、发送、显示调用状态 | FN-007,FN-009 | 已确认 | 部分实现：基础消息与发送 UI 存在；错误/取消/流式状态处理未发现完整实现 |
| UI-015 | UI-011 | AI 连接、模型与头像 | 选择 AI 连接及具体模型，显示头像 | FN-008,FN-010 | 已确认 | 部分实现：已可配置默认 API Profile 并获取模型目录，ConversationActivity 使用已保存模型；对话内连接/模型切换已接入首版（连接选择、当前对话模型 ID 覆盖并持久化）；当前对话可拉取模型目录并选择模型，头像显示仍未实现 |
| UI-016 | UI-011 | 连接 / 资料选择入口 | 通过“＋”选择 Local、File、GitHub 等已授权资源 | FN-011,FN-012,FN-014 | 已确认 | 未发现入口实现 |
| UI-017 | UI-011 | AI Code 三级策略按钮 | 当前普通对话切换“自动 / 询问 / 拦截” | FN-020 | 已确认 | 未发现实现 |
| UI-018 | UI-001 | 设置页 | 连接与服务、权限、验证、系统 | FN-021,FN-022,FN-023 | 已确认 | 未发现页面实现 |
| UI-019 | UI-018 | 连接与服务 | 添加、编辑、测试、启停各类连接 | FN-022 | 已确认 | 未发现统一设置 UI |
| UI-020 | UI-019 | AI / API 配置 | 配置服务地址、认证、模型列表与默认模型 | FN-008 | 已确认 | 部分实现：AISettingsActivity 支持地址、API Key、模型目录、保存/删除；当前仅一个默认 Profile，统一设置入口与连接状态持久展示仍待补齐 |
| UI-021 | UI-019 | GitHub 配置 | 凭证管理、验证、仓库/分支选择与测试 | FN-013,FN-014,FN-015,FN-016 | 已确认 | 代码存在未接 UI：认证、验证、Connector/API 组件存在；设置 UI 未发现 |
| UI-022 | UI-019 | Local / File 配置 | 选择本地目录、授权读写、测试访问 | FN-012,FN-022 | 已确认 | 未发现 Local/File Connector 与设置 UI |
| UI-023 | UI-018 | 系统权限与系统选项 | Android 权限、通知、后台及其他通用选项 | FN-021,FN-023 | 需细化 | 未发现设置 UI |
| UI-024 | UI-018 | 验证能力 | 查看各连接支持的验证方式及其状态 | FN-019,FN-023 | 已确认 | 未发现统一验证设置实现；第一阶段不要求复杂通用自动验证 |

---

## 3. 表二：业务功能表

功能 ID 表达业务含义，不与某个页面或某个类一一绑定。同一功能可以被多个 UI 调用；一个 UI 也可以调用多个功能。

| 功能 ID | 功能名称 | 功能边界 / 完成条件摘要 | 设计状态 | 当前实现状态 | 已知证据 / 待补内容 |
|---|---|---|---|---|---|
| FN-001 | Space 与目录管理 | 管理 Space，按目录组织 Context；有稳定身份与顺序 | 已确认 | 未实现 | 未发现对应 Space/目录模型及 UI |
| FN-002 | Context 管理 | Context 新建、读取、修改、删除、移动/复制/归档；稳定 ID | 已确认 | 部分实现 | `context/Context.kt`、`ContextStore.kt`；多项管理动作和 UI 未发现 |
| FN-003 | Context 模板管理 | 内置/自定义模板、从已有 Context 保存模板、复制/编辑/删除模板 | 已确认 | 未实现 | 未发现模板实现 |
| FN-004 | Address / Resource 关联 | 多个 Address 定位资源；操作前解析实际 Resource；不把地址等同内容 | 已确认 | 部分实现 | 当前 ResourceRef 仅覆盖 GitHub Resource；通用 Address 模型待建 |
| FN-005 | 对话管理 | 创建、查看、切换、重命名、删除普通对话；支持独立/可关联 Context | 已确认 | 部分实现 | ConversationManager/Store 与对应 UI 动作已接入；需补足失败保存边界并做设备交互验收 |
| FN-006 | 对话分组管理 | 分组 CRUD、将对话移动到分组；删组不删对话 | 已确认 | 已实现待验证 | ConversationGroup/Store 与 Activity 分组管理 UI 已接入；CI 通过，设备交互待验收 |
| FN-007 | 对话自动保存 | 对话与分组持久化；记录归属独立于项目文件；失败时不丢用户消息 | 已确认 | 部分实现 | sendAndSave 已在 AI 请求前保存用户消息；新增测试覆盖请求异常时保留用户消息，仍需设备端生命周期验收 |
| FN-008 | AI 连接与模型配置 | 每个连接管理服务地址/认证/模型；对话明确选择连接 + 模型 | 已确认 | 部分实现 | AIProfile、加密存储、模型目录及 OpenAI-compatible Provider 已接入；目前只有一个默认 Profile，对话内选择多个连接/模型仍待实现 |
| FN-009 | AI 请求执行 | 发送真实请求、处理响应、失败、超时、取消和状态展示 | 已确认 | 部分实现 | ConversationActivity 已根据默认 Profile 调用 OpenAI-compatible `/chat/completions`；失败提示存在，流式响应、取消、重试及真实服务端验收仍缺 |
| FN-010 | AI 头像显示 | AI 连接可配置头像；对话标题/消息区按设计显示 | 已确认 | 部分实现 | AIConnection.avatar 字段存在；头像 UI/资源解析未发现 |
| FN-011 | 连接选择与资料引用 | 用户明确选择资源；区分读取、生成、写入；遵守授权 | 已确认 | 未实现 | 未发现通用资源选择与连接调度 UI/链路 |
| FN-012 | Local / File 文件读写 | 选择已授权目录；读取、修改、保存；返回实际结果；失败不伪报成功 | 已确认 | 未实现 | 未发现 Local/File Connector |
| FN-013 | GitHub 凭证与连接验证 | 安全保存凭证；验证身份及仓库访问；明确报告失败 | 已确认 | 代码存在未接 UI | GitHubCredentialStore、GitHubCredentialVerifier 与测试存在；UI 未接入 |
| FN-014 | GitHub 仓库资源读取 | 仓库/分支/提交/文件读取；支持按需并发读取并返回真实响应 | 已确认 | 代码存在未接 UI | GitHubApi、GitHubConnector、GitHubConcurrentReader 存在；未发现 UI 集成 |
| FN-015 | GitHub 写操作 | 明确授权后写文件或触发操作；默认拒绝未授权写入；记录实际返回 | 已确认 | 部分实现 | GitHubWritePolicy 默认 disabled；writeFile/dispatchWorkflow 有权限守卫；用户操作入口、写入后验证待接 |
| FN-016 | GitHub Actions / 验证结果 | 查询 workflow runs、单次 run、artifacts；支持授权触发 workflow | 已确认 | 部分实现 | GitHub Connector/API 有查询与 dispatch 组件；结果解释、证据绑定、UI 未发现 |
| FN-017 | 软件开发结构 | 维护 UI/功能/架构/施工点等节点，支持结构、施工、验证视图 | 已确认 | 未实现 | 当前主要存在规范文档；未发现对应实际数据模型/视图 |
| FN-018 | Task / Dispatcher | Task 生命周期、拆分/做/审查职责、并发写入互斥、执行结果与失败恢复 | 已确认 | 未实现 / 待核实 | 当前可见主源码未发现完整 Task/Dispatcher 运行链 |
| FN-019 | Verify / Evidence | Verify 关联 Task 与产物；失败可重试；只凭实际结果/证据更新状态 | 已确认 | 未实现 / 待核实 | 当前可见主源码未发现统一 Verify/Evidence 实现 |
| FN-020 | AI Code 权限策略 | 普通对话页三级执行策略；同时受系统权限与 Connection 授权约束 | 已确认 | 未实现 | 未发现策略状态、执行守卫或对应 UI |
| FN-021 | Android / APP 系统权限 | 处理运行所需系统授权，并与连接资源授权分离 | 需细化 | 待核实 | Manifest 当前可见声明较少；系统权限 UI/检查流程未发现 |
| FN-022 | 通用 Connection 管理 | 统一列出连接，管理类型、启停、认证、测试与授权；不把 AI 固定为角色 | 已确认 | 部分实现 | Connection 接口及 AI/GitHub 组件存在；通用注册/配置/设置 UI 未发现 |
| FN-023 | 运行日志、诊断与恢复 | 区分 APS 运行日志与项目资料；记录关键操作、错误和恢复依据 | 需细化 | 待核实 | 规范已描述 APS 数据目录；当前可见代码范围未找到完整日志/诊断实现 |

---

## 4. 表三：底层机制表

此表记录功能背后的可复用机制。它不要求每项功能拥有单独的底层实现；多个功能可以共用一个机制。

| 机制 ID | 机制名称 | 责任边界 | 当前实现状态 | 源码 / 规范证据与缺口 |
|---|---|---|---|---|
| ME-001 | 稳定 ID、父子关系与关系边 | 分离对象身份、树形归属、跨对象映射；关系不能从 ID 字符串推导 | 设计已明确，运行实现未发现 | `PROJECT/UI/README.md` 定义规则；实际通用节点/边存储待建 |
| ME-002 | Context Store | 保存/加载/删除 Context；唯一维护 Context 本身状态 | 部分实现 | `context/Context.kt`、`context/ContextStore.kt`；目前 Context Resource 类型覆盖不足，且 Store 测试/错误恢复需补核 |
| ME-003 | Conversation Store | 对话/消息/分组持久化；分组与对话记录分离；移动和删除边界清晰 | 部分实现 | `conversation/ConversationStore.kt` 使用 SharedPreferences + JSON；大数据量、迁移、写入失败保障需后续评估 |
| ME-004 | Conversation Service | 把用户消息、AI 调用、响应消息和保存组合起来 | 部分实现 | `ConversationService.kt`、相关测试；完整错误/取消路径及失败前消息保存需补齐 |
| ME-005 | AI Connector 抽象与 Registry | 将连接 ID 解析为可调用 Connector；不把抽象层误认为具体 Provider | 部分实现 | `ai/AIConnector.kt`、`AIConnectorRegistry.kt` 与 `OpenAICompatibleConnector.kt`；抽象、Registry 和一种兼容 Provider 已存在，其他 Provider 与多连接注册仍待扩展 |
| ME-006 | API Profile、模型目录与密钥管理 | 维护多个端点、认证、模型与默认连接；密钥安全保存；连接与模型选择可分别调整 | 部分实现 | `AIProfile.kt`、`AIProfileStore.kt`、`AISettingsActivity.kt`；已加入多 Profile 存储、默认连接选择和旧单 Profile 迁移；保存/删除/清空按原始元数据与密钥条目分离处理，避免密钥解密失败时静默丢失其他连接配置；仍需验证迁移/密钥隔离，并补连接测试结果、模型能力和请求参数管理 |
| ME-007 | GitHub REST API 与 Connector | 封装账号、仓库、分支、提交、内容、Actions 等远端调用 | 代码存在 | `github/api/GitHubApi.kt`、`GitHubConnector.kt`、对应 Factory；需持续补齐失败状态、速率限制和 UI 集成验证 |
| ME-008 | GitHub 凭证加密与验证 | 凭证加密保存、加载/清除；验证 GitHub 身份与仓库访问 | 代码存在 | `GitHubCredentialStore.kt`、`GitHubCredentialVerifier.kt` 及相应测试；端到端设置流程未接 UI |
| ME-009 | GitHub Resource 映射与并发读取 | Context 资源转 GitHub Connection；并发拉取仓库、分支、提交、文件快照 | 代码存在 | `GitHubConnectionFactory.kt`、`GitHubConcurrentReader.kt`；Resource 地址冲突及错误项处理需按实际场景验证 |
| ME-010 | GitHub 写入权限守卫 | 远端写操作必须经过明确授权，默认关闭 | 部分实现 | `GitHubConnector.kt` 的 GitHubWritePolicy 默认 disabled，并守卫写文件/触发 workflow；授权配置和审计记录未接入 |
| ME-011 | Local / File Connector | 选择本地资源、授权、读写、原子保存、冲突与失败处理 | 部分实现 | `local/LocalConnection.kt`、`LocalConnectionStore.kt`、`LocalFileConnector.kt`、`AndroidLocalDocumentGateway.kt` 已支持 SAF 文档树列目录、文本读取/写入、连接级读写开关和 2 MiB 限制；普通对话页已接目录选择、文件浏览、编辑与显式保存入口。编辑器保存时将打开文件时的原始内容传入 Connector，写入前比对当前内容，发现外部修改则取消保存并提示冲突；保存失败或冲突时编辑器保持打开，避免未保存文本因对话框自动关闭而丢失。随后再次检查以缩小检查与写入之间的竞态。写入后回读校验字节一致性，异常或校验不一致时通过 `LocalWriteRecovery` 尝试恢复；单测覆盖写入失败恢复、恢复失败、成功路径及过期编辑快照冲突。该机制不是原子保存，检查与写入之间仍有竞态，跨 Provider 行为及真实设备故障仍待验证；Context 工作面复用、撤权检测与完整冲突处理仍待补 |
| ME-012 | 通用 Connection 授权机制 | 把 Android 系统授权、Connection 访问授权、AI Code 策略分层判断 | 未发现完整实现 | GitHub 存在局部写策略；统一授权对象、授权检查入口与撤销/过期流程待设计 |
| ME-013 | Task / Dispatcher 状态机 | 任务排队、拆分/做/审查交接、单写入者、取消、重试、恢复与状态持久化 | 未发现完整实现 | 规范已有目标；当前可见源码尚未定位到可运行的统一调度机制 |
| ME-014 | Verify / Evidence 机制 | 保存验证要求、实际结果、证据、失败/重试历史；状态以事实更新 | 未发现完整实现 | 规范已有关系定义；需要正式模型、接口、持久化与测试 |
| ME-015 | 本地运行日志与诊断 | 按类型记录操作、错误、耗时、结果与恢复信息；和项目文件分开 | 待核实 | UI 文档定义 APS/ 运行数据位置；需检查现有日志代码并补真实证据，不以文档代替实现 |
| ME-016 | 测试与验证证据 | 以测试、实际连接结果和必要的端到端证据核对功能状态 | 部分实现 | Conversation、Context、GitHub 有单元测试文件；每个业务闭环的测试覆盖与当前 Verify 状态需要逐项核对 |
| ME-017 | Connection 基础抽象 | 提供连接稳定 ID 与类型；具体 Connector 通过连接身份解析，不把 Connection 当成实际资源 | 部分实现 | `connection/Connection.kt` 目前只定义 GITHUB、AI 两种类型；Local、File、API/Service 等通用类型尚未纳入该接口，需按模块施工时扩展，不提前一次性实现全部类型 |
| ME-018 | Context Conversation Runtime 组装 | 校验 Context、Conversation 与 AI Connection 的归属关系，并解析对应 Connector / ConversationService | 部分实现 | `conversation/ContextConversationRuntime.kt` 已有组装边界及测试；当前 ConversationActivity 未使用该 Runtime，且没有真实 Provider，不能据此认定 Context 对话链已打通 |
| ME-019 | AI Profile 安全持久化 | 多连接分别保存 API 名称、Base URL、模型及加密 API Key；密钥不以明文写入偏好存储 | 部分实现 | `ai/AIProfileStore.kt` 按 Profile ID 使用独立 Android Keystore AES-GCM 密钥，并将非敏感元数据与密钥分开存储；加入旧单 Profile 迁移；保存/删除/清空不再依赖密钥成功解密才保留或清理元数据，仍需验证迁移、密钥隔离、损坏恢复与凭证轮换 |
| ME-020 | OpenAI-compatible Provider | 使用已选模型调用 `/chat/completions` 并解析模型目录与响应 | 部分实现 | `ai/OpenAICompatibleConnector.kt` 已实现模型列表和非流式聊天请求；全局 API 设置已提供真实非流式聊天请求测试入口及单元测试；运行中取消已有单元测试覆盖；SSE 流式输出实现与单元测试已加入，但真实供应商端到端验证仍未完成，不同供应商的兼容差异需要逐一验证 |

---

## 5. 表四：关系 / 依赖表

本表只记录重要的跨对象关系。新增 UI 时先找已有功能；功能再复用现有机制；缺少必要机制时才新增。关系可以多对多。

| source_id | target_id | relation_type | 关系说明 |
|---|---|---|---|
| UI-002 | FN-001 | exposes | 空间页暴露 Space 与目录管理 |
| UI-004 | FN-001 | exposes | 目录与列表呈现 Context 分组 |
| UI-004 | FN-002 | exposes | Context 列表支持进入与管理 |
| UI-005 | FN-002 | exposes | 新建流程最终创建 Context |
| UI-005 | FN-003 | exposes | 模板入口调用模板管理功能 |
| UI-006 | FN-004 | exposes | Context 工作面管理 Address/Resource |
| UI-006 | FN-011 | exposes | Context 工作面可以使用获授权连接与资料 |
| UI-010 | FN-017 | exposes | 软件开发结构提供结构树/施工/验证视图 |
| UI-010 | FN-018 | exposes | 施工视图展示 Task 与执行状态 |
| UI-010 | FN-019 | exposes | 验证视图呈现 Verify/Evidence |
| UI-011 | FN-005 | exposes | 对话页管理普通对话 |
| UI-012 | FN-006 | exposes | 分组管理入口 |
| UI-013 | FN-005 | exposes | 对话列表管理与切换 |
| UI-014 | FN-007 | exposes | 消息交互关联对话保存 |
| UI-014 | FN-009 | exposes | 输入发送触发 AI 请求 |
| UI-015 | FN-008 | exposes | 选择当前 AI 连接与模型 |
| UI-015 | FN-010 | exposes | 显示 AI 连接头像 |
| UI-016 | FN-011 | exposes | 资料/连接选择入口 |
| UI-016 | FN-012 | exposes | 选择本地文件资源 |
| UI-016 | FN-014 | exposes | 选择 GitHub 资源 |
| UI-017 | FN-020 | exposes | 控制当前普通对话 AI Code 策略 |
| UI-019 | FN-022 | exposes | 全局连接管理入口 |
| UI-020 | FN-008 | exposes | API 与模型配置入口 |
| UI-021 | FN-013 | exposes | GitHub 认证与连接测试入口 |
| UI-021 | FN-014 | exposes | GitHub 仓库/分支/资源入口 |
| UI-021 | FN-015 | exposes | GitHub 写操作入口 |
| UI-021 | FN-016 | exposes | GitHub Actions/验证结果入口 |
| UI-022 | FN-012 | exposes | 本地目录和文件能力入口 |
| UI-024 | FN-019 | exposes | 全局验证能力说明与连接支持情况 |
| FN-002 | ME-002 | persists_with | Context 管理依赖 Context Store |
| FN-004 | ME-001 | depends_on | Address/Resource 跨对象关联要遵循 ID/关系边规则 |
| FN-004 | ME-002 | persists_with | 当前 Context Resource 通过 Context Store 持久化 |
| FN-005 | ME-003 | persists_with | 对话记录由 Conversation Store 持久化 |
| FN-006 | ME-003 | persists_with | 分组与对话关系由 Conversation Store 管理 |
| FN-007 | ME-003 | persists_with | 自动保存使用对话持久化机制 |
| FN-007 | ME-004 | depends_on | 单轮发送与保存通过 Conversation Service 组合 |
| FN-008 | ME-005 | depends_on | 对话需要把 AI Connection 解析为 Connector |
| FN-008 | ME-006 | depends_on | 真实模型切换需要模型目录和 API 配置机制 |
| FN-009 | ME-005 | depends_on | AI 请求走 AI Connector 抽象 |
| FN-008 | ME-019 | depends_on | API 凭证通过 Android Keystore 加密持久化 |
| FN-009 | ME-020 | implements_through | 当前真实请求通过 OpenAI-compatible Provider 执行 |
| FN-009 | ME-006 | depends_on | 真实 API 请求依赖 Provider/模型配置机制 |
| FN-011 | ME-012 | guards | 所有连接调用受统一访问授权约束 |
| FN-012 | ME-011 | implements_through | 本地文件操作由 Local/File Connector 执行 |
| FN-013 | ME-008 | implements_through | 凭证保存与连接验证通过 GitHub 凭证机制 |
| FN-014 | ME-007 | implements_through | GitHub 查询通过 GitHub API/Connector |
| FN-014 | ME-009 | implements_through | 资源映射和并发快照读取依赖 GitHub Resource 机制 |
| FN-015 | ME-010 | guards | GitHub 写入前必须经过写权限守卫 |
| FN-016 | ME-007 | implements_through | Actions 查询与 dispatch 使用 GitHub API |
| FN-016 | ME-014 | verifies_with | workflow 结果未来需要转成可追溯的验证证据 |
| FN-017 | ME-001 | depends_on | 软件开发结构需要稳定节点身份、树关系与跨节点关系 |
| FN-018 | ME-013 | implements_through | Task 调度与职责交接依赖 Dispatcher 状态机 |
| FN-019 | ME-014 | implements_through | Verify/Evidence 依赖正式验证机制 |
| FN-020 | ME-012 | guards | AI Code 策略与系统授权、Connection 授权共同约束操作 |
| FN-021 | ME-012 | depends_on | 系统权限与连接授权需要分层而不是混用 |
| FN-023 | ME-015 | implements_through | 日志和诊断功能依赖运行日志机制 |
| FN-023 | ME-016 | depends_on | 功能状态必须由对应测试或实际证据支撑 |
| FN-008 | ME-017 | depends_on | AI Connection 需要稳定连接身份与类型 |
| FN-009 | ME-018 | depends_on | Context 对话运行时需要校验 Context / Conversation / AI Connection 关系；真实 Provider 仍是独立缺口 |
| FN-013 | ME-017 | depends_on | GitHub 连接实现使用统一 Connection 身份边界 |
| FN-014 | ME-017 | depends_on | GitHub 资源读取通过 Connection / Connector 边界执行 |

---

## 6. 修改 UI 时的轻量流程

不要求每次改动重新审核整张表，只检查受影响的路径：

1. **新增 UI**：先新增 UI ID，填写 `parent_id`，查找已有功能；只有业务能力确实不存在时才新增功能 ID。
2. **删除 UI**：确认是否只是隐藏入口。如果功能仍被其他 UI 使用，不删除功能；UI 节点可标记停用并保留历史关系。
3. **修改功能**：更新功能定义及其机制依赖，检查使用该功能的其他 UI。
4. **修改底层共享机制**：反查所有关联功能和 UI；涉及持久化、权限、连接写入、Task/Verify 状态时扩大验证范围。
5. **实现状态更新**：必须附源码、测试或真实执行证据。没有证据时保留“部分实现 / 待核实”。

普通文案、布局、间距调整通常只需更新 UI 设计；若改变交互语义、权限或数据归属，再更新功能/关系表。

## 7. 当前优先补齐顺序

根据第一阶段产品目标与当前可见源码，建议顺序为：

1. **Conversation → AI/API/Model**：保留已有对话数据层，补全分组/管理 UI、真实 Provider、连接和模型选择、错误及保存边界。
2. **Local/File**：建立实际文件连接与读写授权边界，支持对话页及 Context 工作面按授权调用。
3. **GitHub 接入 UI**：复用现有凭证、Connector、API、并发读取与写权限守卫，补设置/资源选择/操作结果链路。
4. **Local ↔ GitHub 协作**：在两侧真实连接能力稳定后再建立双向或指定方向的工作流；先明确覆盖/冲突/验证规则。
5. **Space / 目录 / Context UI**：最后把基础组织页接上已独立稳定的连接和文件模块。
6. **Task / Dispatcher / Verify**：按照软件开发结构逐步建立，不把设计文档或 AI 口头判断当成已运行机制。

这只是基于当前基线的建议顺序，不是所有功能开工前的门槛。开发中发现更短的依赖路径，可以直接更新本表。

## 8. 代码证据索引

第一轮确认到的主要源码：

- 对话 UI：`app/src/main/java/com/abridgefs/app/ConversationActivity.kt`
- AI 连接与调用抽象：`app/src/main/java/com/abridgefs/app/ai/AIConnection.kt`、`AIConnector.kt`、`AIConnectorRegistry.kt`
- Context 与持久化：`app/src/main/java/com/abridgefs/app/context/Context.kt`、`ContextStore.kt`
- Conversation 数据、服务与 Store：`app/src/main/java/com/abridgefs/app/conversation/`
- GitHub API 与 Connector：`app/src/main/java/com/abridgefs/app/github/`
- GitHub 并发读取：`app/src/main/java/com/abridgefs/app/github/api/GitHubConcurrentReader.kt`
- 产品与 UI 规范：`PROJECT/SPEC/APS-PRODUCT-SPEC.md`、`PROJECT/UI/README.md`
- 架构基线：`PROJECT/ARCHITECTURE/APS-CURRENT-ARCHITECTURE.md`

本索引不会替代这些规范或源码。出现冲突时，按正式产品语义判断设计，再以当前源码与实际 Verify 证据更新实现状态。
