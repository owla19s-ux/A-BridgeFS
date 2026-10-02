# Repository Inventory

> 文档性质：**仓库审计快照**，不是实时 Git 状态。
>
> 本文件记录某次审计时观察到的 main、Branch、PR 状态。判断当前代码时，必须重新读取 GitHub 当前 `main`、Commit 和 PR 状态。

更新时间：2026-10-02

## 审计基线

本次快照记录的正式基线：

`main`

```
ba72337485372ea7cd5ebb1d85f36819811359f6
```

当时的原则：**main 是唯一正式代码基线。**

> 注意：上面的 SHA 只代表本次快照时的 main，不应作为后续施工的永久当前 SHA。

---

## 当时的 Branch 快照

以下 Branch 在 2026-10-02 审计时明显落后于当时的 main，且多数与 main 已发生分叉。

| Branch | 相对当时 main | 当时处理 |
|---|---:|---|
| `feat/collaboration-observability` | +10 / -36 | 待审计 |
| `feature/github-module-v021` | +14 / -28 | 待审计 |
| `feature/github-pat-auth` | +4 / -26 | 待审计 |
| `feature/v0.2.1-access-controls` | +9 / -35 | 待审计 |
| `feature/v0.2.1-ui` | +3 / -33 | 待审计 |
| `feature/v021-core-collaboration-batch` | +7 / -22 | 待审计 |
| `feature/v021-ui-redesign` | +0 / -29 | 可视为历史分支 |
| `feature/workspace-github-model` | +4 / -28 | 待审计；可能包含尚未吸收的 GitHubWorkspace 模型 |
| `feature/0.2.1-two-ai-collaboration` | +23 / -49 | 待审计；包含历史双 AI 施工 |
| `fix/chat-api-binding-input-text` | +5 / -24 | 待审计 |
| `fix/chat-api-selector-ui` | +2 / -23 | 待审计 |
| `fix/github-activity-insets` | +1 / -27 | 待审计 |
| `fix/github-activity-syntax` | +1 / -25 | 待审计 |
| `fix/v021-intent-import` | +0 / -31 | 可视为历史分支 |

格式“+A / -B”表示相对当时 main 的 ahead / behind。

### 后续处理规则

不要因为 Branch 落后就直接删除。

每个历史分支需要先确认：

1. 是否存在当时 main 尚未吸收的独立功能。
2. 独立功能是否仍符合当前架构。
3. 是否已经被其他 Commit 以不同实现方式吸收。
4. 是否只是旧版本实现。
5. 是否仍有开放 PR 引用。

确认无价值后，再关闭对应 PR（如有）并清理分支。

---

## 当时的开放 PR

### PR #1

`feat: A-BridgeFS 0.2.1 dual AI collaboration`

分支：

`feature/0.2.1-two-ai-collaboration`

当时判断：

- main 已经包含双 AI 协作的主要实现。
- PR 分支与 main 已明显分叉。
- 分支仍包含部分历史 AI 协作代码与文档。
- **不能直接合并。**
- 在完成历史功能审计前保留。

### PR #6

`refactor(workspace): model GitHub as a collaboration resource`

分支：

`feature/workspace-github-model`

当时判断：

- main 已有 GitHub Workspace 相关 UI / 存储能力。
- 搜索 main 未发现 `GitHubWorkspace` 独立模型类。
- PR 仍可能包含尚未吸收的架构内容。
- **不能直接关闭或合并。**
- 后续单独检查其模型设计是否值得吸收。

---

## 审计原则

### 不做

- 不把所有 Feature Branch 合并到 main。
- 不把旧 Branch 当最新版。
- 不因为 Branch 落后就认为它完全没有价值。
- 不在没有比较 Commit 的情况下删除分支。
- 不为了“整洁”重写 Git 历史。

### 要做

```
Branch
 ↓
Compare
 ↓
Identify unique changes
 ↓
判断是否仍需要
 ↓
需要 → 提取到新的当前分支
不需要 → 标记历史
 ↓
关闭旧 PR
 ↓
清理旧 Branch
```

### 当前真源

本文件只负责保存审计历史。

判断现在的仓库状态时，以：

```
GitHub 当前 main
 ↓
当前 Commit
 ↓
当前 PR / Actions / Check Runs
```

为准。

---

## 最终整理目标

仓库最终应该形成：

```
main
 │
 ├── PROJECT/          正式项目资料
 ├── AI_WORKSPACE/     AI 施工区
 ├── app/              正式代码
 ├── .github/          CI/CD
 └── docs/             过渡 / 历史 / 专项资料
```

资料迁移完成后，再进一步收缩 `docs/`，而不是现在直接大规模移动文件。
