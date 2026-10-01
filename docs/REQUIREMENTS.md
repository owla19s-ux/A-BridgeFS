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
- GitHub Repository / Branch 工作区字段（历史施工，不代表真实连接）

## C. 当前开发

- GitHub 真实授权
- GitHub API 客户端
- Repository / Branch 实时读取
- GitHub 工作区页面
- 工作区 GitHub 状态
- UI Insets / IME 正确处理
- GitHub 本地权限与 GitHub 实际权限的双层判断

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
