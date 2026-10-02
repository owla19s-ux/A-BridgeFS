# GitHub 仓库分支与合并管理规范

> A-BridgeFS 当前阶段以 `main` 作为唯一开发真相源。分支用于短期隔离修改，不用于长期保存已经合并的历史版本。

## 1. 核心规则

### 1.1 main 是唯一集成分支

- 所有可用代码最终必须合并到 `main`。
- 日常开发、构建、APK 验证以 `main` 为准。
- 不允许把“某个功能已经写在某分支”视为已经进入项目。
- 如果 APK 要验证某项功能，必须先确认该代码已经进入本次构建使用的 `main` Commit。

### 1.2 一个任务一个短期分支

推荐命名：

- `feature/<topic>`：新功能
- `fix/<topic>`：问题修复
- `refactor/<topic>`：重构
- `build/<topic>`：构建/发布链
- `docs/<topic>`：文档

原则：

1. 从最新 `main` 创建。
2. 只处理一个明确任务。
3. 完成后通过 PR 合并。
4. 合并后立即删除分支。
5. 不在旧分支继续堆新功能。

### 1.3 不允许“遗留分支继续开发”

特别注意：

- PR 已合并后，该分支视为历史工作分支，不再作为下一轮开发入口。
- 如果新需求与旧任务有关，也从最新 `main` 新建分支。
- 如果发现代码只存在于旧分支，应先判断它是否已经合并；没有合并就不能假定 main 已经拥有它。

## 2. 标准工作流

```text
main
  │
  ├── 新建短期分支
  │       │
  │       ├── 检查 / 修改 / 测试
  │       │
  │       └── PR
  │             │
  │             └── 合并到 main
  │
  └── 删除已完成分支
```

合并后的实际状态必须回到：

```text
main = 当前唯一有效代码基线
```

然后再开始下一项工作。

## 3. AI 协作要求

为了避免“代码做了但没有进入 APK”：

每次准备构建前必须确认：

1. 当前工作分支。
2. 当前 `main` Commit。
3. 本次修改所在分支。
4. PR 是否已经合并。
5. 合并后的 `main` Commit 是否包含目标修改。
6. GitHub Actions 使用的 Commit 是否与确认的 `main` 一致。
7. APK 来源是否为统一 Release 构建链。

**禁止只看 PR 页面上的代码就认为功能已经进入 APK。**

## 4. 分支清理规则

### 可以直接删除

- 已经合并到 `main` 的短期分支。
- 已关闭且确认没有需要保留代码的分支。
- 明确废弃的实验分支。
- 与当前架构无关的旧版本分支。

### 暂时保留

- 正在开发中的分支。
- 有未合并 PR 的分支。
- 尚未确认是否包含独有代码的分支。

### 删除前检查

删除前至少确认：

- PR 状态；
- 是否 merged；
- 分支是否还有 `main` 没有的有效修改；
- 是否存在未合并的独立工作。

Git 历史已经通过 Commit/PR 保存，删除已合并分支不会删除已合并的 Commit。

## 5. 当前仓库清理计划

### 保留

| 分支 | 状态 | 处理 |
|---|---|---|
| `main` | 当前主线 | **永久保留** |
| `feature/workspace-github-model` | PR #6 仍开放 | 暂时保留，先检查是否仍有价值 |
| `feat/collaboration-observability` | 无 PR | 待确认是否存在独有有效代码 |

### 合并后可删除

以下分支对应的 PR 已经合并到 `main`，原则上均可删除：

- `feature/current-github-workspace-boundary` → PR #14
- `feature/github-module-v021` → PR #7
- `feature/github-pat-auth` → PR #9
- `feature/v0.2.1-access-controls` → PR #2
- `feature/v0.2.1-ui` → PR #3
- `feature/v021-core-collaboration-batch` → PR #13
- `feature/v021-ui-redesign` → PR #5
- `fix/chat-api-binding-input-text` → PR #11
- `fix/chat-api-selector-ui` → PR #12
- `fix/github-activity-insets` → PR #8
- `fix/github-activity-syntax` → PR #10
- `fix/github-module-doc-legacy-key` → PR #15
- `fix/unify-release-build-flow` → PR #32
- `fix/v021-intent-import` → PR #4
- `fix/workspace-conversation-separation` → PR #31
- `refactor/workspace-github-v02` → PR #16

### 建议删除

- `feature/0.2.1-two-ai-collaboration`：PR #1 已关闭且未合并，当前不应作为开发基线。
- 上述无 PR 的 `feat/collaboration-observability`：完成独有代码检查后，若无保留价值则删除。

## 6. 新建分支前检查

以后开始新的开发任务时：

```text
1. 检查 main 最新 Commit
2. 确认没有正在使用的旧分支
3. 从 main 创建新分支
4. 完成一个明确任务
5. PR
6. 合并
7. 回 main
8. 删除旧分支
9. 再开始下一项
```

这样可以避免出现：

```text
main 是旧代码
  ↓
AI 在旧分支继续开发
  ↓
新代码没有合并
  ↓
Actions 构建 main
  ↓
APK 看起来还是旧的
```

这正是本项目当前需要避免的问题。
