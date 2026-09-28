
# A-BridgeFS 跨项目资产复用矩阵 V0.1

## 1. 总体关系

BridgeFS 提供本地执行能力。

AI-V1 提供 API、Role、Task、Permission、Runtime 的设计基础。

Weijia 提供 Identity、Account、Connection、Authorization、Policy 等基础语义。

最终组合：

BridgeFS + AI-V1 + Weijia → A-BridgeFS / BridgeFS+

## 2. 复用矩阵

| 能力 | BridgeFS | AI-V1 | Weijia | 第一版 |
|---|---|---|---|---|
| Command | 已实现 | Tool/Action | Action/Execution | 直接迁移 |
| CommandParser | 已实现 | Interaction/Tool | Action | 直接迁移 |
| Executor | 已实现 | Runtime | Execution | 重构迁移 |
| PathSecurity | 已实现 | Permission | Authorization/Policy | 直接迁移并接授权 |
| Receipt | 已实现 | Result/Evidence | Result/Verification | 重构迁移 |
| Android Service | 已实现 | Runtime | Service | 直接迁移 |
| Overlay | 已实现 | Floating | Interaction/View | 参考重写 |
| API Provider | 无 | 有设计基础 | Connection | 参考重写 |
| Account | 简单配置 | ExternalAccount | Account | 参考重写 |
| Connection | 无 | API/WEB/OAUTH/LOCAL | Connection | 参考重写 |
| Role | 无 | 核心设计 | 最小模型 | 采用语义并轻量实现 |
| Capability | Command 隐含 | 核心概念 | 核心语义 | 轻量实现 |
| Permission | PathSecurity 隐含 | V1 Permission | Authorization | 重构迁移 |
| Policy | 无 | Permission/Policy | Policy | 最小实现 |
| Task | 无 | 核心 | Task/Execution | 新增轻量实现 |
| ExecutionRecord | 日志/结果 | ExecutionRecord | Decision/Execution | 新增 |
| Work Context | 无 | 已有模型 | Context/Mapping | 预留 |
| Project | root 隐含 | Project | Project | 暂缓 |
| Mapping | 无 | 候选 | Mapping | 暂缓 |
| Verification | 无正式对象 | 已有设计 | Verification | 第一版保留结果状态 |
| Collaboration | 无 | Role Collaboration | Relation | 暂缓 |
| Multi-Agent | 无 | 有设计 | 非第一版重点 | 暂缓 |
| Scheduler | 无 | Orchestration | Lifecycle | 暂缓 |
| NeuroMesh | 无 | 外部接口 | 独立认知系统 | 不迁移内部实现 |

## 3. 第一版核心

Provider → Connection → Role → Task → Command → Permission/Policy → Executor → Receipt/ExecutionRecord

## 4. Role 与 API

Provider/Model 不等于 Role。

一个 Provider 可以有多个 Model；一个 Role 可以更换 Model；一个 Model 可以被多个 Role 使用。

示例：

GPT → 主AI  
DeepSeek → 执行者

也可以：

DeepSeek → 主AI  
DeepSeek → 执行者

## 5. 权限

最小判断输入：

Subject + Role + Capability + Resource + Action + RiskLevel + Policy

输出：

ALLOW / WAITING_CONFIRMATION / DENY

重点是长期授权，而不是每次操作都人工确认。

## 6. 本地信任边界

AI 不直接操作 Android。

AI/Role → Task → Command → Permission/Policy → BridgeFS Executor → Android → Receipt

即使 Provider 或 Role 更换，也不能绕过权限层。

## 7. 云端与本地

云端：授权后尽量直接执行。

本地：授权后在授权范围内自动执行；超范围或高风险操作按 Policy 确认或拒绝。

## 8. 第一版目标

不是把三个项目全部迁移，而是把它们的成熟资产压缩成一个最小可运行执行层。
