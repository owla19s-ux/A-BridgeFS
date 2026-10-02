# GitHub 模块架构

状态：正式架构候选，施工基线（2026-10-02）

## 1. 定位

A-BridgeFS 中的 GitHub 不是“Repository + Branch 两个配置字段”，而是一个真实的协作资源模块。

它负责把：

用户授权
→ GitHub 账号
→ 可访问 Repository
→ 当前 Branch
→ Repository 资源
→ Commit / Issue / PR / Actions / Release

连接成可操作的资源链。

## 2. 与工作区的关系

GitHub 账号授权与工作区绑定分离。

```
GitHub Authorization
        ↓
GitHub Account
        ↓
可访问 Repositories
        ↓
Workspace 选择 Repository
        ↓
Workspace 选择 Branch
        ↓
GitHub Workspace Resources
```

工作区保存的是“当前使用哪个 GitHub 资源”，而不是 GitHub 凭据本身。

## 3. 三层访问边界

### 全局访问

`AccessPolicy.isGithubEnabled`

关闭后，A-BridgeFS 不发起 GitHub API 请求。

### GitHub 身份授权

负责确认当前 App 代表哪个 GitHub 用户，以及令牌是否有效。

### 工作区权限

工作区进一步限制：

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
└─ GitHub 工作区
   ├─ 文件
   ├─ Commit
   ├─ Issue
   ├─ PR
   ├─ Actions
   └─ Release
```

没有真实授权时，不显示“已连接”的假状态。

## 5. 数据模型

工作区 GitHub 资源保存：

- `githubAccount`：当前绑定的 GitHub 登录名
- `githubRepositoryId`：GitHub Repository 稳定 ID
- `githubRepository`：Repository full name，格式为 `owner/name`
- `githubBranch`：当前 Branch
- `githubReadEnabled`
- `githubWriteEnabled`

授权凭据单独存储，不进入工作区普通 JSON。

历史数据如果只有 `githubAccountLogin` 而没有 `githubAccount`，加载时仍兼容。

## 6. Repository 选择

Repository 列表来自 GitHub API，而不是用户手工输入。

当前流程：

```
GitHubTokenStore
    ↓
GitHubApiClient
    ↓
GitHubWorkspaceService
    ↓
GitHubActivity
    ↓
Workspace.github
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

Branch 查询通过 `GitHubWorkspaceService` 执行，因此必须同时满足：

- GitHub 全局访问开启
- GitHub Token 已授权
- 当前工作区允许读取
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

## 9. GitHub 工作区不是 GitHub 全功能复制品

第一版只需要让 A-BridgeFS 能够：

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

## 11. 与 A-BridgeFS 协作闭环的关系

```
对话 / Decision AI
        ↓
A-BridgeFS
        ↓
GitHub 权限判断
        ↓
GitHub Workspace Service
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

## 12. 当前边界

本阶段：

- 做真实 GitHub 身份连接
- 做 Repository / Branch 读取
- 做工作区绑定
- 做读取 / 修改边界
- 建立 GitHub 工作区页面
- 统一 Repository / Branch 查询入口到 Workspace Service

暂不提前做：

- 多账号复杂管理
- 复杂组织管理
- GitHub 全部 API
- 完整 Git 客户端
- 自动调度器
