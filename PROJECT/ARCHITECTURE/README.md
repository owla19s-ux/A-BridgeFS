# Architecture

正式架构资料入口。

当前正式架构主文件：

- `PROJECT/ARCHITECTURE/APS-CURRENT-ARCHITECTURE.md`

当前架构语义基线：

- **Space**：承载连接与资源的基础工作底座；
- **Address**：定位信息；
- **Resource**：实际内容 / 资源；
- **Context**：Space 内按场景组合能力的具体工作上下文；
- **Connection**：统一外部能力入口；
- **Connector**：具体 Connection 的实现边界；
- **Task**：按需建立的工作交接单位；
- **Dispatcher**：调度能力；
- **Receipt / Evidence**：实际结果及确认依据。
- **软件开发结构**：软件开发 Context 中用于连接目标、UI / 交互、功能、架构、施工点、施工卡、代码 / 产物和验证的可组合结构能力。

**固定的是定位与实际内容；能力是可组合的。**

当前正式 UI 定义位于 `PROJECT/UI/README.md`。其他正式架构文档应与其保持一致。

`docs/architecture/` 与其他明确标记为历史 / 过渡的架构文件，仅用于迁移和追溯，不得作为新功能设计依据。

PROJECT 描述当前应该是什么；实际代码是否已经实现，以源码与 Verify 为准。
