# GitHub 仓库使用方法

本文记录 A-BridgeFS 项目在实际协作、施工、检查、验证和发布过程中形成的仓库使用方法。

> 适用范围：A-BridgeFS 以及采用相同 AI 协作方式的后续项目。
> 
> 核心原则：**代码是实现事实，main 是正式基线，Commit 是施工单位，PR 是审查入口，Verify 是完成条件，Release/Tag 是版本锚点。**

---

## 1. 仓库中什么东西代表什么

| 对象 | 作用 | 是否代表当前事实 |
|---|---|---|
| `main` | 正式代码基线 | 是 |
| Feature Branch | 某一项施工工作的临时工作区 | 仅在施工期间有效 |
| Commit | 一次明确的代码变更 | 是 |
| Pull Request | 对变更进行检查、讨论、合并的入口 | 是 |
| Issue | 需求、问题、待办、决策记录 | 是，但不是代码事实 |
| Actions / Check Run | 自动构建、测试、验证结果 | 是 |
| Release / Tag | 可追溯版本锚点 | 是 |
| `docs/` | 架构、需求、状态、施工记录 | 是设计/项目事实 |
| APK / Artifact | 某次构建产物 | 只代表对应 Commit 的构建结果 |

### 最重要的一条

**不要用分支名、PR 标题、APK 文件名判断“现在是什么版本”。**

判断当前代码：

1. 看 `main` 当前 SHA。
2. 看具体文件实际内容。
3. 看该 Commit 是否经过 Verify。
4. APK 则继续确认它对应的 Commit SHA。

---

## 2. 开始一个任务：先检查，再施工

不要直接修改。

推荐顺序：

```
需求
 ↓
读取相关 docs
 ↓
读取 main 当前代码
 ↓
确认实际入口 / 调用链
 ↓
检查是否存在旧实现
 ↓
确定施工范围
 ↓
开始修改
```

### 必看资料

通常先看：

- `AGENTS.md`
- `docs/STATUS.md`
- `docs/REQUIREMENTS.md`
- 与任务相关的 `docs/architecture/`
- 与任务相关的 UI / 工作记录
- 实际源码

**历史计划不能直接当成当前实现。**

例如：

- 文档写了“已经完成” ≠ 当前代码一定完成。
- 某个旧 Feature Branch 有实现 ≠ `main` 已经包含。
- PR 已经存在 ≠ PR 内容已经进入 `main`。
- APK 看起来像新版本 ≠ APK 一定来自当前 `main`。

---

## 3. 检查代码时，不只看单个文件

一个功能必须沿着真实调用链检查。

例如 UI：

```
AndroidManifest
 ↓
Launcher Activity
 ↓
Activity / Screen
 ↓
Page 状态
 ↓
UI 组件
 ↓
点击事件
 ↓
业务逻辑
 ↓
数据 / API / Service
```

因此发现“代码里已经写了”以后，还必须确认：

- 有没有真正被入口调用？
- 有没有另一套旧 UI？
- 有没有旧 Activity 仍然作为入口？
- 有没有同名或重复功能？
- 新实现是否只写了一半？
- UI 显示的状态是否来自真实数据？
- 点击后是否真的进入新的业务链路？

### A-BridgeFS 当前尤其需要注意

本项目曾出现过“新旧实现同时存在”的情况。

因此：

> **源码存在 ≠ 用户实际看到。**

必须检查实际运行路径。

---

## 4. Git 分支怎么用

推荐：

```
main
 ├── feature/task-a
 ├── feature/task-b
 └── fix/task-c
```

### main

`main` 是唯一正式基线。

原则：

- 不把长期开发工作堆在多个“正式分支”里。
- 不把旧版本分支当作当前版本。
- 不根据分支名称判断新旧。

### Feature Branch

一个分支尽量只承担一个明确任务。

例如：

```
feature/workspace-api-selector
fix/chat-message-width
fix/github-token-storage
```

完成后：

```
施工
 ↓
Commit
 ↓
Verify
 ↓
PR
 ↓
检查
 ↓
Merge
 ↓
main
```

如果已经合并并确认不再需要，历史分支可以删除。

但在项目正在整理阶段：

**不要为了“看起来干净”提前删除历史分支。**

---

## 5. Commit 怎么用

Commit 是最重要的施工单位。

一个 Commit 最好回答：

> “这一次到底改变了什么？”

例如：

```
Fix new-chat API selector initialization
```

比：

```
update
```

更有价值。

### 每次施工后记录

至少知道：

- Commit SHA
- Commit 内容
- 修改了哪些文件
- 是否触发 Verify
- Verify 最终结果

### 不要只看 Commit Status

GitHub 上可能出现：

```
Commit Status = 空
```

这**不能直接解释成“没有构建”或“没有 Verify”**。

应该继续检查：

- Check Runs
- Workflow Runs
- Job
- Job Steps
- Job Logs

只有查到真实结果，才能说：

- 已验证
- 构建失败
- 已取消
- 跳过
- 正在运行

---

## 6. Pull Request 怎么用

PR 是“代码进入 main 前的审查入口”。

正常流程：

```
Feature Branch
 ↓
Commit
 ↓
Verify
 ↓
Pull Request
 ↓
Review
 ↓
修正
 ↓
Verify
 ↓
Merge
```

### PR 不等于已经完成

PR 可能处于：

- Draft
- Open
- Changes requested
- Approved
- Mergeable
- Dirty
- Merged
- Closed

特别注意：

**存在 PR ≠ 内容已经进入 main。**

判断是否进入正式代码，只看：

```
main 当前 commit
```

---

## 7. 遇到多个 PR / 分支怎么办

不要直接选择“看起来最新”的那个。

应该做：

```
main
 ↓
比较各 Branch / PR
 ↓
查看 Commit
 ↓
查看 changed files
 ↓
判断哪些内容已经进入 main
 ↓
识别重复 / 冲突 / 历史实现
```

重点检查：

- ahead / behind
- changed files
- additions / deletions
- 是否已经 merge
- 是否与 main 重复
- 是否包含旧实现
- 是否存在 dirty merge

### 当前项目的经验

历史 PR 可能已经有大部分内容被后续 Commit 吸收。

所以：

> **不要因为某个 PR 标题写着“0.2.1 UI”就认为它仍然代表当前 UI。**

---

## 8. Verify：真正的完成条件

推荐把“完成”拆成：

```
代码施工完成
≠
构建完成
≠
验证完成
```

完整链路：

```
Commit
 ↓
Workflow Run
 ↓
Job
 ↓
Steps
 ↓
Build / Test
 ↓
Artifact
 ↓
实际安装测试
```

### 状态建议

| 状态 | 含义 |
|---|---|
| 开发中 | 正在施工 |
| 已设计未实现 | 文档有设计，代码没有 |
| 已实现 | 代码已经完成 |
| 已构建 | CI / 本地成功生成产物 |
| 已验证 | 关键行为经过实际验证 |
| 已发布 | Release 已产生 |
| 废弃 | 不再作为当前方案 |

不要把：

> “代码看起来没问题”

写成：

> “已验证”。

---

## 9. APK / Release 怎么判断

A-BridgeFS 的正式 APK 不应该通过“某个文件名看起来像新版本”来判断。

正确链路：

```
main Commit
 ↓
GitHub Actions
 ↓
Gradle Build
 ↓
Artifact
 ↓
Release
 ↓
A-BridgeFS.apk
```

当前项目对外下载固定使用：

```
releases/latest/download/A-BridgeFS.apk
```

但测试时还要确认：

> **这个 APK 实际来自哪个 Commit？**

### 当前经验

如果用户安装后发现：

> “一部分是新的，一部分是旧的”

第一反应不要直接归因于“下载了旧 APK”。

应分别检查：

1. APK 对应 Commit。
2. Commit 是否是当前 main。
3. 当前 main 是否本身存在新旧实现混合。
4. 实际启动入口是什么。
5. 页面是否存在两套实现。
6. 新逻辑是否真正接入当前入口。

---

## 10. 文档怎么分

当前推荐：

```
AGENTS.md
 ↓
协作与施工规则

docs/STATUS.md
 ↓
现在做到哪里

docs/REQUIREMENTS.md
 ↓
现在要做什么

docs/architecture/
 ↓
为什么这样设计

docs/A-BRIDGEFS-STAGE-WORKLOG.md
 ↓
阶段施工记录

docs/V0.x-PLAN.md
 ↓
历史计划 / 阶段计划
```

### 一个重要原则

文档之间不要互相抢“事实来源”。

建议：

- **代码**：实际实现事实
- **STATUS**：当前项目状态
- **REQUIREMENTS**：当前需求
- **architecture**：架构原则
- **WORKLOG**：施工过程
- **PLAN**：计划 / 历史设计
- **README**：项目入口和外部说明
- **本文件**：仓库使用与 AI 施工方法

---

## 11. AI 施工时的推荐工作流

### 阶段 A：理解

```
读取 AGENTS
 ↓
读取 STATUS / REQUIREMENTS
 ↓
检查 main
 ↓
找到实际入口
 ↓
找到相关功能
 ↓
找旧实现
```

### 阶段 B：设计

明确：

- 要改什么
- 不改什么
- 哪些属于现有功能
- 哪些属于新功能
- 是否需要改变架构
- 是否需要产品 / 架构决策

### 阶段 C：施工

```
创建 feature branch
 ↓
小批次修改
 ↓
自检
 ↓
Commit
```

不要一次把大量互不相关的改动混进一个 Commit。

### 阶段 D：验证

```
检查 Commit SHA
 ↓
检查 Workflow
 ↓
检查 Job
 ↓
检查日志 / Artifact
 ↓
必要时实际安装 APK
```

### 阶段 E：回收

```
确认 main
 ↓
确认 PR / Merge
 ↓
确认文档
 ↓
确认 Release
 ↓
清理已完成分支
```

---

## 12. AI 应该主动检查什么

AI 不应该只执行用户说出的最后一句。

每次施工前后，应主动检查：

### 代码

- 实际入口
- 调用链
- 重复实现
- 旧实现
- 未接入的新实现
- 空壳 UI
- 假状态 / 假数据
- 权限边界
- 错误处理

### Git

- 当前 main
- 当前 Commit
- Branch 是否落后
- PR 是否已合并
- 是否存在冲突
- 是否有重复提交

### CI

- Workflow 是否触发
- Run 是否对应当前 Commit
- Job 是否成功
- 是否有失败步骤
- Artifact 是否产生

### 产品

- 用户实际看到什么
- 点击之后发生什么
- 数据是否真的保存
- API 是否真的被使用
- Receipt 是否真的产生
- 日志是否真的写入
- 权限是否真的生效

---

## 13. 最容易犯的错误

### 错误 1：把旧 Branch 当成最新版

修正：

**永远先看 main。**

### 错误 2：看到 Commit 就认为功能完成

修正：

**Commit 只代表代码发生变化。**

### 错误 3：看到 CI 页面就认为 Verify 成功

修正：

**找到对应 Run / Job / Step 的真实结果。**

### 错误 4：看到文档写“完成”就不查代码

修正：

**代码优先验证实现事实。**

### 错误 5：看到新 UI 代码就认为用户会看到新 UI

修正：

**检查 Manifest → Activity → 页面入口 → 调用链。**

### 错误 6：为了整理仓库立即删除所有旧 Branch

修正：

**先确认 main 已经吸收需要的内容，再清理。**

### 错误 7：把无关修改混进当前任务

修正：

**一个任务一个施工范围。**

---

## 14. A-BridgeFS 当前推荐的仓库操作口诀

```
先看 main
先读 docs
再查代码
找到真实入口
确认有没有旧实现

明确任务
建立分支
小批施工
Commit

检查 SHA
检查 Workflow
检查 Job
检查 Artifact
必要时装 APK

确认结果
更新文档
进入 PR
合并 main

main 才是正式基线
```

---

## 15. 当前项目的特殊原则

A-BridgeFS 不只是普通 Android 项目。

它同时是：

1. Android 应用
2. AI 协作实验平台
3. GitHub 施工环境
4. BridgeFS 本地执行入口

因此需要同时维护两条链：

### 产品链

```
用户
 ↓
A-BridgeFS
 ↓
AI
 ↓
授权
 ↓
执行
 ↓
Receipt
```

### 工程链

```
需求
 ↓
AI 分析
 ↓
代码施工
 ↓
Commit
 ↓
Verify
 ↓
Review
 ↓
main
 ↓
Release
```

最终希望两条链可以闭合：

```
AI 协作 / 任务阶段角色
 ↓
持有施工权的 AI 施工
 ↓
GitHub Commit
 ↓
Verify
 ↓
A-BridgeFS 获取结果
 ↓
Receipt
 ↓
人
```

这也是 A-BridgeFS 后续 AI 协作能力最重要的基础。

---

## 16. 文档维护规则

本文件记录的是**已经从实际仓库协作中验证过的使用方法**。

以后如果发现新的仓库协作问题：

1. 先确认问题是真实存在的。
2. 找到造成问题的具体原因。
3. 总结成可复用的方法。
4. 更新本文件。
5. 不把一次性的偶然问题直接写成规则。

因此本文应随着项目实践逐步演化，而不是一次性写死。
