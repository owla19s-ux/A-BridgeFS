# APS Phase 1 — 基础模块施工

## 目标

第一阶段不追求完整施工链，而是分别把基础模块做成可独立工作的边界：

1. Context：Context / Task / Connection / State / Store
2. Conversation：Conversation / Message / AI Connection / API Profile
3. Execution：Task / Permission / Receipt
4. Connection：GitHub / Local / File / Device 等 Connection 及其 Resource / Connector
5. UI：空间 / Context / 对话 / 设置及其状态展示

模块稳定后再进入 Integration，不在模块尚未验证时强行串联。

## 当前原则

- 不重新引入 Decision AI / Worker AI。
- 不以 Build 成功作为模块完成条件。
- 模块首先必须能进行云端验证，再进入更高层集成。
- 不因为下一模块需要某个接口，就提前把下一模块逻辑塞进当前模块。
- 一个文件尽量只承担一个明确的模型、服务或页面职责。
- 持久化状态必须有唯一归属，UI 不直接承担业务状态迁移。
- 旧字段允许作为迁移兼容，但新代码不得继续依赖旧命名。
- 当前保持单 `app` 模块；Gradle 多模块只有在规模、依赖、构建或复用等实际需求出现时才评估。

## 本轮已开始

### Context 模块

已完成：
- 将 Context Task / Conversation / Receipt / AI Connection 模型从 ProjectModels.kt 拆分到独立模型文件。
- Task 完成状态修改统一经过 ProjectTaskStateService，避免 UI 直接改变 Task 生命周期状态。
- 保留旧模型字段兼容层，暂不进行破坏性删除。

开发中：
- 收紧 Project / Task 状态不变量。
- 收口 Project Store 的持久化边界。
- 清理新代码对历史 Workspace 命名的依赖。

## 阶段完成标准

Context 模块至少满足：
- 新建 / 加载 / 保存 Context 正常。
- Context 的 Connection / Resource 关系可独立持久化。
- Task 状态修改有唯一业务入口。
- Conversation、AI Connection、Task、Receipt 数据互不承担对方职责。
- 进程重启后 Project 状态可恢复。
- 不依赖完整 AI → Execution → GitHub 链路即可独立验证。
- 对应模块测试通过。
- 跨模块约定有契约测试。
- 云端验证通过后，才进入更高层 Integration。

Build、APK、真机不作为模块完成的第一条件。

## 本轮继续收口

- ContextConfigurationService 统一 Context 配置修改入口（当前实现可暂用 ProjectConfigurationService 命名）。
- ContextConversationSelectionService 统一 Context Conversation 选择（当前实现可暂用 ProjectConversationSelectionService 命名）。
- ContextTaskStateService 统一 Task lifecycle（当前实现可暂用 ProjectTaskStateService 命名）。
- ConstructionLock 生命周期已绑定 Task，不再由旧 continuation 机制驱动。

## 当前收口判断

Context / Connection / Conversation / Execution / GitHub / UI 的职责边界已基本形成。

下一阶段重点是主链 Integration 与云端验证体系建设；正式 APK 和真机验收后置。发现明确硬问题才继续修改。
