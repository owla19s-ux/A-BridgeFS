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
- 不再使用 Worker / Decision AI 作为产品或代码中的角色模型。
- 不再使用固定 AI A / AI B 作为成员模型；需要协助时使用 Project Member / 临时 AI。
- 施工权限属于 Project + Repository + Branch；同一 Repository / Branch 同时最多一个 AI 持有修改权。
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

- 当前 Project
- 当前项目主要对话
- 当前 AI / 必要的协助状态
- 当前需要用户处理的状态

Project UI 以项目对话为主体。项目配置独立展开 / 收起。

底部 ↑ / ↓ 只负责屏幕显示切换，不得联动项目配置展开状态。不要把内部 Store / Service / Lock / Verify 等全部直接暴露为首页配置卡片。

## 当前实验边界

A-BridgeFS
→ Project / Default AI
→ 按需 AI 协助
→ Project 权限 / 施工锁
→ GitHub / BridgeFS
→ Commit / Verify
→ Receipt

原有本地指令与 BridgeFS 执行能力继续保留。


## 已废弃架构

Decision AI、Worker AI、固定 AI A / AI B 以及默认双 AI 讨论循环均已从当前产品架构废弃。历史文档中的相关内容仅作为历史记录，不得据此新增代码或恢复旧运行路径。
