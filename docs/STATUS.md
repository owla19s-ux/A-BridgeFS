# A-BridgeFS 当前状态

更新时间：2026-10-04

## 项目定位

A-BridgeFS 当前以 **Project（项目）** 为核心对象。

Project 负责项目地址、AI 成员、默认 AI、项目对话以及按需 AI 协助。独立「对话」是额外的普通 AI 工具，不承担 Project 工作流。

## 当前正式模型

Project：

- 基本信息
- Project Address
- Default AI
- Project Members
- Project Conversation
- Request AI Assistance

Project Address 当前包括：

- Local
- GitHub
- future other storage

API Profile 是 API 连接资源；Project Member 是项目中的 AI 成员关系；Default AI 是当前 Project 默认 Member。

固定 Decision AI / Worker AI、固定 AI A / AI B 已废弃，不得作为新设计依据。

## Project UI

当前正式 UI 已确认：

项目页面以**项目主要对话**为主体。

结构：

项目主要对话 → ↑ / ↓ 屏幕显示切换 → 项目配置

规则：

1. ↑ / ↓ 只改变屏幕显示区域；
2. ↑ / ↓ 不控制项目配置展开 / 收起；
3. 项目配置独立点击展开 / 收起；
4. 配置收起后，为主要对话区域留下更多空间；
5. 不把 GitHub、API、权限、任务、Verify 等内部对象全部平铺为首页卡片。

底部一级导航：

- 项目
- 对话
- 配置

GitHub 不作为一级导航。

## 当前代码状态

### 已实现 / 已确认

- Project 数据模型已建立；
- Project Member 模型已建立；
- Default Member 字段已加入 Project；
- 新 Project 不再创建固定 AI A / AI B；
- Project Conversation 已作为 Project 数据的一部分；
- 独立「对话」与 Project 已分离；
- 旧固定 Decision / Worker 协作入口及部分旧 Collaboration 运行链已拆除；
- ConstructionLock 已迁移为 Project + Repository + Branch 语义；
- 正式构建统一使用正式签名身份。

### 开发中

- Project UI 按最新设计施工；
- Project 配置管理；
- Project Address 统一；
- Project Member / API Profile UI；
- Request AI Assistance；
- Project Conversation 与完整项目工作链；
- 连续施工 → Commit → Verify → 必要时继续；
- 独立「对话」UI 优化；
- 旧 Workspace 命名和历史 UI 实现清理。

### 已设计未实现

- 更完整的 Project Address 类型扩展；
- 多项目 / 多 API Secretary / Orchestrator；
- 更复杂的项目调度；
- 后续 Remote 能力。

## 当前明确不做

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 普通项目问题自动多 AI；
- 复杂 Project Dashboard；
- 无限 Agent 循环；
- 把 GitHub 做成独立于 Project 的第二套工作区模型。

## 验收原则

功能状态必须按：

设计 → 代码 → UI 入口 → 用户操作 → 状态保存 / 恢复 → 真实行为 → APK → 真机验证

逐级确认。

代码存在不等于功能完成；UI 有入口也不等于底层链路已经接通。

## 构建 / 签名

当前尚无公开发行版本，内部验证构建统一使用正式签名身份。

正式构建链以 main → GitHub Actions → Release 签名 APK → Verify 为准。

最近一次已知成功正式构建：

- Android Build and Release #319
- Run ID：37180243740
- Commit：8f657d7442f7d094a0cc8f807ec94914041b64ee

之后的直接 main 提交若没有对应 Actions Run，不标记为已构建 / 已验证。

## 文档事实优先级

代码事实、GitHub Commit、Actions / Verify、APK / 真机结果高于旧历史文档。

旧 Workspace / 双 AI / Decision / Worker 文档只能作为历史记录，不得继续指导新功能施工。
