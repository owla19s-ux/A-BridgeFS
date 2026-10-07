# APS 当前架构基线

更新时间：2026-10-07

## 一、第一阶段架构

```
                    APS
                     │
              ┌──────┴──────┐
              │ Dispatcher  │
              └──────┬──────┘
                     │
       ┌─────────────┼─────────────┐
       ↓             ↓             ↓
 AI Connector   GitHub Connector  Local Connector
       │             │             │
       ↓             ↓             ↓
    AI API       GitHub API     本地目录
```

UI 位于能力之上：

```
                  APS UI
                    │
          ┌─────────┼─────────┐
          ↓         ↓         ↓
        项目页     对话页     配置页
```

项目页是人类查看和操作项目的主要面板，不是独立执行引擎。

## 二、对象、资源与 State

APS 的架构对象不限定具体业务类型。这里暂称为“对象”，具体产品名称后续单独确定。

一个对象可以关联多个 Resource：

```
对象
├─ Resource → Local
├─ Resource → GitHub
├─ Resource → Cloud / Other
└─ State
```

对象不等于某一个地址，也不要求资源复制到多个端。

### Resource

Resource 是对象实际涉及的外部资源。不同 Resource 通过对应 Connector 访问：

```
Local Resource → Local Connector
GitHub Resource → GitHub Connector
Other Resource → 对应 Connector
```

### State

State 是对象持续工作的权威状态，包含任务、进度、回执及必要的证据引用。

State 有明确的权威存储位置，但不要求与每个 Resource 各保存一份。

例如：

```
对象
├─ State → GitHub .aps/
├─ Resource → GitHub Repository
└─ Resource → Local 文档
```

### Task / Receipt

Task 是可分配的施工单元；Receipt 是 AI / 工具提交的结果及确认依据。

```
Task
 ↓
AI 施工
 ↓
Receipt / Evidence
 ↓
确认
 ↓
State 更新
```

## 三、Connector

### AI Connector

负责：

- API Profile；
- AI 请求；
- AI 响应；
- 不同模型 / Provider 的连接差异。

### GitHub Connector

负责：

- Repository；
- Branch；
- 文件；
- Commit；
- Pull Request；
- Actions / Verify 等 GitHub 能力。

远程 Repository 是 GitHub Project 的资源来源。

### Local Connector

负责：

- Android 本地目录；
- 文件读取；
- 文件修改；
- 本地项目操作。

当前本地操作实现较弱，后续需要独立重构；不得因为旧 BridgeFS 命名而重新建立 BridgeFS 产品模型。

## 四、Dispatcher

Dispatcher 只负责：

- 根据请求选择目标 AI / Connector；
- 传递必要的上下文；
- 接收结果；
- 在明确需要下一能力时再次分配。

第一阶段不要求 Dispatcher 实现完整 Agent Runtime。

## 五、多 AI 协作

多 AI 以 Task 作为交接单位，而不是要求共享完整 AI 长上下文。

基本方向：

```
Task
 ↓
分配 AI
 ↓
施工
 ↓
Receipt
 ↓
验收
 ↓
State 更新
 ↓
下一 AI
```

当前尚未确定：

- Task 树与依赖模型；
- 分配与施工权交接；
- Receipt / Evidence 的正式协议；
- 并行施工与 Resource 隔离；
- 冲突处理与 Merge；
- Dispatcher 需要保存的最小协作状态。

因此本阶段只确定对象 / Resource / State / Task / Receipt 边界，不实现完整多 Agent Runtime。

## 六、状态

第一阶段只保留完成 UI 展示、任务恢复和协作所真正需要的最小状态。

不要预先建立：

- Reducer / Event Bus 全套运行时；
- 无限 Action 队列；
- Sub-Agent 生命周期；
- 大型 Recovery Runtime。

## 七、架构原则

1. Connector 是外部能力边界；
2. Dispatcher 是轻量分配层；
3. 对象是工作上下文容器；
4. 项目页是人类操作面板；
5. GitHub Repository 是 GitHub Resource 的资源来源；
6. Local 保留，但按 Local Connector 演进；
7. 多 AI 协作先定义边界，再设计协议；
8. 代码事实优先于历史文档。


## 八、对象与资源模型

正式模型见 [APS-OBJECT-RESOURCE-MODEL.md](./APS-OBJECT-RESOURCE-MODEL.md)。本模型取代“一个 Project 只有一个 Project Address”的过度简化假设。一个对象可以关联多个 Resource，并单独指定 State 的权威存储位置。
