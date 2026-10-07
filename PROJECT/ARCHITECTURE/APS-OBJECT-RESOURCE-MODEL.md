# APS Context / Connection / Resource 模型

更新时间：2026-10-07

## 一、核心关系

```
                    Space
             【连接与资源底座】
                       │
             ┌─────────┴─────────┐
             │                   │
          Address             Resource
           定位                实际内容
             │                   │
             └─────────┬─────────┘
                       │
                  Context
                       │
                可组合能力
                       │
       Connection / Task / Conversation / ...
                       │
                   Connector
                       │
                    实际能力
```

**固定的是定位与实际内容；能力是可组合的。**

Space 属于 Connection 体系，但承担连接与资源底座职责；Address 与 Resource 是其固定基础。

## 二、Connection 类型

```
Connection
├── Space      ← 特殊：连接与资源底座
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

## 三、Address / Resource

Address 是定位信息，至少包含名称、地址、备注。一个 Space / Context 可以存在多个 Address。

Resource 是 APS 实际可使用、读取或修改的内容。Resource 与 Address 不等同：Address 回答“在哪里”，Resource 表示“实际有什么”。

Connection 提供访问、调用或管理能力，Connector 将对应 Connection 落到真实能力。

## 四、Context

Context 表示 Space 内的具体工作上下文。Context 不固定承载 Task、Conversation、State、内容或工作记录的全部组合，而是根据场景组合所需能力。

例如：

```
软件开发 Context
├── GitHub
├── Local
├── AI
├── Task
└── Dispatcher

图片创作 Context
├── Local
├── File
├── AI
└── 可能没有 Task
```

Context 不等于 Connection，也不等于 Resource。

## 五、State

State 是 Context 持续工作所需的权威状态。是否需要 State、Task 或其他记录能力，由具体 Context 的使用场景决定。

需要任务调度时，State 可以表达当前目标、Task、Task 依赖、当前阶段、当前执行者、权限、最近实际结果、Receipt / Evidence、Commit / Verify 等外部证据引用和下一步。

## 六、Task

Task 是工作的交接和调度单位，不等于 AI，也不是所有 Context 的固定组成部分。需要任务调度的 Context 才建立 Task。

第一阶段 Task 包含目标、所需 Connection / Resource、当前阶段、所需权限、当前执行 AI、审查 AI、状态和 Receipt / Evidence。

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

APS 负责：管理 Space；管理 Space / Context 使用的 Address、Resource 与 Connection；根据 Address 定位 Resource；选择 Connector；按需调度 Task；接收实际结果；形成 Receipt / Evidence；更新 Context State。

APS 不负责：把所有 Resource 复制到本地；默认建立多端同步；保存所有 AI 的完整长上下文；没有需求时建立复杂 Agent Runtime。