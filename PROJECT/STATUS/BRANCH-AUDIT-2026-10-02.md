# A-BridgeFS 分支与 PR 审计

日期：2026-10-02

## 审计基线

- 当前正式基线：main
- 本次文档整理后 HEAD：6dcac6e5ca89f998d050c833ec2b97a662089291
- 规则：不把历史 feature branch 整体合并到当前 main；只提取仍然符合当前设计的具体能力。

## PR #1：双 AI 协作

状态：已关闭，不合并。

旧 PR 采用固定 Decision AI / Worker 身份模型，与当前正式设计不一致。

当前正式设计已改为：

- 两个 AI 共享读取能力。
- Decision / Worker 只是任务阶段角色。
- 施工权限属于 Workspace + Repository + Branch。
- 同一 Repository / Branch 同时最多一个 AI 持有施工权。
- 施工权可以授予、释放、转移。

因此不回收 PR #1 的旧身份模型。

## PR #6：GitHub Workspace 模型

状态：暂不合并，保留为架构参考。

其 GitHubWorkspace 数据模型、服务边界以及 Repository / Branch / 权限分离仍值得提取。

但必须以当前 main 和双 AI 施工权模型为基底重新实现。

## 协作可观测性

feat/collaboration-observability 不直接合并。

当前 main 已有分类日志体系。后续如需更细的协作事件，应在当前 CollaborationTransport / CollaborationCoordinator 上重新实现。

## 当前设计文档整理

本轮新增正式架构：

PROJECT/ARCHITECTURE/AI-COLLABORATION-V0.2.md

本轮同步：

- docs/STATUS.md
- docs/REQUIREMENTS.md
- docs/PROJECT-SPEC.md
- docs/A-BRIDGEFS-STAGE-WORKLOG.md
- AGENTS.md

旧 Decision AI ↔ Worker 协议已明确标记为历史文档。

## 当前审计结论

main 继续保持唯一正式代码基线。

下一阶段不是继续合并旧协作分支，而是依据双 AI + Repository / Branch 施工锁设计检查当前代码模型，并逐步实现。
