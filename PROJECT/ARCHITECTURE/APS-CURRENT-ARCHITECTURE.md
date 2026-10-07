# APS 当前架构基线

更新时间：2026-10-07

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
      │         │         │         │
      └─────────┼─────────┘         │
                ↓                   │
             Resource               │
                │                   │
             Connector ←────────────┘
                │
          实际读取 / 修改 / 调用
```

Connection 是统一外部能力抽象；Connector 是具体能力实现边界；Dispatcher 是跨 Connection 的调度分配能力。

## 二、UI 与架构关系

```
APS UI
├── 空间页
│    └── Space / Context 工作面
├── 对话页
│    └── AI Connection 的独立使用入口
└── 设置页
     └── 全局 Connection / 权限 / 验证 / 系统设置
```

## 三、Space / Context

Space 是 Connection 的一种。Context 是 Space 内可独立承载状态、任务、内容和工作记录的上下文。“项目”可以作为 Context 的用户自定义名称或一种使用方式，但不再是 APS 顶层架构根。

## 四、Connection / Resource

第一阶段 Connection 类型正式定义为：

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

Resource 是 Connection 实际指向的外部资源。APS 不默认复制资源，也不因为多个 Connection 自动建立多端同步。

## 五、Connector

Connector 负责把具体 Connection 落到真实能力。

- AI Connector：负责 AI / 模型 API 连接、请求和响应；
- GitHub Connector：负责 Repository、Branch、文件、Commit、Pull Request、Actions / Verify 等 GitHub 能力；
- Local Connector：负责 Android 本地目录和本地文件的读取、修改及相关操作。

其他 Connection 可在后续建立对应 Connector。

## 六、State / Task / Receipt

State 是 Context 持续工作的权威状态。Task 是可分配、可执行、可审查的工作单元。Receipt / Evidence 是实际执行结果及其确认依据。

```
Context
   ↓
Task
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

## 七、Dispatcher

第一阶段职责：
1. 接收用户任务；
2. 读取当前 Context、Connection 和权限；
3. 组织“拆分 → 做 → 审查”；
4. 为当前阶段选择有对应权限的 AI / Connection；
5. 调用对应 Connector；
6. 获取实际结果；
7. 根据实际结果完成或回退任务。

AI 的口头结果不是执行事实。

## 八、AI 权限

AI 属于 Connection 体系。Context 为 AI 配置三个独立能力权限：拆分、做、审查。权限不是固定角色。一个 AI 可以拥有多个权限；多个 AI 可以成为同一阶段候选。同一任务同一时间只能存在一个实际“做”的修改执行者。

## 九、多 AI

多 AI 以 Task 为交接单位。阶段与权限边界已确定；Task 交接、Context 状态共享、Receipt / Evidence 协议、并行 Task、Resource 冲突与合并、Dispatcher 持久化协作状态仍待设计。

## 十、架构原则

1. Connection 是统一外部能力抽象；
2. Connector 是具体 Connection 的实现边界；
3. Space 是 Connection；
4. Context 是工作上下文；
5. Dispatcher 是轻量调度能力；
6. Task 是工作的交接单位；
7. Receipt / Evidence 是结果确认依据；
8. 不建立 Project Address 作为顶层模型；
9. 不恢复固定 Decision / Worker 角色；
10. 代码事实优先于历史文档。