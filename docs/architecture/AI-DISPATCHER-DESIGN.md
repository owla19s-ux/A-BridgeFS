# APS AI Dispatcher Design

> 状态：**待重新设计**
>
> 本文件保留为调度器研究资料，不再作为当前 APS 架构基线。

## 1. 当前结论

第一阶段正式确认：

Connection 类型包括 Space、AI、GitHub、Local、File、Device、API / Service、Plugin 及未来其他连接。

当前只确认：

> Dispatcher 是 APS 的轻量调度分配层。

```
用户 / 当前 Context
      ↓
 Dispatcher
      ↓
Connection → 对应 Connector
      ↓
结果
```

Dispatcher 不等于 Agent Runtime。

## 2. 多 AI 协作仍是开放问题

一个 Context 可以使用多个 AI Connection。

当前最小协作路径：

```
当前 AI
 ↓
请求 AI 协助
 ↓
Dispatcher
 ↓
目标 AI
 ↓
协助结果
 ↓
当前 AI
```

尚未决定：

- 任务如何交接；
- 上下文如何共享；
- 工作状态如何同步；
- 结果如何确认；
- 施工权如何转移；
- 多 AI 并行如何避免冲突；
- Dispatcher 是否需要持久化协作状态。

这些问题需要后续单独设计和验证。

## 3. 旧方案降级为研究资料

本文件原先借鉴 Agent Runtime，包含 State / Action / Reducer / Event Bus / Side Effect / Recovery / Sub-Agent Lifecycle 等复杂模型。

这些内容现在只保留为研究背景。

**当前不得直接依据这些内容实现 APS。**

## 4. 第一阶段 Dispatcher 最小职责

1. 接收用户任务；
2. 读取当前 Context、Connection 和权限；
3. 组织“拆分 → 做 → 审查”；
4. 为当前阶段选择有对应权限的 AI / Connection；
5. 获取真实执行结果；
6. 根据审查结果完成或返回“做”。

是否需要更复杂的状态和调度机制，以后由真实多 AI 施工需求证明。
