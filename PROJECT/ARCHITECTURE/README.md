# Architecture

正式架构资料入口。

当前正式架构主文件：

- `PROJECT/ARCHITECTURE/APS-CURRENT-ARCHITECTURE.md`

当前专项规范：

- `PROJECT/ARCHITECTURE/THIRD-PARTY-MODULE-COMPATIBILITY.md`：第三方开源模块接入、Connector 契约、兼容性、安全与验证标准。
- `docs/THIRD-PARTY-MODULE-AUDIT.md`：基于当前源码和测试文件的 AI / GitHub / Local 接口兼容性盘点；静态审查结果，不代替实际测试。

当前架构语义基线：

- **Space**：工作空间 / 组织底座；
- **目录**：Space 内的分组管理结构；
- **Context**：实际项目 / 工作上下文；
- **Address**：Context 的项目地址记录；
- **Resource**：实际内容 / 资源；
- **Connection**：统一外部能力入口；
- **Connector**：具体 Connection 的实现边界；
- **Task**：按需建立的工作交接单位；
- **Dispatcher**：调度能力；
- **Receipt / Evidence**：实际结果及确认依据。
- **软件开发结构**：软件开发 Context 中用于连接目标、UI / 交互、功能、架构、施工点、施工卡、代码 / 产物和验证的可组合结构能力。

**Space 负责组织；Context 负责项目；Address 负责定位；Resource 负责实际内容；能力按 Context 组合。**

当前正式 UI 定义位于 `PROJECT/UI/README.md`。其他正式架构文档应与其保持一致。

`docs/architecture/` 与其他明确标记为历史 / 过渡的架构文件，仅用于迁移和追溯，不得作为新功能设计依据。

PROJECT 描述当前应该是什么；实际代码是否已经实现，以源码与 Verify 为准。
