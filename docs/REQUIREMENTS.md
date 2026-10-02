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

### 功能完成标准
任何功能必须同时满足以下条件才视为真正完成：

1. **代码层**：实际业务逻辑已经实现。
2. **UI 层**：用户有明确入口，可以实际操作。
3. **状态层**：操作结果能够正确保存、恢复，并影响后续流程。
4. **验证层**：APK 真机实际操作成功。

因此：
- 代码存在 ≠ 功能完成。
- UI 有按钮 ≠ 功能已经接通。
- 数据模型存在 ≠ 用户可以使用。
- 保存成功 ≠ 业务链已经生效。
- 只有“代码 + UI + 状态 + 真机验证”闭环，才能标记为“已验证”。

每项功能开发时必须检查：
- 用户从哪里进入；
- 用户怎么操作；
- UI 调用哪个业务函数；
- 结果保存在哪里；
- 重新进入是否恢复；
- 后续业务链是否使用这个结果；
- APK 中是否可以实际验证。

## B. 已实现

以下“已实现”仅表示代码/基础能力存在，不代表用户功能已经通过真机验证。

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

## B1. 当前实际功能核对

### 已确认解决
- 消息宽度：当前正常。

### 当前仍未完成
- API 消息需要显示对应 API 名称。
- API 消息需要显示对应 API 头像。
- Workspace 需要在 App 中成为用户可直接进入和管理的独立工作区。
- Workspace 内需要支持独立新建 / 切换 Conversation。
- Workspace / Conversation 的代码拆分必须最终反映到实际 UI 和操作路径。

## C. 当前开发
- Workspace AI 成员 / 权限模型
- Repository / Branch 施工锁
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
5. 持有施工权的 AI 可以在授权范围内连续施工。
6. 普通实现问题不要求用户逐次输入“继续”。
7. 真正需要产品、架构或权限决策时才暂停协作。
8. Verify 必须以真实外部工序结果为依据。
9. 每一项功能还必须满足“代码 → UI → 状态 → APK 真机验证”的闭环。

未完成真实验证，不标记为“已验证”。

## F. 状态定义
待讨论 / 候选 / 已确认 / 开发中 / 已实现 / 已验证 / 阻塞 / 废弃

## G. 功能施工规则

以后新增功能统一采用：

**需求 → Issue → 数据/代码 → UI → 状态恢复 → 实际业务链 → Commit → Actions → 真机验证 → 更新 STATUS / Issue**

如果代码功能已经存在但 UI 没打通，应明确标记为“代码已实现 / UI 未打通”，不能提前关闭任务。
