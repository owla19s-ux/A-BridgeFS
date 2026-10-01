# A-BridgeFS 当前需求板

更新时间：2026-10-02

## A. 已确认

### 一级页面

底部一级导航固定为：

1. 工作区
2. 对话
3. 配置

### 工作区

工作区是 AI 协作资源集合，不是普通设置页。

必须至少包含：

- API 模块
- GitHub 模块
- 工作区操作

### GitHub

GitHub 必须是真实模块。

至少包含：

- 连接状态
- GitHub 账号
- Repository
- Branch
- 读取权限
- 修改权限
- GitHub 工作区入口

### 配置

配置保留分类：

- AI 与 API
- 连接与访问
- 执行与权限
- 指令
- 文件与目录
- 通知
- 外观
- 系统
- 日志与诊断

## B. 已实现

- Android 原生工程
- applicationId `com.abridgefs.app`
- target SDK 35
- API 全局访问开关
- GitHub 全局访问开关
- BridgeApiClient
- BridgeProjectStore
- 三页 UI 粗骨架
- 多 API 配置列表（添加 / 修改 / 移除）
- Chat → API 绑定持久化
- API 输入框文字颜色与 Hint 颜色显式设置
- GitHub PAT / Android Keystore / Repository / Branch 基础能力

## C. 当前开发

- Workspace API 成员 / 权限模型
- Chat 列表与 Chat 独立数据模型
- 对话 → CommandParser → PermissionPolicy → CommandExecutor → Receipt 主链
- GitHub Repository / Branch 实时读取与工作区绑定
- GitHub 工作区文件 / Commit / Issue / PR / Actions / Release 实际能力
- GitHub 本地权限与 GitHub 实际 Token 权限的双层判断
- API Key 统一安全存储

## D. 暂不做

- 多 GitHub 账号复杂管理
- 完整 Git 客户端
- 全量 GitHub API
- 复杂组织管理
- 完整 Agent 调度
- 多 Worker 调度器

## E. 验证要求

任何 GitHub 功能完成后必须至少验证：

代码 Commit
→ Actions
→ APK
→ 安装
→ GitHub 授权
→ 账号读取
→ Repository 列表
→ Branch 列表
→ 工作区绑定
→ 权限边界

未完成真实验证，不标记为“已验证”。

## F. 2026-10-02 对话可用性修复批次

本批次已施工：

- 每个 `BridgeProject` 保存当前 `apiId`，使当前对话能够持久绑定 API。
- 对话页 API 选择会写回当前对话，而不是只修改页面临时变量。
- 新建对话继承当前可用 API。
- 移除 API 时清理受影响对话的绑定。
- 对话输入框、API 编辑输入框显式设置文字与 Hint 颜色，避免主题继承导致文字不可见。

状态：**已实现，待 Actions / APK / 真机验证。**
