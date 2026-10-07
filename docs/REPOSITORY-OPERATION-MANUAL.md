# APS 仓库操作使用手册

更新时间：2026-10-04

> 本手册不是通用 Git 教程，而是按 APS 当前项目结构、AI 协作方式和 GitHub 工作流定制的实际操作规则。

## 1. 核心定位

仓库：owla19s-ux/APS

长期分支只有 main。main 是当前唯一正式施工基线，也是代码、文档、构建和验证判断的主要基准。

核心原则：代码决定实现事实；GitHub Commit / Actions / APK / 真机结果决定工程事实；PROJECT 负责正式设计与规格；docs 负责任务、审查、专项和过渡资料。

## 2. 开工前：先审查，再施工

任何施工先检查：
- 当前 main Commit；
- 相关 PROJECT 与 docs；
- 当前代码实际实现；
- 是否存在旧架构残留；
- 是否有正在进行的相关施工；
- 是否需要临时分支；
- 如何验收。

AI 不得把历史计划当成当前实现，不得把建议说成决定，不得把代码存在说成完成，不得猜测不存在的 Verify 结果。

## 3. main 与临时分支

默认直接施工 main。小修复、文档更新、明确的低风险连续修改，不需要机械创建分支。

以下情况适合临时分支：大型 UI 改造、架构重构、构建/签名链修改、高风险 GitHub/权限改动、实验性方案、明确需要独立 PR 审查的工作。

标准流程：

最新 main → 临时 branch → 连续施工/Commit → Build/Verify → 检查 Diff → PR → 合并 main → 再次 Verify main → 删除临时 branch。

分支是隔离工具，不是历史档案。Commit 和 PR 已经保存历史，完成后的临时分支不需要长期保留。

禁止：从旧分支继续新项目；合并后继续使用旧分支；为了规范机械创建分支；用分支数量代替工程管理。

## 4. Commit

Commit 应代表能够说明清楚的实际变更，例如：fix、feat、refactor、docs。

每次重要施工记录：Commit SHA、修改内容、是否触发 Actions、Run ID、Verify 结果。

Commit 不等于完成。正确链路是：Commit → Actions → Build/Check → Verify → APK → 真机。

## 5. Pull Request

PR 用于需要隔离、审查或合并的独立工作。

PR 至少说明：做了什么、为什么做、影响哪些模块、是否改变架构、如何验证、对应 Commit、已知问题。

合并前检查 Diff、旧架构残留、文档同步、Actions、冲突和当前 Project 模型。合并后以 main 为新的施工基线，不继续在旧 PR 分支施工。

## 6. GitHub Actions

不要只看“构建成功”或界面提示。检查链路：Commit SHA → Workflow Run → Job → Step → Result → Artifact。

必须区分 success、failure、cancelled、skipped、in_progress。没有对应 Run / Check Run，不得声称已经构建或验证。

GitHub 官方说明：Workflow Run 对应触发它的 Commit/Ref；Run 中可以查看 Job 和 Step 的真实状态与日志。失败时应进入具体 Job 查看失败 Step。

## 7. 构建与 APK

APS 当前尚无公开发行版本，因此内部构建统一使用正式签名身份，不再人为区分测试签名与正式签名。

标准链路：main → GitHub Actions → 正式 Release 签名构建 → Artifact/APK → 安装 → 真机验证。

APK 验证至少记录：Commit SHA、Workflow/Build 编号、Run ID、APK、文件大小、SHA-256、签名状态、安装结果、真机结果。

Artifact 是 Workflow 产生的构建文件，可在 Run 完成后下载。它必须能追溯到对应 Run 和 Commit。

## 8. Verify

Verify 必须回答“验证的是哪一次代码”。

推荐记录：Verify 类型、编号、Run ID、Commit、Job、Result。

Build success 只证明构建工序成功，不证明 UI、状态保存、业务链路或真机行为正确。

## 9. 真机验收

Android 功能的完整验收链：代码 → UI 入口 → 用户操作 → 状态保存/恢复 → 真实业务行为 → 正式 APK → 安装 → 真机验证。

例如 AI Connection：添加 → 配置 API → 授予 Context 权限 → 保存 → 离开 Context → 重新进入 → 确认状态仍在 → 发起对话 → 确认实际使用正确 Connection。

只有真实链路完成，才能标记“已验证”。

## 10. 文档维护

PROJECT：当前正式架构、UI、规格、状态。
docs：当前任务、审查、专项说明、历史/过渡资料、构建检查、仓库规则。
AGENTS.md：AI 施工总原则、事实优先级、Verify 规则和架构禁区。

如果代码改变产品行为，应检查代码、PROJECT、docs、AGENTS 是否仍一致；不要为了“看起来同步”修改无关文件。

## 11. 当前架构禁区

以下内容已废弃，不得从历史 Commit、旧分支或旧文档恢复：
- Decision AI；
- Worker AI；
- 固定 AI A / AI B；
- 默认双 AI 循环；
- Project / Workspace-centered 新模型；
- 把 GitHub 做成第二套工作区；
- 普通 Context 问题自动多 AI；
- 复杂 Context Dashboard；
- 无限 Agent Loop。

当前模型：
Space
→ Address / Resource
→ Context
→ 可组合 Connection / 能力
→ 按需 Task / Dispatcher
→ 实际结果
→ Receipt / Evidence / Verify

Space 属于 Connection 体系，但承担连接与资源底座职责；Address + Resource 是固定基础，Task 不是所有 Context 的固定组成部分。

## 12. 连续施工

一次任务按当前阶段推进：拆分 → 做 → 实际执行结果 → 审查；代码任务可继续 Commit → Verify → 必要时返回“做”。

正常施工不要求用户每一步点击“继续”。仅在需要用户决定、权限/资源不足、明确阻塞、达到合理迭代上限或需要改变既定架构/产品行为时暂停。

## 13. 分支清理

检查顺序：是否 main → 是否仍在施工 → 是否有未合并且有价值的 Commit → 是否已等价进入 main → 是否属于废弃架构 → 是否有未关闭 PR。

完成后的历史分支应删除。判断不能只看分支名称或 ahead 数量。

## 14. 出问题时

构建失败：Commit → Run → 失败 Job → 失败 Step → 日志 → 判断代码/配置/环境/签名 → 修复 → 新 Commit → 重新 Build/Verify。

没有 Run：先确认 Commit 是否进入触发范围、Workflow trigger 是否匹配、Workflow 文件是否存在于对应 Commit、是否被取消、是否真的产生 Run。没有证据就保持“未验证”。

APK 与预期不一致：检查 APK 对应 Commit、Build Run、Workflow 使用的 SHA、Artifact 与 main 当前 SHA，不要直接假定下载文件就是最新代码。

## 15. 日常模板

开始：检查 main → 查 PROJECT/docs → 查代码 → 明确目标 → 判断是否需要 branch。

施工：修改 → Commit → 检查 Diff → Build → Verify → 必要时继续。

收口：确认代码 → 更新必要文档 → Build → Verify → APK → 真机 → 更新状态 → 删除临时 branch。

## 16. 最重要的规则

1. main 是唯一长期施工主线。
2. 分支是隔离工具，不是历史仓库。
3. 先审查，再施工。
4. Commit 不等于完成。
5. Build 成功不等于功能验证成功。
6. 没有 Run / Check Run 就不能声称已验证。
7. Verify 必须绑定 Commit。
8. APK 必须能追溯到 Commit / Run。
9. 代码、UI、文档必须围绕同一个当前架构。
10. 旧架构可以留在历史里，但不能从历史里复活。
11. 不为了“规范”机械制造分支。
12. 真正的完成标准是：代码 → UI → 用户操作 → 状态 → 真实行为 → APK → 真机。

## 17. 当前状态

- 长期分支：main
- 旧历史分支：已清理
- 当前产品模型：Space / Address / Resource / Context / Connection
- 固定 Decision / Worker：废弃
- 固定 AI A / AI B：废弃
- Space / Context UI：开发中
- 正式签名构建：统一
- Secretary / Orchestrator：未来能力
- 普通“对话”页：暂缓大改

本手册属于仓库操作规范。未来 GitHub 工作流、构建链或 APS 架构发生变化时，应同步更新。