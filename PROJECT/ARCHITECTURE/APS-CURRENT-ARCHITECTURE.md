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

## 二、Project

Project 是工作上下文容器，至少关联：

- Project Address；
- Default AI；
- Project Members；
- 项目主要对话；
- 当前任务 / 状态；
- 操作记录。

### Project Address

```
Project
├─ Local → Local Connector → Android 本地目录
└─ GitHub → GitHub Connector → GitHub API → Repository / Branch
```

两者并列，不要求统一成本地 Workspace。

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

一个 Project 可以有多个 AI Member。

当前只定义最小路径：

```
当前 AI
 ↓
请求 AI 协助
 ↓
Dispatcher
 ↓
目标 AI Connector / AI Member
 ↓
协助结果
 ↓
当前 AI
```

其余施工协作协议暂未定型，属于独立设计问题：

- 任务交接；
- 上下文共享；
- 结果回执；
- 施工权；
- 并行 / 串行；
- 冲突控制；
- 恢复。

不得从旧 Dispatcher 文档自动推导这些机制。

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
3. Project 是工作上下文；
4. 项目页是人类操作面板；
5. GitHub 远程仓库是 GitHub Project 的资源来源；
6. Local 保留，但按 Local Connector 演进；
7. 多 AI 协作先定义边界，再设计协议；
8. 代码事实优先于历史文档。
