# Repository Guide

A-BridgeFS 是 AI 协作与真实执行环境的 Android 落点。代码是实现事实，docs 是当前设计、状态、需求与施工记录的正式资料。

## 工作方式

- 修改功能前先读取相关 docs 与当前代码，确认实际实现后再施工。
- 不把历史计划、候选设计或讨论内容当成当前实现事实。
- 用户负责方向、重大决策与验收；AI 负责技术分析、施工、自检与验证。
- 建议不等于决定；施工不等于完成；Verify 通过才可进入已验证状态。
- Worker 在明确授权和边界内连续施工；普通实现问题自行处理，不因小问题频繁暂停。
- 遇到架构冲突、需求歧义、超出授权边界或需要改变既定行为的问题，应暂停并请求决策 AI / 人确认。
- 不修改与当前任务无关的代码、配置或文档。

## 施工 → Commit → Verify

施工完成后必须：

1. 记录实际 Commit SHA。
2. 按该 SHA 检查 Verify 是否真正触发。
3. 使用 Check Runs / Workflow Runs 确认实际工序。
4. 继续读取对应 Job 的真实结果。
5. 区分 success、failure、cancelled、skipped、in_progress。
6. 没有对应 Run / Check Run 时不得猜测验证结果。

Commit Status 为空不能解释为“没有 Verify”。

## AI 协作边界

目标方向：

Decision AI
→ 给出目标、方案、边界与决策
→ Worker
→ 在授权范围内连续施工、自检、验证
→ 遇到真正需要决策的问题再返回 Decision AI
→ Decision AI 给出新决策
→ Worker 继续施工

当前阶段优先验证这条关系，不提前建设多 Worker、复杂调度器或完整平台。

## 回执原则

默认简洁：

【项目】
当前状态：
发现的问题：
下一步：

不默认汇报 CI 内部步骤、Job、Run ID、SHA、日志等细节；只有用户要求或异常需要判断时展开。

## 日志边界

- 协作日志：记录人、AI、项目之间的工作过程，面向人和 AI 共同读取。
- 运行日志：A-BridgeFS 自身运行诊断。
- 崩溃日志：Crash / Exception / Stack trace 等工程诊断。

三者不得混为一个产品日志。

## UI 原则

移动端优先，简洁、明确、低负担。

UI 应让人知道：
- 当前项目
- 当前 AI / Worker 状态
- 当前工作进展
- 是否需要人决定

不把内部实现细节直接堆给用户。

## 当前实验边界

当前核心实验为：

A-BridgeFS
→ 控制 / 对话
→ Worker
→ GitHub 项目
→ Commit / Verify
→ 回执

原有本地指令与 BridgeFS 执行能力继续保留，不因 GitHub 协作实验而删除。
