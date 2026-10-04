# GitHub 仓库分支管理与清理注意事项

> A-BridgeFS 当前以 `main` 作为唯一正式施工主线。分支用于短期隔离修改，不用于长期保存历史。

## 1. 核心规则

### 1.1 main 是唯一施工基线

- 正常施工默认直接进入 `main`。
- 构建、APK 验证、项目状态判断均以 `main` Commit 为准。
- 不允许把“代码存在于某个旧分支”视为已经进入项目。
- 新需求如果需要隔离，只从最新 `main` 创建短期分支。

### 1.2 分支必须有明确生命周期

分支只有在以下情况才应该存在：

- 高风险实验；
- 明确需要 PR 审查的独立改动；
- 暂时不能进入 main 的实验性方案；
- 与主线隔离的短期验证。

完成后：

```
分支 → 检查 → PR/合并 → main → Verify → 删除分支
```

**合并后不继续把旧分支当施工入口。**

### 1.3 Git Commit 已经保存历史

不要为了“保留历史”长期保留分支。

- Commit 保存代码历史；
- PR 保存合并关系；
- Tag 适合保存明确版本；
- 分支只表示当前仍存在的工作线。

## 2. 当前架构特别注意

A-BridgeFS 已经从旧的固定双 AI 模型转为 **Project-centered** 模型。

以下历史架构已经废弃：

- Decision AI / Worker AI 固定分类；
- 固定 AI A / AI B；
- 旧双 AI 协作循环；
- 旧 Workspace-centered 模型。

因此，历史分支即使存在独有 Commit，也不能因为“还有代码”就默认继续保留。必须判断这些代码是否属于当前架构。

## 3. 分支清理标准

### A. `ahead=0`

相对 `main` 没有独有 Commit。

如果没有特殊保留理由，**直接列入删除清单**。

### B. `ahead>0`

必须继续检查独有 Commit：

1. 是否已经被等价修改吸收到 main；
2. 是否只是旧架构代码；
3. 是否还有当前架构需要的有效内容；
4. 是否存在未合并 PR；
5. 是否需要把少量有效内容迁移到 main。

不能仅凭 ahead 数量决定保留。

### C. main

`main` 永久保留，是当前唯一正式施工基线。

## 4. 2026-10-04 分支审查

本次检查发现仓库共有 **25 个分支**。

当前 main：

`24557a08b49e6915392589e8670a5b1b6b95304f`

### 已确认没有独有 Commit，可直接列入清理

| 分支 | 相对 main |
|---|---:|
| `docs/repository-branch-management` | ahead 0 / behind 225 |
| `feature/current-github-workspace-boundary` | ahead 0 / behind 348 |
| `feature/v021-ui-redesign` | ahead 0 / behind 404 |
| `fix/chat-scroll-collaboration-feedback` | ahead 0 / behind 212 |
| `fix/github-module-doc-legacy-key` | ahead 0 / behind 346 |
| `fix/ui-chat-workspace-feedback-batch` | ahead 0 / behind 119 |
| `fix/unify-release-build-flow` | ahead 0 / behind 227 |
| `fix/v021-intent-import` | ahead 0 / behind 406 |
| `fix/workspace-conversation-separation` | ahead 0 / behind 231 |
| `fix/workspace-directory-ui` | ahead 0 / behind 216 |
| `refactor/workspace-github-v02` | ahead 0 / behind 241 |

这些分支相对当前 main 已没有独有 Commit。它们属于历史施工线，除非发现外部依赖，否则应清理。

### 仍需进一步审查再清理

以下分支存在独有 Commit：

- `feat/collaboration-observability` — ahead 10
- `feature/github-module-v021` — ahead 14
- `feature/github-pat-auth` — ahead 4
- `feature/v0.2.1-access-controls` — ahead 9
- `feature/v0.2.1-ui` — ahead 3
- `feature/v021-core-collaboration-batch` — ahead 7
- `feature/workspace-github-model` — ahead 4
- `feature/0.2.1-two-ai-collaboration` — ahead 23
- `fix/chat-api-binding-input-text` — ahead 5
- `fix/chat-api-selector-ui` — ahead 2
- `fix/github-activity-insets` — ahead 1
- `fix/github-activity-syntax` — ahead 1
- `fix/ui-separation-settings-github-test` — ahead 25

其中旧双 AI / 协作 / Workspace 模型相关分支属于高风险历史分支，应优先判断其独有 Commit 是否已经被当前架构替代。

## 5. 本次清理顺序

### 第一批

清理上述 **ahead=0** 的 11 个历史分支。

### 第二批

逐个检查 13 个仍有独有 Commit 的分支：

```
独有 Commit
  ↓
是否已等价进入 main？
  ├─ 是 → 删除
  └─ 否
      ↓
是否属于当前 Project-centered 架构？
  ├─ 否 → 作为废弃历史清理
  └─ 是 → 迁移有效内容 → 删除旧分支
```

### 第三批

最终目标：

```
main
└── 少量明确存在的临时/实验分支
```

而不是几十个历史施工分支。

## 6. AI 施工注意事项

以后每次开始施工前：

1. 检查当前 `main` Commit；
2. 确认是否存在正在使用的旧分支；
3. 正常任务优先直接施工 main；
4. 如必须分支，明确分支用途；
5. 一个任务结束后合并；
6. Verify 使用合并后的 main Commit；
7. 清理完成分支。

**不要为了避免直接修改 main 而机械创建分支。**

真正需要控制的是代码是否可追溯、是否可验证，而不是分支数量。

## 7. 当前清理状态

状态：**清理进行中**

- 分支总数：25
- main：1
- 已确认无独有 Commit：11
- 有独有 Commit、待进一步审查：13
- 目标：回到单一 main 主线 + 少量必要实验分支

> 注意：当前 GitHub 连接提供了读取、创建和移动 Branch Ref 的能力，但没有提供安全的 Branch Delete 操作。因此本轮可以完成审查、分类和迁移判断；实际删除动作需要 GitHub 侧可用的删除分支权限/入口，不能用移动 Ref 冒充删除。
