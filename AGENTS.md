# Repository Guide

A-BridgeFS 是 AI 协作与真实执行环境的 Android 落点。

**代码是实现事实；PROJECT 是正式项目资料；AI_WORKSPACE 是施工现场；GitHub 的 Commit / PR / Verify / Release 负责追踪工程事实。**

## 资料分层

- PROJECT：正式项目资料
- AI_WORKSPACE：施工现场
- docs：历史 / 过渡资料，不能与 PROJECT 产生矛盾

## 工作方式

- 修改功能前先读取相关 PROJECT、docs 与当前代码。
- 先确认实际实现，再施工。
- 不把历史计划、候选设计或讨论内容当成当前实现事实。
- 用户负责方向、重大决策与验收；AI 负责技术分析、施工、自检与验证。
- 建议不等于决定；施工不等于完成；Verify 通过才可进入“已验证”。
- Worker / Decision AI 只能作为协作角色标签，不得写成两个永久身份。
- 两个 AI 默认共享工作区允许范围内的读取能力。
- 施工权限属于 Workspace + Repository + Branch；同一 Repository / Branch 同时最多一个 AI 持有修改权。
- 施工权可以授予、释放和转移。
- 不因普通实现问题频繁暂停；只有需要改变既定设计、超出授权、高风险、外部阻塞或需要人授权时才暂停。
- 不修改与当前任务无关的代码、配置或文档。

## 施工 → Commit → Verify

1. 记录实际 Commit SHA。
2. 按该 SHA 检查 Verify 是否真正触发。
3. 使用 Check Runs / Workflow Runs 确认实际工序。
4. 读取对应 Job 的真实结果。
5. 区分 success、failure、cancelled、skipped、in_progress。
6. 没有对应 Run / Check Run 时不得猜测验证结果。

## UI 原则

移动端优先，简洁、明确、低负担。

UI 应让人知道：

- 当前工作区
- 当前 AI / 协作状态
- 当前施工权属于谁
- 当前工作进展
- 是否需要人决定

## 当前实验边界

A-BridgeFS
→ 双 AI 协作
→ Workspace 权限 / 施工锁
→ GitHub / BridgeFS
→ Commit / Verify
→ Receipt

原有本地指令与 BridgeFS 执行能力继续保留。
