# APS 当前状态与工作记录

更新时间：2026-10-06

> 本文件同时承担“当前状态”和“阶段工作记录”职责，减少多个状态文档重复维护。
>
> 当前事实以代码、GitHub Commit、Actions / Verify、APK / 真机结果为准；历史工作记录只用于说明演进过程，不作为新功能设计依据。

## 一、项目定位

APS 当前以 **Project（项目）** 为核心对象。

Project 负责：

- Project Address
- Default AI
- Project Members
- Project Conversation
- Request AI Assistance

独立「对话」是额外的普通 AI 工具，不承担 Project 主工作流。

API Profile 是 API 连接资源；Project Member 是项目中的 AI 成员关系；Default AI 是当前 Project 默认 Member。

固定 Decision AI / Worker AI、固定 AI A / AI B 已废弃，不得作为新设计依据。

## 二、当前正式模型

```
Project
├─ Project Address
├─ Default AI
├─ Project Members
├─ Project Conversation
└─ Request AI Assistance
```

Project Address 当前包括：

- Local
- GitHub
- future other storage

正常 Project 工作：

```
用户
 ↓
Project
 ↓
Project Address
 ↓
Default AI
 ↓
读取 / 分析 / 工作
 ↓
完成
```

连续施工：

```
理解
 ↓
读取
 ↓
修改
 ↓
Commit
 ↓
Verify
 ↓
必要时继续修复
 ↓
完成
```

“继续”不是正常用户操作，只用于暂停、阻塞或需要用户决策的情况。

## 三、Project UI 当前状态

当前正式 UI：

```
项目
├─ 项目主要对话 ★
├─ ↑ / ↓ 屏幕显示切换
└─ 项目配置
    └─ 独立点击展开 / 收起
```

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

## 四、当前代码状态

### 已实现 / 已确认

- Project 数据模型已建立；
- Project Member 模型已建立；
- Default Member 字段已加入 Project；
- 新 Project 不再创建固定 AI A / AI B；
- Project Conversation 已作为 Project 数据的一部分；
- 独立「对话」与 Project 已分离；
- 旧固定 Decision / Worker 协作入口及旧 Collaboration 运行链已拆除；
- ConstructionLock 已迁移为 Project + Repository + Branch 语义；
- Project Members 已支持新增、编辑、删除、API Profile 绑定和 Default AI；
- Local Project Address 已具备目录选择、持久化与恢复；
- GitHub Project Address 已具备 Repository / Branch 配置入口；
- 正式构建统一使用正式签名身份。

### 当前开发中

- Project UI 按最新设计继续收口；
- Project 配置完整闭环；
- Project Address 统一；
- Project Conversation 完整工作链；
- 连续施工 → Commit → Verify → 必要时继续；
- Request AI Assistance；
- 旧 Workspace 命名和历史 UI 实现清理。

### 已设计未实现

- 更完整的 Project Address 类型扩展；
- 多项目 / 多 API Secretary / Orchestrator；
- 更复杂的项目调度；
- 后续 Remote 能力。

### 当前明确不做

- 固定 Decision AI / Worker AI；
- 固定 AI A / AI B；
- 普通 Project 问题自动多 AI；
- 复杂 Project Dashboard；
- 无限 Agent 循环；
- 把 GitHub 做成独立于 Project 的第二套工作区模型。

## 五、验收与构建状态

功能状态必须按：

```
设计
→ 代码
→ UI 入口
→ 用户操作
→ 状态保存 / 恢复
→ 真实行为
→ APK
→ 真机验证
```

逐级确认。

代码存在不等于功能完成；UI 有入口也不等于底层链路已经接通；Build Success 也不等于功能 Verify Success。

当前尚无公开发行版本，内部验证构建统一使用正式签名身份。

最近一次已知成功正式构建：

- Android Build and Release #319
- Run ID：37180243740
- Commit：8f657d7442f7d094a0cc8f807ec94914041b64ee

之后的直接 main 提交若没有对应 Actions Run，不标记为已构建 / 已验证。

每项 Verify 应记录：

- Type
- Number
- Run ID
- Commit
- Job（如适用）
- Result

## 六、阶段工作记录

### 2026-10-02 — 协作架构旧模型审查

当时识别出固定 Decision / Worker、AI A / AI B、Workspace-centered 模型及施工权、GitHub 写入、Verify 等问题。

这部分记录属于**历史演进过程**。后续正式架构已经发生改变，不再以当时模型作为当前设计依据。

当时的重要问题包括：

- AI 角色与固定身份混淆；
- 施工权限缺少 Repository / Branch 级边界；
- GitHub 写入与 Verify 未完全接通；
- Receipt / Log 记录分散；
- Workspace 与 Conversation 数据模型需要拆分。

### 2026-10-04 — Project-centered 模型收口

正式模型改为：

- Project 是核心对象；
- Project Address 是资源地址；
- Project Member 与 API Profile 分离；
- Default AI 是 Project Member；
- Request AI Assistance 是按需动作；
- 不再使用固定 Decision / Worker；
- 不再使用固定 AI A / AI B；
- ConstructionLock 保留为 Project 连续施工的权限基础。

### 2026-10-04 — Project UI 收口

确认：

- Project 主要对话成为项目工作面的主体；
- 项目配置独立展开 / 收起；
- 底部 ↑ / ↓ 只控制屏幕显示空间；
- ↑ / ↓ 不带动项目配置；
- 普通「对话」与 Project 独立；
- 普通「对话」和「配置」暂缓，不阻塞 Project 主线。

### 2026-10-04 — Project 配置与地址施工

已完成：

- Project Members 管理；
- Member → API Profile；
- Default AI；
- Local Project Address；
- GitHub Project Address；
- Project 状态持久化与恢复。

仍需正式 APK / 真机闭环验证。

### 2026-10-04 — 连续施工链施工

当前方向：

```
Project Conversation
 ↓
Default AI
 ↓
读取 / 修改
 ↓
ConstructionLock
 ↓
Commit
 ↓
Verify
 ↓
必要时继续
```

本地施工已经接入 PermissionPolicy → FileBridgeService → Receipt；不再使用 continuation Coordinator。

GitHub 施工已支持文件创建 / 编辑、Commit，并读取对应 Actions runs 作为 Verify 信息。

当前仍需注意：

- Actions 可能尚未启动时不能把“0 runs”直接视为最终 Verify；
- 多文件操作的 Commit / Verify 语义仍需继续审查；
- 连续施工的失败 / 重试边界仍需真机验证；
- 当前仍未完成正式 APK 与真机闭环验证。

## 七、文档事实优先级

当前判断优先级：

```
实际代码
 ↓
GitHub Commit
 ↓
Actions / Check Runs
 ↓
APK
 ↓
真机结果
 ↓
当前 PROJECT / docs 设计
 ↓
历史工作记录
```

历史 Workspace / 双 AI / Decision / Worker 文档可以保留用于追溯，但不得指导新功能施工。

## 八、维护规则

- 本文件负责“当前状态 + 阶段工作记录”；
- 不再另建重复的“当前状态”文档；
- 当前任务清单继续由 `docs/CURRENT-TASKS.md` 维护；
- UI 正式设计继续由 `PROJECT/UI/` 维护；
- 仓库操作规则由 `docs/REPOSITORY-OPERATION-MANUAL.md` 维护；
- 历史专项审计可以单独保留，但必须明确日期和“历史快照”性质。
