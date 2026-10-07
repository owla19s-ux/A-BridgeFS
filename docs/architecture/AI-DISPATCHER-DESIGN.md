# APS AI Dispatcher Design

> 状态：**待重新设计**
>
> 本文件保留为调度器研究资料，不再作为当前 APS 架构基线。

## 1. 当前结论

第一阶段只确认：

> Dispatcher 是 APS 的轻量调度分配层。

```
用户 / 当前 AI
      ↓
 Dispatcher
      ↓
AI Connector / GitHub Connector / Local Connector
      ↓
结果
```

Dispatcher 不等于 Agent Runtime。

## 2. 多 AI 协作仍是开放问题

一个 Project 可以拥有多个 AI Member。

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

1. 接收一个明确请求；
2. 判断目标 AI / Connector；
3. 转发必要上下文；
4. 获取真实结果；
5. 将结果返回给请求方；
6. 在明确需要下一能力时再次分配。

是否需要更复杂的状态和调度机制，以后由真实多 AI 施工需求证明。
