# A-BridgeFS 当前需求板

更新时间：2026-10-02

## A. 已确认

### 一级页面
底部一级导航固定为：工作区、对话、配置。

### 工作区
工作区是 AI 协作资源集合，至少包含：
- AI 成员
- API 模块
- GitHub 模块
- 权限与施工权
- 工作区操作

### 双 AI 协作
工作区支持两个协作 AI。

两 AI：
- 默认共享读取能力
- 可以同时读取同一 Repository
- 可以同时分析、沟通和检查结果
- 不固定谁是 Decision AI、谁是 Worker
- 施工角色随任务变化

施工权限：
- 属于 Workspace + Repository + Branch
- 同一 Repository / Branch 同时最多一个 AI 拥有修改权
- 修改权可转移
- API Profile 不直接拥有工作区施工权

### GitHub
GitHub 必须是真实模块，至少包含：
- 连接状态
- GitHub 账号
- Repository
- Branch
- 读取权限
- 修改权限 / 施工权
- GitHub 工作区入口

## B. 已实现
- Android 原生工程
- applicationId com.abridgefs.app
- target SDK 35
- API / GitHub 全局访问开关
- BridgeApiClient
- BridgeProjectStore
- 三页 UI 粗骨架
- 多 API 配置列表
- Chat → API 绑定持久化
- GitHub PAT / Android Keystore / Repository / Branch 基础能力

## C. 当前开发
- Workspace AI 成员 / 权限模型（基础模型已落地，待完整验证）
- Repository / Branch 施工锁（基础链已落地，待 AI 自主施工接线）
- Chat 独立数据模型
- CommandParser → PermissionPolicy → CommandExecutor → Receipt 主链统一
- GitHub 实际文件修改 / Commit / Issue / PR / Actions / Release 能力
- GitHub 本地权限与 GitHub Token 实际权限的双层判断
- 双 AI 协作消息与连续任务循环
- 协作日志与 Receipt 时间线统一

## D. 暂不做
- 多于两个协作 AI
- 复杂 Agent 调度
- 完整 Git 客户端
- 全量 GitHub API
- 复杂组织管理
- 多层自动调度器
- 自动绕过用户授权

## E. 当前设计验收
1. 两个 AI 可以同时读取允许范围内的 Repository。
2. 两个 AI 可以互相发送协作消息。
3. 同一 Repository / Branch 同时最多一个 AI 可以修改。
4. 施工权可以转移。
5. 持有施工权的 AI 可以在授权范围内连续施工；当前第一阶段仍需完成 Worker 文件修改结果 → `updateFile()` 的协议接线。
6. 普通实现问题不要求用户逐次输入“继续”。
7. 真正需要产品、架构或权限决策时才暂停协作。
8. Verify 必须以真实外部工序结果为依据。
9. 未产生新的 Actions Run / APK / 真机证据前，不将本批次标记为“已验证”。

未完成真实验证，不标记为“已验证”。

## F. 状态定义
待讨论 / 候选 / 已确认 / 开发中 / 已实现 / 已验证 / 阻塞 / 废弃


## 2026-10-02 UI 结构修正：Workspace 协作对话 / 独立对话分离

正式区分两种 Conversation：

- **Workspace 协作对话**：属于 Workspace，用于双 AI 协作、任务、施工锁、Commit / Verify 等工作区业务。
- **独立对话**：属于全局对话页，只与用户和一个指定 API 直接交流，不继承 Workspace 的双 AI 协作状态。

因此：

- 底部「对话」页不得读取或写入当前 Workspace 的 activeConversation。
- 「对话」页不得因为 Workspace 的协作配置而自动进入双 AI 协作。
- Workspace 必须提供明确的「进入协作对话」入口。
- Workspace 内仍可新建 / 切换多个协作 Conversation。
- 独立对话拥有自己的 API 选择、消息历史、回执和本地文件修改权限。
