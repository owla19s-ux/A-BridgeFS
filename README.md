# A-BridgeFS

A-BridgeFS 是面向 **AI 协作与真实执行环境** 的 Android 工作台。

当前阶段重点验证：

```
双 AI 协作
 ↓
Workspace
 ↓
授权 / 权限边界
 ↓
BridgeFS / GitHub 真实执行
 ↓
Commit / Verify
 ↓
Receipt
```

## 最新 APK

对外下载只认 GitHub Release 的固定地址：

**[下载最新版 A-BridgeFS.apk](https://github.com/owla19s-ux/A-BridgeFS/releases/latest/download/A-BridgeFS.apk)**

固定文件名：

```
A-BridgeFS.apk
```

不要从仓库源码目录直接下载 APK，也不要依赖带版本号的 APK 文件名。

## 构建与发布

```
main
 ↓
GitHub Actions
 ↓
checkout 当前 GITHUB_SHA
 ↓
SHA 校验
 ↓
Gradle 构建
 ↓
Artifact：A-BridgeFS-<commit SHA>
 ↓
latest Release
 ↓
A-BridgeFS.apk
```

源码是唯一真源。APK 不提交到 `main`。

每次 `main` 更新后，Actions 构建触发该次提交对应的 APK，并将实际构建文件统一发布为：

```
A-BridgeFS.apk
```

README 与后续项目主页统一使用：

```
releases/latest/download/A-BridgeFS.apk
```

Android `applicationId`、源码目录和 Release APK 文件名彼此独立。

## 项目资料

当前协作资料入口：

- `AGENTS.md`：施工、验证与 AI 协作规则
- `PROJECT/ARCHITECTURE/`：当前正式架构
- `PROJECT/SPEC/`：正式项目规格入口
- `PROJECT/UI/`：正式 UI 资料入口
- `PROJECT/STATUS/`：当前状态与审计快照
- `docs/STATUS.md`：当前实现状态
- `docs/REQUIREMENTS.md`：当前需求与验收
- `docs/A-BRIDGEFS-STAGE-WORKLOG.md`：阶段施工与验证记录
- `docs/architecture/`：当前专项架构与历史架构资料
- `docs/V0.1.1-PLAN.md`：早期版本计划，作为历史资料保留

## 当前协作模型

A-BridgeFS 当前采用**双 AI 协作模型**。

两个 AI：

- 可以共享工作区允许范围内的读取能力；
- 可以分别分析、沟通、检查结果；
- 不因 AI 身份天然获得修改权。

施工权限独立属于：

```
Workspace + Repository + Branch
```

同一 Repository / Branch 同时最多一个 AI 持有施工权；施工权可以授予、释放、转移。

旧的 Decision AI / Worker AI 仅作为历史任务阶段术语保留；当前正式架构不设固定决策 AI / 施工 AI 身份。两个 AI 的角色随任务阶段变化，施工权由 Workspace + Repository + Branch 的 ConstructionLock 动态授予、释放或转移。

## 当前工程闭环

普通 AI 对话首先具备 GitHub 读取能力：

```
普通对话
 ↓
API
 ↓
GitHub 授权 / Repository / Branch
 ↓
读取真实项目
 ↓
AI 分析 / 回答
```

需要实际修改时，再进入施工权限链：

```
用户 / AI 协作
 ↓
选择施工 AI
 ↓
ConstructionLock
 ↓
施工
 ↓
Commit
 ↓
Verify
 ↓
Receipt
```

**读取与修改是两条不同能力链。普通对话可以读 GitHub，但不因此获得修改权。**

当前重点不是提前建设完整 Agent 平台，而是把 **双 AI 协作、工作区、真实执行、GitHub 施工与验证** 逐步连接成可以实际使用和验证的闭环。


## 当前最重要的验证点

当前优先验证：**普通对话能否真正读取 GitHub 仓库**。

仅有 GitHub 配置页面显示“已连接”不算完成。必须让普通对话通过当前 API 实际读取 Repository / Branch / 文件，并根据真实内容回答。

本阶段暂不把 GitHub 修改 / Commit 与该读取问题混在一起。