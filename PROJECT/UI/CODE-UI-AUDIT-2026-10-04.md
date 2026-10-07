# UI / Code 总体审查记录（2026-10-04 历史快照）

> 本文件记录 2026-10-04 的 UI / Code 审查结果。
> **不作为当前 UI 语义定义。当前 UI 语义以 `PROJECT/UI/README.md` 为准。**
> 本文件中的 Project / Workspace 表述属于当时审查快照，后续架构已调整为 Space / Context / Connection 模型。

> 2026-10-04 UI / Code 审查更新。

## 当前结论

当时的 UI 正在从旧的“工作区配置集合”收口到 Project 页面。该结论已被后续 Space / Context UI 定义取代。

当时审查时的 UI 设计：

- Project 是核心页面；
- Project Conversation 是主要内容；
- Project 配置独立展开 / 收起；
- 底部 ↑ / ↓ 只改变屏幕显示区域；
- ↑ / ↓ 不控制 Project 配置展开状态；
- 独立「对话」保持独立；
- 「配置」负责全局资源。

因此，旧审查中的“工作区配置集合化”不再是未来设计方向，而应视为待清理的旧 UI 实现。

## 当时 UI 结构

项目页面：

- 项目主要对话 ★
- ↑ / ↓ 屏幕显示切换
- 项目配置（独立展开 / 收起）

底部一级导航：

- 项目
- 对话
- 配置

GitHub 不作为一级页面。

## 当时 Project 页面审查原则

### 1. 对话优先

Project 页面首先解决：

“我现在在什么项目里，以及我能不能直接问这个项目。”

不要把 GitHub、API、权限、目录、任务、Verify 等内部状态全部做成首页卡片。

### 2. 显示切换与配置展开必须解耦

底部 ↑ / ↓：

- 只改变当前屏幕内容的显示区域；
- 不改变 Project 配置的展开状态。

Project 配置：

- 自己点击展开；
- 自己点击收起；
- 不受 ↑ / ↓ 联动。

### 3. 配置集合不等于主工作区

Project 配置属于项目设置，不等于 Project 的主要工作内容。

### 4. UI 不提前暴露内部复杂性

用户需要看到的是项目、对话、AI、必要的状态与操作。

内部的 Store、Service、ConstructionLock、Verify Run、Receipt、API Profile 关系，只有在确实需要用户理解或操作时才进入 UI。

## 当时代码核对重点

源码中仍存在历史 Workspace 命名和旧卡片式页面结构，包括：

- workspace selector
- workspace card
- workspace directory
- API summary
- 旧协作入口残留

这些不能继续作为 Project UI 的正式设计依据。

下一轮代码施工应以正式 Project UI 文档为准，而不是继续给旧 Workspace 页面增加功能。

## 当时独立「对话」

独立「对话」不需要重新设计成 Project 页面。

保持：

- API 选择
- 消息输入 / 回复
- 历史持久化
- 必要的读取 / 分析能力

主要问题属于 UI 接通、显示和可用性检查。

## 当时验收

UI 功能必须形成：

UI 入口 → 用户操作 → 业务状态 → 持久化 → 重新进入 / 恢复 → 真实功能 → UI 状态回显 → APK / 真机验证

“代码存在”或“UI 有按钮”都不能单独视为完成。

## 当时状态

| 范围 | 状态 |
|---|---|
| Project UI 设计 | 已确认 |
| Project UI 文档 | 已更新 |
| Project UI 代码 | 开发中 |
| 独立「对话」架构 | 已确认 |
| 独立「对话」UI | 开发中 |
| 配置 UI | 开发中 |
| 旧 Workspace UI 清理 | 待施工 |
| UI ↔ 业务闭环 | 开发中 |

## 2026-10-04 本轮代码核对（历史记录）

### 已确认的主要偏差

当前 `V021Activity` 仍以历史 Workspace 页面作为 Project 一级页面：

- `Page.WORKSPACE` / `Page.WORKSPACE_CHAT` 仍然存在；
- 底部一级入口仍显示“工作区”；
- Project 页面仍先展示 Workspace selector、GitHub、请求协助、工作目录、本地权限、API summary 等多张卡片；
- Project Conversation 仍是独立的第二级页面，而不是 Project 首页主要内容；
- ↑ / ↓ 显示切换尚未形成当前正式 UI；
- Project 配置独立展开 / 收起尚未形成当前正式 UI；
- Project Members / Default AI 数据模型已经存在，但当前 Project UI 尚未提供完整的成员管理闭环；
- `workspaceDirectory`、`active_workspace_id` 等历史命名仍大量存在，部分属于迁移兼容，不能机械删除；
- `BridgeProjectStore` 已能保存 / 恢复 Default Member、Members、Conversations 和 Project Address，但 UI 尚未完整接通这些状态。

### 当前判断

这不是继续补旧 Workspace 卡片的问题，而是 **T1 Project 主页面重构**。

施工顺序应为：

1. 先把 Project 首页与 Project Conversation 合并到同一页面结构；
2. 再把 Project 配置变成独立可展开区域；
3. 再把 Project Address / Default AI / Members 等已有模型接入配置区域；
4. 最后再接连续施工和 AI 协助。

普通「对话」与全局「配置」本轮暂缓，不作为 T1～T5 的阻塞项。

### 不应做的事

- 不恢复 Decision AI / Worker AI；
- 不恢复固定 AI A / B；
- 不把 Project 首页重新做成 Dashboard；
- 不把 GitHub 重新提升为一级页面；
- 不因为代码中存在 Workspace 命名就立即删除所有兼容字段；
- 不在 T1 阶段提前施工连续 Agent 循环。


## T1 施工进度更新（历史记录）

截至 Commit `6deb337`：

- Project 首页已成为主要项目对话入口；
- 旧 `WORKSPACE_CHAT` 页面枚举、路由与渲染函数已移除；
- Project 配置保持独立展开 / 收起；
- ↑ / ↓ 已接入 Project 页面滚动显示，不再控制配置展开；
- Project composer 固定在页面底部；
- Project UI 内部主要 Workspace 命名已改为 Project 语义；
- active project 持久化键已迁移为 `active_project_id`；
- Project Members 已从现有模型接入配置显示。

### 仍未完成

- Project Members 的新增 / 删除 / 修改 / Default AI 设置；
- Project Address 的完整编辑闭环；
- GitHub Address 的 Project 级配置闭环；
- T1 真机 UI 验证；
- 正式 APK / Actions Build 验证。

因此 T1 仍标记为 **开发中**，不能标记为已验证。


## T1 Project UI 重构 — 2026-10-04（历史记录）

已施工：
- Project 默认进入项目总览，项目默认收起。
- 点击项目进入独立 Project 工作面，返回后恢复项目总览。
- Project 工作面接入项目信息、项目设置入口、待处理占位、对话区、协助、输入区。
- 对话区可独立收起/展开。
- 底部右侧 ↑/↓ 已改为屏幕显示空间切换，不再滚动 Project 内容。
- Project 设置仍由自身入口控制，不与 ↑/↓ 联动。
- 保留 Decision / Worker AI 架构拆除后的 Project / Default AI 模型。

未完成 / 未验证：
- 待处理任务模型、勾选与 @任务 输入尚未接入；当前 UI 明确显示为空，不伪造任务状态。
- 项目设置中的删除项目、完整 API 绑定管理等仍需继续闭环。
- 真机 UI 验证与正式 Release 构建验证尚未完成。

当前状态：**开发中 / 未验证**。
