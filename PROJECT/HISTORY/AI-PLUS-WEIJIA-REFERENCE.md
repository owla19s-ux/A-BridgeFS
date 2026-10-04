# AI+ / 维加参考资料

> 状态：参考资料 / 历史研究  
> 用途：为 APS 当前设计提供可回看的概念材料与压力测试来源。  
> 重要：本文**不是 APS 当前架构规范**，不得直接作为施工依据。

## 1. 为什么保留这份资料

APS 在正式确定 Project-centered 产品方向后，需要回看过去 AI+、AI-V1 与维加（Weijia）时期已经做过的基础语义研究。

这些研究没有必要整体搬入 APS，也没有必要因为当时难以落地而全部舍弃。

它们更适合作为：

- 概念参考；
- 架构压力测试材料；
- 未来扩展时的边界提醒；
- 避免重新踩已经踩过的坑。

核心原则：

> **参考思想，不继承规模；吸收验证过的概念，不复制整个体系。**

---

## 2. AI+ / AI-V1 的主要思路

AI-V1 是 AI+ 方向较完整的工程骨架，曾系统研究：

- Object / Entity
- Subject / Identity
- Resource
- Capability
- Connection
- Account / Credential
- Permission / Policy
- Relation
- Task / Event / State
- Module / Adapter / Connector
- Runtime / Execution
- Result / Evidence / Verification
- Conversation / Interaction
- Project Engine
- GitHub Integration
- AI Gateway
- Work Context

其中对 APS 最有价值的不是具体目录或类名，而是几个长期边界。

### 2.1 Project 不应该成为整个世界的根

AI-V1 的思路中，Project 更接近一种业务形态或特殊模块，而不是 Foundation 的唯一上位根。

这对 APS 很重要：

APS 产品层可以以 Project 为核心，但长期底层不应演变成：

`Core → Project → 一切`

否则未来 Local、GitHub、Cloud、Remote、Database 等不同资源形态都会被迫围绕 Project 定义。

---

## 3. 维加（Weijia）进一步强调的思想

维加继续把 AI+ 的部分概念向开放、多维连接体系推进。

它关注的不是“做一个更大的项目管理器”，而是：

- 对象之间是什么关系；
- 谁具有什么身份；
- 谁通过什么连接访问什么资源；
- 什么能力由什么载体提供；
- 什么权限允许什么操作；
- 操作如何执行；
- 结果如何留下证据；
- 验证如何成立。

一个重要观察模型可以概括为：

`Object / Subject
→ Relation / Connection
→ Authorization
→ Capability / Resource
→ Mapping
→ Context / State
→ Interaction
→ Task / Action
→ Execution
→ Result / Verification`

这只是维加研究中的观察方式，**不是 APS 当前架构**。

---

## 4. 对 APS 最有价值的概念

### Resource

回答：

> “有什么东西可以被引用、读取、使用或修改？”

文件只是资源的一种载体。

对 APS 的启发：

- Project 不应该把某个本地目录永久当成自己的本体；
- GitHub Repository 也不应该天然等同于 Project；
- Local / GitHub / Cloud / Remote Server / Database 可以成为不同资源位置或资源提供方式。

APS 当前的 Project Address 可以看作这一思想的产品级简化。

### Relation

回答：

> “两个对象之间是什么关系？”

例如：

`Project ← member → AI`

“AI 是 Project Member”本身是关系。

它不能直接推出：

> “AI 可以修改 Project 的全部资源。”

后者需要另外判断 Permission / Policy / Scope 等。

### Capability

回答：

> “能够做什么？”

例如：

> 修改 GitHub Repository 中的文件

这不是 GitHub 本身，也不是 Permission 本身，而可以理解为一种 Capability。

它可以进一步涉及：

- Target Resource
- Required Permission
- Required Connection
- Provider / Module
- Execution
- Result
- Evidence
- Verification

Capability 与 Module、Permission 不应混为一谈。

### Connection

回答：

> “如何建立并管理两个对象之间的连接？”

AI+ / 维加中曾进一步区分：

`Subject → Identity → Account → Connection → Credential`

因此：

- API Key ≠ API；
- Credential ≠ Connection；
- Connection ≠ Permission；
- Connection ≠ Relation；
- GitHub Account ≠ Project。

APS 目前不需要完整实现这套身份体系，但数据结构不应把未来可能性彻底堵死。

### Service

回答：

> “能力以什么服务形式提供？”

例如一个外部 AI Service 可以提供：

- Chat
- Vision
- Image
- Embedding
- 其他能力

因此 API 更适合作为访问某项 Service 的一种连接方式，而不是把“API”直接等同于 AI。

APS 当前的 API Profile 是产品实现层概念，未来可以向 Service / Connection 方向演化，但目前不要求重构。

---

## 5. 几个特别重要的边界

### Relation ≠ Permission

“AI 是 Project Member”不等于“AI 可以修改 Project”。

### Permission ≠ Capability

“允许写入”不等于“系统已经具备写入能力”。

### Capability ≠ Module

Capability 是“能做什么”，Module 更接近“由什么能力载体/扩展机制提供”。

### Connection ≠ Credential

连接是可管理的连接关系；Credential 是认证/秘密材料。

### Project ≠ GitHub Repository

Project 可以使用 GitHub Repository，但不应该被 GitHub Repository 定义。

### Conversation ≠ Chat UI

Conversation 可以关联：

- Message
- Resource
- Capability
- Connection
- Permission
- Task
- Execution
- Result
- Evidence
- Verification

因此“对话页”只是 Conversation 的一种 UI 表现，不应反过来限制 Conversation 的长期语义。

### ConstructionLock ≠ Permission

在 APS 中可以理解为：

- Permission：允许不允许修改；
- ConstructionLock：这一次修改执行权当前由谁占用。

这一区分对多 AI、多 Project、多设备协作尤其重要。

---

## 6. 对 APS 的现实压力测试

这些概念之所以值得保留，不是因为理论漂亮，而是因为它们能够解释 APS 的真实场景。

### 一个 Project + Local

`Project → Resource → Local filesystem`

Project 不必等于本地目录。

### 一个 Project + GitHub

`Project → Resource / Reference → GitHub`

GitHub 是外部系统的一种连接/适配方式，而不是 Project 本体。

### 两个 AI Member

`Project
├── Member → AI-1
└── Member → AI-2`

然后分别判断 Capability、Permission、ConstructionLock。

### 一个 AI 使用多个 API

未来可能：

`AI Member
├── Service / Connection A
├── Service / Connection B
└── Service / Connection C`

因此不宜把“一名 AI 永远只能绑定一个 API”写死为底层真理。

### 两个 Project 共用一个 Repository

`Project A ──┐
            ├── GitHub Repository
Project B ──┘`

这说明 Project 与 Repository 更像引用、使用或连接关系，而不是简单的“一对一拥有”。

### Project 完全不使用 GitHub

未来可以存在：

- Project A → GitHub
- Project B → Local
- Project C → Cloud
- Project D → Remote Server
- Project E → Database

如果 APS 的核心仍然成立，那么 GitHub 只是第一批外部连接/模块之一。

---

## 7. APS 应该吸收多少

### 当前应该直接服务于产品

- Project
- Project Member
- Project Conversation
- Resource / Address
- Service / API
- GitHub
- Task
- Execution
- Commit
- Result
- Evidence
- Verification

### 当前不完整实现，但设计上不要堵死

- Resource
- Relation
- Capability
- Connection
- Account
- Identity
- Policy
- Control Domain

### 属于维加级基础设施，当前不做

- 完整身份体系
- 跨控制域授权
- 通用 Connection 基础设施
- 通用 Capability Registry
- Mapping 体系
- 多维对象基础设施
- 跨组织基础设施
- 完整语义演化与迁移体系

---

## 8. APS 与维加的关系

不应把 APS 做成“缩小版维加”。

更准确的关系是：

`AI+ / AI-V1 / 维加
        ↓
  已经思考和试错过的概念
        ↓
       APS
        ↓
选择真正有用的部分
        ↓
    现实产品`

维加解决的是更大范围的基础设施级问题。

APS 解决的是现实产品问题。

两者规模、目标和工程边界不同，但 APS 可以借用维加已经思考过的语义边界。

反过来，APS 的真实工程实践也可以继续验证：

> 哪些维加概念真的有用，哪些只是理论上漂亮。

---

## 9. 使用规则

以后设计 APS 新功能时：

1. **先看 APS 当前正式架构。**
2. 如果遇到 Project / Resource / Relation / Connection / Capability / Permission 等边界问题，再查本文。
3. 本文中的概念是参考，不是强制模型。
4. 如果现实产品需求与旧理论冲突，以当前经过确认的 APS 产品需求和实际工程验证为准。
5. 新概念只有在现实场景中证明有价值，才进入 APS 正式架构。
6. 不为了“完整”而把维加的全部对象搬进 APS。

> **维加留下的是思考空间，不是施工清单。**
