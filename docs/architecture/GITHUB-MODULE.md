# GitHub 模块架构

状态：专项架构施工基线（2026-10-02，当前语义已按 Project 模型修订）

> 本文是 GitHub 模块专项设计，不取代 `PROJECT/ARCHITECTURE/AI-COLLABORATION-V0.2.md` 的正式协作架构。

## 1. 定位

APS 中的 GitHub 不是“Repository + Branch 两个配置字段”，而是一个真实的协作资源模块。

它负责把：

用户授权
→ GitHub 账号
→ 可访问 Repository
→ 当前 Branch
→ Repository 资源
→ Commit / Issue / PR / Actions / Release

连接成可操作的资源链。

## 2. 与 Project 的关系

GitHub 账号授权与Project Address绑定分离。

```
GitHub Authorization
        ↓
GitHub Account
        ↓
可访问 Repositories
        ↓
Project Address 选择 Repository
        ↓
Project Address 选择 Branch
        ↓
GitHub Project Address Resources
```

Project Address 保存的是“当前 Project 使用哪个 GitHub 资源”，而不是 GitHub 凭据本身。

## 3. 访问边界

GitHub 访问分为“读取”和“修改”两条能力链，不能混为一谈。

### 普通对话读取

普通「对话」页也可以读取 GitHub。它不需要先进入 Project Address，也不需要 ConstructionLock。

前提是：
- GitHub 全局访问已开启；
- GitHub 身份授权有效；
- App 已有可用 GitHub Repository / Branch 资源配置；
- 当前 API 对话允许使用 GitHub 读取能力。

目标链路：

普通对话 → API → GitHub → Repository / Branch → 文件读取 → AI

### Project Address 读取

Project 工作读取当前 Project Address 绑定的 Repository / Branch。不同 Project Member 是否参与由当前 Project 工作关系决定，不预设固定 AI 数量。

### 修改

修改属于另一条链路：

Default AI / 获授权的 Project Member → Project Address / Repository / Branch → ConstructionLock → updateFile → Commit

普通对话读取成功，并不意味着普通对话拥有修改权限。

### 全局访问

### 全局访问

`AccessPolicy.isGithubEnabled`

关闭后，APS 不发起 GitHub API 请求。

### GitHub 身份授权

负责确认当前 App 代表哪个 GitHub 用户，以及令牌是否有效。

### Project 权限

Project 进一步限制：

- 读取
- 修改

默认设计：

- 读取：默认允许
- 修改：默认关闭

未来可继续细分操作权限。

## 4. GitHub 模块页面

进入 GitHub 模块后显示：

```
GitHub
├─ 连接状态
│  ├─ 已连接 / 未连接
│  └─ 当前账号
├─ Repository
│  ├─ 当前 Repository
│  └─ 切换 Repository
├─ Branch
│  ├─ 当前 Branch
│  └─ 切换 Branch
├─ 权限
│  ├─ 读取
│  └─ 修改
└─ Project Address / GitHub 资源
   ├─ 文件
   ├─ Commit
   ├─ Issue
   ├─ PR
   ├─ Actions
   └─ Release
```

没有真实授权时，不显示“已连接”的假状态。

## 5. 数据模型

Project Address 的 GitHub 资源保存：

- `githubAccount`：当前绑定的 GitHub 登录名
- `github.repositoryId`：GitHub Repository 稳定 ID
- `githubRepository`：Repository full name，格式为 `owner/name`
- `github.branch`：当前 Branch
- `github.readEnabled`
- `github.writeEnabled`

授权凭据单独存储，不进入Project Address普通 JSON。

历史数据如果只有 `githubAccountLogin` 而没有 `githubAccount`，加载时仍兼容。

## 6. Repository 选择

Repository 列表来自 GitHub API，而不是用户手工输入。

当前流程：

```
GitHubTokenStore
    ↓
GitHubApiClient
    ↓
ProjectGitHubService
    ↓
GitHubActivity
    ↓
Project Address / GitHub 配置
```

选择后保存：

- Repository ID
- Repository full name
- 默认 Branch
- 当前 GitHub 账号

Repository ID 用于稳定标识资源，full name 用于显示及 GitHub REST 路径。

## 7. Branch 选择

Branch 列表来自当前 Repository。

不能把输入框中的任意字符串直接视为已存在 Branch。

Branch 查询通过 `GitHubProject AddressService` 执行，因此必须同时满足：

- GitHub 全局访问开启
- GitHub Token 已授权
- 当前 Project Address 允许读取
- Repository 已配置

## 8. 能力映射

| 模块 | 基础能力 | 主要权限 |
| --- | --- | --- |
| Repository | 查询仓库、权限、默认分支 | Metadata read |
| Branch | 列出 Branch | Contents read |
| Files | 读取/修改文件 | Contents read/write |
| Commit | 查看/创建提交 | Contents read/write |
| Issue | 查看/创建/修改 Issue | Issues read/write |
| PR | 查看/创建/审查/合并 PR | Pull requests read/write + 对应内容权限 |
| Actions | 查看 Run / Job / Artifact；后续重跑 | Actions read/write |
| Release | 查看/发布 Release | Contents / 对应 Release 能力 |

GitHub 的实际权限以当前授权方式和 GitHub API 返回为准，App 内部权限不能越过 GitHub 授权边界。

## 9. GitHub Project Address 不是 GitHub 全功能复制品

第一版只需要让 APS 能够：

1. 真实连接账号
2. 读取 Repository
3. 选择 Repository
4. 读取 Branch
5. 读取文件
6. 为后续 Commit / PR / Actions 建立模块入口

Issue、PR、Actions、Release 可以先做真实读取入口，再逐步增加写操作。

## 10. 错误状态

GitHub 页面必须区分：

- 未开启全局访问
- 未授权
- 授权已失效
- Repository 不可访问
- Branch 不存在
- GitHub API 错误
- 网络错误
- 权限不足

不能把所有异常都显示成“未连接”。

## 11. 普通对话 GitHub 读取验收

第一阶段先验证读取，不把修改链混入本次问题。

必须能够完成：

1. 普通对话页选择 / 使用 API；
2. GitHub 全局访问与授权状态有效；
3. 普通对话能够定位当前 GitHub Repository / Branch；
4. AI 能读取至少一个真实文件；
5. AI 回答能够引用或分析真实仓库内容；
6. 关闭 GitHub 访问后，普通对话不能继续发起 GitHub API 请求，并显示真实错误状态。

只有以上链路实际可用，才认为“普通对话 GitHub 读取已打通”。

## 12. 与 A-BridgeFS 协作闭环的关系

```
对话 / 协作 AI
        ↓
APS
        ↓
GitHub 权限判断
        ↓
GitHub Project Address Service
        ↓
Repository / Branch
        ↓
文件 / Commit / PR / Actions
        ↓
真实结果
        ↓
回执
```

GitHub 是真实执行资源之一，不是 UI 装饰。

## 13. 当前边界

本阶段：

- 做真实 GitHub 身份连接
- 做 Repository / Branch 读取
- 做 Project Address 绑定
- 做读取 / 修改边界
- 建立 Project Address 的 GitHub 配置入口
- 统一 Repository / Branch 查询入口到 Project GitHub Service

暂不提前做：

- 多账号复杂管理
- 复杂组织管理
- GitHub 全部 API
- 完整 Git 客户端
- 自动调度器


## 14. 与当前 Project / AI 模型的关系

GitHub 模块不决定哪个 AI 承担决策、施工或检查角色；正式架构不设固定 Decision AI / Worker AI 身份。

当前正式模型为：

- Project Members 在授权范围内使用 Project Address 的读取能力；不预设固定 AI 数量或固定 Decision / Worker 角色。
- GitHub Repository / Branch 的施工权由 Project 权限与 ConstructionLock 管理。
- 同一 Repository / Branch 同时最多一个 AI 持有施工权。
- API Profile 与 GitHub Token 都是连接 / 授权资源，不直接等于 APS 施工权。
- GitHub Token 的真实权限仍是最终外部能力边界。

因此 GitHub 模块负责“能访问什么”，Project / ConstructionLock 负责“当前哪个获授权 AI 在当前阶段获得施工权”。施工权是权限状态，不是固定 AI 身份。
