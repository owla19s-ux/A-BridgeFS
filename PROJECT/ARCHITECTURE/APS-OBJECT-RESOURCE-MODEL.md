# APS Context / Connection / Resource 模型

更新时间：2026-10-07

## 一、核心关系

```
Space（Connection）
        ↓
Context
        ↓
Connection
        ↓
Resource
        ↓
Connector
        ↓
实际能力
```

Connection 是统一的外部能力抽象；Resource 是 Connection 实际指向或操作的资源。

## 二、Connection 类型

```
Connection
├── Space
├── AI
├── GitHub
├── Local
├── File
├── Device
├── API / Service
├── Plugin
└── 未来其他连接
```

这些是统一 Connection 体系，不要求每一种类型在第一阶段立即拥有完整实现。

## 三、Context

Context 表示一个可以独立承载状态、任务、内容、对话和工作记录的具体上下文。Context 不限定为软件项目。“项目”只是 Context 的一种使用方式。

Context 可以使用多个 Connection：

```
Context A
├── AI Connection
├── GitHub Connection
├── Local Connection
├── File Connection
└── 其他 Connection
```

Context 不等于 Connection，也不等于 Resource。

## 四、Resource

Resource 是 Connection 实际指向、读取或修改的外部资源。多个 Resource 不意味着复制到 APS 内部，也不意味着建立自动同步。

## 五、State

State 是 Context 持续工作所需的权威状态，至少需要能够表达当前目标、Task、Task 依赖、当前阶段、当前执行者、权限、最近实际结果、Receipt / Evidence、Commit / Verify 等外部证据引用和下一步。

State 可以存储在 Context 指定的持久化 Resource 上。State 的存储位置属于 Context 配置。

## 六、Task

Task 是工作的最小交接和调度单位，不等于 AI。第一阶段 Task 包含目标、所需 Connection / Resource、当前阶段、所需权限、当前执行 AI、审查 AI、状态和 Receipt / Evidence。

阶段模型：拆分 → 做 → 审查；审查不通过时返回“做”。

## 七、AI 与权限

AI 是 Connection，不是固定角色。Context 对 AI 授予三个独立权限：拆分、做、审查。权限是能力授权，不是永久身份。

## 八、实际结果与 Receipt

```
AI / Connector 返回
       ↓
实际执行结果
       ↓
审查 / 验证
       ↓
Receipt / Evidence
       ↓
State 更新
```

AI 返回不直接等于事实。对于代码施工，典型证据可以是 Commit、Actions / Verify、文件读取后的实际内容等。

## 九、并行与冲突

Context 可以存在多个 Task，但是否允许多个 AI 同时修改同一 Resource 必须单独判断。第一阶段不预先建立复杂并行执行引擎。

## 十、APS 职责边界

APS 负责：管理 Context；管理 Context 使用的 Connection；根据 Connection 找到 Resource；选择 Connector；调度 Task；接收实际结果；形成 Receipt / Evidence；更新 Context State。

APS 不负责：把所有 Resource 复制到本地；默认建立多端同步；保存所有 AI 的完整长上下文；没有需求时建立复杂 Agent Runtime。