# A-BridgeFS

A-BridgeFS 是面向 AI 协作与真实执行环境的基础项目。

当前阶段先验证最小闭环：

```
AI
 ↓
A-BridgeFS
 ↓
授权
 ↓
真实执行
 ↓
结果返回
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
- `docs/STATUS.md`：当前项目状态与阶段边界
- `docs/REQUIREMENTS.md`：当前需求板
- `docs/A-BRIDGEFS-STAGE-WORKLOG.md`：阶段施工与验证记录
- `docs/architecture/`：架构设计资料
- `docs/V0.1.1-PLAN.md`：早期 v0.1.1 范围计划，作为历史设计资料保留

当前第一阶段实验：

```
人
 ↓
A-BridgeFS
 ↓
Decision AI / Worker
 ↓
GitHub 项目
 ↓
Commit / Verify
 ↓
回执
 ↓
人
```

当前重点是验证 **Decision AI + Worker** 的连续协作，而不是提前建设完整 Agent 平台。
