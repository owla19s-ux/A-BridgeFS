# UI / Code 总体审查记录

> 2026-10-04 第一轮总体代码与 UI 审查。

## 结论

A-BridgeFS 已具备较完整底层能力，当前主要问题已从“有没有功能”转为：**底层功能、协作运行时、UI 入口与用户可见状态尚未完全形成闭环。**

审查主线：

`UI → 业务状态 → Store / Service → 实际执行 → 状态回写 → UI`

## 当前 UI

主要新 UI 入口为 `V021Activity`，底部导航为：

- 工作区
- 对话
- 配置

工作区当前包含：当前工作区、GitHub、AI 协作、协作任务、工作区目录、本地文件权限、API 概览。

对话包含独立对话与工作区对话。

配置由多个 Activity / 页面承担，包括 API、GitHub、全局访问及其他设置。

### 结构风险

`V021Activity.kt` 体量已经很大，页面、交互、状态恢复、消息发送、执行回执、协作任务及多种配置入口集中在同一个 Activity 中。暂不因此立即重构，但这是后续出现“功能存在、UI 没接通或状态没回显”的高风险来源。

## 已发现问题

### 1. UI 与业务功能必须逐项核对

不能仅依据代码中存在某个 Service / Store 判断功能已经可用。每个 UI 功能都需要确认：

1. UI 是否有入口；
2. 入口是否调用正确业务方法；
3. 业务方法是否真正改变状态；
4. 状态是否持久化；
5. 状态是否能重新读取；
6. UI 是否能回显；
7. 是否存在错误、等待、成功状态；
8. 是否能够实际测试。

### 2. 普通对话 GitHub 读取链未打通

当前普通对话选择 API 后，主要进入 `BridgeApiClient` 的 API 对话链；GitHub 配置则主要挂在 Workspace / `GitHubWorkspaceService`。两条链目前没有形成“普通对话可读取 GitHub”的完整连接。

因此当前存在一个明确的功能断点：

普通对话 → API → X → GitHub Repository / 文件读取

这不是“工作区 GitHub 功能缺失”，而是**普通对话读取能力没有接入 GitHub 资源**。

第一阶段只补读取：
- 不增加 GitHub Agent；
- 不把普通对话变成施工模式；
- 不开放修改；
- 只让普通对话能够确认并读取真实 GitHub 仓库。

### 3. 协作运行时仍有旧模型残留

正式架构已经取消固定 Decision AI / Worker AI。

正式模型：

```text
AI A / AI B
  ↓
共享读取 / 分析 / 沟通
  ↓
任务 + 动态阶段角色
  ↓
当前需要施工的一方取得 ConstructionLock
  ↓
BUILDER：调查 → 修改 → 测试 → Commit
  ↓
WAITING_VERIFY
  ↓
GitHub Actions
  ↓
Verify
  ↓
释放 / 转移 ConstructionLock
  ↓
继续协作
```

代码中仍存在 `DECISION_AI / WORKER`、旧提示词及固定路由方法。这些只能作为迁移残留，不得作为新功能设计基础。

### 4. ConstructionLock / GitHub / Verify 底层方向正确

目前确认：

- ConstructionLock 按 Workspace + Repository + Branch 管理；
- holder 是 AI Member，而不是 API Profile；
- GitHub 写入要求 AI Member 持有施工权；
- Commit 后进入 WAITING_VERIFY；
- Verify 按精确 Commit SHA 查找正式 Android Build and Release workflow；
- Verify 成功后释放实际 ConstructionLock。

这些是当前应保留的底层基础，不因 UI 整理重新设计。

### 5. 工作区页面存在“配置集合化”倾向

当前工作区同时展示 GitHub、AI 协作、任务、目录、权限、API 等多个卡片，更像配置总表而不是工作台。

后续方向可考虑：

```text
工作区
├─ 当前工作状态
├─ AI 协作状态
├─ 当前任务
├─ 当前施工权
├─ Verify 状态
└─ 最近回执

配置
├─ AI / API
├─ GitHub
├─ 权限
└─ BridgeFS
```

本轮不直接改 UI。

### 6. Activity / 版本遗留需要继续核对

当前代码同时存在 `MainActivity`、`V021Activity`、`GitHubActivity`、`ApiSettingsActivity`、`GlobalAccessActivity`、`SettingsCategoryActivity`。目前新 UI 主体明显集中在 `V021Activity`，需要继续确认其他页面哪些仍为正式入口、哪些属于历史/过渡代码。

## 当前状态

| 范围 | 状态 |
|---|---|
| 底层 API / Store | 已实现 |
| GitHub Workspace | 已实现 |
| ConstructionLock | 已实现 |
| Commit / Verify | 已实现 |
| 工作区 UI | 开发中 |
| 对话 UI | 开发中 |
| 配置 UI | 开发中 |
| 普通对话 → GitHub 读取 | 阻塞 / 待施工 | 当前普通对话 API 链尚未接入 GitHub 读取 |
| UI ↔ 业务闭环 | 开发中，当前主要审查目标 |
| 旧 Decision / Worker 路由 | 迁移中，不得继续扩展 |
| UI 架构整理 | 已设计未施工 |

## 下一轮审查

不直接施工，按真实使用链路逐项检查：

- 工作区：创建/切换 → 持久化 → 当前状态 → GitHub → 目录 → 权限
- 对话：选择 AI/API → 输入 → 发送 → 请求 → 回复 → 持久化 → 回显
- 普通对话 GitHub：API → GitHub 授权 → Repository / Branch → 文件读取 → AI 回答
- 双 AI：两个 AI → 共享工作区 → 任务 → 动态角色 → ConstructionLock → 施工
- GitHub：文件变更意图 → FILE_CHANGE_REQUEST → 权限 → Lock → updateFile → Commit
- Verify：Commit → WAITING_VERIFY → Actions → 精确 SHA → PASS/FAIL → Lock 释放 → 状态回写
- 回执：执行结果 → Receipt → Conversation / Workspace → UI 回显 → 可复制
- 配置：API、GitHub、全局访问、权限、本地 BridgeFS 等逐项核对真实连接

## 审查原则

- 先查，不急着改；
- 不以“代码存在”作为“功能完成”的依据；
- 不重新设计已经正确的 ConstructionLock / Commit / Verify 基础；
- 不再使用固定 Decision AI / Worker AI 作为新功能模型；
- UI 必须与实际业务状态对应；
- 功能完成必须能从 UI 进入、执行、看到结果并实际测试。
