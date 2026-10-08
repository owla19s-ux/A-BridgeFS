# APS 当前架构基线

更新时间：2026-10-08

## 一、第一阶段架构

```
                         APS
                          │
                ┌─────────┴─────────┐
                │                   │
          Connection             Dispatcher
                │                   │
      ┌─────────┼─────────┐         │
      ↓         ↓         ↓         │
    Space      AI      GitHub ...    │
      │                             │
      └── 目录 → Context             │
             └── Address[] → Resource│
                                     │
             可组合能力 ─────────────┘
                │
             Connector
                │
          实际读取 / 修改 / 调用
```

Connection 是统一外部能力抽象；Connector 是具体能力实现边界；Dispatcher 是跨 Connection 的调度分配能力。

## 二、UI 与架构关系

```
APS UI
├── 空间页
│    └── Space → 目录 → Context 工作面
├── 对话页
│    └── AI Connection 的独立使用入口
└── 设置页
     └── 全局 Connection / 权限 / 验证 / 系统设置
```

## 三、Space / 目录 / Context / Address / Resource

Space 是 APS 中负责组织目录与 Context 的基础工作空间。它属于 Connection 体系，但不是普通 Connection 的简单同类；Space 的核心职责是组织 Space 名称、目录和 Context。

Space 的组织结构是：

```
Space
├── 名称
└── 目录
    ├── Context（项目）
    │   ├── 名称
    │   ├── Address[]
    │   │   ├── 名称
    │   │   ├── 地址
    │   │   └── 备注
    │   └── 可组合能力
    └── ...
```

目录是 Space 内的分组管理结构；Context 是实际项目 / 工作上下文；Address 是 Context 的项目地址记录，可以有多个。

Resource 是 Address 所定位并由 Connection / Connector 实际使用的内容。Address 与 Resource 不等同。

Context 是 Space 内的具体工作上下文。Context 不预设固定的 Task、Conversation、State 或工作记录组合，而是根据使用场景组合所需能力。

### 软件开发结构能力

软件开发类 Context 可以额外组合“软件开发结构”能力。它不建立新的顶层 Project 对象，而是在 Context 内维护统一的结构关系。

```text
Context
└── 软件开发结构
    ├── 目标
    ├── UI / 交互
    ├── 功能
    ├── 架构
    ├── 施工点
    ├── 施工卡
    ├── 代码 / 产物
    └── 验证
```

结构层负责描述关系，Task 负责实际工作交接，Resource / Connection 提供实际材料和外部能力，Receipt / Evidence / Verify 提供事实依据。关系允许一对多和多对多；UI 可以将同一结构以树、表、关系或施工视图呈现，但这些视图共享同一结构数据。

## 四、Connection / Resource / Connector

第一阶段 Connection 类型正式定义为：

```
Connection
├── Space      ← 特殊：工作空间 / 组织底座
├── AI
├── GitHub
├── Local
├── File
├── Device
├── API / Service
├── Plugin
└── 未来其他连接
```

Connection 提供连接、访问、调用或管理能力；Resource 是实际内容。Address 用于定位资源，不等同于 Resource。

Connector 负责把具体 Connection 落到真实能力。

- AI Connector：负责 AI / 模型 API 连接、请求和响应；
- GitHub Connector：负责 Repository、Branch、文件、Commit、Pull Request、Actions / Verify 等 GitHub 能力；
- Local Connector：负责 Android 本地目录和本地文件的读取、修改及相关操作。

其他 Connection 可在后续建立对应 Connector。

## 五、State / Task / Receipt

State 是 Context 持续工作时需要的权威状态。Task 是可分配、可执行、可审查的工作单元；Receipt / Evidence 是实际执行结果及其确认依据。

Task 不是每个 Context 的固定组成部分。只有需要任务调度的 Context 才建立 Task。

```
Context
   ↓
可选 Task
   ↓
Dispatcher
   ↓
Connection / AI
   ↓
实际执行
   ↓
Receipt / Evidence
   ↓
State 更新
```

## 六、Dispatcher

第一阶段职责：
1. 接收用户任务；
2. 读取当前 Context、Connection 和权限；
3. 组织“拆分 → 做 → 审查”；
4. 为当前阶段选择有对应权限的 AI / Connection；
5. 调用对应 Connector；
6. 获取实际结果；
7. 根据实际结果完成或回退任务。

AI 的口头结果不是执行事实。

## 七、AI 权限

AI 属于 Connection 体系。Context 为 AI 配置三个独立能力权限：拆分、做、审查。权限不是固定角色。一个 AI 可以拥有多个权限；多个 AI 可以成为同一阶段候选。同一任务同一时间只能存在一个实际“做”的修改执行者。

## 八、多 AI

多 AI 以 Task 为交接单位。阶段与权限边界已确定；Task 交接、Context 状态共享、Receipt / Evidence 协议、并行 Task、Resource 冲突与合并、Dispatcher 持久化协作状态仍待设计。

## 九、架构原则

1. Connection 是统一外部能力抽象；
2. Connector 是具体 Connection 的实现边界；
3. Space 是负责组织目录与 Context 的基础工作空间，并属于 Connection 体系；
4. 目录用于分组管理 Context；
5. Context 是实际项目 / 工作上下文，并可维护多个 Address（名称 / 地址 / 备注）；
6. Resource 由 Address 定位并由 Connection / Connector 实际使用；
7. Context 是可组合能力的具体工作上下文，不是固定对象集合；
8. Dispatcher 是轻量调度能力；
9. Task 是工作的交接单位，但不是所有 Context 的固定组成部分；
10. Receipt / Evidence 是结果确认依据；
11. 不建立 Project Address 作为顶层模型；
12. 不恢复固定 Decision / Worker 角色；
13. 代码事实优先于历史文档。