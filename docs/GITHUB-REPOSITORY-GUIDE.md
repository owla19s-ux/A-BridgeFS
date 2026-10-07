# APS GitHub 仓库协作入口

更新时间：2026-10-04

> 本文件是 **导航页**，不是第二套仓库操作手册。
>
> 详细的 GitHub / Branch / Commit / PR / Actions / APK / Verify 操作规则统一维护在：
> `docs/REPOSITORY-OPERATION-MANUAL.md`

## 1. 当前仓库基线

- Repository：`owla19s-ux/APS`
- 长期分支：`main`
- 产品：APS（AI + Context + Service）
- 当前产品模型：Space / Context / Connection
- 当前正式架构：`PROJECT/ARCHITECTURE/APS-CURRENT-ARCHITECTURE.md`
- 当前正式规格：`PROJECT/SPEC/APS-PRODUCT-SPEC.md`
- 当前正式 UI：`PROJECT/UI/`
- 当前状态：`docs/STATUS.md`
- 当前任务：`docs/CURRENT-TASKS.md`
- 仓库操作：`docs/REPOSITORY-OPERATION-MANUAL.md`
- 分支规则：`docs/REPOSITORY-BRANCH-MANAGEMENT.md`

## 2. 事实来源

判断“现在到底是什么状态”时按以下顺序：

```
实际代码
 ↓
GitHub Commit
 ↓
Actions / Check Runs
 ↓
APK / Artifact
 ↓
真机结果
 ↓
当前 PROJECT / docs
 ↓
历史资料
```

不要用旧 Branch、旧 PR、旧 APK 文件名或历史文档替代当前 main 的实际内容。

## 3. 开始施工

统一采用：

```
先看 main
 ↓
读取相关 PROJECT / docs
 ↓
检查真实代码入口与调用链
 ↓
识别旧实现 / 重复实现
 ↓
明确施工范围
 ↓
施工
 ↓
Commit
 ↓
Actions / Verify
 ↓
必要时继续修复
 ↓
更新必要文档
```

是否创建临时 Branch，由风险和是否需要独立审查决定，不机械创建。

## 4. 完成判断

必须区分：

- 已设计未实现
- 已实现
- 已构建
- 已验证
- 已发布
- 废弃

尤其：

**Commit ≠ 完成；Build Success ≠ 功能 Verify Success。**

Verify 必须能够追溯到对应 Commit / Run。

## 5. AI 协作特别规则

APS 曾经历过旧架构与新架构并存的问题，因此 AI 施工时必须主动检查：

- 实际启动入口；
- UI 是否真正连接到底层功能；
- 是否存在旧实现仍被调用；
- 是否存在重复业务链；
- Connection / API Profile / Context AI 权限边界；
- 权限与 ConstructionLock；
- Commit / Verify 是否真实发生。

旧 Decision AI、Worker AI、固定 AI A / AI B、旧 Space-centered 模型只可作为历史资料，不得重新作为当前施工依据。

## 6. 文档职责

| 文件 | 职责 |
|---|---|
| `AGENTS.md` | AI 协作总原则与架构禁区 |
| `PROJECT/` | 当前正式产品 / 架构 / UI / 规格 |
| `docs/STATUS.md` | 当前状态 + 阶段演进记录 |
| `docs/CURRENT-TASKS.md` | 当前待施工任务与验收顺序 |
| `docs/REPOSITORY-OPERATION-MANUAL.md` | 仓库实际操作规则 |
| `docs/REPOSITORY-BRANCH-MANAGEMENT.md` | 分支生命周期与清理 |
| 其他历史文档 | 追溯用途，不作为当前设计依据 |

## 7. 维护原则

本文件只负责告诉 AI / 人：

**去哪里看、谁负责什么、什么是当前基线。**

新的仓库操作经验不要继续堆到本文件；确认后应归入 `REPOSITORY-OPERATION-MANUAL.md`。

