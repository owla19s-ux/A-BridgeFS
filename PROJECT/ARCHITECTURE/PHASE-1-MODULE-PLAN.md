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


## 当前收口进度

- `BridgeProjectTask` 已收窄为 Project Task 业务状态，不再保存 continuation / receipt runtime 字段。
- `BridgeProjectStore` 已不再持久化 Task 内部 continuation / receipt 字段；continuation 运行态保留在 Integration 的 `BridgeTaskExecutionState`。
- `ProjectTaskStateService.start()` 不再接收 continuation policy，避免 Project Task 生命周期反向承担 Integration 策略。
- `ProjectContinuationCoordinator` 仅负责 receipt → execution continuation，不再写入 Task 的 receipt/runtime 字段。
- 当前仍保留 `BridgeProject.taskState` 作为 Project 与 Integration 之间的临时边界；在 Integration 阶段再决定是否进一步外置，当前不继续拆。

## Project 收口补充

- `BridgeProjectStore.normalizeState()` 现在会将未知 Task status 归一为 `PENDING`，避免旧数据或异常数据进入未定义状态。
- Project Task 的业务状态与 continuation runtime 已保持分离；runtime 仍暂存在 Project 的 `taskState` 边界，Integration 阶段再决定是否完全外置。
- Project Task 生命周期入口已增加 start / pause / complete 的前置条件检查。


## Project 收口补充 2

- Task 状态归一化已保留 FAILED，避免执行失败在重新加载 Project 后被错误恢复为 PENDING。
- BridgeTaskExecutionState 与 Task 状态映射已明确：FAILED → Task FAILED；LIMIT_REACHED / WAITING → Task WAITING；COMPLETED 会完成 Task 并清空 runtime。
- Project Store 加载 continuation count 时会将负值归一为 0，旧数据缺失字段继续使用兼容默认值。
- Project 对话选择已增加独立的 ProjectConversationSelectionService；页面通过服务修改 active conversation。