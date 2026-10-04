# APS Architecture Rebuild

> 状态：施工计划 / 架构重建分支  
> 分支：`architecture-rebuild`  
> 基线：`main`  
> 定位：在保留现有可用能力的基础上，以新架构重新适配 UI、功能、底层与模块。

## 一、这次重建是什么

这不是从零重写 APS，也不是对旧代码做一次全面的 Workspace → Project 改名迁移。

本次采用：

**新架构 + 维加体系参考 + 现有 APS 代码零件库 → 按完整业务链路重新适配。**

旧代码中已经验证可用的能力优先复用；结构不合适但逻辑有价值的部分提取/适配；与新架构冲突或已经废弃的部分不迁移。

目标不是“把旧代码整理得更漂亮”，而是建立一套真正打通：

**UI → 业务 → Store → Service → Module → 外部能力 → Execution → Result / Evidence → Verify**

的 APS 新结构。

## 二、核心重建原则

### 1. 新架构定方向，旧代码提供零件

- 新 Project-centered 架构作为主方向。
- 参考 AI+ / AI-V1 / 维加已经思考和试错过的概念。
- 不复制维加规模，不把维加完整体系搬入 APS。
- 现有代码按实际价值选择复用、适配、提取、重写或放弃。

分类原则：

| 旧代码状态 | 处理 |
|---|---|
| 与新架构一致、已验证可用 | 复用 |
| 功能正确、边界/命名旧 | 适配 |
| 核心逻辑有价值、结构不合适 | 提取 |
| 局部与新架构冲突 | 重写该部分 |
| 已废弃、无有效调用 | 不迁移 / 删除 |
| 暂时无法判断 | 暂留，不强行处理 |

### 2. 按业务链路施工，不按旧文件追引用施工

不再采用：

旧文件 → 找引用 → 改引用 → 再发现引用 → 回头修旧文件

作为主要施工方式。

改为：

**先定义完整业务链路 → 从 UI 一直向下落 → 每层确定职责 → 复用/适配已有实现 → 完整验证。**

### 3. UI 与功能必须真正打通

功能存在但 UI 无法调用，不视为完成。

UI 存在但下面没有真实功能，也不视为完成。

每条主要链路都必须能够从用户操作进入真实功能，并最终得到可验证结果。

### 4. Activity 不再成为业务总管家

特别是当前 `V021Activity` 的职责过载问题，本次重建不继承。

Activity 主要承担：

- Android 生命周期；
- 页面承载；
- 导航；
- 页面级状态协调。

不把 Project、Conversation、GitHub、API、施工、文件执行、权限等大量业务逻辑继续堆进一个 Activity。

### 5. 按职责拆分文件

不追求“文件越多越好”，但也不接受“所有东西塞进一个大文件”。

原则：

> 一个文件 / 类承担一个清晰职责；一个业务链路可以由多个职责明确的文件共同完成。

候选结构：

```
UI
├── Project
├── Conversation
├── Config
└── Navigation

Domain
├── Project
├── Member
├── Conversation
├── Task
└── Execution

Store
├── Project
├── Conversation
├── API
└── ...

Service
├── Project
├── Conversation
├── Construction
└── ...

Module
├── GitHub
├── Local
└── Future modules

Infrastructure
├── GitHub API
├── File bridge
└── Android system integration
```

具体目录和类名在实际链路施工前确定，不预先制造大量空壳。

### 6. 包名同步重建

本次重建同时处理包结构与包名，使代码组织能够反映新的职责边界。

不以旧包结构为不可变约束。

Android `applicationId` `com.abridgefs.app` 是否保持不变，与 Kotlin/Java package 的重建分开处理；除非另有确认，不因内部包重建自动修改 applicationId。

### 7. Project 是当前产品核心，但不是未来所有能力的唯一根

当前 APS 继续以 Project 为主要业务容器。

同时避免把所有概念都强行塞进 Project。

重点保持以下边界：

- Project
- Project Member
- Project Conversation
- Address / Resource
- Service / API
- GitHub
- Task
- Execution
- Result
- Evidence
- Verification

以下概念可以作为架构空间保留，但当前不做完整基础设施：

- Resource
- Relation
- Capability
- Connection
- Account
- Identity
- Policy
- Control Domain

### 8. 维加只提供参考，不成为 APS 的施工清单

核心吸收：

- Resource 与路径/地址分离；
- Capability 与 Module 分离；
- Connection、Relation、Permission、Credential 不混为一谈；
- Conversation 不等于 Chat UI；
- Execution → Result + Evidence → Verification；
- ConstructionLock 不等于 Permission；
- UI 是可替换层；
- 优先扩展 Module / Adapter，而不是不断修改 Core。

不施工：

- 完整身份体系；
- 通用 Capability Registry；
- 通用 Connection 基础设施；
- 跨控制域授权；
- Mapping；
- 大规模多维对象基础设施；
- 跨组织基础设施。

## 三、重建施工顺序

### Phase 0：基线确认

- 保留 `main` 作为当前可运行基线。
- architecture-rebuild 从 main 开始。
- 记录当前关键功能和构建状态。
- 不在重建分支中破坏 main。

### Phase 1：包与基础结构

- 确定新的 package 结构。
- 建立 UI / Domain / Store / Service / Module / Infrastructure 的边界。
- 将现有可复用代码按职责迁入或适配。
- 不为了拆分制造空壳层。

### Phase 2：Project 主链路

目标：

```
Project UI
 ↓
Project state / controller
 ↓
Project domain
 ↓
Project Store
 ↓
Project Service
 ↓
Project Address
```

先把项目本身跑通。

### Phase 3：Project Conversation

```
Project
 ↓
Project Conversation
 ↓
Default Member
 ↓
API Profile
 ↓
AI Request
 ↓
Response
```

确保 UI、API、消息、状态完整打通。

### Phase 4：Project Member / API

明确：

```
Project Member ≠ API Profile
AI 身份关系 ≠ API 连接资源
```

完成 Member 管理、Default Member、API Profile 绑定与实际调用。

### Phase 5：Address / Module

建立：

```
Project
 ↓
Address
 ↓
Module
 ↓
具体外部能力
```

GitHub 作为一种 Module / Address 实现，不再让 GitHub Workspace 成为业务核心。

Local File、GitHub 等已有能力优先适配。

### Phase 6：施工链路

建立完整：

```
Project
 ↓
Default Member
 ↓
Construction Permission
 ↓
Construction Lock
 ↓
Module
 ↓
修改
 ↓
Commit
 ↓
Execution
 ↓
Result
 ↓
Evidence
 ↓
Verify
```

连续施工以真实执行链路为准，不恢复旧 Decision AI / Worker AI 模型。

### Phase 7：Request AI Assistance

在 Default Member 连续施工稳定后，再接：

```
Default Member
 ↓
Request AI Assistance
 ↓
Other Project Member / Temporary AI
 ↓
Assistance Result
 ↓
Default Member continues
```

不把多 AI 协作重新变成固定角色系统。

### Phase 8：独立 Conversation / Config

保持独立「对话」与「配置」的边界。

重点确认：

- 独立对话不自动获得 Project 施工权限；
- GitHub 读取与 Project GitHub 使用边界清楚；
- API Profile 为独立连接资源；
- 全局 Access Switch 不与细粒度 Permission 混淆。

### Phase 9：清理与验证

只有新结构和新链路验证稳定后，才处理：

- 旧 Workspace 残留；
- 无效兼容字段；
- 废弃 Store；
- 旧 Activity；
- 无效包；
- 无调用旧类；
- 旧文档引用。

**不为了清理而提前破坏可用能力。**

## 四、每条链路的完成标准

每个功能不能只做到“代码存在”。

至少需要：

1. UI 可以进入；
2. UI 可以触发；
3. 中间业务层职责清楚；
4. Store / Service / Module 正常工作；
5. 真实外部能力执行；
6. 结果返回 UI；
7. 必要时产生 Evidence；
8. 可以通过真实构建和实际操作验证。

状态仍区分：

- 已设计未实现
- 开发中
- 已实现
- 已验证
- 废弃

## 五、重建期间的保护原则

- `main` 是安全基线。
- 重建分支可以大胆调整结构，但不能假装验证完成。
- 每完成一个完整链路就构建/验证，不等全部重写后才第一次运行。
- 发生架构冲突先记录和判断，不用兼容代码无限堆叠。
- 不为了“新架构”强行重写已经稳定且边界合理的底层实现。
- 不为了“复用旧代码”继承明显不合理的旧结构。
- 不恢复 Decision AI / Worker AI / 固定 AI A/B。
- 不把 V021Activity 再次发展成巨型业务文件。

## 六、最终目标

本次重建的目标不是：

**“把旧 APS 改得更干净。”**

而是：

**“用新思路重新组织已有能力，让 APS 的 UI、功能、底层和模块真正成为一条可以持续扩展的产品链路。”**

最终形成：

```
用户
 ↓
UI
 ↓
业务对象 / State
 ↓
Store
 ↓
Service
 ↓
Module / Address
 ↓
真实能力
 ↓
Execution
 ↓
Result / Evidence
 ↓
Verification
```

并在这个基础上继续发展 APS，而不是继续被旧 Workspace 架构牵着走。


## 七、APS「浴火重生」落地定义

本次重建正式定义为：**不是迁移旧 APS，而是让 APS 在新方向、新架构下重新落地。**

旧 `main` 只作为当前可运行基线、已验证能力参考、旧代码零件库和历史实现证据；`architecture-rebuild` 才是新 APS 的主要施工场。

### 1. 方向
APS 以 Project-centered 为当前产品主方向。核心边界围绕 Project / Project Member / Project Conversation / Address / Service / Module / Task / Execution / Result / Evidence / Verification 展开。GitHub、Local 等是能力接入方式，不再反过来定义核心业务模型。

不恢复 Decision AI / Worker AI、固定 AI A/B、旧 Workspace 中心模型和旧悬浮球体系。

### 2. 架构
新架构不以旧文件和旧包结构为边界，遵循：

`UI → Domain / State → Store → Service → Module / Address → Infrastructure → Execution → Result / Evidence → Verification`

Activity 只承担页面承载、生命周期、导航和页面级状态协调，不重新成为业务总管家。

旧类如果功能正确但结构错误，可以重构后留下；不能因为已有代码就让旧结构成为新 APS 的骨架。

### 3. 命名
**旧命名不保留为兼容理由。**如果旧名称已经不能准确表达新模型，应直接按实际职责改名。例如 Workspace、GitHubWorkspace、workspaceId、BridgeAiMember / aiMembers 等，均按新模型重新判断和命名；不进行机械全局替换。

### 4. 旧代码处理方式
旧代码是待筛选资产，不是默认迁移资产：

| 情况 | 做法 |
|---|---|
| 能力正确 + 结构合理 | 复用 |
| 能力正确 + 命名旧 | 改名 |
| 能力正确 + 结构旧 | 重构后复用 |
| 只有局部逻辑有价值 | 提取 |
| 与新架构冲突 | 重写 |
| 无调用 / 已废弃 | 删除 |
| 暂时无法判断 | 暂留并标记 |

目标是最大化复用有效能力，同时最小化旧架构残留。

### 5. 清洗方式
清洗不是最后一次性处理，而是：**边重建、边清洗、边验证。**发现旧结构后立即判断是否仍属于 APS、是否有真实有效调用、能否自然落入新职责、是否应提取或删除。确认废弃后尽早删除，避免新代码再次依赖。

所有激进清理只在 `architecture-rebuild` 进行，`main` 保持安全基线。

### 6. 悬浮球处理
旧 A-BridgeFS 悬浮球正式列为**不迁移**。旧 Overlay / Floating Ball UI 不作为新 APS 资产。旧 `FileBridgeService` 中若存在有价值的纯文件执行能力可以提取；旧悬浮球及专属结构直接废弃。未来需要悬浮能力时重新设计、重新实现。

### 7. 落地方式
不采用“先把 main 全部复制到分支，再慢慢整理”。采用：

`新骨架 → 选择旧能力 → 新结构适配 → UI 打通 → 真实构建 → 实机验证 → 下一链路`

第一阶段建立 Project 主链，而不是一次性迁移所有旧功能。每完成一条完整链路就构建、验证；发现模型或职责边界不合理，就在当前链路解决。

### 8. “浴火重生”的最终标准
- APS 核心命名直接表达当前产品模型；
- 旧 Workspace 不再主导代码组织；
- Activity 不承担巨量业务逻辑；
- Project / Member / Conversation / Address / Service / Module 边界清楚；
- 旧悬浮球不存在于新 APS；
- 留下的是有效能力，而不是历史包袱；
- UI、业务、底层、模块真实打通；
- 关键链路均能构建、运行、验证；
- 新能力优先通过清晰边界扩展，而不是继续向旧巨型类堆逻辑。

> **旧 APS 可以留下它有价值的“能力”，但新 APS 不再继承它的“时代”。**
