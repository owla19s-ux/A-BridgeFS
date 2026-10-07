# Repository Guide

APS 第一阶段定位：

> **APS = AI 与外部能力之间的连接器 + 调度分配器。**

代码是实现事实；PROJECT 是正式项目资料；AI_WORKSPACE 是施工现场；GitHub Commit / PR / Verify / Release 负责追踪工程事实。

## 资料分层

- `PROJECT/`：当前正式产品、架构与 UI 资料；
- `AI_WORKSPACE/`：施工现场；
- `docs/`：状态、任务、专项、过渡与历史资料；
- `docs/bridgefs-assets/`：BridgeFS 历史资料，不得作为当前 APS 架构依据。

## 当前架构边界

```text
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
        ↓
   Connector
        ↑
    Dispatcher
        ↑
Context / Task / 权限
```

Space 属于 Connection 体系，但承担连接与资源底座职责；Address + Resource 是 Space 的固定基础。Context 是工作上下文；AI 是 Connection；Dispatcher 负责按需组织“拆分 → 做 → 审查”等阶段。

Connector 负责真实外部能力边界；Dispatcher 不得在没有需求和证据时扩展为完整 Agent Runtime。

## 项目页规则

空间页 / Context 工作面负责展示和操作：

- 当前 Context；
- Context 使用的 Connection / Resource；
- AI Connection 及其 Context 权限；
- Context 主要对话；
- 请求 AI 协助；
- 当前施工、Commit、Verify 等状态。

项目页中的状态卡、配置和记录主要服务于人类查看与操作，不应反向定义底层业务架构。

## 多 AI 协作规则

一个 Context 可以使用多个 AI Connection。

当前只确认：

```
当前 AI
 ↓
请求 AI 协助
 ↓
其他 AI Member / 临时 AI
 ↓
协助结果
 ↓
当前 AI 继续
```

以下内容目前**未定型**，不得擅自实现为正式架构：

- 多 AI 固定角色；
- Decision AI / Worker AI；
- Secretary / Orchestrator；
- 复杂 Agent Runtime；
- 多 AI 无限自动循环。

真正的多 AI 施工协议需要单独设计并经过确认。

## Local / GitHub

- Local Connection → Local Connector → Android 本地目录；
- GitHub Connection → GitHub Connector → GitHub API → 远程 Repository。

BridgeFS 已不属于当前 APS 产品。旧 BridgeFS 代码和文档只能视为遗留资料；本地能力继续存在时，应按 Local Connector 重新理解。

## 工作方式

- 修改功能前先读取相关 PROJECT、docs 与当前代码；
- 先确认实际实现，再施工；
- 历史设计不能被当成当前实现事实；
- 不修改与当前任务无关的代码、配置或文档；
- 建议不等于决定，代码存在不等于功能完成；
- Verify 通过才可标记为已验证。

## Commit → Verify

1. 记录 Commit SHA；
2. 检查对应 Workflow / Check Run 是否真正触发；
3. 检查 Job 真实结果；
4. 区分 success / failure / cancelled / skipped / in_progress；
5. 没有对应 Run / Check Run 时不得猜测验证结果。

## 已明确废弃

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 默认双 AI 讨论循环；
- Workspace-centered 新产品模型；
- BridgeFS 作为当前产品模块。

历史资料可以保留用于追溯，但不得据此恢复新架构。
