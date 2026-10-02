# A-BridgeFS 分支与 PR 审计

日期：2026-10-02

## 审计基线

- 当前正式基线：`main`
- 当前 HEAD：`5c4bc0dde5089548accfd5347440446a024dedf9`
- 规则：不把历史 feature branch 直接合并到当前 main；先比较唯一变化，再决定提取、保留或废弃。

## PR #1：双 AI 协作

状态：**已关闭，不合并**

原因：

1. 分支 `feature/0.2.1-two-ai-collaboration` 相对当前 main 已落后 62 commits。
2. PR 中的核心实现已经被当前 main 的新协作链路替代，包括：
   - `CollaborationProtocol`
   - `CollaborationTransport`
   - `CollaborationCoordinator`
   - `ApiSecretStore`
   - 当前 `V021Activity` / API 配置
3. PR 中的 `BridgeCollaborationProtocol`、`BridgeToolRuntime` 属于较早的另一套协议/Tool Calling 实验，不应与当前正式协作协议并存。
4. PR 的旧 `BridgeProjectStore` 数据结构也已经被当前 main 的 GitHub/API 工作区结构替代。

处理：2026-10-02 已在 PR 留下审计说明并关闭。

## PR #6：GitHub Workspace 模型

状态：**暂不合并，保留为架构参考**

PR 提出的：

- `GitHubWorkspace` 数据模型
- `GitHubWorkspaceService` 服务边界
- GitHub 授权与工作区 Repository/Branch/权限分离

与当前产品方向一致，且当前 main 仍主要把 GitHub 工作区字段直接放在 `BridgeProject` 中。

因此它不是简单的“旧代码”，而是一个值得继续提取的架构改进。

但 PR 分支相对 main 已严重落后，直接合并会回退大量当前 UI/API/权限实现。

后续应单独创建当前 feature branch，只提取 Workspace 模型与服务边界，并保留当前 GitHubActivity / GitHubApiClient / AccessPolicy 实现。

## 其他历史分支初审

### 已基本被当前 main 吸收

- `feature/v0.2.1-ui`
- `feature/v021-core-collaboration-batch`
- `fix/chat-api-binding-input-text`
- `fix/chat-api-selector-ui`
- `feature/v0.2.1-access-controls`

这些分支的主要功能已能在当前 main 找到对应实现；不应直接合并。

### GitHub 模块历史施工链

- `feature/github-module-v021`
- `feature/github-pat-auth`
- `fix/github-activity-insets`
- `fix/github-activity-syntax`

当前 main 已包含 GitHub Token、PAT、Repository/Branch、全局访问开关及 GitHub Activity 入口，因此这些分支应视为历史施工源。后续只提取仍然缺失的具体能力，不做整分支合并。

### 协作可观测性

`feat/collaboration-observability` 仍值得单独检查。

其主要变化集中在：

- `AppLogger`
- `CollaborationTransport`
- `ApiSettingsActivity`
- `SettingsCategoryActivity`

当前 main 已有分类型日志体系，但需要确认该分支是否包含尚未吸收的日志事件或协作状态记录。

## 当前整理原则

1. `main` 是唯一正式实现基线。
2. feature branch 是施工源，不是第二套正式代码。
3. 历史 PR 不通过“整分支合并”回收。
4. 有价值的旧实现必须以当前 main 为基底重新提取。
5. PROJECT 记录正式架构与状态；AI_WORKSPACE 记录施工任务；旧 docs 逐步迁移，不机械复制。


## 协作可观测性复查结果

`feat/collaboration-observability` 已完成复查。

当前 main 已经具备：

- Runtime / Collaboration / Execution / API / GitHub 五类日志目录
- API 与协作运行日志记录
- 日志查看与复制入口
- Crash 日志

该分支额外提供的 JSONL 协作事件、task/message/reply/duration 字段具有诊断价值，但它依赖该分支的旧协作传输实现。当前不直接合并；如果后续需要更细的 AI 协作可观测性，应在当前 `CollaborationTransport` / `CollaborationCoordinator` 上重新实现事件字段。

## 当前审计结论

本轮没有把任何历史 feature branch 整体合并进 main。

已经完成的实际整理：

- PR #1 已关闭并明确标记为历史实现。
- 分支审计记录已进入 PROJECT/STATUS。
- PR #6 保留为 GitHub Workspace 架构参考。
- 协作可观测性分支确认不直接合并。
- 当前 main 继续保持唯一正式代码基线。

下一阶段最值得施工的不是继续合并旧分支，而是基于当前 main 单独提取 GitHub Workspace 边界，并随后再做一次运行链路审计。
