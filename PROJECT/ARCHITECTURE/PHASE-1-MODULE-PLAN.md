# APS Phase 1 — 基础模块施工

## 目标

第一阶段不追求完整施工链，而是分别把基础模块做成可独立工作的边界：

1. Project：Project / Task / Address / Member / Store
2. Conversation：Conversation / Message / API Profile
3. Execution：Command / Permission / Receipt
4. GitHub：Project Address / Read / Write / Commit / Actions / Verify
5. UI：项目 / 对话 / 设置及其状态展示

模块完成后再进入 Integration，不在模块尚未稳定时强行串联。

## 当前原则

- 不重新引入 Decision AI / Worker AI。
- 不以 Build 成功作为模块完成条件。
- 不因为下一模块需要某个接口，就提前把下一模块的逻辑塞进当前模块。
- 一个文件尽量只承担一个明确的模型、服务或页面职责。
- 持久化状态必须有唯一归属，UI 不直接承担业务状态迁移。
- 旧字段允许作为迁移兼容，但新代码不得继续依赖旧命名。

## 本轮已开始

### Project 模块

已完成：
- 将 Project Task / Conversation / Receipt / AI Member 模型从 ProjectModels.kt 拆分到独立模型文件。
- Task 完成状态修改统一经过 ProjectTaskStateService，避免 UI 直接改变 Task 生命周期状态。
- 保留旧模型字段兼容层，暂不进行破坏性删除。

开发中：
- 收紧 Project / Task 状态不变量。
- 收口 Project Store 的持久化边界。
- 清理新代码对历史 Workspace 命名的依赖。

## 阶段完成标准

Project 模块至少满足：
- 新建 / 加载 / 保存 Project 正常。
- Project Address 可独立持久化。
- Task 状态修改有唯一业务入口。
- Conversation、Member、Task、Receipt 数据互不承担对方职责。
- 进程重启后 Project 状态可恢复。
- 不依赖完整 AI → Execution → GitHub 链路即可独立验证。

之后再进入 Conversation 模块.


## 本轮继续收口

- 新增 `ProjectConfigurationService`，统一 Project 名称、Project Address、Default API 的业务修改入口。
- `ProjectPage` 不再直接承担上述配置状态的持久化修改。
- `ProjectTaskStateService` 已移除 ConstructionLock 操作，避免 Project 状态层反向依赖 Execution/Integration。
- `ProjectContinuationCoordinator` 暂留 Integration 实验区，不在 Project 模块继续扩展。
